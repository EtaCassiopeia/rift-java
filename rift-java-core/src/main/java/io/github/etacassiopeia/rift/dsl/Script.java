package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.model.RiftScriptConfig;

import java.util.Optional;

/**
 * A {@code _rift} script source, for use with {@link RiftDsl#script(Script)}: inline Rhai or
 * JavaScript source, a file reference, or a named reference resolved by the engine's configuration.
 * Exactly one of code/file/ref is set, matching {@link RiftScriptConfig}'s own contract.
 */
public final class Script {

    private final RiftScriptConfig config;

    private Script(RiftScriptConfig config) {
        this.config = config;
    }

    /** An inline Rhai script. */
    public static Script rhai(String inlineCode) {
        return new Script(new RiftScriptConfig(Optional.of("rhai"), Optional.of(inlineCode), Optional.empty(), Optional.empty()));
    }

    /** An inline JavaScript script. */
    public static Script js(String inlineCode) {
        return new Script(new RiftScriptConfig(Optional.of("js"), Optional.of(inlineCode), Optional.empty(), Optional.empty()));
    }

    /** A Rhai script loaded from the given file path at engine start. */
    public static Script rhaiFile(String path) {
        return new Script(new RiftScriptConfig(Optional.of("rhai"), Optional.empty(), Optional.of(path), Optional.empty()));
    }

    /** A JavaScript script loaded from the given file path at engine start. */
    public static Script jsFile(String path) {
        return new Script(new RiftScriptConfig(Optional.of("js"), Optional.empty(), Optional.of(path), Optional.empty()));
    }

    /** A named script reference, resolved against scripts registered elsewhere in the engine's configuration. */
    public static Script ref(String name) {
        return new Script(new RiftScriptConfig(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(name)));
    }

    /** The underlying model configuration this script builds. */
    RiftScriptConfig toConfig() {
        return config;
    }
}
