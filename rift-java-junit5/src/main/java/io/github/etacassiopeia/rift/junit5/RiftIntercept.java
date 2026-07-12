package io.github.etacassiopeia.rift.junit5;

import io.github.etacassiopeia.rift.TruststoreFormat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts a TLS-MITM intercept listener for a {@code @RiftTest} class. The listener and its CA live
 * for the class; only its rules reset per test (per the {@code @RiftTest} {@link Reset} policy).
 * Declare rules with a {@link RiftInterceptRules} method and get the live handle with
 * {@link InjectIntercept}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RiftIntercept {

    /** Bind port; {@code 0} = OS-assigned. Fix it for a container SUT that points at a stable port. */
    int port() default 0;

    /** Bind host; an IP literal ({@code "0.0.0.0"} to reach it from another container). */
    String host() default "127.0.0.1";

    /** Committed CA cert PEM path (with {@link #caKey}); {@code ${property}} placeholders are resolved. Empty = ephemeral CA. */
    String caCert() default "";

    /** Committed CA key PEM path (with {@link #caCert}). */
    String caKey() default "";

    /** When set, a truststore is written here during {@code beforeAll} (for a container to mount). {@code ${property}} resolved. */
    String exportTruststore() default "";

    /** Format for {@link #exportTruststore}. */
    TruststoreFormat exportFormat() default TruststoreFormat.PKCS12;

    /** Password for {@link #exportTruststore}. */
    String exportPassword() default "changeit";
}
