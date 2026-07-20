package io.github.achirdlabs.rift;

/** The truststore file format {@link InterceptTrust#exportTruststore} writes; {@code name()} is the {@link java.security.KeyStore} type. */
public enum TruststoreFormat {
    PKCS12,
    JKS
}
