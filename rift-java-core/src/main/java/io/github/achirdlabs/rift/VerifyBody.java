package io.github.achirdlabs.rift;

import io.github.achirdlabs.rift.json.JsonArray;
import io.github.achirdlabs.rift.json.JsonBool;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.json.JsonValue;
import io.github.achirdlabs.rift.model.Predicate;
import io.github.achirdlabs.rift.verify.RequestMatch;
import io.github.achirdlabs.rift.verify.VerifyDetail;

import java.util.List;
import java.util.Optional;

/** Builds the {@code POST /imposters/{port}/verify} request body shared by {@link ImposterImpl} and {@link SpaceImpl}. */
final class VerifyBody {

    private VerifyBody() {}

    /**
     * A detail flag is emitted only when requested — the engine defaults each to false, so an
     * omitted flag is the cheap path (no journal shipped, no closest scoring) rather than a default
     * this SDK has to restate.
     */
    static JsonValue build(RequestMatch match, Optional<String> flowId, VerifyDetail... details) {
        List<VerifyDetail> requested = List.of(details);
        JsonObject.Builder body = JsonObject.builder()
                .put("predicates", new JsonArray(match.predicates().stream()
                        .map(VerifyBody::toJsonValue).toList()));
        flowId.ifPresent(id -> body.put("flowId", new JsonString(id)));
        if (requested.contains(VerifyDetail.REQUESTS)) {
            body.put("includeRequests", new JsonBool(true));
        }
        if (requested.contains(VerifyDetail.CLOSEST)) {
            body.put("includeClosest", new JsonBool(true));
        }
        return body.build();
    }

    private static JsonValue toJsonValue(Predicate predicate) {
        return JsonValue.parse(predicate.toJson());
    }
}
