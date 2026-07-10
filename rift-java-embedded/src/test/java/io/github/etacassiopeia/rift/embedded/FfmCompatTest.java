package io.github.etacassiopeia.rift.embedded;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two FFM string helpers were renamed between the JDK 21 preview API
 * ({@code allocateUtf8String}/{@code getUtf8String}) and the JDK 22 stable API
 * ({@code allocateFrom}/{@code getString}). {@link FfmCompat} bridges both via method handles resolved
 * at class load, so this same test runs on both toolchains and pins the round-trip either way.
 */
class FfmCompatTest {

    @Test
    void allocatesAndReadsBackAUtf8CString() {
        String original = "héllo-世界-🚀"; // multibyte UTF-8 + a surrogate pair
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = FfmCompat.allocateCString(arena, original);
            assertEquals(original, FfmCompat.readCString(segment));
        }
    }

    @Test
    void readsBackAnEmptyString() {
        try (Arena arena = Arena.ofConfined()) {
            assertEquals("", FfmCompat.readCString(FfmCompat.allocateCString(arena, "")));
        }
    }
}
