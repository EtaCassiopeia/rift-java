package io.github.etacassiopeia.rift.embedded;

import io.github.etacassiopeia.rift.error.EngineError;
import io.github.etacassiopeia.rift.error.EngineUnavailable;
import io.github.etacassiopeia.rift.json.JsonBool;
import io.github.etacassiopeia.rift.json.JsonObject;
import io.github.etacassiopeia.rift.json.JsonValue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Optional;

/** Ownership + error discipline over {@link RiftFfi}, holding the live {@code RiftHandle} segment. */
final class FfiCalls {

    private final RiftFfi ffi;
    private final MemorySegment handle;
    private volatile boolean stopped;

    private FfiCalls(RiftFfi ffi, MemorySegment handle) {
        this.ffi = ffi;
        this.handle = handle;
    }

    static FfiCalls start(RiftFfi ffi) {
        MemorySegment handle = ffi.start();
        if (RiftFfi.isNull(handle)) {
            throw new EngineUnavailable("failed to start the embedded rift engine");
        }
        return new FfiCalls(ffi, handle);
    }

    /**
     * A downcall into a stopped handle (or, worse, an unloaded library) is undefined behaviour at the
     * native level — segfault, not a Java exception. Guard every call so use-after-close fails cleanly.
     */
    private void ensureLive() {
        if (stopped) {
            throw new IllegalStateException("the embedded rift engine is closed");
        }
    }

    int createImposter(JsonValue def) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            int port = ffi.createImposter(handle, FfmCompat.allocateCString(args, def.toJson()));
            if (port == 0) {
                throw engineError();
            }
            return port;
        }
    }

    void replaceStubs(int port, JsonValue stubs) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            int rc = ffi.replaceStubs(handle, port, FfmCompat.allocateCString(args, stubs.toJson()));
            if (rc != 0) {
                throw engineError();
            }
        }
    }

    void deleteImposter(int port) {
        ensureLive();
        if (ffi.deleteImposter(handle, port) != 0) {
            throw engineError();
        }
    }

    void deleteAll() {
        ensureLive();
        if (ffi.deleteAll(handle) != 0) {
            throw engineError();
        }
    }

    JsonValue recorded(int port) {
        ensureLive();
        return readJsonAndFree(ffi.recorded(handle, port));
    }

    JsonValue applyConfig(JsonValue config) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            return readJsonAndFree(ffi.applyConfig(handle, FfmCompat.allocateCString(args, config.toJson())));
        }
    }

    Optional<JsonValue> flowStateGet(int port, String flowId, String key) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            MemorySegment seg = ffi.flowStateGet(handle, port, FfmCompat.allocateCString(args, flowId),
                    FfmCompat.allocateCString(args, key));
            JsonValue envelope = readJsonAndFree(seg);
            if (envelope instanceof JsonObject obj && obj.get("found") instanceof JsonBool found && found.value()) {
                return Optional.of(obj.get("value"));
            }
            return Optional.empty();
        }
    }

    void flowStatePut(int port, String flowId, String key, JsonValue value) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            int rc = ffi.flowStatePut(handle, port, FfmCompat.allocateCString(args, flowId), FfmCompat.allocateCString(args, key),
                    FfmCompat.allocateCString(args, value.toJson()));
            if (rc != 0) {
                throw engineError();
            }
        }
    }

    void flowStateDelete(int port, String flowId, String key) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            int rc = ffi.flowStateDelete(handle, port, FfmCompat.allocateCString(args, flowId),
                    FfmCompat.allocateCString(args, key));
            if (rc != 0) {
                throw engineError();
            }
        }
    }

    void spaceAddStub(int port, String flowId, JsonValue stub) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            int rc = ffi.spaceAddStub(handle, port, FfmCompat.allocateCString(args, flowId),
                    FfmCompat.allocateCString(args, stub.toJson()));
            if (rc != 0) {
                throw engineError();
            }
        }
    }

    JsonValue spaceListStubs(int port, String flowId) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            return readJsonAndFree(ffi.spaceListStubs(handle, port, FfmCompat.allocateCString(args, flowId)));
        }
    }

    void spaceDelete(int port, String flowId) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            int rc = ffi.spaceDelete(handle, port, FfmCompat.allocateCString(args, flowId));
            if (rc != 0) {
                throw engineError();
            }
        }
    }

    JsonValue spaceRecorded(int port, String flowId) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            return readJsonAndFree(ffi.spaceRecorded(handle, port, FfmCompat.allocateCString(args, flowId)));
        }
    }

    JsonValue buildInfo() {
        ensureLive();
        // The static rift_build_info() pointer is never freed.
        return JsonValue.parse(RiftFfi.readString(ffi.buildInfo()));
    }

    JsonValue serveAdmin(JsonValue options) {
        ensureLive();
        try (Arena args = Arena.ofConfined()) {
            return readJsonAndFree(ffi.serveAdmin(handle, FfmCompat.allocateCString(args, options.toJson())));
        }
    }

    void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        ffi.stop(handle);
    }

    private JsonValue readJsonAndFree(MemorySegment segment) {
        if (RiftFfi.isNull(segment)) {
            throw engineError();
        }
        // Copy the string out of native memory before freeing the original pointer.
        String json = RiftFfi.readString(segment);
        ffi.free(segment);
        return JsonValue.parse(json);
    }

    private RuntimeException engineError() {
        MemorySegment errSeg = ffi.lastError();
        if (RiftFfi.isNull(errSeg)) {
            return new EngineError(-1, "rift engine call failed with no error message");
        }
        String message = RiftFfi.readString(errSeg);
        ffi.free(errSeg);
        return new EngineError(-1, message);
    }
}
