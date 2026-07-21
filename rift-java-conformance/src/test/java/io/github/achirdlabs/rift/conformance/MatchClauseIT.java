package io.github.achirdlabs.rift.conformance;

import io.github.achirdlabs.rift.Imposter;
import io.github.achirdlabs.rift.MatchClause;
import io.github.achirdlabs.rift.RecordedPage;
import io.github.achirdlabs.rift.Rift;
import io.github.achirdlabs.rift.RecordedRequest;
import io.github.achirdlabs.rift.SpawnOptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static io.github.achirdlabs.rift.dsl.RiftDsl.imposter;
import static io.github.achirdlabs.rift.dsl.RiftDsl.inMemoryFlowState;
import static io.github.achirdlabs.rift.dsl.RiftDsl.okJson;
import static io.github.achirdlabs.rift.dsl.RiftDsl.onGet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Server-side {@code match=} clauses against a real engine. Two contracts here can only be proved
 * against the engine itself, because a fake would just agree with whatever this SDK renders:
 * that the clause grammar we emit is the one the engine actually parses (percent-encoding and all),
 * and that a filtered tail's cursor <em>advances past the entries the filter rejected</em> — the
 * property that stops a filtered tail from re-scanning the same range forever.
 *
 * <p>Gated to {@link ConformanceTransport#SPAWN}: {@code match=} is a query parameter on the admin
 * API, which the in-process FFI transport does not have — it refuses filtered reads rather than
 * answering them unfiltered. Needs no corpus, just {@code RIFT_IT=1}.
 */
class MatchClauseIT {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final int PORT = 4595;
    private static final String FLOW_HEADER = "X-Flow-Id";

    @TestFactory
    Stream<DynamicTest> filteredTailAdvancesPastRejectedEntries() {
        return gated("a filtered tail never re-scans what it rejected", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = recordingImposter(rift);

                // A(flow-1), B(flow-2), C(flow-1): the filter must reject B, and the cursor must
                // still move past it.
                get(imp.uri() + "/a", "flow-1");
                get(imp.uri() + "/b", "flow-2");
                get(imp.uri() + "/c", "flow-1");

                RecordedPage page = imp.recordedSince(0, MatchClause.flowId("flow-1"));

                assertEquals(List.of("/a", "/c"), page.requests().stream().map(RecordedRequest::path).toList(),
                        "only flow-1's entries survive the clause");
                // Reaches the tip. Note this alone does NOT prove the cursor passed the rejected B:
                // C is both the last entry returned and the last scanned, so returned-only and
                // scanned-past cursors agree here. The discriminating case is below.
                assertEquals(3, page.nextIndex().orElse(-1), "the cursor reaches the journal tip");

                RecordedPage tip = imp.recordedSince(page.nextIndex().getAsLong(), MatchClause.flowId("flow-1"));
                assertTrue(tip.requests().isEmpty(), "nothing new for flow-1");
                assertEquals(3, tip.nextIndex().orElse(-1));

                // THE contract: record an entry the filter rejects, and nothing else. The page comes
                // back empty, and the cursor must STILL move — 4, not 3. A cursor that advanced only
                // past entries it returned would sit at 3 and re-scan this entry on every future
                // poll, which is the re-scan-forever bug the engine's rule exists to prevent.
                get(imp.uri() + "/d", "flow-2");
                RecordedPage rejected = imp.recordedSince(3, MatchClause.flowId("flow-1"));
                assertTrue(rejected.requests().isEmpty(), "the new entry belongs to another flow");
                assertEquals(4, rejected.nextIndex().orElse(-1),
                        "an empty filtered page still advances past what it rejected");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> headerClauseFiltersOnTheWire() {
        return gated("a header clause is parsed by the engine as written", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = recordingImposter(rift);

                // The value carries a space and a `=`, which is where a mis-encoded clause breaks:
                // the engine would either 400 or match nothing.
                get(imp.uri() + "/x", "flow-1", "X-Tenant", "acme corp=1");
                get(imp.uri() + "/y", "flow-1", "X-Tenant", "other");

                RecordedPage page = imp.recordedPage(MatchClause.header("X-Tenant", "acme corp=1"));

                assertEquals(List.of("/x"), page.requests().stream().map(RecordedRequest::path).toList(),
                        "the engine matched the exact header value we encoded");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> clausesIntersectRatherThanUnion() {
        return gated("two clauses AND on the engine, they do not widen", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = recordingImposter(rift);

                get(imp.uri() + "/both", "flow-1", "X-Tenant", "acme");
                get(imp.uri() + "/flow-only", "flow-1", "X-Tenant", "other");
                get(imp.uri() + "/header-only", "flow-2", "X-Tenant", "acme");

                RecordedPage page = imp.recordedPage(MatchClause.flowId("flow-1"), MatchClause.header("X-Tenant", "acme"));

                // Only /both satisfies both clauses. Every wrong combination is distinguishable
                // here: OR would return all three, last-clause-wins the two acme entries, and
                // first-clause-wins the two flow-1 entries.
                assertEquals(List.of("/both"), page.requests().stream().map(RecordedRequest::path).toList(),
                        "clauses intersect");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> scopedClearRemovesOnlyTheMatchingSlice() {
        return gated("a scoped clear keeps what it was not asked to delete", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = recordingImposter(rift);

                get(imp.uri() + "/a", "flow-1");
                get(imp.uri() + "/b", "flow-2");
                get(imp.uri() + "/c", "flow-1");

                imp.clearRecorded(MatchClause.flowId("flow-1"));

                assertEquals(List.of("/b"), imp.recorded().stream().map(RecordedRequest::path).toList(),
                        "flow-2's traffic survives a clear scoped to flow-1");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> methodAndPathClausesFilterOnTheWire() {
        return gated("method= and path= are parsed by the engine as written (engine >= 0.15.0)", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = recordingImposter(rift);

                // Three entries differing on exactly one axis each, so a clause that filtered on the
                // wrong one is distinguishable: POST /a shares the path, GET /b shares the method.
                send(imp.uri() + "/a", "GET", "flow-1");
                send(imp.uri() + "/a", "POST", "flow-1");
                send(imp.uri() + "/b", "GET", "flow-1");

                RecordedPage page = imp.recordedSince(0, MatchClause.method("GET"), MatchClause.path("/a"));

                assertEquals(List.of("/a"), page.requests().stream().map(RecordedRequest::path).toList(),
                        "only the GET /a satisfies both clauses");
                assertEquals(List.of("GET"), page.requests().stream().map(RecordedRequest::method).toList());
                assertEquals(3, page.nextIndex().orElse(-1), "the cursor reaches the journal tip");

                // Record only an entry BOTH clauses reject: the page is empty and the cursor must
                // still move, or a filtered tail would re-scan this entry on every future poll.
                send(imp.uri() + "/b", "POST", "flow-1");
                RecordedPage rejected = imp.recordedSince(3, MatchClause.method("GET"), MatchClause.path("/a"));

                assertTrue(rejected.requests().isEmpty(), "the new entry matches neither clause");
                assertEquals(4, rejected.nextIndex().orElse(-1),
                        "an empty filtered page still advances past what it rejected");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> theMethodClauseIsCaseSensitiveOnTheEngine() {
        return gated("a lower-case method clause matches nothing rather than being coerced", () -> {
            try (Rift rift = Rift.spawn(SpawnOptions.builder().build())) {
                Imposter imp = recordingImposter(rift);

                send(imp.uri() + "/a", "GET", "flow-1");

                // Proves the pair of contracts together: the engine compares methods case-sensitively,
                // AND the SDK does not quietly upper-case the caller's value on the way out. If either
                // half were wrong this would come back with the GET.
                assertTrue(imp.recordedPage(MatchClause.method("get")).requests().isEmpty(),
                        "\"get\" is not \"GET\" — the clause rides verbatim and matches nothing");
                assertEquals(List.of("/a"),
                        imp.recordedPage(MatchClause.method("GET")).requests().stream()
                                .map(RecordedRequest::path).toList(),
                        "...while the exact-case clause still matches");
            }
        });
    }

    private static Imposter recordingImposter(Rift rift) {
        // The engine resolves flow_id from this header, which is what `match=flow_id=` filters on.
        return rift.create(imposter("match")
                .port(PORT)
                .protocol("http")
                .record()
                .flowState(inMemoryFlowState().flowIdFromHeader(FLOW_HEADER))
                .stub(onGet("/a").willReturn(okJson("{\"ok\":true}"))));
    }

    private static void get(String url, String flowId) throws Exception {
        get(url, flowId, null, null);
    }

    /**
     * Same recorded round trip as {@link #get}, with the method under the caller's control. Like
     * {@code get}, it relies on an unmatched request still being served (Mountebank's empty
     * fallback) and therefore still being recorded — the journal is what is under test, not the stub.
     */
    private static void send(String url, String method, String flowId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header(FLOW_HEADER, flowId)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "the request must be served, so it is recorded");
    }

    private static void get(String url, String flowId, String header, String headerValue) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header(FLOW_HEADER, flowId);
        if (header != null) {
            builder.header(header, headerValue);
        }
        HttpResponse<String> response = HTTP.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        // Unmatched paths still record (the engine serves Mountebank's empty fallback), which is all
        // these cases need — the journal, not the stub, is under test.
        assertEquals(200, response.statusCode(), "the request must be served, so it is recorded");
    }

    /** Reports the two skip conditions separately so a lane that silently lost RIFT_IT is diagnosable. */
    private static Stream<DynamicTest> gated(String name, Executable body) {
        return Stream.of(DynamicTest.dynamicTest(name, () -> {
            assumeTrue(integrationEnabled(), "set RIFT_IT=1 to run the live-engine match= lane");
            assumeTrue(ConformanceTransport.selected() == ConformanceTransport.SPAWN,
                    "match= is an admin-API query parameter; the FFI transport has none — SPAWN lane only");
            body.run();
        }));
    }

    @FunctionalInterface
    private interface Executable {
        void run() throws Exception;
    }

    private static boolean integrationEnabled() {
        String it = System.getenv("RIFT_IT");
        return it != null && !it.isBlank() && !it.equals("0");
    }
}
