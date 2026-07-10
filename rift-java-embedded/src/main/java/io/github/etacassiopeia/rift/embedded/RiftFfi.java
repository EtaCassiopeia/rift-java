package io.github.etacassiopeia.rift.embedded;

import io.github.etacassiopeia.rift.error.EngineUnavailable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/** Raw FFM binding to the rift C-ABI v2 ({@code librift_ffi}). One downcall handle per symbol. */
final class RiftFfi {

    private final MethodHandle start;
    private final MethodHandle stop;
    private final MethodHandle createImposter;
    private final MethodHandle replaceStubs;
    private final MethodHandle deleteImposter;
    private final MethodHandle deleteAll;
    private final MethodHandle recorded;
    private final MethodHandle stubWarnings;
    private final MethodHandle applyConfig;
    private final MethodHandle flowStateGet;
    private final MethodHandle flowStatePut;
    private final MethodHandle flowStateDelete;
    private final MethodHandle spaceAddStub;
    private final MethodHandle spaceListStubs;
    private final MethodHandle spaceDelete;
    private final MethodHandle spaceRecorded;
    private final MethodHandle serveAdmin;
    private final MethodHandle buildInfo;
    private final MethodHandle lastError;
    private final MethodHandle free;
    private final MethodHandle startIntercept;
    private final MethodHandle interceptAddRules;
    private final MethodHandle interceptClearRules;
    private final MethodHandle interceptListRules;
    private final MethodHandle interceptCaPem;
    private final MethodHandle interceptExportTruststore;

    private RiftFfi(SymbolLookup lookup, Linker linker) {
        this.start = handle(lookup, linker, "rift_start",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        this.stop = handle(lookup, linker, "rift_stop",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.createImposter = handle(lookup, linker, "rift_create_imposter",
                FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.replaceStubs = handle(lookup, linker, "rift_replace_stubs",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS));
        this.deleteImposter = handle(lookup, linker, "rift_delete_imposter",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT));
        this.deleteAll = handle(lookup, linker, "rift_delete_all",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.recorded = handle(lookup, linker, "rift_recorded",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT));
        this.stubWarnings = handle(lookup, linker, "rift_stub_warnings",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT));
        this.applyConfig = handle(lookup, linker, "rift_apply_config",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.flowStateGet = handle(lookup, linker, "rift_flow_state_get",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.flowStatePut = handle(lookup, linker, "rift_flow_state_put",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.flowStateDelete = handle(lookup, linker, "rift_flow_state_delete",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.spaceAddStub = handle(lookup, linker, "rift_space_add_stub",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.spaceListStubs = handle(lookup, linker, "rift_space_list_stubs",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS));
        this.spaceDelete = handle(lookup, linker, "rift_space_delete",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS));
        this.spaceRecorded = handle(lookup, linker, "rift_space_recorded",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS));
        this.serveAdmin = handle(lookup, linker, "rift_serve_admin",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.buildInfo = handle(lookup, linker, "rift_build_info",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        this.lastError = handle(lookup, linker, "rift_last_error",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        this.free = handle(lookup, linker, "rift_free",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.startIntercept = handle(lookup, linker, "rift_start_intercept",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.interceptAddRules = handle(lookup, linker, "rift_intercept_add_rules",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.interceptClearRules = handle(lookup, linker, "rift_intercept_clear_rules",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.interceptListRules = handle(lookup, linker, "rift_intercept_list_rules",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.interceptCaPem = handle(lookup, linker, "rift_intercept_ca_pem",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.interceptExportTruststore = handle(lookup, linker, "rift_intercept_export_truststore",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }

    private static MethodHandle handle(SymbolLookup lookup, Linker linker, String name, FunctionDescriptor descriptor) {
        return linker.downcallHandle(
                lookup.find(name).orElseThrow(() -> new EngineUnavailable(
                        "rift-java-embedded requires rift >= 0.12.0 (C-ABI v2): the loaded native library is missing " + name)),
                descriptor);
    }

    /**
     * Binds every C-ABI v2 symbol against {@code lookup}. Probes {@code rift_build_info} first so a
     * pre-v2 (or unrelated) library fails fast with a precise message rather than a linkage failure
     * deep inside a later call.
     */
    static RiftFfi bind(SymbolLookup lookup, Linker linker) {
        if (lookup.find("rift_build_info").isEmpty()) {
            throw new EngineUnavailable(
                    "rift-java-embedded requires rift >= 0.12.0 (C-ABI v2): the loaded native library is missing rift_build_info");
        }
        return new RiftFfi(lookup, linker);
    }

    static RiftFfi load(Path lib, Arena libArena) {
        return bind(SymbolLookup.libraryLookup(lib, libArena), Linker.nativeLinker());
    }

    private static Object invoke(MethodHandle handle, Object... args) {
        try {
            return handle.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new RuntimeException("FFM downcall failed: " + t.getMessage(), t);
        }
    }

    MemorySegment start() {
        return (MemorySegment) invoke(start);
    }

    void stop(MemorySegment handle) {
        invoke(stop, handle);
    }

    int createImposter(MemorySegment handle, MemorySegment json) {
        return (short) invoke(createImposter, handle, json) & 0xFFFF;
    }

    int replaceStubs(MemorySegment handle, int port, MemorySegment json) {
        return (int) invoke(replaceStubs, handle, (short) port, json);
    }

    int deleteImposter(MemorySegment handle, int port) {
        return (int) invoke(deleteImposter, handle, (short) port);
    }

    int deleteAll(MemorySegment handle) {
        return (int) invoke(deleteAll, handle);
    }

    MemorySegment recorded(MemorySegment handle, int port) {
        return (MemorySegment) invoke(recorded, handle, (short) port);
    }

    MemorySegment stubWarnings(MemorySegment handle, int port) {
        return (MemorySegment) invoke(stubWarnings, handle, (short) port);
    }

    MemorySegment applyConfig(MemorySegment handle, MemorySegment json) {
        return (MemorySegment) invoke(applyConfig, handle, json);
    }

    MemorySegment flowStateGet(MemorySegment handle, int port, MemorySegment flowId, MemorySegment key) {
        return (MemorySegment) invoke(flowStateGet, handle, (short) port, flowId, key);
    }

    int flowStatePut(MemorySegment handle, int port, MemorySegment flowId, MemorySegment key, MemorySegment valueJson) {
        return (int) invoke(flowStatePut, handle, (short) port, flowId, key, valueJson);
    }

    int flowStateDelete(MemorySegment handle, int port, MemorySegment flowId, MemorySegment key) {
        return (int) invoke(flowStateDelete, handle, (short) port, flowId, key);
    }

    int spaceAddStub(MemorySegment handle, int port, MemorySegment flowId, MemorySegment stubJson) {
        return (int) invoke(spaceAddStub, handle, (short) port, flowId, stubJson);
    }

    MemorySegment spaceListStubs(MemorySegment handle, int port, MemorySegment flowId) {
        return (MemorySegment) invoke(spaceListStubs, handle, (short) port, flowId);
    }

    int spaceDelete(MemorySegment handle, int port, MemorySegment flowId) {
        return (int) invoke(spaceDelete, handle, (short) port, flowId);
    }

    MemorySegment spaceRecorded(MemorySegment handle, int port, MemorySegment flowId) {
        return (MemorySegment) invoke(spaceRecorded, handle, (short) port, flowId);
    }

    MemorySegment serveAdmin(MemorySegment handle, MemorySegment optionsJson) {
        return (MemorySegment) invoke(serveAdmin, handle, optionsJson);
    }

    /** The static build-identity string. Its pointer must never be freed. */
    MemorySegment buildInfo() {
        return (MemorySegment) invoke(buildInfo);
    }

    MemorySegment lastError() {
        return (MemorySegment) invoke(lastError);
    }

    void free(MemorySegment p) {
        invoke(free, p);
    }

    MemorySegment startIntercept(MemorySegment handle, MemorySegment optionsJson) {
        return (MemorySegment) invoke(startIntercept, handle, optionsJson);
    }

    int interceptAddRules(MemorySegment handle, MemorySegment rulesJson) {
        return (int) invoke(interceptAddRules, handle, rulesJson);
    }

    int interceptClearRules(MemorySegment handle) {
        return (int) invoke(interceptClearRules, handle);
    }

    MemorySegment interceptListRules(MemorySegment handle) {
        return (MemorySegment) invoke(interceptListRules, handle);
    }

    MemorySegment interceptCaPem(MemorySegment handle) {
        return (MemorySegment) invoke(interceptCaPem, handle);
    }

    int interceptExportTruststore(MemorySegment handle, MemorySegment format, MemorySegment password, MemorySegment outPath) {
        return (int) invoke(interceptExportTruststore, handle, format, password, outPath);
    }

    /** Reads a returned C string. Delegates to {@link FfmCompat} for the two-JDK method rename. */
    static String readString(MemorySegment segment) {
        return FfmCompat.readCString(segment);
    }

    static boolean isNull(MemorySegment segment) {
        return segment.address() == 0;
    }
}
