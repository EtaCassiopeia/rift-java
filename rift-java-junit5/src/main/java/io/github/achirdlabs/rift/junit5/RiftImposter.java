package io.github.achirdlabs.rift.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static} {@link io.github.achirdlabs.rift.dsl.ImposterSpec} field declaring an
 * imposter to create on the {@code @RiftTest} engine. The imposter is keyed for {@link InjectImposter}
 * by its declared name (the {@code ImposterDefinition} name, read client-side — no engine round-trip),
 * which every spec created via {@link io.github.achirdlabs.rift.dsl.RiftDsl#imposter(String)} carries.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RiftImposter {
}
