package io.github.achirdlabs.rift.junit5;

import io.github.achirdlabs.rift.ConnectOptions;
import io.github.achirdlabs.rift.EmbeddedOptions;
import io.github.achirdlabs.rift.SpawnOptions;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Tier-2 builder accepts transport-specific engine options and validates them against the
 * selected transport (issue #95). Engine construction is exercised end-to-end by the IT lanes; here
 * we pin the accept/reject matrix, which needs no engine.
 */
class RiftExtensionOptionsTest {

    private static EmbeddedOptions embedded() {
        return EmbeddedOptions.builder().build();
    }

    private static ConnectOptions connect() {
        return ConnectOptions.builder(URI.create("http://localhost:2525")).build();
    }

    private static SpawnOptions spawn() {
        return SpawnOptions.builder().build();
    }

    @Test
    void matchingOptionsBuild() {
        assertDoesNotThrow(() -> RiftTestExtension.newInstance()
                .transport(Transport.EMBEDDED).embeddedOptions(embedded()).build());
        assertDoesNotThrow(() -> RiftTestExtension.newInstance()
                .transport(Transport.AUTO).embeddedOptions(embedded()).build());
        assertDoesNotThrow(() -> RiftTestExtension.newInstance()
                .transport(Transport.SPAWN).spawnOptions(spawn()).build());
        assertDoesNotThrow(() -> RiftTestExtension.newInstance()
                .transport(Transport.AUTO).spawnOptions(spawn()).build());
        assertDoesNotThrow(() -> RiftTestExtension.newInstance()
                .transport(Transport.CONNECT).connectOptions(connect()).build());
    }

    @Test
    void connectOptionsRequireConnectTransport() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> RiftTestExtension.newInstance()
                .transport(Transport.AUTO).connectOptions(connect()).build());
        assertTrue(e.getMessage().contains("connectOptions"), e.getMessage());
    }

    @Test
    void embeddedOptionsRejectExplicitNonEmbeddedTransport() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> RiftTestExtension.newInstance()
                .transport(Transport.SPAWN).embeddedOptions(embedded()).build());
        assertTrue(e.getMessage().contains("embeddedOptions"), e.getMessage());
    }

    @Test
    void spawnOptionsRejectConnectTransport() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> RiftTestExtension.newInstance()
                .transport(Transport.CONNECT).spawnOptions(spawn()).build());
        assertTrue(e.getMessage().contains("spawnOptions"), e.getMessage());
    }
}
