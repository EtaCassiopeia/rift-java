package io.github.achirdlabs.rift.junit5;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables the rift-java JUnit 5 test integration on a test class: starts one {@link
 * io.github.achirdlabs.rift.Rift} engine for the test class and drives the {@link RiftImposter}
 * declarations, injecting {@link InjectRift}/{@link InjectImposter} fields and parameters.
 *
 * <p>The annotation intentionally carries no per-transport engine options: configure the engine from
 * the launch command ({@code -Drift.ffi.lib}, {@code -Drift.versionCheck}), or use the
 * {@code @RegisterExtension} builder ({@link RiftTestExtension#newInstance()}) with
 * {@code embeddedOptions(...)} / {@code connectOptions(...)} / {@code spawnOptions(...)} for full
 * programmatic control.
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

    /**
     * When {@code true}, a failing test publishes each imposter's recorded requests to the JUnit
     * report as an entry keyed {@code rift.recorded.<name>} (capped at 20 requests per imposter),
     * to aid debugging a test that hit a mock. Off by default.
     */
    boolean dumpRecordedOnFailure() default false;
}
