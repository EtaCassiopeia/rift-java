package io.github.achirdlabs.rift.spawn;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.SpawnOptions;
import io.github.achirdlabs.rift.error.EngineUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real process lifecycle by pointing {@link Rift#spawn(SpawnOptions)} at a generated shell
 * launcher that execs {@link FakeRift}. Unix-only: the launcher is a {@code /bin/sh} script (the Windows
 * DLL/exe lane is covered by the conformance suite once a real engine binary ships).
 */
@DisabledOnOs(OS.WINDOWS)
class SpawnLifecycleTest {

    /** Writes a {@code sh} launcher that records the java PID (via {@code exec}, PID is preserved) then runs FakeRift. */
    private static Path launcher(Path dir, Path pidFile, String... fakeArgs) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String cp = System.getProperty("java.class.path");
        String extra = String.join(" ", fakeArgs);
        String script = "#!/bin/sh\n"
                + "echo $$ > \"" + pidFile + "\"\n"
                + "exec \"" + java + "\" -cp \"" + cp + "\" io.github.achirdlabs.rift.spawn.FakeRift " + extra + " \"$@\"\n";
        Path launcher = dir.resolve("rift-launcher.sh");
        Files.writeString(launcher, script);
        launcher.toFile().setExecutable(true);
        return launcher;
    }

    private static long awaitPid(Path pidFile) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (Files.exists(pidFile)) {
                String s = Files.readString(pidFile).trim();
                if (!s.isEmpty()) {
                    return Long.parseLong(s);
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("fake rift never wrote its pid");
    }

    private static void awaitDead(long pid) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (ProcessHandle.of(pid).map(h -> !h.isAlive()).orElse(true)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("process " + pid + " is still alive after close()");
    }

    @Test
    void spawnRunsThenCloseLeavesNoOrphan(@TempDir Path dir) throws Exception {
        Path pidFile = dir.resolve("fake.pid");
        SpawnOptions opts = SpawnOptions.builder()
                .binaryPath(launcher(dir, pidFile))
                .startupTimeout(Duration.ofSeconds(20))
                .build();
        Rift rift = Rift.spawn(opts);
        long pid = awaitPid(pidFile);
        assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive(), "spawned process should be alive");
        rift.close();
        awaitDead(pid);
    }

    @Test
    void spawnedRiftRoundTripsAnAdminCall(@TempDir Path dir) throws Exception {
        SpawnOptions opts = SpawnOptions.builder()
                .binaryPath(launcher(dir, dir.resolve("fake.pid")))
                .startupTimeout(Duration.ofSeconds(20))
                .build();
        try (Rift rift = Rift.spawn(opts)) {
            // a real admin round-trip through the spawned process (not just the health poll)
            Imposter imp = rift.create(imposter("x").port(4545));
            assertEquals(4545, imp.port());
        }
    }

    @Test
    void crashDuringStartupThrowsEngineUnavailableReportingTheExitCode(@TempDir Path dir) throws Exception {
        SpawnOptions opts = SpawnOptions.builder()
                .binaryPath(launcher(dir, dir.resolve("fake.pid"), "--crash"))
                .startupTimeout(Duration.ofSeconds(10))
                .build();
        EngineUnavailable ex = assertThrows(EngineUnavailable.class, () -> Rift.spawn(opts));
        assertTrue(ex.getMessage().contains("exited with code"), ex.getMessage());
    }

    @Test
    void startupTimeoutThrowsEngineUnavailableWithLogTail(@TempDir Path dir) throws Exception {
        SpawnOptions opts = SpawnOptions.builder()
                .binaryPath(launcher(dir, dir.resolve("fake.pid"), "--hang"))
                .startupTimeout(Duration.ofSeconds(2))
                .build();
        EngineUnavailable ex = assertThrows(EngineUnavailable.class, () -> Rift.spawn(opts));
        assertTrue(ex.getMessage().contains("fake rift hanging"),
                "timeout error should carry the captured process log tail: " + ex.getMessage());
    }

    @Test
    void twoConcurrentSpawnsUseDistinctEphemeralPorts(@TempDir Path dir) throws Exception {
        Path launcher = launcher(dir, dir.resolve("a.pid"));
        SpawnOptions opts = SpawnOptions.builder().binaryPath(launcher).startupTimeout(Duration.ofSeconds(20)).build();
        try (Rift a = Rift.spawn(opts); Rift b = Rift.spawn(opts)) {
            assertNotEquals(a.adminUri().getPort(), b.adminUri().getPort());
            // both admin endpoints answer (health-polled), so both processes are live
            assertEquals("127.0.0.1", a.adminUri().getHost());
        }
    }
}
