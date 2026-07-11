package io.github.etacassiopeia.rift.transport;

import io.github.etacassiopeia.rift.error.CommunicationError;
import io.github.etacassiopeia.rift.error.EngineError;
import io.github.etacassiopeia.rift.error.EngineUnavailable;
import io.github.etacassiopeia.rift.error.ImposterNotFound;
import io.github.etacassiopeia.rift.error.InvalidDefinition;
import io.github.etacassiopeia.rift.json.JsonArray;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link RiftTransport} over the JDK's built-in {@link HttpClient}, talking to a running rift
 * engine's admin HTTP API. One {@code HttpClient} per instance; zero external dependencies.
 *
 * <p>Every method maps engine responses to the {@code io.github.etacassiopeia.rift.error}
 * hierarchy: a connection failure (or {@link IOException} of any kind) becomes {@link
 * EngineUnavailable}; HTTP 400 becomes {@link InvalidDefinition}; HTTP 404 on a
 * {@code /imposters/{port}} path becomes {@link ImposterNotFound}; a 2xx response whose body
 * fails to parse becomes {@link CommunicationError}; any other non-2xx status becomes {@link
 * EngineError}. {@link #flowStateGet} is the one exception: a 404 there means "no such key" and
 * is reported as {@link Optional#empty()}, not an exception.
 */
public final class RemoteTransport implements RiftTransport {

    private final URI adminUri;
    private final String base;
    private final Optional<String> apiKey;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RemoteTransport(URI adminUri, Optional<String> apiKey, Duration requestTimeout) {
        this.adminUri = adminUri;
        String s = adminUri.toString();
        this.base = s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
        this.apiKey = apiKey;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(requestTimeout)
                .build();
    }

    @Override
    public URI adminUri() {
        return adminUri;
    }

    @Override
    public JsonValue createImposter(JsonValue def) {
        return executeJson("POST", "/imposters", def.toJson(), OptionalInt.empty());
    }

    @Override
    public JsonValue getImposter(int port) {
        return executeJson("GET", "/imposters/" + port, null, OptionalInt.of(port));
    }

    @Override
    public JsonValue getImposter(int port, boolean replayable, boolean removeProxies) {
        List<String> params = new ArrayList<>();
        if (replayable) {
            params.add("replayable=true");
        }
        if (removeProxies) {
            params.add("removeProxies=true");
        }
        String path = "/imposters/" + port;
        if (!params.isEmpty()) {
            path += "?" + String.join("&", params);
        }
        return executeJson("GET", path, null, OptionalInt.of(port));
    }

    @Override
    public void deleteImposter(int port) {
        executeVoid("DELETE", "/imposters/" + port, null, OptionalInt.of(port));
    }

    @Override
    public void deleteAll() {
        executeVoid("DELETE", "/imposters", null, OptionalInt.empty());
    }

    @Override
    public JsonValue listImposters(boolean replayable, boolean removeProxies) {
        List<String> params = new ArrayList<>();
        if (replayable) {
            params.add("replayable=true");
        }
        if (removeProxies) {
            params.add("removeProxies=true");
        }
        String path = params.isEmpty() ? "/imposters" : "/imposters?" + String.join("&", params);
        return executeJson("GET", path, null, OptionalInt.empty());
    }

    @Override
    public void replaceAllImposters(JsonValue doc) {
        executeVoid("PUT", "/imposters", doc.toJson(), OptionalInt.empty());
    }

    @Override
    public JsonValue applyConfig(JsonValue config) {
        return executeJson("POST", "/admin/reload", config.toJson(), OptionalInt.empty());
    }

    @Override
    public void addStub(int port, JsonValue stub) {
        // POST /imposters/{port}/stubs expects a {"stub":{...}} envelope; a bare stub is rejected (400).
        JsonValue body = JsonObject.builder().put("stub", stub).build();
        executeVoid("POST", "/imposters/" + port + "/stubs", body.toJson(), OptionalInt.of(port));
    }

    @Override
    public void replaceStubs(int port, JsonValue stubs) {
        // The admin API's PUT /imposters/{port}/stubs expects a {"stubs":[...]} envelope; a bare
        // array is rejected (400). Callers pass the stubs array; wrap it here.
        JsonValue body = JsonObject.builder().put("stubs", stubs).build();
        executeVoid("PUT", "/imposters/" + port + "/stubs", body.toJson(), OptionalInt.of(port));
    }

    @Override
    public void replaceStub(int port, StubAddress addr, JsonValue stub) {
        executeVoid("PUT", stubPath(port, addr), stub.toJson(), OptionalInt.of(port));
    }

    @Override
    public void deleteStub(int port, StubAddress addr) {
        executeVoid("DELETE", stubPath(port, addr), null, OptionalInt.of(port));
    }

    @Override
    public JsonValue getStub(int port, StubAddress addr) {
        return executeJson("GET", stubPath(port, addr), null, OptionalInt.of(port));
    }

    private static String stubPath(int port, StubAddress addr) {
        if (addr instanceof StubAddress.ByIndex idx) {
            return "/imposters/" + port + "/stubs/" + idx.index();
        }
        if (addr instanceof StubAddress.ById id) {
            return "/imposters/" + port + "/stubs/by-id/" + enc(id.id());
        }
        throw new IllegalStateException("unreachable: " + addr);
    }

    /**
     * Percent-encodes a caller-supplied URL segment (a flow id, scenario name, or flow-state key).
     * These are arbitrary strings; concatenated raw, a {@code /}, {@code ?}, or space would mis-route
     * the request or make {@link URI#create} throw a non-{@code RiftException}. {@link URLEncoder}
     * form-encodes, so {@code +} (its space encoding) is promoted to the path-safe {@code %20}.
     */
    private static String enc(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public JsonValue recorded(int port) {
        return executeJson("GET", "/imposters/" + port + "/savedRequests", null, OptionalInt.of(port));
    }

    @Override
    public void clearRecorded(int port) {
        executeVoid("DELETE", "/imposters/" + port + "/savedRequests", null, OptionalInt.of(port));
    }

    @Override
    public void clearProxyResponses(int port) {
        executeVoid("DELETE", "/imposters/" + port + "/savedProxyResponses", null, OptionalInt.of(port));
    }

    @Override
    public void enable(int port) {
        executeVoid("POST", "/imposters/" + port + "/enable", null, OptionalInt.of(port));
    }

    @Override
    public void disable(int port) {
        executeVoid("POST", "/imposters/" + port + "/disable", null, OptionalInt.of(port));
    }

    @Override
    public JsonValue scenarios(int port, Optional<String> flowId) {
        String path = "/imposters/" + port + "/scenarios" + flowId.map(f -> "?flowId=" + enc(f)).orElse("");
        return executeJson("GET", path, null, OptionalInt.of(port));
    }

    @Override
    public void setScenarioState(int port, String name, String state) {
        String body = JsonObject.builder().put("state", new JsonString(state)).build().toJson();
        executeVoid("PUT", "/imposters/" + port + "/scenarios/" + enc(name) + "/state", body, OptionalInt.of(port));
    }

    @Override
    public void resetScenarios(int port) {
        executeVoid("POST", "/imposters/" + port + "/scenarios/reset", null, OptionalInt.of(port));
    }

    @Override
    public Optional<JsonValue> flowStateGet(int port, String flowId, String key) {
        String path = "/admin/imposters/" + port + "/flow-state/" + enc(flowId) + "/" + enc(key);
        HttpResponse<String> response = send("GET", path, null);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (isSuccess(response.statusCode())) {
            return Optional.of(parseJsonBody(response, "GET " + path));
        }
        throw mapError(response, OptionalInt.of(port));
    }

    @Override
    public void flowStatePut(int port, String flowId, String key, JsonValue value) {
        String path = "/admin/imposters/" + port + "/flow-state/" + enc(flowId) + "/" + enc(key);
        String body = JsonObject.builder().put("value", value).build().toJson();
        executeVoid("PUT", path, body, OptionalInt.of(port));
    }

    @Override
    public void flowStateDelete(int port, String flowId, String key) {
        String path = "/admin/imposters/" + port + "/flow-state/" + enc(flowId) + "/" + enc(key);
        executeVoid("DELETE", path, null, OptionalInt.of(port));
    }

    @Override
    public void spaceAddStub(int port, String flowId, JsonValue stub) {
        executeVoid("POST", "/imposters/" + port + "/spaces/" + enc(flowId) + "/stubs", stub.toJson(), OptionalInt.of(port));
    }

    @Override
    public JsonValue spaceListStubs(int port, String flowId) {
        return executeJson("GET", "/imposters/" + port + "/spaces/" + enc(flowId) + "/stubs", null, OptionalInt.of(port));
    }

    @Override
    public JsonValue spaceRecorded(int port, String flowId) {
        return executeJson("GET", "/imposters/" + port + "/spaces/" + enc(flowId) + "/recorded", null, OptionalInt.of(port));
    }

    @Override
    public void spaceDelete(int port, String flowId) {
        executeVoid("DELETE", "/imposters/" + port + "/spaces/" + enc(flowId), null, OptionalInt.of(port));
    }

    @Override
    public JsonValue buildInfo() {
        return executeJson("GET", "/config", null, OptionalInt.empty());
    }

    @Override
    public JsonValue verify(int port, JsonValue body) {
        return executeJson("POST", "/imposters/" + port + "/verify", body.toJson(), OptionalInt.of(port));
    }

    @Override
    public JsonValue stubWarnings(int port) {
        // The admin API has no dedicated warnings route; warnings ride along as _rift.warnings on
        // the imposter detail response, and are omitted entirely (both _rift and, within it,
        // warnings are skip_serializing_if-empty) when there are none. Distinguish a genuinely
        // absent field (⇒ empty) from a present-but-wrong-typed one (version skew / engine bug),
        // which must surface rather than masquerade as "no warnings".
        JsonValue imposter = getImposter(port);
        JsonValue rift = imposter instanceof JsonObject obj ? obj.get("_rift") : null;
        if (rift == null) {
            return JsonArray.of();
        }
        JsonValue warnings = rift instanceof JsonObject riftObj ? riftObj.get("warnings") : null;
        if (warnings == null && rift instanceof JsonObject) {
            return JsonArray.of();
        }
        if (warnings instanceof JsonArray arr) {
            return arr;
        }
        throw new CommunicationError(
                "port " + port + ": imposter response has a _rift.warnings of an unexpected shape: " + imposter.toJson());
    }

    // ------------------------------------------------------------------
    // Intercept (TLS-MITM)
    // ------------------------------------------------------------------
    //
    // UNCERTAIN ROUTE: the rift engine's admin API (crates/rift-http-proxy/src/admin_api/
    // handlers/intercept.rs + router.rs) only ever *manages* an intercept listener that was
    // already started at process launch via the `--intercept-port` CLI flag — there is no admin
    // route to start one remotely (route_request only dispatches to intercept::route when an
    // Option<Arc<InterceptState>> is already Some, and that Some only ever comes from the CLI
    // wiring in server.rs). "POST /intercept" below is therefore a best guess at what such a
    // route would look like if/when the engine grows one, mirroring the {interceptPort,
    // interceptUrl} shape rift_start_intercept already returns over FFI; against today's engine
    // it 404s, surfaced as an ordinary EngineError. The other four routes are confirmed directly
    // from intercept.rs's route() match: POST/GET/DELETE /intercept/rules, GET /intercept/ca.pem.

    @Override
    public JsonValue startIntercept(JsonValue options) {
        return executeJson("POST", "/intercept", options.toJson(), OptionalInt.empty());
    }

    @Override
    public void interceptAddRules(JsonValue rules) {
        executeVoid("POST", "/intercept/rules", rules.toJson(), OptionalInt.empty());
    }

    @Override
    public JsonValue interceptListRules() {
        return executeJson("GET", "/intercept/rules", null, OptionalInt.empty());
    }

    @Override
    public void interceptClearRules() {
        executeVoid("DELETE", "/intercept/rules", null, OptionalInt.empty());
    }

    @Override
    public String interceptCaPem() {
        // application/x-pem-file, not JSON — read the body as raw text rather than parsing it.
        HttpResponse<String> response = send("GET", "/intercept/ca.pem", null);
        if (isSuccess(response.statusCode())) {
            return response.body();
        }
        throw mapError(response, OptionalInt.empty());
    }

    @Override
    public void close() {
        // java.net.http.HttpClient has no close() at the Java 17 API level (added in 21); marking
        // the transport closed is all there is to release.
        closed.set(true);
    }

    // ------------------------------------------------------------------
    // Request execution + error mapping
    // ------------------------------------------------------------------

    private JsonValue executeJson(String method, String path, String body, OptionalInt port) {
        HttpResponse<String> response = send(method, path, body);
        if (isSuccess(response.statusCode())) {
            return parseJsonBody(response, method + " " + path);
        }
        throw mapError(response, port);
    }

    private void executeVoid(String method, String path, String body, OptionalInt port) {
        HttpResponse<String> response = send(method, path, body);
        if (!isSuccess(response.statusCode())) {
            throw mapError(response, port);
        }
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private static JsonValue parseJsonBody(HttpResponse<String> response, String context) {
        try {
            return JsonValue.parse(response.body());
        } catch (RuntimeException e) {
            throw new CommunicationError(
                    "rift admin API returned an unparseable response body for " + context, e);
        }
    }

    private static RuntimeException mapError(HttpResponse<String> response, OptionalInt port) {
        String message = extractErrorMessage(response.body());
        int status = response.statusCode();
        if (status == 400) {
            return new InvalidDefinition(message);
        }
        if (status == 404 && port.isPresent()) {
            return new ImposterNotFound(port.getAsInt(), message);
        }
        return new EngineError(status, message);
    }

    /** Extracts {@code errors[0].message} from a {@code {"errors":[{"code","message"}]}} error body, falling back to the raw body if it isn't that shape. */
    private static String extractErrorMessage(String body) {
        try {
            if (JsonValue.parse(body) instanceof JsonObject obj
                    && obj.get("errors") instanceof JsonArray errors
                    && !errors.items().isEmpty()
                    && errors.items().get(0) instanceof JsonObject first
                    && first.get("message") instanceof JsonString message) {
                return message.value();
            }
        } catch (RuntimeException ignored) {
            // Not the {"errors": [...]} shape (or not JSON at all) — fall through to the raw body.
        }
        return body;
    }

    private HttpResponse<String> send(String method, String path, String body) {
        if (closed.get()) {
            throw new IllegalStateException("this RiftTransport is closed");
        }
        URI uri = URI.create(base + path);
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .method(method, publisher);
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        apiKey.ifPresent(key -> builder.header("Authorization", key));
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EngineUnavailable("cannot reach the rift admin API at " + adminUri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineUnavailable("interrupted while calling the rift admin API at " + adminUri, e);
        }
    }
}
