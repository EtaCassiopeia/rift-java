package io.github.achirdlabs.rift.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.achirdlabs.rift.RiftVersion;
import org.junit.jupiter.api.Test;

class JacksonWiringTest {

    @Test
    void jacksonIsOnTheClasspath() throws Exception {
        String json = new ObjectMapper().writeValueAsString(java.util.Map.of("k", "v"));
        assertEquals("{\"k\":\"v\"}", json);
    }

    @Test
    void coreIsOnTheClasspath() {
        assertFalse(RiftVersion.get().isBlank());
    }
}
