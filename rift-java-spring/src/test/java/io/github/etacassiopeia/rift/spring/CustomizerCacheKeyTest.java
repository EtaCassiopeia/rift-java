package io.github.etacassiopeia.rift.spring;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The Spring context-cache key is derived from the {@code @EnableRift}/{@code @ConfigureImposter}
 * attributes via the customizer's {@code equals}/{@code hashCode}. Identical configuration on two
 * classes must produce equal customizers (⇒ one shared context ⇒ one engine); different
 * configuration must not.
 */
class CustomizerCacheKeyTest {

    private final RiftContextCustomizerFactory factory = new RiftContextCustomizerFactory();

    @EnableRift(transport = Transport.CONNECT, adminUri = "${x}")
    @ConfigureImposter(name = "a", baseUrlProperty = "a.url")
    static class ConfigA {
    }

    @EnableRift(transport = Transport.CONNECT, adminUri = "${x}")
    @ConfigureImposter(name = "a", baseUrlProperty = "a.url")
    static class ConfigACopy {
    }

    @EnableRift(transport = Transport.CONNECT, adminUri = "${x}")
    @ConfigureImposter(name = "b", baseUrlProperty = "b.url")
    static class ConfigB {
    }

    static class NoRift {
    }

    private ContextCustomizer customizerFor(Class<?> testClass) {
        List<ContextConfigurationAttributes> none = List.of();
        return factory.createContextCustomizer(testClass, none);
    }

    @Test
    void identicalConfigProducesEqualCustomizers() {
        ContextCustomizer a = customizerFor(ConfigA.class);
        ContextCustomizer copy = customizerFor(ConfigACopy.class);
        assertNotNull(a);
        assertNotNull(copy);
        assertEquals(a, copy);
        assertEquals(a.hashCode(), copy.hashCode());
    }

    @Test
    void differentConfigProducesUnequalCustomizers() {
        assertNotEquals(customizerFor(ConfigA.class), customizerFor(ConfigB.class));
    }

    @Test
    void noEnableRiftYieldsNoCustomizer() {
        assertNull(customizerFor(NoRift.class));
    }
}
