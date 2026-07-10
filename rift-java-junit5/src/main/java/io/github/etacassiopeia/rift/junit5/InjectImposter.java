package io.github.etacassiopeia.rift.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the named {@link io.github.etacassiopeia.rift.Imposter} into a test field or test method
 * parameter.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectImposter {

    /** The {@link RiftImposter}-declared imposter name to inject. */
    String value();
}
