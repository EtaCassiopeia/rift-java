package io.github.achirdlabs.rift.spring;

import io.github.achirdlabs.rift.Imposter;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reset.PER_TEST clears recorded requests between test methods (ordered to observe the transition). */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResetTest extends RiftSpringTestBase {

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
