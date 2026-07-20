package io.github.achirdlabs.rift;

import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * How a transport reacts to a running engine older than the SDK requires.
 *
 * <p>When a caller does not set a mode explicitly on the options builder, the default is resolved
 * from the {@code rift.versionCheck} system property, then the {@code RIFT_VERSION_CHECK}
 * environment variable, then {@link #FAIL} — so the whole SDK's preflight can be tuned from the
 * launch command without touching code (mirroring the {@code rift.ffi.lib} library-path override):
 *
 * <pre>{@code
 * -Drift.ffi.lib=native/librift_ffi.dylib -Drift.versionCheck=off
 * }</pre>
 *
 * Accepted tokens are {@code off} / {@code warn} / {@code fail} (case-insensitive); any other value
 * is rejected with an {@link IllegalArgumentException}.
 */
public enum VersionCheck {

    /** Throw {@code EngineUnavailable} and refuse to connect. */
    FAIL,

    /** Log a warning and connect anyway. */
    WARN,

    /** Skip the preflight entirely. */
    OFF;

    /**
     * Parses one {@code off|warn|fail} token (case-insensitive), or returns {@code null} for a
     * {@code null}/blank token so the caller can fall through to the next source.
     *
     * @throws IllegalArgumentException if the token is non-blank but unrecognized
     */
    static VersionCheck parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "off" -> OFF;
            case "warn" -> WARN;
            case "fail" -> FAIL;
            default -> throw new IllegalArgumentException(
                    "invalid version check '" + token + "' (expected one of: off, warn, fail)");
        };
    }

    /**
     * Resolves the default mode from {@code rift.versionCheck} (property), then
     * {@code RIFT_VERSION_CHECK} (env), then {@link #FAIL}. The lookups are injectable so the
     * precedence is unit-testable without mutating real process state.
     */
    static VersionCheck resolveDefault(UnaryOperator<String> sysProps, UnaryOperator<String> env) {
        VersionCheck fromProp = parseToken(sysProps.apply("rift.versionCheck"));
        if (fromProp != null) {
            return fromProp;
        }
        VersionCheck fromEnv = parseToken(env.apply("RIFT_VERSION_CHECK"));
        if (fromEnv != null) {
            return fromEnv;
        }
        return FAIL;
    }

    /** The builder default: {@link #resolveDefault(UnaryOperator, UnaryOperator)} over the real process. */
    static VersionCheck resolveDefault() {
        return resolveDefault(System::getProperty, System::getenv);
    }
}
