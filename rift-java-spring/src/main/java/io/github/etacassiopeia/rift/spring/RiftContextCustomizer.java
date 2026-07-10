package io.github.etacassiopeia.rift.spring;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.dsl.ImposterSpec;
import io.github.etacassiopeia.rift.dsl.RiftDsl;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Starts the {@link Rift} engine and its configured imposters for a Spring test context, publishes
 * their properties, and registers a {@link RiftTestContext} bean.
 *
 * <p>This is a record so that {@code equals}/{@code hashCode} — the Spring context-cache key — are
 * derived from every field automatically: two {@code @EnableRift}/{@code @ConfigureImposter}
 * configurations that agree on all of {@code transport}, {@code adminUri}, {@code reset}, and the
 * ordered imposter configs are equal, and therefore share one cached context and one engine.
 */
record RiftContextCustomizer(
        Transport transport, String adminUri, Reset reset, List<ImposterConfig> imposterConfigs)
        implements ContextCustomizer {

    RiftContextCustomizer {
        imposterConfigs = List.copyOf(imposterConfigs);
    }

    /** One {@link ConfigureImposter} declaration, reduced to its cache-key-relevant attributes. */
    record ImposterConfig(String name, String baseUrlProperty, String portProperty,
                           Class<? extends Supplier<ImposterSpec>> specClass) {
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        String resolvedAdminUri = adminUri.isEmpty()
                ? adminUri
                : context.getEnvironment().resolveRequiredPlaceholders(adminUri);
        Rift rift = buildRift(resolvedAdminUri);
        // The engine is live from here on; release it if any imposter fails to initialize, otherwise
        // a partially-built context (e.g. a bad spec supplier) would orphan a spawned process/connection.
        try {
            Map<String, Imposter> impostersByName = new LinkedHashMap<>();
            Map<String, Object> properties = new LinkedHashMap<>();
            for (ImposterConfig config : imposterConfigs) {
                Imposter imposter = createImposter(rift, config);
                impostersByName.put(config.name(), imposter);
                if (!config.baseUrlProperty().isEmpty()) {
                    properties.put(config.baseUrlProperty(), imposter.uri().toString());
                }
                if (!config.portProperty().isEmpty()) {
                    properties.put(config.portProperty(), String.valueOf(imposter.port()));
                }
            }

            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("rift", properties));

            RiftTestContext riftTestContext = new RiftTestContext(rift, impostersByName, reset);
            context.getBeanFactory().registerSingleton("riftTestContext", riftTestContext);
            context.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> riftTestContext.close());
        } catch (RuntimeException e) {
            try {
                rift.close();
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private Rift buildRift(String resolvedAdminUri) {
        if (transport == Transport.CONNECT) {
            if (resolvedAdminUri.isEmpty()) {
                throw new IllegalStateException(
                        "@EnableRift(transport=CONNECT) requires a non-empty adminUri");
            }
            return Rift.connect(URI.create(resolvedAdminUri));
        }
        if (transport == Transport.SPAWN) {
            return Rift.spawn();
        }
        return resolvedAdminUri.isEmpty() ? Rift.spawn() : Rift.connect(URI.create(resolvedAdminUri));
    }

    private Imposter createImposter(Rift rift, ImposterConfig config) {
        if (config.specClass() == NoSpec.class) {
            return rift.create(RiftDsl.imposter(config.name()).record());
        }
        Supplier<ImposterSpec> supplier;
        try {
            supplier = config.specClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "failed to instantiate imposter spec supplier " + config.specClass().getName(), e);
        }
        ImposterSpec spec;
        try {
            spec = supplier.get();
        } catch (RuntimeException e) {
            throw new IllegalStateException("imposter spec supplier " + config.specClass().getName()
                    + " threw while building spec for imposter '" + config.name() + "'", e);
        }
        if (spec == null) {
            throw new IllegalStateException("imposter spec supplier " + config.specClass().getName()
                    + " returned null for imposter '" + config.name() + "'");
        }
        return rift.create(spec);
    }
}
