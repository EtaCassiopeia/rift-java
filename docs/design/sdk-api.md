# rift-java API Design (v1)

Status: **accepted design** — this document pins down the public API so implementation issues
carry no open design decisions. Canonical location: `docs/design/sdk-api.md`; mirrored as
issue #19. Sources: rift engine 0.12.0 (`EtaCassiopeia/rift`), C-ABI v2
(`librift_ffi`, `include/rift_ffi.h`), the rift-conformance corpus + Plane-B SPI, rift-node
0.12.x (reference SDK), the rift-scala design issues (consumer of this SDK), and a DX benchmark
of WireMock 3 / MockServer / Hoverfly / Testcontainers / wiremock-spring-boot.

---

## 1. Goals and hard constraints

1. **Full engine surface on every transport.** Stubs/predicates/responses, response cycling,
   behaviors, proxy record/playback, faults (connection + probabilistic `_rift` faults),
   stateful scenarios, spaces/flow-state, request verification, TLS-MITM intercept. No
   transport is a reduced subset (README promise; conformance Plane-B requirement).
2. **Cross-SDK contracts are fixed** (shared with rift-node and rift-scala):
   - Transports: `Rift.embedded()` / `Rift.connect(uri)` / `Rift.spawn()`.
   - Error taxonomy, exactly five leaves: `InvalidDefinition`, `EngineUnavailable`,
     `CommunicationError`, `ImposterNotFound`, `EngineError(code, message)`.
   - DSL vocabulary: `imposter`, `onGet/onPost/…`, `willReturn`, `ok/okJson/created/status/
     notFound/noContent`, `proxyTo`, `fault`, `inject`, `scenario().startingAt().when()
     .respond().goTo()`, `record()`, `verify(match, times(n))`.
3. **Scala-wrappable by construction** (rift-scala delegates everything to this SDK):
   synchronous, throwing, `AutoCloseable`, `Optional` (never `null`), `java.util` collections,
   immutable value types, plus a **raw-JSON escape hatch** on every ingestion point so the
   Scala bridge can bypass Java builders entirely.
4. **Framework-neutral core.** `rift-java-core` stays zero-runtime-dep, JDK 17, plain types —
   usable from bare JUnit, TestNG, or any framework. Spring/JUnit5/Jackson support are
   separate modules that adapt the core, never the other way around.
5. **Conformance is the acceptance test.** Every fixture in the shared corpus must be
   expressible in the typed DSL and round-trip losslessly (byte-fidelity via the existing
   `JsonNumber`-raw + `semanticEquals` machinery).

## 2. Module map (final)

| Artifact | JDK | Contents |
|---|---|---|
| `rift-java-core` | 17 | wire model, zero-dep JSON codec, DSL, `Rift` client + remote/spawn transports, verification, body-codec SPI |
| `rift-java-embedded` | 22 | FFM bridge for C-ABI v2, `EmbeddedEngineProvider` |
| `rift-java-embedded-jdk21` | 21 (preview) | same sources, preview FFM |
| `rift-java-natives` | — | per-platform classifier jars (`native/<os>-<arch>/librift_ffi.<ext>`) |
| `rift-java-junit5` | 17 | `@RiftTest`, `RiftExtension`, parameter injection |
| `rift-java-jackson` | 17 | `RiftBodyCodec` implementation over `jackson-databind` |
| `rift-java-spring` | 17 | **new** — Spring Boot test integration (`@EnableRift`, `@ConfigureImposter`, `@InjectImposter`) |
| `rift-java-bom` | — | **new** — BOM aligning all modules + natives classifiers |

Backlog modules (issues filed, not v1-blocking): `rift-java-testcontainers`
(`RiftContainer`), record/playback golden-file sugar.

## 3. Package map (final)

```
io.github.etacassiopeia.rift            Rift, Imposter, Space, FlowState, Scenarios, StubRef,
                                        RecordedRequest, EngineInfo, VerificationTimes,
                                        ConnectOptions, SpawnOptions, EmbeddedOptions,
                                        Intercept, InterceptOptions, InterceptTrust
io.github.etacassiopeia.rift.error      RiftException (sealed) + 5 leaves, WireFormatException
io.github.etacassiopeia.rift.verify     VerificationException, RequestMatch, PredicateEvaluator
io.github.etacassiopeia.rift.model      wire records (ImposterDefinition, Stub, Predicate, …)
io.github.etacassiopeia.rift.json       JsonValue + codec (unchanged)
io.github.etacassiopeia.rift.dsl        RiftDsl + spec builders (unchanged home)
io.github.etacassiopeia.rift.codec      RiftBodyCodec SPI
io.github.etacassiopeia.rift.transport  internal SPI (RiftTransport, EmbeddedEngineProvider)
```

### 3.1 Naming decision: handle vs. definition

The live, transport-bound handle is **`Imposter`** (root package) — it is what users hold 99%
of the time (`Imposter users = rift.create(...)`, per README and rift-scala examples). The
wire-model record currently named `model.Imposter` is renamed **`model.ImposterDefinition`**
(and `model.Imposters` → `model.ImposterDefinitions`). Rationale: two types named `Imposter`
in adjacent packages is a permanent ambiguity tax on the Scala bridge and on anyone using the
escape hatches; the rename is mechanical and we are pre-release. `imposter.definition()`
returns the record, closing the loop.

## 4. Error model (cross-SDK contract, sealed)

```java
package io.github.etacassiopeia.rift.error;

public sealed class RiftException extends RuntimeException
    permits InvalidDefinition, EngineUnavailable, CommunicationError,
            ImposterNotFound, EngineError { … }

public final class InvalidDefinition  extends RiftException { }          // HTTP 400 / FFI validation
public final class EngineUnavailable  extends RiftException { }          // connect refused / lib missing / spawn failed
public final class CommunicationError extends RiftException { }          // unparseable response / broken pipe
public final class ImposterNotFound   extends RiftException { public int port(); }   // HTTP 404
public final class EngineError        extends RiftException { public int code(); }   // any other engine error
```

- Unchecked. Class names deliberately match rift-node / the cross-SDK contract verbatim (no
  `-Exception` suffix); the base carries the suffix.
- Uniform across transports: remote maps HTTP status + `{errors:[{code,message}]}` envelope;
  embedded maps FFI sentinel + `rift_last_error()`; spawn maps process-launch failures to
  `EngineUnavailable`.
- `VerificationException extends AssertionError` (NOT `RiftException`) so test runners report
  verification failures as *failures*, not errors (see §8).

## 5. The client: `Rift`

```java
public interface Rift extends AutoCloseable {

  // -- construction (one client, three transports) --
  static Rift connect(URI adminUri);
  static Rift connect(ConnectOptions options);
  static Rift spawn();                       // managed rift binary, ephemeral admin port
  static Rift spawn(SpawnOptions options);
  static Rift embedded();                    // in-process via ServiceLoader (see §5.2)
  static Rift embedded(EmbeddedOptions options);
  static boolean isEmbeddedAvailable();      // provider on classpath AND native resolvable

  // -- imposters --
  Imposter create(ImposterSpec spec);        // DSL spec directly — no .build() at call sites
  Imposter create(ImposterDefinition definition);
  Imposter create(JsonValue imposterJson);   // escape hatch (Scala bridge, raw fixtures)
  Imposter create(String imposterJson);      // escape hatch
  Optional<Imposter> imposter(int port);
  List<Imposter> imposters();
  void deleteAll();
  ApplyResult applyConfig(JsonValue config); // reconcile ({imposters:[…]}); mirrors POST /admin/reload
  void replaceAll(List<ImposterDefinition> imposters);  // atomic PUT /imposters

  // -- engine --
  EngineInfo info();                         // {version, commit, features} — GET /config | rift_build_info
  URI adminUri();                            // remote/spawn: the admin endpoint; embedded: lazily
                                             // starts rift_serve_admin on first call
  // -- intercept (TLS-MITM) --
  Intercept intercept();                     // default options
  Intercept intercept(InterceptOptions options);

  // -- async facade (secondary surface) --
  RiftAsync async();                         // CompletableFuture mirrors of create/deleteAll/imposters

  @Override void close();                    // idempotent; never throws checked
}
```

Conventions: all methods synchronous + throwing (`RiftException` leaves); thread-safe; safe on
virtual threads (documented: embedded downcalls block a carrier only for the call duration).

### 5.1 Options types (immutable builders)

```java
public final class ConnectOptions {
  static Builder builder(URI adminUri);
  // adminUri (required), apiKey (→ Authorization), requestTimeout (default 30s),
  // versionCheck: FAIL | WARN | OFF (default FAIL, engine >= minEngineVersion via GET /config),
  // hostResolver: IntFunction<URI>  — maps an imposter port to the base URI the SUT should use.
  //   Default: adminUri.host + imposter port. Needed for Docker/remapped-port setups
  //   (the conformance "hostFor seam").
}

public final class SpawnOptions {
  // binaryPath (Path), version (String, default = SDK's pinned engine version),
  // host (default 127.0.0.1), adminPort (0 = ephemeral), allowInjection (default true),
  // localOnly (default true), logLevel, env (Map), workingDir, mirrorUrl,
  // startupTimeout (default 15s), shutdownTimeout (default 5s), inheritLog (bool)
  // Binary resolution order: binaryPath → $RIFT_BINARY_PATH → PATH (rift) →
  //   version cache (~/.cache/rift-java/binaries/rift-<ver>/) → download via release
  //   ffi-manifest.json (SHA-256 verified; RIFT_OFFLINE/RIFT_SKIP_BINARY_DOWNLOAD → fail).
}

public final class EmbeddedOptions {
  // libraryPath (Path — overrides resolution), minEngineVersion, versionCheck FAIL|WARN|OFF,
  // serveAdminEagerly (bool, default false), adminHost/adminPort/apiKey (for rift_serve_admin)
}
```

### 5.2 `Rift.embedded()` lives in core, implementation in `rift-java-embedded`

Core defines:

```java
package io.github.etacassiopeia.rift.transport;
public interface EmbeddedEngineProvider {
  boolean isAvailable();                       // native lib resolvable for this OS/arch
  Rift start(EmbeddedOptions options);
}
```

`Rift.embedded()` ServiceLoader-locates the provider; if absent it throws `EngineUnavailable`
with the exact dependency coordinates to add (`rift-java-embedded` + the natives classifier
for the current platform). This keeps the "one client, three transports" story in a single
entry-point class while preserving the JDK-17 core / JDK-22 embedded split.

### 5.3 Transport SPI (internal)

```java
package io.github.etacassiopeia.rift.transport;
public interface RiftTransport extends AutoCloseable {
  JsonValue createImposter(JsonValue definition);          // returns created definition (with port)
  JsonValue getImposter(int port);                         // ImposterNotFound on 404
  void deleteImposter(int port);
  void deleteAll();
  JsonValue listImposters(boolean replayable, boolean removeProxies);
  void replaceAllImposters(JsonValue impostersDoc);
  JsonValue applyConfig(JsonValue config);
  void addStub(int port, JsonValue stub);
  void replaceStubs(int port, JsonValue stubs);
  void replaceStub(int port, StubAddress address, JsonValue stub);   // index- or id-addressed
  void deleteStub(int port, StubAddress address);
  JsonValue getStub(int port, StubAddress address);        // default: UnsupportedOperationException
  JsonValue recorded(int port);                            // savedRequests
  void clearRecorded(int port);
  void enable(int port);  void disable(int port);
  // scenarios
  JsonValue scenarios(int port, Optional<String> flowId);
  void setScenarioState(int port, String name, String state);
  void resetScenarios(int port);
  // flow state
  Optional<JsonValue> flowStateGet(int port, String flowId, String key);
  void flowStatePut(int port, String flowId, String key, JsonValue value);
  void flowStateDelete(int port, String flowId, String key);
  // spaces
  void spaceAddStub(int port, String flowId, JsonValue stub);
  JsonValue spaceListStubs(int port, String flowId);
  JsonValue spaceRecorded(int port, String flowId);
  void spaceDelete(int port, String flowId);
  // intercept
  JsonValue startIntercept(JsonValue options);
  void interceptAddRules(JsonValue rules);
  JsonValue interceptListRules(); void interceptClearRules();
  String interceptCaPem();
  void interceptExportTruststore(String format, String password, Path out);
  // engine
  JsonValue buildInfo(); URI adminUri();
  JsonValue verify(int port, JsonValue body);              // default: UnsupportedOperationException
  JsonValue stubWarnings(int port);                        // default: UnsupportedOperationException
}
```

`getStub`, `verify`, and `stubWarnings` are `default` methods (throwing `UnsupportedOperationException`
unless overridden) so existing `RiftTransport` implementations and test fakes keep compiling.

Implementations: `RemoteTransport` (`java.net.http`, admin HTTP), `EmbeddedTransport`
(FFM ↔ the C-ABI table below). `spawn` = `RemoteTransport` + process lifecycle. Everything
above `RiftTransport` (handles, verification, DSL) is transport-agnostic — this is what makes
"full surface on each transport" cheap to guarantee and lets the conformance harness run the
same corpus over both.

Embedded mapping (C-ABI v2): `createImposter→rift_create_imposter`, `replaceStubs→
rift_replace_stubs`, `recorded→rift_recorded`, `deleteImposter→rift_delete_imposter`,
`deleteAll→rift_delete_all`, `applyConfig→rift_apply_config`, `flowState*→rift_flow_state_*`,
`space*→rift_space_*`, `startIntercept→rift_start_intercept`, `intercept*→rift_intercept_*`,
`buildInfo→rift_build_info`, `adminUri→rift_serve_admin` (lazy, once). The v2-admin long tail is
now direct FFI too (rift ≥ 0.13.1): `getImposter`/`listImposters→rift_get_imposter`/
`rift_list_imposters`, `addStub→rift_add_stub`, `getStub→rift_get_stub`, `replaceStub→
rift_update_stub`, `deleteStub→rift_delete_stub`, `clearRecorded→rift_clear_recorded`,
`clearProxyResponses→rift_clear_proxy_recordings`, `enable`/`disable→
rift_set_imposter_enabled`, `scenarios→rift_scenarios`, `setScenarioState→
rift_set_scenario_state`, `resetScenarios→rift_reset_scenarios`, `verify→rift_verify`,
`stubWarnings→rift_stub_warnings`. Only `replaceAllImposters` (bulk `PUT /imposters`, no C-ABI
counterpart) still routes through the lazily-started in-process admin server. Ownership
discipline (per-call confined arenas, `rift_free`, sentinel + same-thread `rift_last_error`,
static `build_info` never freed, symbol-probe for graceful degradation) is encapsulated once in
the bridge (issue #8).

## 6. The handle: `Imposter`

```java
public interface Imposter {
  int port();
  URI uri();                                  // via ConnectOptions.hostResolver seam
  Optional<String> name();
  ImposterDefinition definition();            // live GET (includes recorded state if requested)

  // -- stubs --
  StubRef addStub(StubSpec spec);             // returns index- (and id-, if set) addressed ref
  StubRef addStub(StubSpec spec, int index);  // insert at position (0 = first, highest priority)
  StubRef addStubFirst(StubSpec spec);        // sugar for (spec, 0) — the overlay idiom (addStubFirst → ref.delete() reverts)
  StubRef addStub(JsonValue stub);            // escape hatch
  void replaceStubs(List<StubSpec> specs);
  void replaceStubs(ScenarioSpec scenario);   // convenience: scenario → stub list
  StubRef stub(String id);                    // id-addressed handle; ImposterNotFound-style miss → EngineError
  List<Stub> stubs();
  List<StubWarning> stubWarnings();           // rift_stub_warnings / overlap lint

  // -- recorded requests + verification (§8) --
  List<RecordedRequest> recorded();
  List<RecordedRequest> recorded(RequestMatch match);      // client-side filtered
  void clearRecorded();
  void clearProxyResponses();                              // DELETE savedProxyResponses
  void verify(RequestMatch match);                         // == atLeastOnce()
  void verify(RequestMatch match, VerificationTimes times);
  void verifyNoInteractions();

  // -- scenarios (FSM) --
  Scenarios scenarios();

  // -- spaces + flow state --
  Space space(String flowId);
  FlowState flowState(String flowId);

  // -- lifecycle --
  void enable(); void disable();
  void delete();                              // DELETE /imposters/:port; handle unusable after
}

public interface StubRef {
  int index(); Optional<String> id();
  Stub definition();
  void replace(StubSpec spec); void delete();
}

public interface Scenarios {
  record State(String name, String state) {}
  List<State> list();
  List<State> list(String flowId);            // space-scoped FSM state
  String state(String name);
  void setState(String name, String state);
  void reset();
}

public interface Space {
  String flowId();
  StubRef addStub(StubSpec spec);
  List<Stub> stubs();
  List<RecordedRequest> recorded();
  List<RecordedRequest> recorded(RequestMatch match);
  void verify(RequestMatch match);            // same semantics as Imposter.verify, space-scoped
  void verify(RequestMatch match, VerificationTimes times);
  void delete();                              // tears down stubs + recorded + state for the space
}

public interface FlowState {
  Optional<JsonValue> get(String key);
  void put(String key, JsonValue value);
  void put(String key, String value);
  void delete(String key);
}
```

`Imposter`, `Space`, `FlowState`, `Scenarios` are thin transport-backed views — no client-side
caching, every call hits the engine (test code wants truth, not staleness).

**Flow-state / spaces configuration (uniform across transports).** The engine backs the flow-state
and per-space APIs with a real store only when the def declares one — an explicit `_rift.flowState`,
a scenario stub (`scenarioName`/`requiredScenarioState`/`newScenarioState`), or a `_rift.script`
stub; otherwise it uses a silent no-op store (reads return empty). Separately, a per-space stub only
matches when a header-form `flowIdSource` is configured (`flowIdFromHeader(...)`), because the
engine's flow-id source defaults to the imposter port. The SDK does **not** auto-inject a flow store
(a transport is a faithful wire mapping — it never rewrites user input); instead it fails fast and
warns: `ImposterSpec.build()` throws if a space stub is declared without a header `flowIdSource`, and
`Imposter.space()`/`flowState()` log one advisory warning on a source-less / trigger-less def. Declare
`flowState(inMemoryFlowState().flowIdFromHeader("X-Your-Header"))` for spaces.

## 7. DSL v2 — closing the surface gaps

`RiftDsl` remains the single static-import hub (WireMock model). Grammar and existing entry
points are unchanged; the following is added/fixed.

### 7.1 Collision policy (documented in the class javadoc)

- `equalTo(String|JsonValue)` becomes the **primary** equality matcher name (WireMock muscle
  memory; `equals` cannot be usefully static-imported because of `Object.equals`). `eq` stays
  as the terse alias; `equals` overloads stay for discoverability. Note: `eq` collides with
  `Mockito.eq`, `equalTo` with Hamcrest — no name is globally safe; the javadoc shows the
  qualified-import fix.
- `exactly(n)` is added as an alias for `times(n)` because `Mockito.times` is ubiquitous in
  the same test files (wildcard-importing both otherwise forces qualification).

### 7.2 Compile-time response typing (kills the `IllegalStateException` guard)

```java
public sealed interface ResponseSpec permits IsSpec, ProxySpec, FaultSpec, InjectSpec, ScriptSpec { }
```

- `ok()/okJson()/created()/noContent()/notFound()/status(n)` return **`IsSpec`** — the only
  type carrying body/header/behavior chain methods.
- `fault(Fault)` → `FaultSpec`, `inject(js)` → `InjectSpec`, `script(Script)` → `ScriptSpec`
  (terminal: no body/header methods exist to call).
- `willReturn(ResponseSpec...)` accepts them all. `ImposterSpec.defaultResponse(IsSpec)` is
  typed to the engine's actual constraint (defaultResponse is an `is` response, no behaviors).
- `Fault` becomes a plain 4-value enum mirroring the engine (and WireMock) exactly:
  `CONNECTION_RESET_BY_PEER, EMPTY_RESPONSE, RANDOM_DATA_THEN_CLOSE, MALFORMED_RESPONSE_CHUNK`.
  The current `Fault.latencySpike(Duration)` hybrid moves to the probabilistic `_rift` fault
  surface below.

### 7.3 New `IsSpec` methods (full behavior + `_rift` coverage)

```java
// RiftDsl factory: okJsonRaw(String) serves the JSON verbatim (byte-for-byte, no reparse),
// unlike okJson(String) which parses + canonicalizes — for payloads whose exact form matters.
IsSpec withBinaryBody(byte[] bytes)                      // base64 + _mode=binary
IsSpec withBodyFromCodec(Object pojo)                    // via RiftBodyCodec SPI (§10)
IsSpec copy(CopySpec... copies)                          // _behaviors.copy (array wire form)
IsSpec copyObject(CopySpec copy)                         // _behaviors.copy (single-object wire form)
IsSpec lookup(LookupSpec... lookups)                     // _behaviors.lookup (array wire form)
IsSpec lookupObject(LookupSpec lookup)                   // _behaviors.lookup (single-object wire form)
IsSpec shellTransform(String... commands)                // _behaviors.shellTransform
IsSpec waitScript(String source)                         // _behaviors.wait as a bare function string
IsSpec templated()                                       // rename of template(); _rift.templated
// probabilistic _rift faults (chainable, composable):
IsSpec withLatencyFault(double probability, Duration min, Duration max)
IsSpec withLatencyFault(double probability, Duration fixed)
IsSpec withErrorFault(double probability, int status)
IsSpec withErrorFault(double probability, int status, JsonValue body)
IsSpec withTcpFault(Fault kind)                          // always fires (bare wire form)
IsSpec withTcpFault(double probability, Fault kind)      // probabilistic object form; requires rift >= 0.13.2 (rift#531)

// helper factories on RiftDsl:
static CopySpec copyFrom(String from)                    // .into("$TOKEN").using(regex(...)|jsonPath(...)|xPath(...))
static CopySpec copyFromQuery(String name)               // copy from {"query": name}
static CopySpec copyFromHeader(String name)              // copy from {"headers": name}
static LookupSpec lookupKey(String from)                 // .using(...).fromCsv(path, keyColumn).into("$ROW")
```

### 7.4 New `StubSpec` methods

```java
StubSpec inSpace(String flowId)                          // stub.space
StubSpec withId(String id)                               // stub.id (enables by-id ops)
StubSpec withRoute(String pattern)                       // stub.routePattern ("/users/:id")
StubSpec withPredicateInject(String script)              // PredicateOperation.Inject
// WireMock-style stub-level scenario sugar (alternative to ScenarioSpec):
StubSpec inScenario(String name)
StubSpec whenScenarioState(String state)
StubSpec willSetScenarioState(String state)
```

`ScenarioSpec.stubs()` gains fail-fast validation: `startingAt(...)` must have been called
(missing start state currently emits an unsatisfiable guard silently).

### 7.5 New `ImposterSpec` methods (imposter-level config + `_rift`)

```java
ImposterSpec host(String bindHost)
ImposterSpec https(String certPem, String keyPem)        // both-or-neither, validated at build
ImposterSpec defaultForward(String upstreamUrl)
ImposterSpec strictBehaviors()
ImposterSpec serviceName(String name)
ImposterSpec serviceInfo(JsonValue info)
ImposterSpec allowCors()                                 // exists
ImposterSpec flowState(FlowStateSpec spec)               // _rift.flowState
ImposterSpec metrics(int port)                           // _rift.metrics
ImposterSpec scriptEngine(ScriptEngine engine, Duration timeout)   // _rift.scriptEngine
ImposterSpec script(String name, Script script)          // _rift.scripts named registry
ImposterSpec proxyPool(int maxIdlePerHost, Duration idleTimeout)   // _rift.proxy.connectionPool

// factories:
static FlowStateSpec inMemoryFlowState()                 // .ttl(Duration).flowIdFromHeader(name)
static FlowStateSpec redisFlowState(String url)          // .poolSize(n).keyPrefix(s).ttl(d)
```

### 7.6 `ProxySpec` typed predicate generators

```java
ProxySpec generateBy(RequestField... fields)             // enum METHOD, PATH, QUERY, HEADERS, BODY
ProxySpec generateBy(PredicateGeneratorSpec generator)   // .matching(fields).caseSensitive(b).jsonPath(sel)
ProxySpec addWaitBehavior()                              // currently hardcoded false
ProxySpec decorateWith(String script)                    // addDecorateBehavior
```

### 7.7 Verification grammar (single vocabulary, WireMock-quality diffs)

`StubSpec implements RequestMatch` — the same openers stub and verify:

```java
users.verify(onGet("/api/users/1"), times(1));
users.verify(onPost("/api/users").withHeader("Content-Type", contains("json")), atLeast(2));
List<RecordedRequest> hits = users.recorded(onGet("/api/users/1"));
```

```java
public interface RequestMatch {
  List<Predicate> predicates();
  // verification-path escape hatch (mirrors the raw-JSON creation hatches — §1.3):
  static RequestMatch of(List<Predicate> predicates);   static RequestMatch of(Predicate... predicates);
  static RequestMatch ofJson(JsonValue predicateArray); static RequestMatch ofJson(String predicateArrayJson);
}

public final class VerificationTimes {                    // factories re-exported on RiftDsl
  static VerificationTimes times(int n);      static VerificationTimes exactly(int n);   // alias
  static VerificationTimes atLeast(int n);    static VerificationTimes atMost(int n);
  static VerificationTimes between(int min, int max);     static VerificationTimes never();
}
```

Matching runs **client-side** over `recorded()` via a `PredicateEvaluator` in core that
implements Mountebank predicate semantics against `RecordedRequest` (all field ops +
`and/or/not` + `caseSensitive/keyCaseSensitive/except` + `jsonpath` (subset evaluator over our
own `JsonValue`) + `xpath` via the JDK's `javax.xml.xpath` — still zero external deps).
`inject` predicates are not evaluable client-side → `verify` throws `InvalidDefinition` with
that exact explanation.

On failure, `VerificationException extends AssertionError` renders the WireMock-grade report:

```
Verification failed for imposter :4545 ("users")
Expected: GET /api/users/1  —  exactly 1 time, but was 0.

3 recorded requests, closest match first:
  ✗ GET /api/users/2         path: expected "/api/users/1" (equals), got "/api/users/2"
  ✗ POST /api/users/1        method: expected "GET" (equals), got "POST"
  ✗ GET /health              path: expected "/api/users/1" (equals), got "/health"
```

Near-miss ranking = number of satisfied clauses, descending; each line names the first failing
clause. `recordRequests` must be on for verification — `verify` on a non-recording imposter
throws `InvalidDefinition("imposter :4545 does not record requests — add .record()")`.

## 8. `RecordedRequest` (typed, lossless)

```java
public record RecordedRequest(
    String method, String path,
    Map<String, List<String>> query,
    Map<String, List<String>> headers,
    String body,                               // raw; binary arrives base64 per engine contract
    Optional<Instant> timestamp,
    Optional<String> requestFrom,              // client ip:port
    Optional<String> flowId,
    Map<String, String> pathParams,            // when routePattern matched
    JsonValue raw) {                           // lossless escape hatch
  public Optional<JsonValue> bodyAsJson();     // empty if not parseable
  public Optional<String> header(String name);        // case-insensitive, first value
  public Optional<String> queryParam(String name);
}
```

## 9. Intercept (TLS-MITM) — typed surface

```java
public interface Intercept extends AutoCloseable {
  InetSocketAddress address();                       // for ProxySelector / http.proxyHost
  URI uri();
  ProxySelector proxySelector();                     // convenience for java.net.http clients
  InterceptRule serve(String host, IsSpec response); // rule: answer host directly
  InterceptRule forward(String host, String hostPort);
  InterceptRule redirectTo(String host, Imposter imposter);
  InterceptRuleBuilder rule();                       // predicate-scoped + optional-host: rule().host(h).when(match).serve/forward/redirectTo
  List<InterceptRule> rules(); void clearRules();
  InterceptTrust trust();
  @Override void close();                            // clears rules; stops listener where supported
}

public interface InterceptTrust {
  String caPem();
  SSLContext sslContext();                           // in-memory truststore with the CA (hermetic SUT)
  SSLContext sslContextWithSystemCAs();              // CA + JVM default anchors (SUT also calls real HTTPS)
  void exportTruststore(TruststoreFormat format, String password, Path out);               // PKCS12 | JKS
  void exportTruststoreWithSystemCAs(TruststoreFormat format, String password, Path out);  // + system anchors
}

public final class InterceptOptions {                // builder
  // port (0 = OS-assigned), host (IP literal); committed CA (both-or-neither, else ephemeral) as:
  //   ca(Path cert, Path key)                         // engine reads the files from its own fs
  //   ca(String|byte[] cert, key) | ca(KeyStore, pw)  // inline PEM in the start body (rift >= 0.13.4) — no mount
  //   generateCa()                                    // engine mints one; retrieve via intercept.caMaterial()
  // -> intercept.caMaterial(): Optional<Intercept.CaMaterial(String certPem, String keyPem)> (generateCa only)
}
```

Remote transport maps to `/intercept/*` admin endpoints (rift ≥ 0.13.3 `POST /intercept` starts a
listener at runtime, #493); embedded maps to `rift_start_intercept` / `rift_intercept_*`.
`InterceptOptions.attach(host, port)` binds to a listener the engine started at launch
(`--intercept-port` / `RIFT_INTERCEPT_PORT`) instead of starting one — used by
`RiftContainer.withInterceptPort(...)` + `interceptOptions()`. Bind host must be an IP literal.
Target ergonomics: the rift-java-demo ~30-line hand-rolled flow becomes ~8 lines (issue #12's goal).

## 10. Body codec SPI (`rift-java-jackson` becomes real)

```java
package io.github.etacassiopeia.rift.codec;
public interface RiftBodyCodec {
  JsonValue toJson(Object value);
  <T> T fromJson(JsonValue json, Class<T> type);
}
```

- Discovered via `ServiceLoader` (first hit wins; explicit `RiftDsl.useBodyCodec(codec)`
  override for non-classpath setups).
- Enables DSL overloads: `okJson(Object)`, `IsSpec.withBodyFromCodec(Object)`,
  `equalTo(Object)`, `deepEquals(Object)`, and `RecordedRequest.bodyAs(Class<T>)`.
- Without a codec on the classpath these overloads throw `IllegalStateException` naming the
  `rift-java-jackson` coordinates. Core stays zero-dep.

## 11. JUnit 5 (`rift-java-junit5`)

Two-tier, Testcontainers/WireMock conventions:

```java
@RiftTest                                            // AUTO transport, per-test reset
class UserApiTest {
  @RiftImposter                                      // static spec field → created once per class
  static ImposterSpec users = imposter("users").record()
      .stub(onGet("/api/users/1").willReturn(okJson("{\"id\":1}")));

  @Test
  void findsUser(Imposter users, Rift rift) {        // parameter injection: by type; multiple
    // point SUT at users.uri() …                    // imposters disambiguated by parameter name
    users.verify(onGet("/api/users/1"), times(1));
  }
}
```

```java
@RiftTest(transport = Transport.EMBEDDED,            // EMBEDDED | SPAWN | CONNECT | AUTO
          reset = Reset.PER_TEST,                    // PER_TEST (default) | PER_CLASS | NONE
          adminUri = "…",                            // CONNECT only; property-resolvable
          dumpRecordedOnFailure = true)              // default true: failed test → recorded dump
```

- `Transport.AUTO` (default): embedded if `Rift.isEmbeddedAvailable()` → spawn if binary
  resolvable → fail with a message naming both fixes.
- **Intercept**: `@RiftIntercept` (class) starts a listener for the class; `@RiftInterceptRules`
  (a `static void` method, params `Intercept`/`@InjectRift`/`@InjectImposter`) declares rules, applied
  on start and re-applied after each per-test rules reset; `@InjectIntercept` injects the live handle.
  CA (`caCert`/`caKey`) and `exportTruststore` attributes cover the shared-CA / containerized-SUT flows.
- One `Rift` engine per test class (static) — per-method engine via instance-field
  `@RegisterExtension`.
- `Reset.PER_TEST`: between tests, recorded requests + scenario states + flow state cleared,
  `@RiftImposter` stubs restored to their spec (imposters are NOT recreated — ports stay
  stable for the class lifetime).
- Programmatic tier:

```java
@RegisterExtension
static RiftExtension rift = RiftExtension.newInstance()
    .transport(Transport.SPAWN).options(SpawnOptions.builder()…)
    .imposter("users", imposter("users")…)
    .reset(Reset.PER_TEST)
    .build();
```

- On test failure with `dumpRecordedOnFailure`, the extension publishes the recorded-request
  dump via `TestReporter` and appends it to the failure message.

## 12. Spring Boot (`rift-java-spring`, new module)

Model: wiremock-spring-boot (named instances + user-chosen property keys), not
`@ServiceConnection` (wrong abstraction for generic HTTP deps — there is no `ConnectionDetails`
type to target).

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@EnableRift(transport = Transport.AUTO)
@ConfigureImposter(name = "users",    baseUrlProperty = "user-client.base-url")
@ConfigureImposter(name = "payments", baseUrlProperty = "payment-client.base-url",
                   spec = PaymentImposters.class)     // Supplier<ImposterSpec> for pre-stubbing
class OrderServiceIT {

  @InjectImposter("users") Imposter users;            // field injection
  @InjectRift Rift rift;

  @Test void ordersCallUsers() {
    users.addStub(onGet("/api/users/1").willReturn(okJson("{\"id\":1}")));
    // … drive the app; its user-client.base-url already points at the imposter …
    users.verify(onGet("/api/users/1"), times(1));
  }

  // parameter injection (equivalent) — zero-config via @EnableRift's @ExtendWith meta-annotation
  @Test void ordersCallPayments(@InjectImposter("payments") Imposter payments, @InjectRift Rift r) {
    payments.addStub(onPost("/pay").willReturn(created()));
  }
}
```

Implementation contract (so the implementer has zero decisions):
- A `ContextCustomizerFactory` (registered in `META-INF/spring.factories`) starts **one `Rift`
  engine per application context** (cached with the context), creates the named imposters
  before the `Environment` is frozen, and registers a `MapPropertySource`
  (`"rift"`, highest test precedence) with each `baseUrlProperty` → `imposter.uri()` (and
  optional `portProperty`). Same-named config across test classes → same cache key → context
  reuse is preserved (the wiremock-spring-boot lesson: never pollute the context with beans).
- A `TestExecutionListener` resets per test method (same semantics as JUnit5 `Reset.PER_TEST`)
  and handles `@InjectImposter`/`@InjectRift` field injection; a `RiftParameterResolver`
  (auto-registered by `@ExtendWith` meta-annotated on `@EnableRift`) handles the same
  annotations as `@Test`/lifecycle method parameters — no extra `@ExtendWith` required.
- Engine shutdown via context-closed event.
- No Spring beans are added to the user's context; the module depends only on
  `spring-test`/`spring-context` (provided scope) + core.

## 13. Wire-model fidelity additions

- Every aggregate record (`ImposterDefinition`, `Stub`, `IsResponse`, `ProxyResponse`) gains a
  trailing `Map<String, JsonValue> extra` component: unknown keys are preserved on read and
  re-emitted (insertion order) on write — mirroring `Behavior.Unknown`. This is required for
  corpus replay of real engine output (issues #7/#14) and future-proofs against engine
  additions.
- Lenient reads already handled (statusCode string/number, `behaviors` alias, `allowCors`).
  Add: flat/recorded response form (top-level statusCode/headers/body without `is` wrapper,
  engine issue #304) — read as `Is`, write canonical.
- `rift-verify`'s `_verify` stays a raw `JsonValue` passthrough (it is a corpus annotation, not
  an engine field; the SDK never interprets it).

## 14. Versioning, BOM, capability negotiation

- `rift-java-bom` pins all modules + the natives classifier jars. Natives version == engine
  version; SDK minor tracks the engine line (as rift-node does: SDK 0.12.x ↔ engine 0.12.x).
- `EngineInfo { String version(); String commit(); Set<String> features(); }` — from
  `GET /config` / `rift_build_info`. `features` powers honest capability negotiation for the
  conformance harness (Plane-B `require(caps…)`).
- Version preflight: `versionCheck FAIL|WARN|OFF` against `minEngineVersion` (constant in core,
  bumped with each SDK release), uniform across the three transports.

## 15. What is deliberately NOT in v1

- Reactive/streaming recorded-request tail (rift-scala will poll `recorded()`; SSE when the
  engine grows it).
- OpenAPI → imposter import (differentiator; backlog).
- Record/playback golden-file sugar (`startRecording(origin)` / auto-capture annotation flow —
  Hoverfly's best idea; backlog issue filed).
- Testcontainers module (`RiftContainer`); backlog issue filed.
- TCP/SMTP protocols — the engine itself is HTTP/HTTPS only.

## 16. Issue mapping

| Surface | Issue |
|---|---|
| Client + handles + remote transport | #4 (expanded) |
| Spawn transport | #5 (expanded) |
| Verification | #6 (expanded) |
| FFM bridge + transport SPI | #8 (expanded) |
| Natives | #9 (unchanged) |
| `Rift.embedded()` + ServiceLoader | #10 (expanded) |
| jdk21 dual-publish | #11 (unchanged) |
| Intercept | #12 (expanded) |
| JUnit 5 | #13 (expanded) |
| DSL v2 completeness + response typing | #20 |
| Wire-model fidelity + `ImposterDefinition` rename | #21 |
| Body codec SPI + Jackson | #22 |
| Spring Boot module | #23 |
| BOM | #24 |
| Backlog: record/playback sugar | #25 |
| Backlog: Testcontainers module | #26 |
