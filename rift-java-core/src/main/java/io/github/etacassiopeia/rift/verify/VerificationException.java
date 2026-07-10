package io.github.etacassiopeia.rift.verify;

import io.github.etacassiopeia.rift.RecordedRequest;
import io.github.etacassiopeia.rift.json.JsonString;
import io.github.etacassiopeia.rift.json.JsonValue;
import io.github.etacassiopeia.rift.model.Predicate;
import io.github.etacassiopeia.rift.model.PredicateOperation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thrown by {@code Imposter.verify}/{@code Space.verify} when the recorded traffic does not
 * satisfy the expected {@link VerificationTimes}. Extends {@link AssertionError} (rather than the
 * {@code io.github.etacassiopeia.rift.error} hierarchy) so test runners report it as a failed
 * assertion, matching WireMock's {@code VerificationException}.
 *
 * <p>The message ranks the recorded requests closest-match-first (most satisfied top-level
 * predicate clauses, ties broken by recency) and shows, for each, the first predicate clause it
 * failed — a near-miss diff in the style of WireMock's verification failures.
 */
public final class VerificationException extends AssertionError {

    private static final int MAX_DIFF_LINES = 10;

    public VerificationException(
            int port,
            Optional<String> name,
            RequestMatch match,
            VerificationTimes times,
            int actualCount,
            List<RecordedRequest> recorded) {
        super(buildMessage(port, name, match, times, actualCount, recorded));
    }

    private static String buildMessage(
            int port,
            Optional<String> name,
            RequestMatch match,
            VerificationTimes times,
            int actualCount,
            List<RecordedRequest> recorded) {
        List<Predicate> predicates = match.predicates();
        StringBuilder sb = new StringBuilder();
        sb.append("Verification failed for imposter :").append(port)
                .append(" (\"").append(name.orElse("")).append("\")\n");
        sb.append("Expected: ").append(requestSummary(predicates))
                .append("  —  ").append(times.describe())
                .append(", but was ").append(actualCount).append(".\n\n");

        if (recorded.isEmpty()) {
            sb.append("0 recorded requests.");
            return sb.toString();
        }

        List<Ranked> ranked = rank(recorded, predicates);
        sb.append(recorded.size()).append(" recorded requests, closest match first:\n");
        int shown = Math.min(MAX_DIFF_LINES, ranked.size());
        for (int i = 0; i < shown; i++) {
            sb.append("  ").append(diffLine(ranked.get(i).request(), predicates));
            if (i < shown - 1) {
                sb.append("\n");
            }
        }
        if (ranked.size() > shown) {
            sb.append("\n  … and ").append(ranked.size() - shown).append(" more");
        }
        return sb.toString();
    }

    private record Ranked(int index, RecordedRequest request, int satisfied) {}

    private static List<Ranked> rank(List<RecordedRequest> recorded, List<Predicate> predicates) {
        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < recorded.size(); i++) {
            ranked.add(new Ranked(i, recorded.get(i), PredicateEvaluator.satisfiedClauses(recorded.get(i), predicates)));
        }
        ranked.sort(Comparator.comparingInt(Ranked::satisfied).reversed()
                .thenComparing(Comparator.comparingInt(Ranked::index).reversed()));
        return ranked;
    }

    private static String diffLine(RecordedRequest request, List<Predicate> predicates) {
        String method = request.method().isEmpty() ? "?" : request.method();
        String path = request.path().isEmpty() ? "/" : request.path();
        String header = "✗ " + method + " " + path;
        Optional<PredicateEvaluator.Failure> failure = PredicateEvaluator.firstFailure(request, predicates);
        if (failure.isEmpty()) {
            return header + "    (matches)";
        }
        PredicateEvaluator.Failure f = failure.get();
        return header + "    " + f.field() + ": expected \"" + f.expected() + "\" (" + f.op() + "), got \"" + f.actual() + "\"";
    }

    /** The expected method+path from a simple {@code equals} predicate, if determinable; else a generic label. */
    private static String requestSummary(List<Predicate> predicates) {
        String method = null;
        String path = null;
        for (Predicate predicate : predicates) {
            Map<String, JsonValue> fields = equalsFieldsOf(predicate.operation());
            if (fields.get("method") instanceof JsonString s) {
                method = s.value();
            }
            if (fields.get("path") instanceof JsonString s) {
                path = s.value();
            }
        }
        if (method == null && path == null) {
            return "the given request";
        }
        return (method == null ? "*" : method) + " " + (path == null ? "*" : path);
    }

    private static Map<String, JsonValue> equalsFieldsOf(PredicateOperation op) {
        return op instanceof PredicateOperation.Equals e ? e.fields() : Map.of();
    }
}
