package io.github.etacassiopeia.rift.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static void} method that declares the intercept rules for a {@link RiftIntercept}
 * class. It is invoked once the listener starts and re-invoked after each per-test rules reset.
 * Its parameters are resolved like test parameters: an {@link io.github.etacassiopeia.rift.Intercept}
 * (the live handle), an {@link InjectRift} {@code Rift}, and {@link InjectImposter} {@code Imposter}s.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RiftInterceptRules {
}
