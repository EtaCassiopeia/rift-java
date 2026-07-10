package io.github.etacassiopeia.rift.spring;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Parameter;

/**
 * Resolves {@link InjectImposter}/{@link InjectRift} method parameters from the {@link
 * RiftTestContext} bean — the parameter-injection counterpart to {@link
 * RiftTestExecutionListener}'s field injection. Auto-registered by the {@code @ExtendWith}
 * meta-annotation on {@link EnableRift}, so {@code @EnableRift} alone enables both field and
 * parameter injection with no extra {@code @ExtendWith}.
 */
public final class RiftParameterResolver implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.isAnnotated(InjectImposter.class)
                || parameterContext.isAnnotated(InjectRift.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(extensionContext);
        if (applicationContext.getBeanNamesForType(RiftTestContext.class).length == 0) {
            throw new ParameterResolutionException(
                    "no RiftTestContext in the Spring context; is @EnableRift present on the test class?");
        }
        return resolveValue(parameterContext.getParameter(),
                applicationContext.getBean(RiftTestContext.class));
    }

    /**
     * Resolves the value for a {@link InjectImposter}/{@link InjectRift} parameter against a {@link
     * RiftTestContext}. Package-private and context-free so the resolution contract is unit-testable
     * without the JUnit/Spring extension plumbing.
     */
    static Object resolveValue(Parameter parameter, RiftTestContext riftTestContext) {
        InjectImposter injectImposter = parameter.getAnnotation(InjectImposter.class);
        if (injectImposter != null) {
            requireType(parameter, Imposter.class, "@InjectImposter");
            try {
                return riftTestContext.imposter(injectImposter.value());
            } catch (IllegalArgumentException e) {
                throw new ParameterResolutionException(e.getMessage(), e);
            }
        }
        if (parameter.isAnnotationPresent(InjectRift.class)) {
            requireType(parameter, Rift.class, "@InjectRift");
            return riftTestContext.rift();
        }
        throw new ParameterResolutionException("parameter '" + parameter.getName()
                + "' is annotated with neither @InjectImposter nor @InjectRift");
    }

    private static void requireType(Parameter parameter, Class<?> expected, String annotation) {
        if (!expected.isAssignableFrom(parameter.getType())) {
            throw new ParameterResolutionException("parameter '" + parameter.getName() + "' annotated "
                    + annotation + " must be of type " + expected.getSimpleName()
                    + ", was " + parameter.getType().getName());
        }
    }
}
