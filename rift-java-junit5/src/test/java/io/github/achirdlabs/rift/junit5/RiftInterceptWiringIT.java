package io.github.achirdlabs.rift.junit5;

import io.github.achirdlabs.rift.Intercept;
import io.github.achirdlabs.rift.dsl.RiftDsl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code @RiftIntercept} drives the extension lifecycle against a fake admin (portable CONNECT, no
 * native lib): it starts the listener, applies the {@code @RiftInterceptRules} method, injects the
 * live handle, and re-applies rules after a per-test reset. Real TLS interception is proven at the
 * core / testcontainers level (InterceptE2eIT, RiftContainerInterceptIT).
 */
@RiftTest(transport = Transport.CONNECT, adminUri = "${rift.junit.intercept}", reset = Reset.PER_TEST)
@RiftIntercept(port = 0)
class RiftInterceptWiringIT {

    static final FakeRiftAdmin ADMIN = new FakeRiftAdmin();

    static {
        System.setProperty("rift.junit.intercept", ADMIN.baseUri().toString());
    }

    @RiftInterceptRules
    static void rules(Intercept intercept) {
        intercept.serve("cdn.example.com", RiftDsl.ok());
    }

    @InjectIntercept
    Intercept interceptField;

    @Test
    void startsInjectsAndAppliesRules() {
        // beforeAll started the listener and applied the rules method.
        assertTrue(ADMIN.interceptStarts.get() >= 1, "intercept listener started");
        assertTrue(ADMIN.interceptRuleAdds.get() >= 1, "@RiftInterceptRules applied a rule");
        // Field injection gives the live handle bound to the started listener's endpoint.
        assertNotNull(interceptField);
        assertEquals(19000, interceptField.address().getPort());
    }

    @Test
    void perTestResetClearsAndReappliesRules(@InjectIntercept Intercept injected) {
        // A second test: beforeEach cleared the rules and re-invoked the rules method.
        assertTrue(ADMIN.interceptRuleClears.get() >= 1, "rules cleared per test");
        assertTrue(ADMIN.interceptRuleAdds.get() >= 2, "rules re-applied after reset");
        assertNotNull(injected, "@InjectIntercept resolves as a parameter too");
    }
}
