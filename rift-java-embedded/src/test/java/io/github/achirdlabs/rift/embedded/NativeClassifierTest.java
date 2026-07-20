package io.github.achirdlabs.rift.embedded;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The {@code <os>-<arch>} classifier (matching rift-java-natives jars) computed from the JVM's os props. */
class NativeClassifierTest {

    @Test
    void mapsOsAndArchToTheNativesClassifier() {
        assertEquals("darwin-aarch64", NativeLibraryResolver.classifierFor("Mac OS X", "aarch64", false));
        assertEquals("darwin-x86_64", NativeLibraryResolver.classifierFor("Mac OS X", "x86_64", false));
        assertEquals("linux-x86_64", NativeLibraryResolver.classifierFor("Linux", "amd64", false));
        assertEquals("linux-aarch64", NativeLibraryResolver.classifierFor("Linux", "aarch64", false));
        assertEquals("linux-musl-x86_64", NativeLibraryResolver.classifierFor("Linux", "x86_64", true));
        assertEquals("windows-x86_64", NativeLibraryResolver.classifierFor("Windows 11", "amd64", false));
    }
}
