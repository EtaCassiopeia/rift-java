# Intercept (TLS-MITM)

`Intercept` turns a rift engine into a forward proxy that terminates TLS for arbitrary
hostnames — answering some requests inline, forwarding others to a real backend or to one of
this SDK's own imposters — without changing anything about the traffic's actual destination
host name. It's obtained from a running `Rift` client:

```java
Intercept intercept = rift.intercept();               // default options
Intercept intercept = rift.intercept(InterceptOptions.builder().port(8443).build());
```

At most one intercept listener is allowed per engine: a second `rift.intercept()` call on the
same `Rift` (or a different `Rift` bound to the same engine) throws `IllegalStateException`. A
call that fails to start (e.g. a bad committed CA — see below) leaves intercept available to
retry; it does not poison the engine.

## Rules: what happens to an intercepted host

Every rule is scoped to one hostname and decides what happens to requests the client sends to
that host through the intercept proxy:

```java
// Answer inline — the real host is never contacted.
intercept.serve("example.com", RiftDsl.status(418));

// Forward to a plain host:port on localhost, still terminating TLS at the intercept.
intercept.forward("payments.internal", "localhost:9443");

// Forward to one of this SDK's own imposters, by port.
intercept.redirectTo("api.partner.com", partnerImposter);
```

`serve`'s response is an `IsSpec` — the same response builder the DSL uses for stub responses
(`RiftDsl.ok()`, `okJson(...)`, `status(code)`, and so on). `redirectTo` rides the same
wire-level `forward` action as `forward` itself, pointed at `imposter.port()`; a rule read back
from `intercept.rules()` therefore only ever reports `RuleKind.SERVE` or `RuleKind.FORWARD` — the
engine has no way to echo back that a `forward` action originated from `redirectTo`.

```java
List<InterceptRule> rules = intercept.rules();   // in the order they were added
intercept.clearRules();                          // removes every rule; the listener stays up
```

`intercept.close()` clears rules too; the listener itself is torn down when the owning `Rift`
closes.

## Trusting the intercept's CA

With no CA supplied, the engine mints a fresh ephemeral CA per listener and signs a leaf
certificate per intercepted hostname on the fly. A client has to be told to trust that CA, or
its TLS handshake fails. `Intercept.trust()` returns an `InterceptTrust` with three ways to do
that:

### 1. In-memory `SSLContext` — recommended for tests

No files, nothing written to disk — built entirely from the CA's PEM text in memory:

```java
SSLContext ssl = intercept.trust().sslContext();

HttpClient client = HttpClient.newBuilder()
        .sslContext(ssl)
        .proxy(intercept.proxySelector())
        .build();
```

This is the right default for test code: it needs no cleanup, leaves nothing behind, and works
identically whether the CA is ephemeral or committed.

### 2. Exported truststore — for a JVM-wide `-Djavax.net.ssl.trustStore`

When the client under test is out of your control (a third-party HTTP client, a subprocess, a
whole JVM configured only via system properties), export a truststore file instead:

```java
Path truststore = Path.of("build/tmp/intercept-trust.p12");
intercept.trust().exportTruststore(TruststoreFormat.PKCS12, "changeit", truststore);
```

```
-Djavax.net.ssl.trustStore=build/tmp/intercept-trust.p12
-Djavax.net.ssl.trustStorePassword=changeit
-Djavax.net.ssl.trustStoreType=PKCS12
```

`TruststoreFormat.JKS` is also available. A `null` password defaults to `"changeit"`. This is a
plain Java `KeyStore` write with no engine round-trip — same as `sslContext()`, just persisted.

### 3. A committed CA — shared across engine instances

By default every listener gets a fresh, ephemeral CA, which is fine for a single test process
but means two independently-started engines don't trust each other's intercepted traffic and a
CA can't be pinned once and reused across runs. Supply your own instead:

```java
InterceptOptions options = InterceptOptions.builder()
        .ca(Path.of("ca-cert.pem"), Path.of("ca-key.pem"))
        .build();
Intercept intercept = rift.intercept(options);
```

Both paths are required, or neither — a half-supplied pair is rejected client-side by
`InterceptOptions.Builder.ca(...)` (`IllegalArgumentException`) rather than surfacing later as a
confusing engine-side error. With a committed CA, every engine instance that loads it produces
byte-identical trust material, so one exported truststore (or one `sslContext()` derived from the
same PEM) works across all of them.

## Wiring an `HttpClient`

The two pieces a `java.net.http.HttpClient` needs are the trusted `SSLContext` and a
`ProxySelector` that routes traffic through the intercept listener — `Intercept` hands back both
directly:

```java
HttpClient client = HttpClient.newBuilder()
        .sslContext(intercept.trust().sslContext())
        .proxy(intercept.proxySelector())
        .build();

HttpResponse<String> response = client.send(
        HttpRequest.newBuilder(URI.create("https://example.com/")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
```

`intercept.address()` (an `InetSocketAddress`) is also available directly, for clients configured
via `http.proxyHost`/`http.proxyPort`-style properties rather than a `ProxySelector`.

## Caveats

- **One intercept per engine.** If your test suite spins up more than one `Rift` engine (e.g. one
  per test class), each gets its own intercept and its own CA (unless you commit one — see
  above). Don't assume trust material from one engine's intercept applies to another's.
- **Corporate/CI proxies.** If the process under test already goes through an upstream
  HTTP(S) proxy (a corporate proxy, a CI sidecar), that proxy and the intercept's `ProxySelector`
  compete for the same client configuration — `HttpClient.newBuilder().proxy(...)` only accepts
  one. Either route through the intercept exclusively for the duration of the test, or configure
  the intercept's own upstream via `forward`/`redirectTo` rules so it, not the client, talks to
  the real proxy.
- **No engine-side echo of `REDIRECT`.** As noted above, rules fetched back via
  `intercept.rules()` can't distinguish a `redirectTo` rule from a plain `forward` rule — both
  report `RuleKind.FORWARD`. Track the association client-side if your test needs it.
