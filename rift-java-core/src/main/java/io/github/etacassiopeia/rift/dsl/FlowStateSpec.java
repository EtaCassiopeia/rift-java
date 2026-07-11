package io.github.etacassiopeia.rift.dsl;

import io.github.etacassiopeia.rift.model.RiftFlowStateConfig;
import io.github.etacassiopeia.rift.model.RiftRedisConfig;

import java.time.Duration;
import java.util.Optional;

/**
 * A flow-state configuration under construction, produced by {@link RiftDsl#inMemoryFlowState()}
 * or {@link RiftDsl#redisFlowState(String)}, for use with {@link ImposterSpec#flowState}.
 *
 * <p>Instances are immutable: every chain method returns a new {@code FlowStateSpec}. The terminal
 * {@link #build()} produces the {@link RiftFlowStateConfig} model value.
 */
public final class FlowStateSpec {

    private final String backend;
    private final long ttlSeconds;
    private final Optional<String> flowIdSource;
    private final Optional<String> redisUrl;
    private final int redisPoolSize;
    private final String redisKeyPrefix;

    private FlowStateSpec(
            String backend,
            long ttlSeconds,
            Optional<String> flowIdSource,
            Optional<String> redisUrl,
            int redisPoolSize,
            String redisKeyPrefix) {
        this.backend = backend;
        this.ttlSeconds = ttlSeconds;
        this.flowIdSource = flowIdSource;
        this.redisUrl = redisUrl;
        this.redisPoolSize = redisPoolSize;
        this.redisKeyPrefix = redisKeyPrefix;
    }

    static FlowStateSpec inMemory() {
        return new FlowStateSpec(
                "inmemory", RiftFlowStateConfig.DEFAULT_TTL_SECONDS, Optional.empty(),
                Optional.empty(), RiftRedisConfig.DEFAULT_POOL_SIZE, RiftRedisConfig.DEFAULT_KEY_PREFIX);
    }

    static FlowStateSpec redis(String url) {
        return new FlowStateSpec(
                "redis", RiftFlowStateConfig.DEFAULT_TTL_SECONDS, Optional.empty(),
                Optional.of(url), RiftRedisConfig.DEFAULT_POOL_SIZE, RiftRedisConfig.DEFAULT_KEY_PREFIX);
    }

    /** Sets how long flow state survives without being touched. */
    public FlowStateSpec ttl(Duration duration) {
        return new FlowStateSpec(backend, duration.toSeconds(), flowIdSource, redisUrl, redisPoolSize, redisKeyPrefix);
    }

    /**
     * Correlates flow state using the named request header rather than the imposter's port. Required
     * for per-space stubs ({@link StubSpec#inSpace}) to match: without it the engine resolves every
     * request's flow id to the port, so a space stub never matches.
     */
    public FlowStateSpec flowIdFromHeader(String name) {
        return new FlowStateSpec(backend, ttlSeconds, Optional.of("header:" + name), redisUrl, redisPoolSize, redisKeyPrefix);
    }

    /** Sets the Redis connection pool size (only meaningful for {@link RiftDsl#redisFlowState(String)}). */
    public FlowStateSpec poolSize(int size) {
        return new FlowStateSpec(backend, ttlSeconds, flowIdSource, redisUrl, size, redisKeyPrefix);
    }

    /** Sets the Redis key prefix (only meaningful for {@link RiftDsl#redisFlowState(String)}). */
    public FlowStateSpec keyPrefix(String prefix) {
        return new FlowStateSpec(backend, ttlSeconds, flowIdSource, redisUrl, redisPoolSize, prefix);
    }

    /** Builds the immutable {@link RiftFlowStateConfig} this spec represents. */
    RiftFlowStateConfig build() {
        Optional<RiftRedisConfig> redis = redisUrl.map(url -> new RiftRedisConfig(url, redisPoolSize, redisKeyPrefix));
        return new RiftFlowStateConfig(backend, ttlSeconds, redis, flowIdSource);
    }
}
