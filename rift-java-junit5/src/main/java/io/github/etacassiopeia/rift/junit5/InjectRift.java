package io.github.etacassiopeia.rift.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Injects the test class's {@link io.github.etacassiopeia.rift.Rift} into a test field or test method parameter. */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectRift {
}
