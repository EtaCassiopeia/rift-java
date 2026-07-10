package io.github.etacassiopeia.rift.spring;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftTestContextTest {

    @Test
    void unknownImposterNameThrowsIllegalArgument() {
        RiftTestContext context = new RiftTestContext(null, Map.of(), Reset.NONE);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> context.imposter("missing"));
        assertTrue(ex.getMessage().contains("missing"), ex.getMessage());
    }
}
