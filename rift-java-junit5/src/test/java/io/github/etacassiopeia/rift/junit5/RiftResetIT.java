package io.github.etacassiopeia.rift.junit5;

import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.dsl.ImposterSpec;
import io.github.etacassiopeia.rift.dsl.RiftDsl;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code Reset.PER_TEST} clears recorded requests between test methods (ordered to observe the transition). */
@RiftTest(transport = Transport.CONNECT, adminUri = "${rift.junit.reset}", reset = Reset.PER_TEST)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiftResetIT {

    static final FakeRiftAdmin ADMIN = new FakeRiftAdmin();

    static {
        System.setProperty("rift.junit.reset", ADMIN.baseUri().toString());
    }

    @RiftImposter
    static ImposterSpec usersSpec = RiftDsl.imposter("users").record();

    @InjectImposter("users")
    Imposter users;

    @Test
    @Order(1)
    void recordedRequestIsVisibleWithinTheSameTest() {
        ADMIN.pushRecorded(users.port(), "GET", "/seen");
        assertEquals(1, users.recorded().size(), "the imposter recorded the simulated request");
    }

    @Test
    @Order(2)
    void perTestResetClearedRecordedBeforeThisMethod() {
        assertTrue(users.recorded().isEmpty(),
                "PER_TEST reset cleared recorded requests before the next method ran");
    }
}
