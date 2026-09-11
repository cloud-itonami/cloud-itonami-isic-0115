# cloud-itonami-isic-0115

Open Occupation Blueprint for **ISIC Rev. 4 0115**: Growing of tobacco.

This repository implements a forkable OSS **tobacco-growing operations
coordinator**: a field-management and record-keeping robot manages
planting/curing/grading batch and leaf-quality logging, field/curing
operation (planting/topping/harvesting/curing/grading) scheduling, and
supply procurement under a governor-gated actor, so a tobacco farm keeps
its own operational records and maintains full transparency over
decisions.

**Maturity: `:implemented`.** `src/tobaccoops/` implements the
`TobaccoOpsAdvisor` (`tobaccoops.advisor`) and the independent
`TobaccoOperationsGovernor` (`tobaccoops.governor`), composed by
`tobaccoops.operation` following the itonami actor pattern
(ADR-2607011000): `intake -> advise -> govern -> decide -> commit |
request-approval -> commit | hold`, compiled to a real `langgraph-clj`
`StateGraph` (`langgraph.graph/state-graph` + `compile-graph`, mirroring
`cerealops.operation`, cloud-itonami-isic-0111) with
`interrupt-before #{:request-approval}` and checkpoint-based
human-in-the-loop resume for escalated operations. Every commit/hold/
approval-rejected decision fact is appended to `tobaccoops.store`'s
append-only audit ledger (`ledger`/`append-ledger!`), implemented on
both `MemStore` and a `DatomicStore` (backed by `langchain.db` via
`kotoba-lang/langchain-store`) that pass the same store-contract test
(`test/tobaccoops/store_contract_test.cljk`). 43 tests / 162 assertions
green (`kbb -M:dev:test`); the demo runner (`kbb -M:dev:run`)
drives the compiled graph end-to-end through a commit path, an
escalate→approve→commit path, an escalate→reject→hold path, and a
hard-hold path, printing the resulting audit ledger.

Previously `tobaccoops.operation` was a synchronous stub of this flow
(`build` returned a plain `fn` that never required `langgraph.graph`,
despite `deps.edn` claiming a real `io.github.kotoba-lang/langgraph`
dependency reachable only from the never-invoked `:dev :override-deps`
alias) — the deferred-stub gap is now fixed; `langgraph` and
`langchain-store` live in the real `:deps` map, and `build` returns a
genuinely compiled StateGraph, proven end-to-end by
`test/tobaccoops/operation_test.cljk`.

## What this does NOT do

This actor coordinates **back-office logistics only**. It explicitly does **NOT**:

- **Direct field-equipment operation** — remains the farmer's exclusive authority
- **Curing-barn temperature decisions** — remains the farmer/curing-operator authority
- **Pesticide-application decisions** — remains the agronomist/farmer authority
- **Agronomic decision authority** (what/when/how much to plant, top,
  harvest, or cure) — remains human authority; this actor only coordinates
  the logistics around those decisions
- **Direct execution of any kind** — any proposal for direct field-equipment
  control or finalizing a curing-barn temperature/pesticide-application
  decision is a hard block

## HARD invariants (always hold, never overridable)

1. **field-not-registered** — the request's `field-id` must resolve to a
   registered field in the Store before any proposal can proceed
2. **no-execution** — every proposal's `:effect` must be `:propose` (the governor
   never directly operates field equipment, never finalizes a
   curing-barn temperature or pesticide-application decision)
3. **equipment-or-curing-decision-blocked** — `:operate-field-equipment`,
   `:finalize-curing-barn-temperature-decision`, and
   `:finalize-pesticide-application` proposals are unconditionally,
   permanently blocked
4. **op-not-allowed** — any op outside the closed allowlist below is rejected
5. **cultivation-record-invalid** — `:log-cultivation-record` with a
   non-positive acreage is rejected
6. **leaf-grade-invalid** — `:log-cultivation-record` with a leaf-quality
   grade code outside the actor's recognized closed vocabulary
   (`tobaccoops.facts/leaf-quality-grades`) is rejected

## Always-escalate operations (human sign-off, regardless of confidence)

- `:flag-crop-health-concern` — any pest (e.g. tobacco hornworm)/disease
  (e.g. blue mold)/curing-defect (e.g. barn rot, house burn) concern →
  automatic escalation
- `:order-supplies` over its category cost threshold (default 500 currency
  units; see `tobaccoops.facts/supply-categories`)
- Any proposal with confidence below the Governor's floor (0.7)

## Operational requests (closed allowlist, all `:effect :propose`)

```text
:log-cultivation-record
  — record planting/curing/grading batch and leaf-quality data
  — requires a registered field; non-positive acreage or an unrecognized
    leaf-grade code is rejected

:schedule-field-operation
  — propose a planting/topping/harvesting/curing/grading scheduling operation
  — does NOT make agronomic decisions

:flag-crop-health-concern
  — surface a pest (e.g. tobacco hornworm), disease (e.g. blue mold), or
    curing-defect (e.g. barn rot) concern
  — ALWAYS escalates for human review

:order-supplies
  — procurement for fertilizer, pesticide, curing-fuel
  — escalates if cost exceeds its category threshold
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs the
physical domain work**. Here a field-management robot handles:

- Cultivation record logging and entry
- Field/curing-operation scheduling and reminders
- Supply inventory and ordering
- Audit ledger maintenance

The **TobaccoOperationsGovernor** is the independent safety layer that gates all
proposals before a robot action is executed. The governor never dispatches
hardware directly; `:high`/`:safety-critical` actions (such as escalated
crop-health/curing-defect concerns or high-cost supply orders) require human
sign-off.

## Core Contract

```text
operational request (log, schedule, concern, order)
        |
        v
TobaccoOpsAdvisor -> TobaccoOperationsGovernor -> phase gate -> commit, or escalate for human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated operation can dispatch a robot action the governor refuses, suppress an
operating record, or hide a crop-health/curing-defect concern without governor
approval and audit evidence.

## Module structure

Mirrors `cloud-itonami-isic-0116` (`fibreops.*`) module-for-module:

- `tobaccoops.facts` — reference data: supply-category cost thresholds,
  tobacco types, field/curing-operation vocabulary, leaf-quality grade
  vocabulary
- `tobaccoops.registry` — pure independent verification functions
  (cost/acreage/leaf-grade/confidence)
- `tobaccoops.store` — `Store` protocol: field registration lookup + append-only audit
  ledger, implemented by `MemStore` (in-memory, default) and `DatomicStore`
  (`langchain.db`-backed, via `kotoba-lang/langchain-store`)
- `tobaccoops.advisor` — `Advisor` protocol + `MockAdvisor` (the sealed LLM/decision
  node; a real-LLM `Advisor` implementation is the documented next seam, same as
  every sibling cloud-itonami actor's advisor)
- `tobaccoops.governor` — `TobaccoOperationsGovernor`: hard invariants + escalation gates
- `tobaccoops.phase` — 0→3 rollout phase gate
- `tobaccoops.operation` — compiles the `langgraph-clj` `StateGraph`: advise → govern →
  decide → commit | request-approval → commit | hold, with `interrupt-before` +
  checkpoint-based resume for escalated operations
- `tobaccoops.sim` — demo runner (`kbb -M:dev:run`)

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISIC Rev. 4 `0115`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger
- :telemetry

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Testing

```bash
kbb -M:dev:test   # run the test suite (langgraph/langchain-store resolved via local sibling checkouts)
kbb -M:lint       # clj-kondo, 0 errors / 0 warnings
kbb -M:dev:run    # demo runner -- drives the compiled StateGraph end-to-end
```

`:dev` pins the transitive `langchain` dependency to the in-monorepo local
checkout (`../../kotoba-lang/langchain`) for offline workspace development;
a standalone fork should override `deps.edn`'s `:local/root` coordinates
with git coordinates (see `deps.edn`'s own comment).

## License

AGPL-3.0-or-later.
