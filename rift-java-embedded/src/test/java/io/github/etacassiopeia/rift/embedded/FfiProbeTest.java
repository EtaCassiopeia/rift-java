package io.github.etacassiopeia.rift.embedded;

import io.github.etacassiopeia.rift.error.EngineUnavailable;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The v2 ABI is detected by probing the {@code rift_build_info} symbol. A pre-v2 (or wrong) library
 * lacks it, and binding must fail fast with a precise "requires rift >= …" error rather than a raw
 * linkage failure deep in a later call. Uses a fake {@link SymbolLookup} so no real v1 lib is needed.
 */
class FfiProbeTest {

    @Test
    void missingBuildInfoSymbolReportsUnsupportedLibrary() {
        SymbolLookup noSymbols = name -> Optional.empty();

        EngineUnavailable ex = assertThrows(EngineUnavailable.class,
                () -> RiftFfi.bind(noSymbols, Linker.nativeLinker()));

        String msg = ex.getMessage();
        assertTrue(msg.contains("0.12.0") || msg.toLowerCase().contains("rift_build_info"),
                "message should name the required version or the missing probe symbol: " + msg);
    }
}
