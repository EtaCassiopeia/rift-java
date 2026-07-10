package io.github.etacassiopeia.rift.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables the rift-java Spring test integration on a test class: starts one {@link
 * io.github.etacassiopeia.rift.Rift} engine for the (cacheable) Spring test context and drives the
 * {@link ConfigureImposter} declarations.
 *
 * <p>The annotation attributes are the Spring context-cache key contributor (see {@code
 * RiftContextCustomizer#equals}); two test classes with identical {@code @EnableRift}/{@code
 * @ConfigureImposter} configuration share one application context and therefore one engine.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface EnableRift {

    /** How to obtain the engine. */
    Transport transport() default Transport.AUTO;

    /**
     * Admin API URI for {@link Transport#CONNECT} (and {@link Transport#AUTO} when set). Supports
     * {@code ${property}} placeholders resolved against the test context environment.
     */
    String adminUri() default "";

    /** When configured imposters are reset during a test class run. */
    Reset reset() default Reset.PER_TEST;
}
