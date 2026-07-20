# rift-java-jackson

Optional Jackson-backed implementation of `rift-java-core`'s `RiftBodyCodec` SPI: lets the DSL's
`Object`-typed overloads (`okJson(Object)`, `equalTo(Object)`, `deepEquals(Object)`,
`RecordedRequest.bodyAs`) serialize/deserialize plain POJOs instead of requiring hand-built
`JsonValue` bodies.

`rift-java-core` ships no codec implementation — it stays zero-runtime-deps and discovers one via
`ServiceLoader`. Adding this module's jar to the classpath is enough; it registers
`JacksonBodyCodec` under `META-INF/services/io.github.achirdlabs.rift.codec.RiftBodyCodec` and
it's picked up automatically, no further wiring required.

```xml
<dependency>
  <groupId>io.github.achird-labs</groupId>
  <artifactId>rift-java-jackson</artifactId>
</dependency>
```

```java
record UserDto(long id, String name) {}

Imposter users = rift.create(
    imposter("users").record()
        .stub(onGet("/api/users/1")
                .willReturn(okJson(new UserDto(1, "Ada")))));   // serialized via Jackson

users.verify(onPost("/api/users").withBody(equalTo(new UserDto(2, "Bob"))));
```

To use a different JSON mapping library instead, implement `RiftBodyCodec` yourself and either
register it via `ServiceLoader` or pass it explicitly to `RiftDsl.useBodyCodec(...)`, which takes
priority over discovery.
