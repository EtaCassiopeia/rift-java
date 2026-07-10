# rift-java-core

The zero-runtime-dependency core of rift-java: a typed Rift/Mountebank wire model, a fluent
static-import DSL over it (`RiftDsl`), the `Rift` client with its `connect`/`spawn` transports,
and request verification. `rift-java-embedded` (in-process transport) and the JSON codec are the
only pieces that live outside this module.

```java
import static io.github.etacassiopeia.rift.dsl.RiftDsl.*;

try (Rift rift = Rift.connect(URI.create("http://localhost:2525"))) {
    Imposter users = rift.create(
        imposter("users").record()
            .stub(onGet("/api/users/1").willReturn(okJson("{\"id\":1}"))));

    // point your SUT at users.uri(), then:
    users.verify(onGet("/api/users/1"), times(1));
}
```

What's in here:

- **Wire model** (`io.github.etacassiopeia.rift.model`) — typed request/response/predicate/stub
  shapes plus a hand-rolled, dependency-free JSON codec.
- **DSL** (`io.github.etacassiopeia.rift.dsl.RiftDsl`) — `imposter`/`onGet`/`onPost`/...,
  matchers (`equalTo`, `contains`, `matches`, ...), response builders (`ok`, `okJson`, `status`,
  `proxyTo`, `fault`, ...), and verification helpers (`times`, `atLeast`, `never`, ...).
- **Transports** — `Rift.connect(uri)` against a running admin API, `Rift.spawn()` to launch and
  own a managed `rift` binary.
- **Verification** — `Imposter.verify(match, times)` against recorded requests, with ranked
  near-miss diffs on failure.
- **Intercept** — `Rift.intercept()` for TLS-MITM proxying; see
  [../docs/intercept.md](../docs/intercept.md).

For `Rift.embedded()`, add `rift-java-embedded` (JDK 22+) or `rift-java-embedded-jdk21` (JDK 21).
For POJO body (de)serialization in the DSL's `Object`-typed overloads, add `rift-java-jackson` (or
any `RiftBodyCodec` implementation).

See [../docs/design/sdk-api.md](../docs/design/sdk-api.md) for the full API design rationale.
