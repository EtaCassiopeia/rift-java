package io.github.etacassiopeia.rift.spawn;

import io.github.etacassiopeia.rift.error.EngineUnavailable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The release target triple + archive/binary naming for the current JVM's OS/arch, mirroring the
 * matrix published by rift's release pipeline (and consumed identically by rift-node).
 */
final class TargetPlatform {

    private final String triple;
    private final boolean windows;

    private TargetPlatform(String triple, boolean windows) {
        this.triple = triple;
        this.windows = windows;
    }

    static TargetPlatform current() {
        return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""),
                Files.exists(Path.of("/etc/alpine-release")));
    }

    /** Package-private and fully parameterized so the whole OS/arch matrix is testable without a real host. */
    static TargetPlatform of(String osName, String osArch, boolean musl) {
        String os = osName.toLowerCase();
        String arch = normalizeArch(osArch);
        if (os.contains("win")) {
            if (!arch.equals("x86_64")) {
                throw unsupported(os, arch);
            }
            return new TargetPlatform("x86_64-pc-windows-msvc", true);
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return switch (arch) {
                case "x86_64" -> new TargetPlatform("x86_64-apple-darwin", false);
                case "aarch64" -> new TargetPlatform("aarch64-apple-darwin", false);
                default -> throw unsupported(os, arch);
            };
        }
        if (os.contains("linux")) {
            return switch (arch) {
                case "x86_64" -> new TargetPlatform(musl ? "x86_64-unknown-linux-musl" : "x86_64-unknown-linux-gnu", false);
                case "aarch64" -> new TargetPlatform("aarch64-unknown-linux-gnu", false);
                default -> throw unsupported(os, arch);
            };
        }
        throw unsupported(os, arch);
    }

    String triple() {
        return triple;
    }

    private static String normalizeArch(String arch) {
        String a = arch.toLowerCase();
        if (a.equals("amd64") || a.equals("x86_64")) {
            return "x86_64";
        }
        if (a.equals("aarch64") || a.equals("arm64")) {
            return "aarch64";
        }
        return a;
    }

    private static EngineUnavailable unsupported(String os, String arch) {
        return new EngineUnavailable("no published rift binary for platform os.name=" + os + " os.arch=" + arch);
    }

    String archiveName(String version) {
        return "rift-v" + version + "-" + triple + (windows ? ".zip" : ".tar.gz");
    }

    String binaryName() {
        return windows ? "rift.exe" : "rift";
    }

    boolean isWindows() {
        return windows;
    }
}
