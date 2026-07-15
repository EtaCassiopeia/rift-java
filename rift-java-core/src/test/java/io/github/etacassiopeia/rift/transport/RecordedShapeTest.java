package io.github.etacassiopeia.rift.transport;

import io.github.etacassiopeia.rift.ConnectOptions;
import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.RecordedRequest;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.VersionCheck;
import io.github.etacassiopeia.rift.error.CommunicationError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a recorded-request list is read off the wire. The engine serves a bare array; some fakes and
 * older shapes serve a {@code {"requests":[...]}} envelope. Both are accepted.
 *
 * <p>Anything else is an error, not an empty journal. A 2xx carrying JSON of an unexpected shape —
 * an engine bug answering an error body with status 200, a proxy substituting a page — used to read
 * as "this imposter recorded nothing", which is indistinguishable from the truth at the call site
 * and leaves nothing to correlate. The element level stays lenient: fields the engine omits come
 * back empty, by design.
 */
class RecordedShapeTest {

    private static final String IMP = "{\"port\":4545,\"protocol\":\"http\",\"stubs\":[]}";
    private static final String TWO = "[{\"method\":\"GET\",\"path\":\"/a\"},{\"method\":\"GET\",\"path\":\"/b\"}]";

    private static Rift connect(FakeAdminServer s) {
        return Rift.connect(ConnectOptions.builder(s.baseUri()).versionCheck(VersionCheck.OFF).build());
    }

    private static Imposter created(FakeAdminServer s, Rift rift) {
        s.respond("POST /imposters", 201, IMP);
        return rift.create(imposter("x").port(4545));
    }

    @Test
    void bothAcceptedShapesParse() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/savedRequests", 200, TWO);
            try (Rift rift = connect(s)) {
                assertEquals(List.of("/a", "/b"),
                        created(s, rift).recorded().stream().map(RecordedRequest::path).toList());
            }
        }
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/savedRequests", 200, "{\"requests\":" + TWO + "}");
            try (Rift rift = connect(s)) {
                assertEquals(List.of("/a", "/b"),
                        created(s, rift).recorded().stream().map(RecordedRequest::path).toList());
            }
        }
    }

    @Test
    void anEmptyJournalIsStillEmptyInBothShapes() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/savedRequests", 200, "[]");
            try (Rift rift = connect(s)) {
                assertTrue(created(s, rift).recorded().isEmpty(), "an empty array is a real empty journal");
            }
        }
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/savedRequests", 200, "{\"requests\":[]}");
            try (Rift rift = connect(s)) {
                assertTrue(created(s, rift).recorded().isEmpty(), "an empty envelope is a real empty journal");
            }
        }
    }

    @Test
    void anUnrecognizedShapeIsAnErrorNotAnEmptyJournal() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            // A 2xx error body: the status says success, the body says otherwise. mapError never
            // sees it, and reading it as "recorded nothing" is a lie the caller cannot detect.
            s.respond("GET /imposters/4545/savedRequests", 200,
                    "{\"errors\":[{\"code\":\"oops\",\"message\":\"engine bug\"}]}");
            try (Rift rift = connect(s)) {
                Imposter imp = created(s, rift);

                CommunicationError e = assertThrows(CommunicationError.class, imp::recorded);
                assertTrue(e.getMessage().contains("savedRequests"), e.getMessage());
            }
        }
    }

    @Test
    void theCursorPathsRejectAnUnrecognizedShapeToo() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/savedRequests", 200, "\"a bare string, not a journal\"");
            try (Rift rift = connect(s)) {
                Imposter imp = created(s, rift);

                assertThrows(CommunicationError.class, imp::recordedPage);
                assertThrows(CommunicationError.class, () -> imp.recordedSince(3));
            }
        }
    }

    @Test
    void anEnvelopeWhoseRequestsIsNotAnArrayIsAnError() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            // Shaped like the envelope but not one — the wrongness is exactly what must not pass
            // silently as "nothing recorded".
            s.respond("GET /imposters/4545/savedRequests", 200, "{\"requests\":\"soon\"}");
            try (Rift rift = connect(s)) {
                assertThrows(CommunicationError.class, created(s, rift)::recorded);
            }
        }
    }

    @Test
    void aSpaceRecordedListIsHeldToTheSameContract() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/spaces/flow-A/recorded", 200, "{\"nope\":true}");
            try (Rift rift = connect(s)) {
                Imposter imp = created(s, rift);

                // The space view parsed its list with a verbatim copy of the same logic, so it had a
                // verbatim copy of the same silent empty.
                assertThrows(CommunicationError.class, () -> imp.space("flow-A").recorded());
            }
        }
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/spaces/flow-A/recorded", 200, TWO);
            try (Rift rift = connect(s)) {
                assertEquals(List.of("/a", "/b"),
                        created(s, rift).space("flow-A").recorded().stream().map(RecordedRequest::path).toList());
            }
        }
    }

    @Test
    void elementLevelLeniencySurvives() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            // The container contract is strict; the elements are not. An entry the SDK does not
            // recognize comes back mostly-empty rather than failing the whole read — the engine's
            // per-protocol/per-version shape drift is expected, a wrong container is not.
            s.respond("GET /imposters/4545/savedRequests", 200, "[123, {\"method\":\"GET\",\"path\":\"/a\"}]");
            try (Rift rift = connect(s)) {
                List<RecordedRequest> recorded = created(s, rift).recorded();

                assertEquals(2, recorded.size());
                assertEquals("", recorded.get(0).path(), "an unrecognized element is empty, not fatal");
                assertEquals("/a", recorded.get(1).path());
            }
        }
    }
}
