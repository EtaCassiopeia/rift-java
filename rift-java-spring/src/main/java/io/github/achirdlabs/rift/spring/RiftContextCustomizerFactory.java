package io.github.achirdlabs.rift.spring;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detects {@link EnableRift} on a test class and builds the {@link RiftContextCustomizer} that
 * drives the engine and imposter lifecycle for its Spring test context.
 */
public final class RiftContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        EnableRift enable = AnnotatedElementUtils.findMergedAnnotation(testClass, EnableRift.class);
        if (enable == null) {
            return null;
        }

        Set<ConfigureImposter> declarations = AnnotatedElementUtils.findMergedRepeatableAnnotations(
                testClass, ConfigureImposter.class, ConfigureImposters.class);
        List<RiftContextCustomizer.ImposterConfig> configs = new ArrayList<>();
        for (ConfigureImposter declaration : declarations) {
            configs.add(new RiftContextCustomizer.ImposterConfig(
                    declaration.name(), declaration.baseUrlProperty(), declaration.portProperty(),
                    declaration.spec()));
        }

        return new RiftContextCustomizer(enable.transport(), enable.adminUri(), enable.reset(), configs);
    }
}
