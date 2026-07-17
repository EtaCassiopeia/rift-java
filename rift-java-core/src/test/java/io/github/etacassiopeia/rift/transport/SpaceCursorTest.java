package io.github.etacassiopeia.rift.transport;

import io.github.etacassiopeia.rift.ConnectOptions;
import io.github.etacassiopeia.rift.Imposter;
import io.github.etacassiopeia.rift.MatchClause;
import io.github.etacassiopeia.rift.RecordedPage;
import io.github.etacassiopeia.rift.RecordedRequest;
import io.github.etacassiopeia.rift.Rift;
import io.github.etacassiopeia.rift.VersionCheck;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static io.github.etacassiopeia.rift.dsl.RiftDsl.imposter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Space-scoped cursor reads (#149): the imposter journal cut engine-side by an auto-injected
 * {@code flow_id} clause, AND-ed with the caller's filters. There is still no space-scoped
 * recorded-requests route — a space's page and tail ride the same {@code savedRequests} cursor as
 * the imposter's ({@code SpaceRecordedTest} records why).
 */
class SpaceCursorTest {

    private static final String IMP = "{\"port\":4545,\"protocol\":\"http\",\"stubs\":[]}";
    private static final String TWO = "[{\"method\":\"GET\",\"path\":\"/a\"},{\"method\":\"GET\",\"path\":\"/b\"}]";

    private static Rift connect(FakeAdminServer s) {
        return Rift.connect(ConnectOptions.builder(s.baseUri()).versionCheck(VersionCheck.OFF).build());
    }

    private static Imposter created(FakeAdminServer s, Rift rift) {
        s.respond("POST /imposters", 201, IMP);
        return rift.create(imposter("x").port(4545));
    }

    private static String lastGet(FakeAdminServer s) {
        return s.received().stream()
                .filter(r -> r.method().equals("GET") && r.path().startsWith("/imposters/4545/savedRequests"))
                .map(FakeAdminServer.Received::path)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no savedRequests GET was issued; requests seen: " + s.received()));
    }

    @Test
    void aSpacePageIsTheJournalCutByItsFlowIdClause() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/savedRequests", 200, TWO);
            try (Rift rift = connect(s)) {
                RecordedPage page = created(s, rift).space("alice").recordedPage();

                assertEquals(List.of("/a", "/b"), page.requests().stream().map(RecordedRequest::path).toList());
                assertEquals("/imposters/4545/savedRequests?match=flow_id%3Dalice", lastGet(s));
                // No cursor headers on the response: do-not-advance, and a baseline is never a hole.
                assertEquals(OptionalLong.empty(), page.nextIndex());
                assertFalse(page.truncated());
            }
        }
    }

    @Test
    void aSpaceTailCutsByCursorFirstThenTheFlowIdClause() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.on("GET /imposters/4545/savedRequests",
                    (r, v) -> FakeAdminServer.Response.ok("[]").withHeader("x-rift-next-index", "9"));
            try (Rift rift = connect(s)) {
                RecordedPage page = created(s, rift).space("alice").recordedSince(12);

                assertEquals("/imposters/4545/savedRequests?since=12&match=flow_id%3Dalice", lastGet(s));
                assertEquals(OptionalLong.of(9), page.nextIndex());
            }
        }
    }

    @Test
    void callerFiltersAndAfterTheFlowIdClause() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/savedRequests", 200, "[]");
            try (Rift rift = connect(s)) {
                created(s, rift).space("alice").recordedPage(MatchClause.header("X-K", "v"));
                assertEquals("/imposters/4545/savedRequests?match=flow_id%3Dalice&match=header%3AX-K%3Dv",
                        lastGet(s));

                created(s, rift).space("alice").recordedSince(3, MatchClause.header("X-K", "v"));
                assertEquals("/imposters/4545/savedRequests?since=3&match=flow_id%3Dalice&match=header%3AX-K%3Dv",
                        lastGet(s));

                created(s, rift).space("alice")
                        .recordedSince(3, MatchClause.header("X-K", "v"), MatchClause.header("X-T", "t"));
                assertEquals("/imposters/4545/savedRequests?since=3"
                                + "&match=flow_id%3Dalice&match=header%3AX-K%3Dv&match=header%3AX-T%3Dt",
                        lastGet(s));
            }
        }
    }

    @Test
    void cursorZeroIsARealCursorNotABaseline() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/savedRequests", 200, "[]");
            try (Rift rift = connect(s)) {
                created(s, rift).space("alice").recordedSince(0);

                // A present 0 means "nothing seen yet", which is not the same read as a baseline:
                // the engine must still cut by cursor and may report truncation.
                assertEquals("/imposters/4545/savedRequests?since=0&match=flow_id%3Dalice", lastGet(s));
            }
        }
    }

    @Test
    void theTruncationSignalRidesOntoThePage() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.on("GET /imposters/4545/savedRequests",
                    (r, v) -> FakeAdminServer.Response.ok("[]")
                            .withHeader("x-rift-next-index", "7")
                            .withHeader("x-rift-truncated", "true"));
            try (Rift rift = connect(s)) {
                RecordedPage page = created(s, rift).space("alice").recordedSince(3);

                assertTrue(page.truncated(), "retention evicted unseen entries: the hole must be visible");
                assertEquals(OptionalLong.of(7), page.nextIndex());
            }
        }
    }

    @Test
    void aCallerSuppliedFlowIdClauseIsRejected() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            try (Rift rift = connect(s)) {
                var space = created(s, rift).space("alice");

                // Clauses AND server-side: a different flow_id silently selects nothing, the same one
                // is a duplicate. Silent-empty is the loss mode this API exists to remove, so both
                // are rejected up front — no request may reach the wire.
                assertThrows(IllegalArgumentException.class,
                        () -> space.recordedPage(MatchClause.flowId("bob")));
                assertThrows(IllegalArgumentException.class,
                        () -> space.recordedSince(1, MatchClause.flowId("alice")));
                assertFalse(s.received().stream().anyMatch(r -> r.path().contains("savedRequests")),
                        "a rejected clause must not produce a request: " + s.received());
            }
        }
    }

    @Test
    void theFlowIdIsPercentEncodedAndNoSpaceRouteIsTouched() {
        try (FakeAdminServer s = new FakeAdminServer()) {
            s.respond("GET /imposters/4545/savedRequests", 200, "[]");
            try (Rift rift = connect(s)) {
                created(s, rift).space("a b&c/é").recordedPage();

                String path = lastGet(s);
                assertTrue(path.startsWith("/imposters/4545/savedRequests?match=flow_id%3D"), path);
                assertFalse(path.contains("+"), "a space must ride as %20, not + : " + path);
                assertTrue(path.contains("%20") && path.contains("%26") && path.contains("%C3%A9"), path);
                assertFalse(s.received().stream().anyMatch(r -> r.path().contains("/spaces/")),
                        "no space-scoped route should be touched: " + s.received());
            }
        }
    }
}
