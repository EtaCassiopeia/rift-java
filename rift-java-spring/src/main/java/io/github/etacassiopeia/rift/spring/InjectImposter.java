package io.github.etacassiopeia.rift.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Injects the named {@link io.github.etacassiopeia.rift.Imposter} into a test field. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectImposter {

    /** The {@link ConfigureImposter#name()} to inject. */
    String value();
}
