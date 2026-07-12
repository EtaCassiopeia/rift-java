package io.github.etacassiopeia.rift;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersionCheckTest {

    private static UnaryOperator<String> lookup(Map<String, String> map) {
        return map::get;
    }

    @Test
    void parsesTokensCaseInsensitively() {
        assertEquals(VersionCheck.OFF, VersionCheck.parseToken("off"));
        assertEquals(VersionCheck.WARN, VersionCheck.parseToken("WARN"));
        assertEquals(VersionCheck.FAIL, VersionCheck.parseToken(" Fail "));
    }

    @Test
    void blankOrNullTokenFallsThrough() {
        assertNull(VersionCheck.parseToken(null));
        assertNull(VersionCheck.parseToken("   "));
    }

    @Test
    void invalidTokenIsRejectedNamingValidValues() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> VersionCheck.parseToken("bogus"));
        assertEquals(true, e.getMessage().contains("off, warn, fail"));
    }

    @Test
    void propertyWinsOverEnv() {
        VersionCheck resolved = VersionCheck.resolveDefault(
                lookup(Map.of("rift.versionCheck", "off")),
                lookup(Map.of("RIFT_VERSION_CHECK", "fail")));
        assertEquals(VersionCheck.OFF, resolved);
    }

    @Test
    void envUsedWhenNoProperty() {
        VersionCheck resolved = VersionCheck.resolveDefault(
                lookup(Map.of()),
                lookup(Map.of("RIFT_VERSION_CHECK", "warn")));
        assertEquals(VersionCheck.WARN, resolved);
    }

    @Test
    void defaultsToFailWhenNeitherSet() {
        assertEquals(VersionCheck.FAIL, VersionCheck.resolveDefault(lookup(Map.of()), lookup(Map.of())));
    }

    @Test
    void blankPropertyFallsThroughToEnv() {
        VersionCheck resolved = VersionCheck.resolveDefault(
                lookup(Map.of("rift.versionCheck", "  ")),
                lookup(Map.of("RIFT_VERSION_CHECK", "off")));
        assertEquals(VersionCheck.OFF, resolved);
    }

    @Test
    void invalidPropagatesFromResolve() {
        assertThrows(IllegalArgumentException.class,
                () -> VersionCheck.resolveDefault(lookup(Map.of("rift.versionCheck", "nope")), lookup(Map.of())));
    }
}
