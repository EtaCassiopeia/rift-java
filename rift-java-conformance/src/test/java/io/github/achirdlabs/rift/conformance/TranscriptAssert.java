package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.json.JsonValue;
import io.github.achirdlabs.rift.model.ImposterDefinition;
import io.github.achirdlabs.rift.model.Stub;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives a served imposter's {@code _verify} request/response transcripts over HTTP and asserts each
 * expectation, exactly as the engine's reference {@code rift-verify} replayer does: for every stub
 * carrying {@code _verify.sequence[]}, each request is issued in order and its response's
 * {@code status} / {@code bodyContains} are checked.
 */
final class TranscriptAssert {

    private static final java.util.Set<String> KNOWN_EXPECT_KEYS = java.util.Set.of("status", "bodyContains", "bodyEquals");


    private final HttpClient http;

    TranscriptAssert(HttpClient http) {
        this.http = http;
    }

    /** Replays every stub's {@code _verify} sequence against {@code http://<host>:<port>}. */
    void replay(ImposterDefinition imposter, String host, int port) {
        String base = "http://" + host + ":" + port;
        for (int s = 0; s < imposter.stubs().size(); s++) {
            Stub stub = imposter.stubs().get(s);
            Optional<JsonValue> verify = stub.verify();
            if (verify.isEmpty()) {
                continue;
            }
            JsonArray sequence = sequenceOf(verify.get());
            for (int i = 0; i < sequence.items().size(); i++) {
                JsonObject step = asObject(sequence.items().get(i));
                String where = "stub[" + s + "]._verify.sequence[" + i + "]";
                assertStep(base, step, where);
            }
        }
    }

    /** A minimal reachability check for a fixture without {@code _verify}: the imposter answers HTTP. */
    void smoke(String host, int port) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + "/"))
                .timeout(Duration.ofSeconds(10)).GET().build());
        assertTrue(response.statusCode() > 0, () -> "smoke GET returned no HTTP status from " + host + ":" + port);
    }

    private void assertStep(String base, JsonObject step, String where) {
        JsonObject request = asObject(step.get("request"));
        JsonObject expect = asObject(step.get("expect"));

        String path = string(request.get("path"), where + ".request.path");
        String method = request.has("method") ? string(request.get("method"), where + ".request.method") : "GET";

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20));
        if (request.get("headers") instanceof JsonObject headers) {
            headers.fields().forEach((name, value) -> builder.header(name, string(value, where + ".request.headers." + name)));
        }
        if (request.has("body")) {
            String body = string(request.get("body"), where + ".request.body");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        rejectUnknownExpectKeys(expect, where);
        HttpResponse<String> response = send(builder.build());

        if (expect.get("status") instanceof JsonNumber status) {
            assertEquals(Integer.parseInt(status.raw().trim()), response.statusCode(),
                    () -> where + ": unexpected status for " + method + " " + path);
        }
        if (expect.get("bodyContains") instanceof JsonString needle) {
            assertTrue(response.body().contains(needle.value()),
                    () -> where + ": response body did not contain '" + needle.value() + "'. Body was: " + response.body());
        }
        if (expect.get("bodyEquals") instanceof JsonString exact) {
            assertEquals(exact.value(), response.body(),
                    () -> where + ": response body did not equal the expected value for " + method + " " + path);
        }
    }

    /**
     * Fails on any {@code expect} key the driver does not assert. Silently ignoring an unhandled key
     * would let a fixture pass while a real expectation goes unchecked — so a new corpus form is a
     * loud "extend the driver" error, not a weakened gate.
     */
    private static void rejectUnknownExpectKeys(JsonObject expect, String where) {
        for (String key : expect.fields().keySet()) {
            if (!KNOWN_EXPECT_KEYS.contains(key)) {
                throw new IllegalStateException(where + ": unsupported _verify expect key '" + key
                        + "' — extend TranscriptAssert to assert it rather than silently skip it.");
            }
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AssertionError("request failed: " + request.method() + " " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted replaying " + request.uri(), e);
        }
    }

    private static JsonArray sequenceOf(JsonValue verify) {
        if (asObject(verify).get("sequence") instanceof JsonArray seq) {
            return seq;
        }
        throw new IllegalStateException("_verify has no sequence array: " + verify.toJson());
    }

    private static JsonObject asObject(JsonValue value) {
        if (value instanceof JsonObject obj) {
            return obj;
        }
        throw new IllegalStateException("expected a JSON object, got: " + (value == null ? "null" : value.toJson()));
    }

    private static String string(JsonValue value, String where) {
        if (value instanceof JsonString s) {
            return s.value();
        }
        throw new IllegalStateException(where + ": expected a JSON string, got: " + (value == null ? "null" : value.toJson()));
    }
}
