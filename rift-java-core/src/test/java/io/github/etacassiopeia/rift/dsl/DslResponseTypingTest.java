package io.github.etacassiopeia.rift.dsl;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.created;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.fault;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.inject;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.ok;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.proxyTo;
import static io.github.etacassiopeia.rift.dsl.RiftDsl.script;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compile-time response typing: {@code ResponseSpec} is a sealed interface whose only
 * body/header/behavior-carrying variant is {@code IsSpec}. The terminal variants
 * ({@code FaultSpec}/{@code InjectSpec}/{@code ScriptSpec}) carry no chain methods, so
 * {@code fault(...).withHeader(...)} is a compile error rather than a runtime exception. These
 * structural assertions stand in for the "does not compile" acceptance check.
 */
class DslResponseTypingTest {

    @Test
    void responseSpecIsSealedOverTheFiveVariants() {
        Class<?> rs = ResponseSpec.class;
        assertTrue(rs.isSealed(), "ResponseSpec must be sealed");
        Set<String> permitted = Arrays.stream(rs.getPermittedSubclasses())
                .map(Class::getSimpleName).collect(Collectors.toSet());
        assertEquals(Set.of("IsSpec", "ProxySpec", "FaultSpec", "InjectSpec", "ScriptSpec"), permitted);
    }

    @Test
    void factoriesReturnTheCorrectVariantTypes() {
        assertTrue(ok() instanceof IsSpec);
        assertTrue(created() instanceof IsSpec);
        assertTrue(proxyTo("http://up") instanceof ProxySpec);
        assertTrue(fault(Fault.CONNECTION_RESET_BY_PEER) instanceof FaultSpec);
        assertTrue(inject("function(){}") instanceof InjectSpec);
        assertTrue(script(Script.rhai("x")) instanceof ScriptSpec);
    }

    @Test
    void isSpecCarriesTheChainMethodsButTerminalsDoNot() throws Exception {
        // IsSpec has the body/header/behavior chain...
        assertTrue(IsSpec.class.getMethod("withHeader", String.class, String[].class) != null);
        assertTrue(IsSpec.class.getMethod("withTextBody", String.class) != null);
        assertTrue(IsSpec.class.getMethod("templated") != null);
        // ...the terminal variants do not (so `fault(...).withHeader(...)` cannot compile).
        assertThrows(NoSuchMethodException.class,
                () -> FaultSpec.class.getMethod("withHeader", String.class, String[].class));
        assertThrows(NoSuchMethodException.class,
                () -> InjectSpec.class.getMethod("withTextBody", String.class));
        assertThrows(NoSuchMethodException.class, () -> ScriptSpec.class.getMethod("templated"));
    }

    @Test
    void faultEnumHasExactlyTheFourEngineValuesAndNoLatencySpike() throws Exception {
        Set<String> names = Arrays.stream(Fault.values()).map(Enum::name).collect(Collectors.toSet());
        assertEquals(Set.of("CONNECTION_RESET_BY_PEER", "EMPTY_RESPONSE",
                "RANDOM_DATA_THEN_CLOSE", "MALFORMED_RESPONSE_CHUNK"), names);
        // latencySpike(Duration) is removed — latency spikes move to IsSpec.withLatencyFault.
        assertThrows(NoSuchMethodException.class, () -> Fault.class.getMethod("latencySpike", java.time.Duration.class));
    }
}
