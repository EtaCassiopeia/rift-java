# rift-java

Official Java SDK for [Rift](https://github.com/EtaCassiopeia/rift) — a high-performance,
Mountebank-compatible HTTP/HTTPS mock server written in Rust.

> **Status: design phase.** The public API is pinned down in
> [docs/design/sdk-api.md](docs/design/sdk-api.md) (mirrored as
> [#19](https://github.com/EtaCassiopeia/rift-java/issues/19)); implementation is tracked in
> the issues of this repo (milestones M1/M2). No artifacts are published yet.

## What it will look like

```java
import static rift.dsl.RiftDsl.*;

try (Rift rift = Rift.embedded()) {                  // or Rift.connect(uri) / Rift.spawn()
    Imposter users = rift.create(
        imposter("users").record()
            .stub(onGet("/api/users/1").willReturn(okJson("{\"id\":1}")))
            .stub(onPost("/api/users").willReturn(created())));

    // point your SUT at users.uri(), then:
    users.verify(onGet("/api/users/1"), times(1));
}
```

## Modules

| Artifact | JDK | Contents |
|---|---|---|
| `rift-java-core` | 17+ | typed wire model + fluent DSL, remote (admin API) + spawn transports, verification. Zero runtime deps. |
| `rift-java-embedded` | 22+ | in-process engine over Panama FFM (`librift_ffi` C-ABI v2) |
| `rift-java-embedded-jdk21` | 21 | same, `--enable-preview` build |
| `rift-java-natives` | — | per-platform classifier jars bundling the cdylib |
| `rift-java-junit5` | 17+ | `@RiftTest` extension, imposter injection |
| `rift-java-jackson` | 17+ | optional POJO body codec |

One client, three transports — embedded (in-process, no Docker, OS-assigned ports),
connect (any running Rift admin endpoint), spawn (managed `rift` binary). Full feature
surface on each: stubs/predicates/responses, response cycling, behaviors, proxy
record/playback, fault injection, stateful scenarios, spaces/flow-state, request
verification, and TLS-MITM intercept with truststore/`SSLContext` helpers.
