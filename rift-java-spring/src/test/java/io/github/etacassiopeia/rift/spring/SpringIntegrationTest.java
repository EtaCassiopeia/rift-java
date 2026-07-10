package io.github.etacassiopeia.rift.spring;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.Rift;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Field injection, property binding, and no context-bean pollution over the real Spring machinery. */
@SpringBootTest
class SpringIntegrationTest extends RiftSpringTestBase {

    @InjectImposter("users")
    Imposter users;

    @InjectImposter("payments")
    Imposter payments;

    @InjectRift
    Rift rift;

    @Autowired
    ApplicationContext ctx;

    @Autowired
    UserClientProps props;

    @Test
    void injectsImpostersAndRift() {
        assertNotNull(users, "users imposter injected");
        assertNotNull(payments, "payments imposter injected");
        assertNotNull(rift, "rift injected");
        assertEquals("127.0.0.1", rift.adminUri().getHost());
        assertTrue(users.port() >= 5000, "imposter got an engine-assigned port");
    }

    @Test
    void baseUrlAndPortPropertiesBindToImposter() {
        String envProp = ctx.getEnvironment().getProperty("user-client.base-url");
        assertEquals(users.uri().toString(), envProp, "highest-precedence property source wins");
        assertEquals(users.uri().toString(), props.getBaseUrl(), "@ConfigurationProperties sees it");
        assertEquals(users.port(), props.getPort().intValue(), "portProperty bound");
        assertEquals(payments.uri().toString(),
                ctx.getEnvironment().getProperty("payment-client.base-url"));
    }

    @Test
    void onlyOneInfraBeanIsAdded() {
        assertEquals(1, ctx.getBeanNamesForType(RiftTestContext.class).length,
                "exactly one infrastructure bean");
        assertEquals(0, ctx.getBeanNamesForType(Rift.class).length, "Rift is not a context bean");
        assertEquals(0, ctx.getBeanNamesForType(Imposter.class).length, "imposters are not context beans");
    }
}
