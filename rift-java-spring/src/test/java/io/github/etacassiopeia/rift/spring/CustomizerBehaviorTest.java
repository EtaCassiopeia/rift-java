package io.github.etacassiopeia.rift.spring;

import io.github.etacassiopeia.rift.dsl.ImposterSpec;
import io.github.etacassiopeia.rift.dsl.RiftDsl;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link RiftContextCustomizer#customizeContext} directly (no @SpringBootTest) to cover the
 * custom {@code spec()} supplier branch, the transport selection, and the failure/leak paths that a
 * happy-path @SpringBootTest never reaches. Uses the shared {@link RiftSpringTestBase#ADMIN} fake.
 */
class CustomizerBehaviorTest {

    static final AtomicInteger GOOD_SPEC_CALLS = new AtomicInteger();

    /** A real custom spec supplier: pre-stubs the imposter, proving the non-NoSpec branch ran. */
    public static class GoodSpec implements Supplier<ImposterSpec> {
        @Override
        public ImposterSpec get() {
            GOOD_SPEC_CALLS.incrementAndGet();
            return RiftDsl.imposter("orders").record()
                    .stub(RiftDsl.onGet("/pre").willReturn(RiftDsl.ok()));
        }
    }

    /** No accessible no-arg constructor → reflective instantiation must fail with context. */
    public static class NoNoArgSpec implements Supplier<ImposterSpec> {
        public NoNoArgSpec(String unused) {
        }

        @Override
        public ImposterSpec get() {
            return RiftDsl.imposter("x").record();
        }
    }

    public static class NullSpec implements Supplier<ImposterSpec> {
        @Override
        public ImposterSpec get() {
            return null;
        }
    }

    public static class ThrowingSpec implements Supplier<ImposterSpec> {
        @Override
        public ImposterSpec get() {
            throw new IllegalArgumentException("boom");
        }
    }

    private RiftContextCustomizer customizer(Transport transport, String adminUri,
            RiftContextCustomizer.ImposterConfig... configs) {
        return new RiftContextCustomizer(transport, adminUri, Reset.NONE, List.of(configs));
    }

    private String adminUri() {
        return RiftSpringTestBase.ADMIN.baseUri().toString();
    }

    @Test
    void customSpecSupplierIsInstantiatedInvokedAndWired() {
        GOOD_SPEC_CALLS.set(0);
        RiftContextCustomizer customizer = customizer(Transport.CONNECT, adminUri(),
                new RiftContextCustomizer.ImposterConfig("orders", "orders.url", "orders.port", GoodSpec.class));
        GenericApplicationContext ctx = new GenericApplicationContext();

        customizer.customizeContext(ctx, null);

        assertEquals(1, GOOD_SPEC_CALLS.get(), "the custom supplier was reflectively created and called");
        assertEquals(1, ctx.getBeanFactory().getBeanNamesForType(RiftTestContext.class).length);
        RiftTestContext rtc = ctx.getBeanFactory().getBean(RiftTestContext.class);
        assertNotNull(rtc.imposter("orders"));
        assertEquals(rtc.imposter("orders").uri().toString(), ctx.getEnvironment().getProperty("orders.url"));
        assertEquals(String.valueOf(rtc.imposter("orders").port()), ctx.getEnvironment().getProperty("orders.port"));
        rtc.close();
    }

    @Test
    void autoTransportConnectsWhenAdminUriIsSet() {
        RiftContextCustomizer customizer = customizer(Transport.AUTO, adminUri(),
                new RiftContextCustomizer.ImposterConfig("a", "a.url", "", NoSpec.class));
        GenericApplicationContext ctx = new GenericApplicationContext();

        customizer.customizeContext(ctx, null);

        RiftTestContext rtc = ctx.getBeanFactory().getBean(RiftTestContext.class);
        assertEquals("127.0.0.1", rtc.rift().adminUri().getHost());
        assertNotNull(rtc.imposter("a"));
        rtc.close();
    }

    @Test
    void connectWithEmptyAdminUriFailsClearly() {
        RiftContextCustomizer customizer = customizer(Transport.CONNECT, "");
        GenericApplicationContext ctx = new GenericApplicationContext();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> customizer.customizeContext(ctx, null));
        assertTrue(ex.getMessage().contains("CONNECT"), ex.getMessage());
        assertEquals(0, ctx.getBeanFactory().getBeanNamesForType(RiftTestContext.class).length);
    }

    @Test
    void specSupplierWithoutNoArgConstructorFailsWithContext() {
        RiftContextCustomizer customizer = customizer(Transport.CONNECT, adminUri(),
                new RiftContextCustomizer.ImposterConfig("bad", "", "", NoNoArgSpec.class));
        GenericApplicationContext ctx = new GenericApplicationContext();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> customizer.customizeContext(ctx, null));
        assertTrue(ex.getMessage().contains(NoNoArgSpec.class.getName()), ex.getMessage());
    }

    @Test
    void specSupplierReturningNullFailsWithContext() {
        RiftContextCustomizer customizer = customizer(Transport.CONNECT, adminUri(),
                new RiftContextCustomizer.ImposterConfig("nul", "", "", NullSpec.class));
        GenericApplicationContext ctx = new GenericApplicationContext();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> customizer.customizeContext(ctx, null));
        assertTrue(ex.getMessage().contains("returned null"), ex.getMessage());
        assertTrue(ex.getMessage().contains("nul"), ex.getMessage());
    }

    @Test
    void specSupplierThrowingIsWrappedWithContext() {
        RiftContextCustomizer customizer = customizer(Transport.CONNECT, adminUri(),
                new RiftContextCustomizer.ImposterConfig("kab", "", "", ThrowingSpec.class));
        GenericApplicationContext ctx = new GenericApplicationContext();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> customizer.customizeContext(ctx, null));
        assertTrue(ex.getMessage().contains("threw while building"), ex.getMessage());
        assertTrue(ex.getCause() instanceof IllegalArgumentException, "original cause preserved");
    }
}
