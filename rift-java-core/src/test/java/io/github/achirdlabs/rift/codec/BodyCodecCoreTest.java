package io.github.achirdlabs.rift.codec;

import io.github.achirdlabs.rift.RecordedRequest;
import io.github.achirdlabs.rift.dsl.RiftDsl;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;
import io.github.achirdlabs.rift.model.Predicate;
import io.github.achirdlabs.rift.model.PredicateOperation;
import io.github.achirdlabs.rift.model.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core-side codec SPI: POJO overloads fail loudly when no {@link RiftBodyCodec} is on the classpath
 * (none is, in the core module's own tests), and use an explicitly-registered codec when one is set.
 */
class BodyCodecCoreTest {

    record Pojo(String name) {}

    /** A stand-in codec so the core tests can exercise the explicit-registration path without Jackson. */
    static final class FakeCodec implements RiftBodyCodec {
        @Override
        public JsonValue toJson(Object value) {
            return JsonValue.parse("{\"fake\":true}");
        }

        @Override
        public <T> T fromJson(JsonValue json, Class<T> type) {
            return type.cast(new Pojo("decoded"));
        }
    }

    @AfterEach
    void resetCodec() {
        RiftDsl.useBodyCodec(null);
    }

    @Test
    void noCodecThrowsIllegalStateNamingTheArtifact() {
        RiftDsl.useBodyCodec(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> RiftDsl.okJson(new Pojo("x")));
        assertTrue(ex.getMessage().contains("rift-java-jackson"), ex.getMessage());
    }

    @Test
    void explicitCodecDrivesOkJsonObject() {
        RiftDsl.useBodyCodec(new FakeCodec());
        Response.Is is = (Response.Is) RiftDsl.okJson(new Pojo("x")).build();
        assertEquals(JsonValue.parse("{\"fake\":true}"), is.is().body().orElseThrow());
    }

    @Test
    void explicitCodecDrivesEqualToObject() {
        RiftDsl.useBodyCodec(new FakeCodec());
        Predicate p = RiftDsl.onRequest().withBody(RiftDsl.equalTo(new Pojo("x"))).build().predicates().get(0);
        PredicateOperation.Equals op = (PredicateOperation.Equals) p.operation();
        assertEquals(JsonValue.parse("{\"fake\":true}"), op.fields().get("body"));
    }

    @Test
    void recordedBodyAsUsesCodec() {
        RiftDsl.useBodyCodec(new FakeCodec());
        RecordedRequest r = new RecordedRequest("POST", "/x", Map.of(), Map.of(), "{\"a\":1}",
                Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), JsonObject.of());
        Pojo decoded = r.bodyAs(Pojo.class);
        assertEquals("decoded", decoded.name());
    }

    @Test
    void explicitCodecDrivesDeepEqualsObject() {
        RiftDsl.useBodyCodec(new FakeCodec());
        Predicate p = RiftDsl.onRequest().withBody(RiftDsl.deepEquals(new Pojo("x"))).build().predicates().get(0);
        PredicateOperation.DeepEquals op = (PredicateOperation.DeepEquals) p.operation();
        assertEquals(JsonValue.parse("{\"fake\":true}"), op.fields().get("body"));
    }

    @Test
    void explicitCodecDrivesWithBodyFromCodec() {
        RiftDsl.useBodyCodec(new FakeCodec());
        Response.Is is = (Response.Is) RiftDsl.ok().withBodyFromCodec(new Pojo("x")).build();
        assertEquals(JsonValue.parse("{\"fake\":true}"), is.is().body().orElseThrow());
    }

    @Test
    void useBodyCodecNullResetsToDiscovery() {
        RiftDsl.useBodyCodec(new FakeCodec());
        assertEquals(JsonValue.parse("{\"fake\":true}"),
                ((Response.Is) RiftDsl.okJson(new Pojo("x")).build()).is().body().orElseThrow());
        RiftDsl.useBodyCodec(null);
        // no codec on the core test classpath → falls back to (empty) discovery → ISE
        assertThrows(IllegalStateException.class, () -> RiftDsl.okJson(new Pojo("x")));
    }

    @Test
    void bodyAsWithoutCodecThrowsNamingTheArtifact() {
        RiftDsl.useBodyCodec(null);
        RecordedRequest r = new RecordedRequest("POST", "/x", Map.of(), Map.of(), "{\"a\":1}",
                Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), JsonObject.of());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> r.bodyAs(Pojo.class));
        assertTrue(ex.getMessage().contains("rift-java-jackson"), ex.getMessage());
    }
}
