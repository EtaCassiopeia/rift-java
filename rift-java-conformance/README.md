# rift-java-conformance

The **M1 conformance gate**. Replays the engine-canonical SDK conformance corpus
(`sdk-conformance-<version>.tar.gz`, published per Rift release) over the **remote/spawn** transport
and asserts DSL ↔ engine parity. This module is test-only and never published to Maven Central.

Every official Rift SDK replays this same corpus; it is the single source of truth for whether the
typed DSL has drifted from the engine grammar. A fixture the DSL cannot express is a **red build**
— by design, a new corpus fixture forces the DSL to grow.

## The four gates (per fixture)

| Gate | Where | What it proves |
|------|-------|----------------|
| **1 · Parse fidelity** | `ParseFidelityTest` | `ImposterDefinition.fromJson(fixture).toJson()` is `semanticEquals` to the fixture — no key dropped, renamed, or retyped. |
| **2 · DSL expressibility** | `DslExpressibilityTest` + `DslFixtures` | the fixture rebuilt through the typed `RiftDsl` API serializes back to the fixture (modulo the `_verify` test annotation). A runnable fixture with no `DslFixtures` entry fails. |
| **3 · Raw engine replay** | `CorpusReplayIT` | `rift.create(fixtureJson)` on a spawned engine serves the fixture; each stub's `_verify` request/response transcript holds. |
| **4 · DSL engine replay** | `CorpusReplayIT` | the DSL-built imposter serves the **same** transcripts — the DSL's output is engine-equivalent, not merely JSON-equivalent. |

Fixtures without `_verify` still run gates 1–2 and a smoke GET.

## What gets skipped, and why (never silently)

- **Capability lane-skip** — the corpus `manifest.json` declares each fixture's `requires` from the
  closed set `{injection, proxy, redis, https, shell}`. The remote/spawn lane provides `injection`
  (the engine spawns with `--allowInjection`) but not `proxy`/`redis`/`https`/`shell`, so fixtures
  needing those are skipped per the corpus replay contract §4.
- **Known fidelity gaps** — a fixture the wire model cannot yet round-trip (gate 1) is listed in
  `KnownFidelityGaps` with the core issue tracking it, and excluded from gates 2–4. This is *not* a
  silence: `ParseFidelityTest` asserts each listed fixture still fails, so the day core is fixed the
  entry flips the build red and must be removed. A gap can never rot into a silent pass.

## Running it

The gates need the corpus. Point at an extracted `sdk-conformance-<version>` directory (the one
holding `manifest.json` and `corpus/`):

```bash
# Download + extract the corpus for the pinned engine version, then:
./mvnw -pl rift-java-conformance -am test \
  -Drift.corpus.root=/path/to/sdk-conformance-v0.13.1
```

Resolution order for the corpus: `-Drift.corpus.root` → `RIFT_CORPUS_ROOT` → `target/corpus`. When
the corpus is absent the pure gates skip (locally) or fail loud (in CI, where `RIFT_IT=1`).

The engine replay lane (gates 3–4, `CorpusReplayIT`) is heavier — it spawns a real engine (its
binary is downloaded and cached on first use) — so it self-skips unless `RIFT_IT=1`:

```bash
RIFT_IT=1 ./mvnw -pl rift-java-conformance -am verify \
  -Drift.corpus.root=/path/to/sdk-conformance-v0.13.1
```

CI runs exactly this in the `conformance` job (see `.github/workflows/ci.yml`), downloading the
corpus version-locked to `rift.engine.version`.

## Adding a fixture to the DSL registry

When the corpus grows, gate 2 fails for the new fixture until you add its entry to `DslFixtures`:
one `buildNN()` method that reconstructs the imposter through `RiftDsl` only (see the existing
entries and core's `CorpusExpressibilityTest` for the idioms). If the DSL genuinely cannot express a
construct, that is the signal to grow the DSL — not to weaken the gate.
