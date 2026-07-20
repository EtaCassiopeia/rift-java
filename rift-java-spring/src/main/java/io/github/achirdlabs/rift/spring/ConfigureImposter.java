package io.github.achirdlabs.rift.spring;

import io.github.achirdlabs.rift.dsl.ImposterSpec;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

/**
 * Declares a named imposter to create when the test context starts. Repeatable.
 *
 * <p>The imposter's URI (and optionally port) are published as Spring environment properties so the
 * application under test is wired to the imposter without any test-specific configuration.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ConfigureImposters.class)
@Inherited
@Documented
public @interface ConfigureImposter {

    /** Logical name; used for {@link InjectImposter} lookup and as the engine imposter name. */
    String name();

    /** Property set to {@code imposter.uri().toString()} (highest test precedence). Empty = skip. */
    String baseUrlProperty() default "";

    /** Property set to {@code imposter.port()}. Empty = skip. */
    String portProperty() default "";

    /**
     * Supplier of the imposter specification (pre-stubbing, recording, etc.). The default {@link
     * NoSpec} is a sentinel meaning "a bare recording imposter" — the integration creates {@code
     * imposter(name).record()}.
     */
    Class<? extends Supplier<ImposterSpec>> spec() default NoSpec.class;
}
