package io.github.achirdlabs.rift.junit5;

import io.github.achirdlabs.rift.dsl.ImposterSpec;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.Events;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EngineTestKit.engine;

/**
 * @RiftGolden misconfiguration paths (#25 Part 2), driven over a fake admin via EngineTestKit — no
 * Docker. The happy-path capture→replay round-trip is covered by the real-engine RiftGoldenIT.
 */
class RiftGoldenUnitTest {

    static final FakeRiftAdmin ADMIN = new FakeRiftAdmin();

    static {
        System.setProperty("rift.golden.unit.admin", ADMIN.baseUri().toString());
    }

    @Test
    void stublessGoldenFileFailsLoudly() throws Exception {
        Path file = Path.of("target/rift-golden-unit/empty.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"stubs\":[]}");   // present but records nothing → REPLAY must not serve blindly
        assertFailureContains(EmptyGoldenFixture.class, "no stubs");
    }

    @Test
    void unknownGoldenImposterNameFailsClearly() {
        assertFailureContains(UnknownImposterFixture.class, "nope");
    }

    private static void assertFailureContains(Class<?> fixture, String expected) {
        Events containers = engine("junit-jupiter").selectors(selectClass(fixture)).execute().containerEvents();
        String message = containers.failed().stream()
                .map(event -> event.getPayload(TestExecutionResult.class).orElseThrow())
                .map(result -> result.getThrowable().map(Throwable::getMessage).orElse(""))
                .reduce("", (a, b) -> a + b);
        assertTrue(message.contains(expected), "expected a golden failure mentioning '" + expected + "', got: " + message);
    }

    @RiftTest(transport = Transport.CONNECT, adminUri = "${rift.golden.unit.admin}")
    @RiftGolden(origin = "http://unused", file = "target/rift-golden-unit/empty.json")
    static class EmptyGoldenFixture {
        @RiftImposter
        static ImposterSpec users = imposter("users").record();

        @Test
        void unreached() {
        }
    }

    @RiftTest(transport = Transport.CONNECT, adminUri = "${rift.golden.unit.admin}")
    @RiftGolden(origin = "http://unused", file = "target/rift-golden-unit/missing.json", imposter = "nope")
    static class UnknownImposterFixture {
        @RiftImposter
        static ImposterSpec users = imposter("users").record();

        @Test
        void unreached() {
        }
    }
}
