package io.github.etacassiopeia.rift.natives;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class NativesLayoutTest {

    @Test
    void reservedNativeResourceLayoutIsPackaged() {
        assertNotNull(
                NativesLayoutTest.class.getResource("/native/README.md"),
                "the native/ resource root must ship in the natives jar for the embedded resolver");
    }
}
