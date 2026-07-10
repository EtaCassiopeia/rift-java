package io.github.etacassiopeia.rift.spring;

/**
 * Shared base for the Spring integration tests: starts one fake admin server for the JVM and
 * publishes its URI as a system property that {@code @EnableRift(adminUri = "${rift.test.admin-uri}")}
 * resolves. The rift annotations are {@code @Inherited}, so every subclass gets identical
 * configuration — which is also what lets Spring cache and share a single application context.
 */
@EnableRift(transport = Transport.CONNECT, adminUri = "${rift.test.admin-uri}", reset = Reset.PER_TEST)
@ConfigureImposter(name = "users", baseUrlProperty = "user-client.base-url", portProperty = "user-client.port")
@ConfigureImposter(name = "payments", baseUrlProperty = "payment-client.base-url")
abstract class RiftSpringTestBase {

    static final FakeRiftAdmin ADMIN = new FakeRiftAdmin();

    static {
        System.setProperty("rift.test.admin-uri", ADMIN.baseUri().toString());
    }
}
