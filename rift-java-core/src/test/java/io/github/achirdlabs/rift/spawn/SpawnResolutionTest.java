package io.github.achirdlabs.rift.spawn;

import io.github.achirdlabs.rift.SpawnOptions;
import io.github.achirdlabs.rift.error.EngineUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binary resolution order, exercised against an injected environment map (JUnit can't set {@code
 * System.getenv}), so the tests are deterministic and portable — no real {@code rift} binary needed.
 */
class SpawnResolutionTest {

    @Test
    void explicitBinaryPathWinsOverEverything(@TempDir Path dir) throws Exception {
        Path binary = Files.createFile(dir.resolve("rift"));
        binary.toFile().setExecutable(true);
        SpawnOptions opts = SpawnOptions.builder().binaryPath(binary).build();
        // even with an env pointing elsewhere, the explicit path wins
        Path resolved = BinaryResolver.resolve(opts, Map.of("RIFT_BINARY_PATH", "/nope/rift"));
        assertEquals(binary, resolved);
    }

    @Test
    void riftBinaryPathEnvIsUsedWhenNoExplicitPath(@TempDir Path dir) throws Exception {
        Path binary = Files.createFile(dir.resolve("rift"));
        binary.toFile().setExecutable(true);
        Path resolved = BinaryResolver.resolve(SpawnOptions.builder().build(),
                Map.of("RIFT_BINARY_PATH", binary.toString()));
        assertEquals(binary, resolved);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void pathScanFindsExecutableRift(@TempDir Path dir) throws Exception {
        Path bin = Files.createFile(dir.resolve("rift"));
        bin.toFile().setExecutable(true);
        Path resolved = BinaryResolver.resolve(SpawnOptions.builder().build(), Map.of("PATH", dir.toString()));
        assertEquals(bin, resolved);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void versionCacheHitIsUsed(@TempDir Path dir) throws Exception {
        Path cacheBin = dir.resolve(".cache/rift-java/binaries/rift-0.12.0/rift");
        Files.createDirectories(cacheBin.getParent());
        Files.createFile(cacheBin);
        cacheBin.toFile().setExecutable(true);
        Path resolved = BinaryResolver.resolve(SpawnOptions.builder().version("0.12.0").build(),
                Map.of("HOME", dir.toString(), "PATH", ""));
        assertEquals(cacheBin, resolved);
    }

    @Test
    void skipBinaryDownloadThrowsListingTheVersionCacheStep() {
        // RIFT_SKIP_BINARY_DOWNLOAD is the other offline-equivalent env var; its message lists the cache step
        EngineUnavailable ex = assertThrows(EngineUnavailable.class,
                () -> BinaryResolver.resolve(SpawnOptions.builder().version("9.9.9").build(),
                        Map.of("RIFT_SKIP_BINARY_DOWNLOAD", "1", "PATH", "", "HOME", "/nonexistent-home-xyz")));
        assertTrue(ex.getMessage().toLowerCase().contains("version cache"), ex.getMessage());
    }

    @Test
    void offlineWithNoResolvableBinaryThrowsListingTheStepsTried() {
        // RIFT_OFFLINE set, no binaryPath, empty PATH, HOME with no cache → all local steps miss, download skipped
        EngineUnavailable ex = assertThrows(EngineUnavailable.class,
                () -> BinaryResolver.resolve(SpawnOptions.builder().build(),
                        Map.of("RIFT_OFFLINE", "1", "PATH", "", "HOME", "/nonexistent-home-xyz")));
        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("rift_binary_path"), "message should list the RIFT_BINARY_PATH step: " + ex.getMessage());
        assertTrue(msg.contains("path"), "message should mention the PATH lookup: " + ex.getMessage());
    }
}
