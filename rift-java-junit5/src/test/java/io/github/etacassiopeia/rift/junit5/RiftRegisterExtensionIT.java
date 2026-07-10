package io.github.etacassiopeia.rift.junit5;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.dsl.RiftDsl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 (#45): the programmatic {@code @RegisterExtension} builder — no {@code @RiftTest}
 * annotation. Transport/imposters/reset come from {@link RiftTestExtension#newInstance()}, and the
 * same field/parameter injection + reset apply as in annotation mode.
 */
class RiftRegisterExtensionIT {

    static final FakeRiftAdmin ADMIN = new FakeRiftAdmin();

    @RegisterExtension
    static final RiftTestExtension rift = RiftTestExtension.newInstance()
            .transport(Transport.CONNECT)
            .adminUri(ADMIN.baseUri().toString())
            .imposter(RiftDsl.imposter("users").record())
            .reset(Reset.PER_TEST)
            .build();

    @InjectRift
    Rift riftClient;

    @InjectImposter("users")
    Imposter users;

    @Test
    void injectsFromProgrammaticBuilder() {
        assertNotNull(riftClient, "@InjectRift field from builder-configured engine");
        assertNotNull(users, "@InjectImposter field from builder-configured imposter");
        assertEquals("127.0.0.1", riftClient.adminUri().getHost());
        assertTrue(users.port() >= 5000, "imposter got an engine-assigned port");
    }

    @Test
    void resolvesParametersFromBuilder(@InjectRift Rift r, @InjectImposter("users") Imposter u) {
        assertNotNull(r, "@InjectRift parameter from builder");
        assertEquals(users.port(), u.port(), "same class-scoped imposter as the field");
    }

    // PER_TEST reset wiring works in builder mode: beforeEach clears recorded before each method,
    // so each test starts from a clean slate regardless of order.
    @Test
    void perTestResetRunsInBuilderMode() {
        assertTrue(users.recorded().isEmpty(), "recorded cleared by PER_TEST reset at test start");
        ADMIN.pushRecorded(users.port(), "GET", "/seen");
        assertEquals(1, users.recorded().size(), "the just-pushed request is visible within the test");
    }
}
