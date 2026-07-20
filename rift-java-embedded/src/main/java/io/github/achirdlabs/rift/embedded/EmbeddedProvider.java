package io.github.achirdlabs.rift.embedded;

import io.github.achirdlabs.rift.EmbeddedOptions;
import io.github.achirdlabs.rift.transport.EmbeddedEngine;
import io.github.achirdlabs.rift.transport.EmbeddedEngineProvider;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;

/** The {@link EmbeddedEngineProvider} discovered via {@code ServiceLoader} by {@code rift-java-core}. */
public final class EmbeddedProvider implements EmbeddedEngineProvider {

    private static final Logger LOG = System.getLogger(EmbeddedProvider.class.getName());

    @Override
    public boolean isAvailable() {
        // Non-extracting probe: checks env/property/classpath resolvability without writing a temp file.
        return NativeLibraryResolver.resolvable(EmbeddedOptions.builder().build());
    }

    @Override
    public EmbeddedEngine start(EmbeddedOptions options) {
        NativeLibraryResolver.ResolvedLibrary resolved = NativeLibraryResolver.resolve(options);
        EmbeddedTransport transport = EmbeddedTransport.open(resolved.path());
        if (options.serveAdminEagerly()) {
            transport.adminUri();
        }
        Runnable onClose = resolved.temporary()
                ? () -> {
                    try {
                        Files.deleteIfExists(resolved.path());
                    } catch (IOException e) {
                        // Best-effort: the deleteOnExit hook registered at extraction is the fallback,
                        // but leave a trail so a stale temp lib isn't a silent mystery later.
                        LOG.log(Level.DEBUG, "failed to delete extracted native library " + resolved.path()
                                + "; relying on deleteOnExit", e);
                    }
                }
                : () -> { };
        return new EmbeddedEngine(transport, onClose);
    }
}
