package io.github.etacassiopeia.rift.embedded;

import io.github.etacassiopeia.rift.EmbeddedOptions;
import io.github.etacassiopeia.rift.error.EngineUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The librift_ffi resolution order: explicit {@code libraryPath} → {@code -Drift.ffi.lib} → {@code
 * $RIFT_FFI_LIB} → a {@code native/<classifier>/librift_ffi.<ext>} classpath resource (extracted to a
 * temp file) → a precise {@link EngineUnavailable}. Uses injected env/sysprops/classifier/classloader
 * so it runs identically on every platform (no real library needed).
 */
class NativeLibraryResolverTest {

    private static final ClassLoader TEST_LOADER = NativeLibraryResolverTest.class.getClassLoader();

    @Test
    void explicitLibraryPathWinsAndIsNotTemporary(@TempDir Path dir) throws Exception {
        Path lib = Files.writeString(dir.resolve("librift_ffi.dylib"), "x");
        EmbeddedOptions options = EmbeddedOptions.builder().libraryPath(lib).build();

        NativeLibraryResolver.ResolvedLibrary r = NativeLibraryResolver.resolve(
                options, Map.of("RIFT_FFI_LIB", "/ignored"), Map.of("rift.ffi.lib", "/ignored"),
                "linux-x86_64", TEST_LOADER);

        assertEquals(lib, r.path());
        assertFalse(r.temporary());
    }

    @Test
    void systemPropertyBeatsEnv(@TempDir Path dir) throws Exception {
        Path fromProp = Files.writeString(dir.resolve("prop.so"), "p");
        Path fromEnv = Files.writeString(dir.resolve("env.so"), "e");

        NativeLibraryResolver.ResolvedLibrary r = NativeLibraryResolver.resolve(
                EmbeddedOptions.builder().build(),
                Map.of("RIFT_FFI_LIB", fromEnv.toString()),
                Map.of("rift.ffi.lib", fromProp.toString()),
                "linux-x86_64", TEST_LOADER);

        assertEquals(fromProp, r.path());
    }

    @Test
    void envUsedWhenNoPropertyOrExplicit(@TempDir Path dir) throws Exception {
        Path fromEnv = Files.writeString(dir.resolve("env.so"), "e");

        NativeLibraryResolver.ResolvedLibrary r = NativeLibraryResolver.resolve(
                EmbeddedOptions.builder().build(),
                Map.of("RIFT_FFI_LIB", fromEnv.toString()), Map.of(), "linux-x86_64", TEST_LOADER);

        assertEquals(fromEnv, r.path());
    }

    @Test
    void classpathResourceIsExtractedToATempFile() throws Exception {
        // src/test/resources/native/linux-x86_64/librift_ffi.so is a tiny fake payload.
        NativeLibraryResolver.ResolvedLibrary r = NativeLibraryResolver.resolve(
                EmbeddedOptions.builder().build(), Map.of(), Map.of(), "linux-x86_64", TEST_LOADER);

        assertTrue(r.temporary(), "a classpath-extracted lib is temporary");
        assertTrue(Files.exists(r.path()));
        assertArrayEquals("FAKE-LIBRIFT-FFI-FOR-EXTRACTION-TEST".getBytes(), Files.readAllBytes(r.path()));
        Files.deleteIfExists(r.path());
    }

    @Test
    void resolvableProbesWithoutExtracting() {
        // classpath resource present → resolvable true, and (unlike resolve) no temp file is written.
        assertTrue(NativeLibraryResolver.resolvable(
                EmbeddedOptions.builder().build(), Map.of(), Map.of(), "linux-x86_64", TEST_LOADER));
        // isolated loader, no env/prop/path → not resolvable.
        ClassLoader empty = new URLClassLoader(new java.net.URL[0], null);
        assertFalse(NativeLibraryResolver.resolvable(
                EmbeddedOptions.builder().build(), Map.of(), Map.of(), "darwin-aarch64", empty));
    }

    @Test
    void explicitPathThatIsNotARegularFileFailsClearly(@TempDir Path dir) {
        EmbeddedOptions options = EmbeddedOptions.builder().libraryPath(dir.resolve("missing.so")).build();
        EngineUnavailable ex = assertThrows(EngineUnavailable.class, () -> NativeLibraryResolver.resolve(
                options, Map.of(), Map.of(), "linux-x86_64", TEST_LOADER));
        assertTrue(ex.getMessage().contains("missing.so"), ex.getMessage());
        assertTrue(ex.getMessage().contains("libraryPath"), ex.getMessage());
    }

    @Test
    void nothingResolvableNamesTheNativesArtifactAndClassifier() {
        // An isolated loader with no rift resources, no env/prop/path.
        ClassLoader empty = new URLClassLoader(new java.net.URL[0], null);
        EngineUnavailable ex = assertThrows(EngineUnavailable.class, () -> NativeLibraryResolver.resolve(
                EmbeddedOptions.builder().build(), Map.of(), Map.of(), "darwin-aarch64", empty));

        assertTrue(ex.getMessage().contains("rift-java-natives"), ex.getMessage());
        assertTrue(ex.getMessage().contains("darwin-aarch64"), ex.getMessage());
    }
}
