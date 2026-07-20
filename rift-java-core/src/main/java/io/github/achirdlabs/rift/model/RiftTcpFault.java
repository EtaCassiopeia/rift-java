package io.github.achirdlabs.rift.model;

import io.github.achirdlabs.rift.json.JsonNumber;
import io.github.achirdlabs.rift.json.JsonObject;
import io.github.achirdlabs.rift.json.JsonString;
import io.github.achirdlabs.rift.json.JsonValue;

/**
 * A raw TCP-level fault. Two mutually-exclusive wire forms (engine rift#531, rift &ge; 0.13.2): the
 * bare string {@code "CONNECTION_RESET_BY_PEER"} (always fires, probability 1.0), or the object
 * {@code {"probability":p,"type":"..."}} (fires with the given probability). Serialization emits
 * exactly the parsed form, so each round-trips — the two forms are bijective with the two variants,
 * and {@code probability} is required in the object form (its sole reason to exist).
 */
public sealed interface RiftTcpFault {

    /** The fault kind wire name (e.g. {@code CONNECTION_RESET_BY_PEER}). */
    String type();

    /** The probability the fault fires; 1.0 for the always-fires bare form. */
    double probability();

    /** The bare always-fires form {@code "TYPE"}. */
    record Bare(String type) implements RiftTcpFault {
        public Bare {
            java.util.Objects.requireNonNull(type, "type");
        }

        @Override
        public double probability() {
            return 1.0;
        }
    }

    /** The probabilistic object form {@code {"probability":p,"type":"TYPE"}}. */
    record Probabilistic(double probability, String type) implements RiftTcpFault {
        public Probabilistic {
            java.util.Objects.requireNonNull(type, "type");
        }
    }

    static RiftTcpFault read(JsonValue v) {
        if (v instanceof JsonString s) {
            return new Bare(s.value());
        }
        JsonObject obj = JsonSupport.requireObject(v, "tcp");
        return new Probabilistic(JsonSupport.requireDouble(obj, "probability"), JsonSupport.requireString(obj, "type"));
    }

    default JsonValue toJsonValue() {
        if (this instanceof Bare bare) {
            return new JsonString(bare.type());
        }
        Probabilistic p = (Probabilistic) this;
        return JsonObject.builder()
                .put("probability", JsonNumber.of(p.probability()))
                .put("type", new JsonString(p.type()))
                .build();
    }
}
