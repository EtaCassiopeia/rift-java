package io.github.etacassiopeia.rift;

/** How {@link Rift#connect(ConnectOptions)} reacts to a running engine older than it requires. */
public enum VersionCheck {

    /** Throw {@code EngineUnavailable} and refuse to connect. */
    FAIL,

    /** Log a warning and connect anyway. */
    WARN,

    /** Skip the preflight entirely. */
    OFF
}
