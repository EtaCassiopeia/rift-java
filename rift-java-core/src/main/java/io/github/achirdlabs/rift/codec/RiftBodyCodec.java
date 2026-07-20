package io.github.achirdlabs.rift.codec;

import io.github.achirdlabs.rift.json.JsonValue;

/**
 * SPI for converting arbitrary POJOs to and from the wire {@link JsonValue} tree, so the DSL's
 * {@code Object}-typed body overloads ({@code okJson(Object)}, {@code equalTo(Object)}, {@code
 * deepEquals(Object)}, {@code RecordedRequest.bodyAs}) don't hard-depend on any particular JSON
 * mapping library.
 *
 * <p>{@code rift-java-core} ships no implementation; it stays zero-runtime-deps and discovers an
 * implementation via {@link java.util.ServiceLoader}. {@code rift-java-jackson} provides one. An
 * implementation can also be registered explicitly with {@link
 * io.github.achirdlabs.rift.dsl.RiftDsl#useBodyCodec}, which takes priority over discovery.
 */
public interface RiftBodyCodec {

    /** Converts {@code value} to its {@link JsonValue} representation. */
    JsonValue toJson(Object value);

    /** Converts {@code json} back into an instance of {@code type}. */
    <T> T fromJson(JsonValue json, Class<T> type);
}
