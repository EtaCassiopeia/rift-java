package io.github.achirdlabs.rift.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.achirdlabs.rift.codec.RiftBodyCodec;
import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonBodyCodecTest {

    record Person(String name, Instant when) {}

    record Address(String city, int zip) {}

    record Company(String name, Address hq, List<String> tags) {}

    @Test
    void roundTripsPojoIncludingInstant() {
        RiftBodyCodec codec = new JacksonBodyCodec();
        Person p = new Person("Alice", Instant.parse("2020-01-01T00:00:00Z"));
        JsonValue json = codec.toJson(p);
        Person back = codec.fromJson(json, Person.class);
        assertEquals(p, back);
    }

    @Test
    void longNumberFidelityIsPreservedAsRawText() {
        RiftBodyCodec codec = new JacksonBodyCodec();
        JsonValue json = codec.toJson(Map.of("n", 9007199254740993L));
        JsonValue n = ((JsonObject) json).get("n");
        // 9007199254740993 is not representable as a double — must survive via raw-text, not float coercion
        assertEquals("9007199254740993", ((JsonNumber) n).raw());
    }

    @Test
    void roundTripsNestedAndArrayPojo() {
        RiftBodyCodec codec = new JacksonBodyCodec();
        Company c = new Company("Acme", new Address("NYC", 10001), List.of("a", "b"));
        assertEquals(c, codec.fromJson(codec.toJson(c), Company.class));
    }

    @Test
    void objectFieldOrderIsPreserved() {
        RiftBodyCodec codec = new JacksonBodyCodec();
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("z", 1);
        m.put("a", 2);
        m.put("m", 3);
        assertEquals("{\"z\":1,\"a\":2,\"m\":3}", codec.toJson(m).toJson());
    }

    @Test
    void fromJsonIntoIncompatibleTypeThrowsIllegalArgument() {
        RiftBodyCodec codec = new JacksonBodyCodec();
        JsonValue notAPerson = JsonValue.parse("\"just a string\"");
        assertThrows(IllegalArgumentException.class, () -> codec.fromJson(notAPerson, Person.class));
    }

    @Test
    void customObjectMapperConstructorIsUsed() {
        RiftBodyCodec codec = new JacksonBodyCodec(new ObjectMapper().findAndRegisterModules());
        Person p = new Person("Bob", Instant.parse("2021-06-01T00:00:00Z"));
        assertEquals(p, codec.fromJson(codec.toJson(p), Person.class));
    }

    @Test
    void serviceLoaderDiscoversJacksonCodec() {
        boolean found = ServiceLoader.load(RiftBodyCodec.class).stream()
                .anyMatch(provider -> provider.type().equals(JacksonBodyCodec.class));
        assertTrue(found, "JacksonBodyCodec must be registered via META-INF/services");
    }
}
