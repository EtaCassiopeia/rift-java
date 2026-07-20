package io.github.achirdlabs.rift.spring;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.Rift;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parameter injection (#36): {@link RiftParameterResolver} resolves {@link InjectImposter}/{@link
 * InjectRift} method parameters, auto-registered via the {@code @ExtendWith} meta-annotation on
 * {@link EnableRift} — no explicit {@code @ExtendWith(RiftParameterResolver.class)} here.
 */
@SpringBootTest
class ParameterInjectionTest extends RiftSpringTestBase {

    // AC1 + AC4: zero-config parameter injection into a @Test method (imposter by name + rift).
    @Test
    void resolvesImposterAndRiftParameters(@InjectImposter("users") Imposter users, @InjectRift Rift rift) {
        assertNotNull(users, "users imposter injected as parameter");
        assertNotNull(rift, "rift injected as parameter");
        assertEquals("127.0.0.1", rift.adminUri().getHost());
        assertTrue(users.port() >= 5000, "imposter got an engine-assigned port");
    }

    // AC3: @Target restored to {FIELD, PARAMETER} on both annotations.
    @Test
    void annotationsTargetFieldAndParameter() {
        Target imposterTarget = InjectImposter.class.getAnnotation(Target.class);
        Target riftTarget = InjectRift.class.getAnnotation(Target.class);
        assertTrue(hasBoth(imposterTarget), "@InjectImposter targets FIELD and PARAMETER");
        assertTrue(hasBoth(riftTarget), "@InjectRift targets FIELD and PARAMETER");
    }

    // AC2: unknown imposter name → clear ParameterResolutionException naming the missing imposter.
    @Test
    void unknownImposterNameParameterFailsClearly() throws Exception {
        Parameter param = Samples.class
                .getDeclaredMethod("wantsMissing", Imposter.class)
                .getParameters()[0];
        RiftTestContext ctx = new RiftTestContext(null, Map.of(), Reset.NONE);

        ParameterResolutionException ex = assertThrows(ParameterResolutionException.class,
                () -> RiftParameterResolver.resolveValue(param, ctx));
        assertTrue(ex.getMessage().contains("missing"),
                "failure names the unknown imposter: " + ex.getMessage());
    }

    // Type mismatch: @InjectImposter on a non-Imposter parameter → clear ParameterResolutionException.
    @Test
    void mismatchedParameterTypeFailsClearly() throws Exception {
        Parameter param = Samples.class
                .getDeclaredMethod("wrongType", Rift.class)
                .getParameters()[0];
        RiftTestContext ctx = new RiftTestContext(null, Map.of(), Reset.NONE);

        ParameterResolutionException ex = assertThrows(ParameterResolutionException.class,
                () -> RiftParameterResolver.resolveValue(param, ctx));
        assertTrue(ex.getMessage().contains("Imposter"),
                "failure names the expected type: " + ex.getMessage());
    }

    private static boolean hasBoth(Target target) {
        boolean field = false;
        boolean parameter = false;
        for (ElementType t : target.value()) {
            field |= t == ElementType.FIELD;
            parameter |= t == ElementType.PARAMETER;
        }
        return field && parameter;
    }

    private static final class Samples {
        @SuppressWarnings("unused")
        void wantsMissing(@InjectImposter("missing") Imposter imposter) {
        }

        @SuppressWarnings("unused")
        void wrongType(@InjectImposter("users") Rift notAnImposter) {
        }
    }
}
