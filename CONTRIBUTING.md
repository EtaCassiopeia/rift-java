# Contributing to rift-java

Thanks for helping build the official Java SDK for [Rift](https://github.com/EtaCassiopeia/rift).

## Prerequisites

- A JDK. The zero-dependency modules build on **JDK 17+**. Two modules are JDK-gated (see below):
  `rift-java-embedded` needs **JDK 22+**, `rift-java-embedded-jdk21` needs **JDK 21**.
- No local Maven install is required — the repo ships the **Maven Wrapper** (`./mvnw`).

## Build & verify

```sh
./mvnw -B verify        # compile every active module + run tests
./mvnw -pl rift-java-core test   # a single module
```

The CI matrix runs `./mvnw -B verify` on **JDK 17, 21, 22** across **Linux, macOS, and Windows**.
Run the wrapper on your JDK before opening a PR; CI covers the rest of the matrix.

## Module layout

| Module | JDK | Contents |
|---|---|---|
| `rift-java-core` | 17+ | typed wire model, fluent DSL, remote + spawn transports, verification. Zero runtime deps. |
| `rift-java-jackson` | 17+ | optional Jackson POJO body codec |
| `rift-java-junit5` | 17+ | `@RiftTest` extension, imposter injection |
| `rift-java-natives` | 17+ | per-platform classifier jars bundling the `librift_ffi` cdylib |
| `rift-java-embedded` | 22+ | in-process engine over the **stable** Panama FFM API |
| `rift-java-embedded-jdk21` | 21 | same engine on JDK 21 (**preview** FFM) |

### JDK gating

The two embedded modules are added to the reactor by JDK-activated Maven profiles, so `./mvnw verify`
builds the right set on any supported JDK:

- **JDK 17** → core, jackson, junit5, natives
- **JDK 21** → the above **+ `rift-java-embedded-jdk21`** (profile `embedded-jdk21`)
- **JDK 22+** → the above four **+ `rift-java-embedded`** (profile `embedded`)

You never need to pass a profile by hand for a normal build.

## Conventions

- **Branches**: `feat/`, `fix/`, `refactor/`, `test/`, `build/`, `docs/` prefixes.
- **Commits**: [Conventional Commits](https://www.conventionalcommits.org/), imperative mood,
  explaining *why* over *what*.
- **Code style**: no `null` returns in public APIs where an `Optional`/sealed type fits; errors are
  values or typed exceptions, never swallowed; public API carries Javadoc.

## Releasing

Artifacts publish to Maven Central under the `io.github.etacassiopeia` namespace via the
[Central Publishing plugin](https://central.sonatype.org/publish/publish-portal-maven/).

- **Snapshots** deploy automatically from `master` (`0.1.0-SNAPSHOT`).
- **Releases** deploy from a published GitHub Release.

The `Publish` workflow is a no-op until these repository secrets are configured:
`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` (a Central Portal user token), `GPG_PRIVATE_KEY`,
and `MAVEN_GPG_PASSPHRASE`. Signing and the sources/javadoc jars live in the `release` profile
(`./mvnw -Prelease deploy`).
