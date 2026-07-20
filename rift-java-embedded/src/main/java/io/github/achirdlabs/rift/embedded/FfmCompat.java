package io.github.achirdlabs.rift.embedded;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Bridges the two FFM string operations that were renamed between the JDK 21 preview API and the
 * JDK 22 stable API:
 *
 * <ul>
 *   <li>read a C string: JDK 22 {@code MemorySegment.getString(long)}; JDK 21 preview
 *       {@code MemorySegment.getUtf8String(long)}</li>
 *   <li>allocate a C string: JDK 22 {@code Arena.allocateFrom(String)}; JDK 21 preview
 *       {@code Arena.allocateUtf8String(String)}</li>
 * </ul>
 *
 * <p>Both are resolved once, via {@link MethodHandle}s bound at class-load, preferring the JDK 22
 * name and falling back to the JDK 21 preview name. This source file deliberately never spells
 * either renamed method as a direct call — only as a string literal passed to {@code findVirtual} —
 * so it compiles unchanged under both {@code --release 22} (this module) and
 * {@code --release 21 --enable-preview} (rift-java-embedded-jdk21, which reuses this same file).
 */
final class FfmCompat {

    private static final MethodHandle GET_STRING;
    private static final MethodHandle ALLOCATE_FROM;

    static {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        GET_STRING = resolveGetString(lookup);
        ALLOCATE_FROM = resolveAllocateFrom(lookup);
    }

    private FfmCompat() {
    }

    private static MethodHandle resolveGetString(MethodHandles.Lookup lookup) {
        MethodType signature = MethodType.methodType(String.class, long.class);
        try {
            return lookup.findVirtual(MemorySegment.class, "getString", signature);
        } catch (NoSuchMethodException | IllegalAccessException jdk22NotAvailable) {
            try {
                return lookup.findVirtual(MemorySegment.class, "getUtf8String", signature);
            } catch (NoSuchMethodException | IllegalAccessException jdk21NotAvailable) {
                throw new ExceptionInInitializerError(
                        "FfmCompat: neither MemorySegment.getString(long) (JDK 22+) nor "
                                + "MemorySegment.getUtf8String(long) (JDK 21 preview) is available on this JVM "
                                + "(" + Runtime.version() + ")");
            }
        }
    }

    private static MethodHandle resolveAllocateFrom(MethodHandles.Lookup lookup) {
        MethodType signature = MethodType.methodType(MemorySegment.class, String.class);
        try {
            return lookup.findVirtual(Arena.class, "allocateFrom", signature);
        } catch (NoSuchMethodException | IllegalAccessException jdk22NotAvailable) {
            try {
                return lookup.findVirtual(Arena.class, "allocateUtf8String", signature);
            } catch (NoSuchMethodException | IllegalAccessException jdk21NotAvailable) {
                throw new ExceptionInInitializerError(
                        "FfmCompat: neither Arena.allocateFrom(String) (JDK 22+) nor "
                                + "Arena.allocateUtf8String(String) (JDK 21 preview) is available on this JVM "
                                + "(" + Runtime.version() + ")");
            }
        }
    }

    /** Reads a returned C string, reinterpreting the zero-length ADDRESS segment before reading it. */
    static String readCString(MemorySegment segment) {
        MemorySegment reinterpreted = segment.reinterpret(Long.MAX_VALUE);
        try {
            return (String) GET_STRING.invoke(reinterpreted, 0L);
        } catch (Throwable t) {
            throw new RuntimeException("FfmCompat: failed to read a native C string", t);
        }
    }

    /** Allocates a NUL-terminated native copy of {@code s} in {@code arena}. */
    static MemorySegment allocateCString(Arena arena, String s) {
        try {
            return (MemorySegment) ALLOCATE_FROM.invoke(arena, s);
        } catch (Throwable t) {
            throw new RuntimeException("FfmCompat: failed to allocate a native C string", t);
        }
    }
}
