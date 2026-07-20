package io.github.achirdlabs.rift.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Golden-file capture/replay for a {@code @RiftTest} class (Hoverfly-style auto-capture). On a
 * {@code @RiftGolden} class the configured imposter is driven by the presence of {@link #file()}:
 *
 * <ul>
 *   <li><b>file missing</b> → CAPTURE: the imposter proxies to {@link #origin()}, records the
 *       traffic, and the recorded stubs are persisted to {@code file} when the class finishes.</li>
 *   <li><b>file present</b> → REPLAY: the recorded stubs are loaded from {@code file} and served
 *       directly — no network, so CI never touches the origin.</li>
 *   <li>{@code -Drift.golden=recapture} forces CAPTURE even when the file exists.</li>
 * </ul>
 *
 * <p>The persisted format is the engine's replayable imposter JSON, portable across the rift SDKs
 * and {@code rift --configfile}. The golden imposter is the sole {@code @RiftImposter} on the class,
 * or the one named by {@link #imposter()} when there is more than one.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RiftGolden {

    /** The real upstream to proxy to and record from during CAPTURE. */
    String origin();

    /** The golden file: absent → CAPTURE and write it; present → REPLAY from it. */
    String file();

    /** The {@code @RiftImposter} name to record; defaults to the sole configured imposter. */
    String imposter() default "";
}
