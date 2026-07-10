package io.github.etacassiopeia.rift.spring;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.TestContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage of {@link RiftTestExecutionListener}'s reset routing table: {@code PER_TEST}
 * resets in {@code beforeTestMethod}; {@code NONE} never does; {@code PER_CLASS} resets in {@code
 * beforeTestClass}. Drives the listener against a real (fake-admin-backed) context rather than mocking
 * the imposters, so the reset actually hits {@code DELETE /savedRequests}.
 */
class ResetRoutingTest {

    @EnableRift(reset = Reset.PER_CLASS)
    static class PerClassMarker {
    }

    @EnableRift(reset = Reset.PER_TEST)
    static class PerTestMarker {
    }

    private final RiftTestExecutionListener listener = new RiftTestExecutionListener();

    private RiftTestContext populate(Reset reset, GenericApplicationContext ctx) {
        RiftContextCustomizer customizer = new RiftContextCustomizer(
                Transport.CONNECT, RiftSpringTestBase.ADMIN.baseUri().toString(), reset,
                List.of(new RiftContextCustomizer.ImposterConfig("r", "", "", NoSpec.class)));
        customizer.customizeContext(ctx, null);
        // The listener under test resolves the RiftTestContext bean off the context, which requires a
        // refreshed context (in real use it always is by the time the listener runs).
        ctx.refresh();
        return ctx.getBean(RiftTestContext.class);
    }

    private TestContext testContext(GenericApplicationContext ctx, Class<?> testClass) {
        TestContext testContext = mock(TestContext.class);
        when(testContext.getApplicationContext()).thenReturn(ctx);
        when(testContext.getTestClass()).thenAnswer(invocation -> testClass);
        return testContext;
    }

    @Test
    void perTestModeResetsInBeforeTestMethod() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        RiftTestContext rtc = populate(Reset.PER_TEST, ctx);
        int port = rtc.imposter("r").port();
        RiftSpringTestBase.ADMIN.pushRecorded(port, "GET", "/x");
        assertEquals(1, rtc.imposter("r").recorded().size());

        listener.beforeTestMethod(testContext(ctx, PerTestMarker.class));

        assertTrue(rtc.imposter("r").recorded().isEmpty(), "PER_TEST cleared recorded in beforeTestMethod");
        rtc.close();
    }

    @Test
    void noneModeDoesNotResetInBeforeTestMethod() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        RiftTestContext rtc = populate(Reset.NONE, ctx);
        int port = rtc.imposter("r").port();
        RiftSpringTestBase.ADMIN.pushRecorded(port, "GET", "/x");

        listener.beforeTestMethod(testContext(ctx, PerTestMarker.class));

        assertEquals(1, rtc.imposter("r").recorded().size(), "NONE leaves recorded untouched");
        rtc.close();
    }

    @Test
    void perClassModeResetsInBeforeTestClass() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        RiftTestContext rtc = populate(Reset.PER_CLASS, ctx);
        int port = rtc.imposter("r").port();
        RiftSpringTestBase.ADMIN.pushRecorded(port, "GET", "/x");

        listener.beforeTestClass(testContext(ctx, PerClassMarker.class));

        assertTrue(rtc.imposter("r").recorded().isEmpty(), "PER_CLASS cleared recorded in beforeTestClass");
        rtc.close();
    }

    @Test
    void perTestModeSkipsBeforeTestClassReset() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        RiftTestContext rtc = populate(Reset.PER_TEST, ctx);
        int port = rtc.imposter("r").port();
        RiftSpringTestBase.ADMIN.pushRecorded(port, "GET", "/x");

        listener.beforeTestClass(testContext(ctx, PerTestMarker.class));

        assertEquals(1, rtc.imposter("r").recorded().size(),
                "beforeTestClass only resets PER_CLASS classes");
        rtc.close();
    }
}
