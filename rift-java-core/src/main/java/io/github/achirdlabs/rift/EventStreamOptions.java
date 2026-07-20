package io.github.achirdlabs.rift;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/** How to open an {@link EventStream}: which events to receive, and when to give up on a silent one. */
public final class EventStreamOptions {

    /** A family of events the engine can send. */
    public enum EventType {
        /** An imposter recorded a request ({@link RiftEvent.RequestRecorded}). */
        REQUESTS,
        /** An imposter was created, replaced, or deleted ({@link RiftEvent.ImposterChanged}). */
        LIFECYCLE
    }

    private final Set<EventType> types;
    private final OptionalInt port;
    private final List<MatchClause> match;
    private final Duration idleTimeout;

    private EventStreamOptions(Builder b) {
        this.types = Set.copyOf(b.types);
        this.port = b.port;
        this.match = List.copyOf(b.match);
        this.idleTimeout = b.idleTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<EventType> types() {
        return types;
    }

    public OptionalInt port() {
        return port;
    }

    public List<MatchClause> match() {
        return match;
    }

    public Duration idleTimeout() {
        return idleTimeout;
    }

    public static final class Builder {

        /**
         * The engine heartbeats every 15s, so silence this long means three were missed — long
         * enough not to trip on a slow link, short enough that a dead connection is noticed.
         */
        private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofSeconds(45);

        private Set<EventType> types = EnumSet.allOf(EventType.class);
        private OptionalInt port = OptionalInt.empty();
        private List<MatchClause> match = List.of();
        private Duration idleTimeout = DEFAULT_IDLE_TIMEOUT;

        private Builder() {}

        /** Receive only these event families. Defaults to all of them. */
        public Builder types(EventType... types) {
            Objects.requireNonNull(types, "types");
            if (types.length == 0) {
                throw new IllegalArgumentException("a stream with no event types would deliver nothing");
            }
            this.types = EnumSet.copyOf(List.of(types));
            return this;
        }

        /** Receive only events for this imposter. Engine-wide events (all-deleted) still arrive. */
        public Builder port(int port) {
            this.port = OptionalInt.of(port);
            return this;
        }

        /**
         * Filter request events server-side, with the same clause grammar the journal uses. Clauses
         * AND together and apply to request events only — lifecycle events carry no request to match.
         *
         * @see MatchClause
         */
        public Builder match(MatchClause... match) {
            this.match = List.of(match);
            return this;
        }

        /**
         * Treat silence longer than this as a dead connection, failing iteration with {@link
         * io.github.achirdlabs.rift.error.EngineUnavailable}. The engine's heartbeat resets the
         * clock, so this measures "no traffic at all", not "no events". Defaults to 45s.
         */
        public Builder idleTimeout(Duration idleTimeout) {
            Objects.requireNonNull(idleTimeout, "idleTimeout");
            if (idleTimeout.isNegative() || idleTimeout.isZero()) {
                throw new IllegalArgumentException("idleTimeout must be positive: " + idleTimeout);
            }
            this.idleTimeout = idleTimeout;
            return this;
        }

        public EventStreamOptions build() {
            return new EventStreamOptions(this);
        }
    }
}
