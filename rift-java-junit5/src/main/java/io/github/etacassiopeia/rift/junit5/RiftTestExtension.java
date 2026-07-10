package io.github.etacassiopeia.rift.junit5;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.dsl.ImposterSpec;
import io.github.etacassiopeia.rift.model.ImposterDefinition;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code @RiftTest} JUnit 5 extension: starts one {@link Rift} engine per test class, creates
 * the {@link RiftImposter}-declared imposters, injects {@link InjectRift}/{@link InjectImposter}
 * fields and parameters, and drives the {@link Reset} policy between test methods.
 */
public final class RiftTestExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback,
        TestInstancePostProcessor, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(RiftTestExtension.class);
    private static final String STORE_KEY = "riftTestContext";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();

        // The test class's static initializer commonly publishes the admin-URI system property
        // @RiftTest.adminUri()'s ${...} placeholder resolves against (see RiftExtensionIT); JUnit
        // only guarantees that runs once the class is loaded, which isn't otherwise guaranteed to
        // have happened yet when this callback fires. Force it now, before resolving adminUri.
        try {
            Class.forName(testClass.getName(), true, testClass.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("failed to force-initialize test class " + testClass.getName(), e);
        }

        RiftTest riftTest = AnnotationSupport.findAnnotation(testClass, RiftTest.class)
                .orElseThrow(() -> new IllegalStateException("@RiftTest not found on " + testClass.getName()));

        String resolvedAdminUri = resolveAdminUri(riftTest.adminUri());
        Rift rift = buildRift(riftTest.transport(), resolvedAdminUri);
        try {
            Map<String, Imposter> impostersByName = createConfiguredImposters(rift, testClass);
            RiftTestContext riftTestContext = new RiftTestContext(rift, impostersByName, riftTest.reset());
            if (riftTest.reset() == Reset.PER_CLASS) {
                riftTestContext.resetConfiguredImposters();
            }
            // Stored only after the (possibly-throwing) PER_CLASS reset — so a failure here closes the
            // engine exactly once (via the catch below) rather than also via afterAll's stored context.
            context.getStore(NAMESPACE).put(STORE_KEY, riftTestContext);
        } catch (RuntimeException e) {
            try {
                rift.close();
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        RiftTestContext riftTestContext = riftTestContext(context);
        if (riftTestContext.reset() == Reset.PER_TEST) {
            riftTestContext.resetConfiguredImposters();
        }
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        RiftTestContext riftTestContext = riftTestContext(context);
        for (Field field : AnnotationSupport.findAnnotatedFields(
                testInstance.getClass(), InjectRift.class, f -> true, HierarchyTraversalMode.TOP_DOWN)) {
            setField(field, testInstance, riftTestContext.rift());
        }
        for (Field field : AnnotationSupport.findAnnotatedFields(
                testInstance.getClass(), InjectImposter.class, f -> true, HierarchyTraversalMode.TOP_DOWN)) {
            InjectImposter injectImposter = field.getAnnotation(InjectImposter.class);
            setField(field, testInstance, riftTestContext.imposter(injectImposter.value()));
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.isAnnotated(InjectRift.class) || parameterContext.isAnnotated(InjectImposter.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        RiftTestContext riftTestContext = riftTestContext(extensionContext);
        if (parameterContext.isAnnotated(InjectRift.class)) {
            return riftTestContext.rift();
        }
        InjectImposter injectImposter = parameterContext.findAnnotation(InjectImposter.class)
                .orElseThrow(() -> new ParameterResolutionException("expected @InjectImposter on parameter " + parameterContext.getParameter()));
        return riftTestContext.imposter(injectImposter.value());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        RiftTestContext riftTestContext = context.getStore(NAMESPACE).get(STORE_KEY, RiftTestContext.class);
        if (riftTestContext != null) {
            riftTestContext.close();
        }
    }

    private static RiftTestContext riftTestContext(ExtensionContext context) {
        RiftTestContext riftTestContext = context.getStore(NAMESPACE).get(STORE_KEY, RiftTestContext.class);
        if (riftTestContext == null) {
            throw new IllegalStateException("no RiftTestContext found; is @RiftTest present on the test class?");
        }
        return riftTestContext;
    }

    private static String resolveAdminUri(String adminUri) {
        if (adminUri.startsWith("${") && adminUri.endsWith("}")) {
            String propertyName = adminUri.substring(2, adminUri.length() - 1);
            String value = System.getProperty(propertyName);
            return value == null ? "" : value;
        }
        return adminUri;
    }

    private static Rift buildRift(Transport transport, String resolvedAdminUri) {
        return switch (transport) {
            case CONNECT -> {
                if (resolvedAdminUri.isBlank()) {
                    throw new IllegalStateException("@RiftTest(transport=CONNECT) requires a non-blank adminUri");
                }
                yield Rift.connect(URI.create(resolvedAdminUri));
            }
            case SPAWN -> Rift.spawn();
            case EMBEDDED -> Rift.embedded();
            case AUTO -> Rift.isEmbeddedAvailable() ? Rift.embedded() : Rift.spawn();
        };
    }

    /**
     * Creates every {@code static} {@link ImposterSpec} field annotated {@link RiftImposter}, keyed
     * by the spec's own declared name (via {@link ImposterDefinition#name()}) rather than the name
     * reported back by the engine: a minimal admin API is not required to echo a {@code name} field
     * on {@code GET}/{@code POST /imposters} (it is client-side bookkeeping, not wire-required), so
     * relying on {@code Imposter#name()} here would break against such engines.
     */
    private static Map<String, Imposter> createConfiguredImposters(Rift rift, Class<?> testClass) throws Exception {
        Map<String, Imposter> impostersByName = new LinkedHashMap<>();
        for (Field field : AnnotationSupport.findAnnotatedFields(
                testClass, RiftImposter.class,
                f -> Modifier.isStatic(f.getModifiers()) && ImposterSpec.class.isAssignableFrom(f.getType()),
                HierarchyTraversalMode.TOP_DOWN)) {
            field.trySetAccessible();
            ImposterSpec spec = (ImposterSpec) field.get(null);
            if (spec == null) {
                throw new IllegalStateException("@RiftImposter field " + field + " is null");
            }
            ImposterDefinition definition = spec.build();
            String name = definition.name().orElseThrow(() -> new IllegalStateException(
                    "@RiftImposter field " + field + " has no name"));
            if (impostersByName.containsKey(name)) {
                throw new IllegalStateException("duplicate @RiftImposter name '" + name + "' (field " + field + ")");
            }
            Imposter imposter = rift.create(definition);
            impostersByName.put(name, imposter);
        }
        return impostersByName;
    }

    private static void setField(Field field, Object testInstance, Object value) throws IllegalAccessException {
        field.trySetAccessible();
        field.set(testInstance, value);
    }
}
