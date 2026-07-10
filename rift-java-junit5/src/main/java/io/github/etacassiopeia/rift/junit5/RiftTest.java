package io.github.etacassiopeia.rift.junit5;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables the rift-java JUnit 5 test integration on a test class: starts one {@link
 * io.github.etacassiopeia.rift.Rift} engine for the test class and drives the {@link RiftImposter}
 * declarations, injecting {@link InjectRift}/{@link InjectImposter} fields and parameters.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@ExtendWith(RiftTestExtension.class)
public @interface RiftTest {

    /** How to obtain the engine. */
    Transport transport() default Transport.AUTO;

    /**
     * Admin API URI for {@link Transport#CONNECT} (and {@link Transport#AUTO} when set). Supports a
     * {@code ${property}} placeholder resolved against a system property of the same name.
     */
    String adminUri() default "";

    /** When configured imposters are reset during a test class run. */
    Reset reset() default Reset.PER_TEST;
}
