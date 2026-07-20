package io.github.achirdlabs.rift;

/**
 * How a proxy started via {@link Imposter#startRecording} captures upstream responses as new
 * stubs. Maps directly onto the engine's {@code proxy.mode} values.
 */
public enum RecordMode {

    /** Proxies each matching request and records only the first response as a permanent stub. */
    ONCE,

    /** Proxies every matching request, always forwarding live and recording every response. */
    ALWAYS,

    /** Proxies every matching request without ever recording a new stub. */
    TRANSPARENT
}
