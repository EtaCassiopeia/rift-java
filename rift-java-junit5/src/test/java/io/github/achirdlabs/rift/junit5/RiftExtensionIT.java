package io.github.achirdlabs.rift.junit5;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.dsl.ImposterSpec;
import io.github.achirdlabs.rift.dsl.RiftDsl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code @RiftTest} extension against a fake admin (portable {@code CONNECT}, no native lib): one
 * engine per class, imposters declared via {@code @RiftImposter} static specs, injected into fields and
 * test parameters.
 */
@RiftTest(transport = Transport.CONNECT, adminUri = "${rift.junit.ext}", reset = Reset.PER_TEST)
class RiftExtensionIT {

    static final FakeRiftAdmin ADMIN = new FakeRiftAdmin();

    static {
        System.setProperty("rift.junit.ext", ADMIN.baseUri().toString());
    }

    @RiftImposter
    static ImposterSpec users = RiftDsl.imposter("users").record();

    @InjectRift
    Rift rift;

    @InjectImposter("users")
    Imposter usersField;

    @Test
    void injectsRiftAndImposterFields() {
        assertNotNull(rift, "@InjectRift field");
        assertNotNull(usersField, "@InjectImposter field");
        assertEquals("127.0.0.1", rift.adminUri().getHost());
        assertTrue(usersField.port() >= 5000, "imposter got an engine-assigned port");
    }

    @Test
    void injectsRiftAndImposterParameters(@InjectRift Rift r, @InjectImposter("users") Imposter u) {
        assertNotNull(r, "@InjectRift parameter");
        assertNotNull(u, "@InjectImposter parameter");
        assertEquals(usersField.port(), u.port(), "same class-scoped imposter as the field");
    }
}
