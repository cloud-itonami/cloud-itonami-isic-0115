# Operator Guide: Tobacco-Growing Operations Coordinator

## Overview

The Tobacco-Growing Operations Coordinator is a field-management robot that:

1. **Logs operational data** — planting acreage, curing-batch, leaf-quality notes
2. **Schedules coordination** — planting/topping/harvesting/curing/grading windows, supply orders
3. **Escalates concerns** — any crop pest (e.g. tobacco hornworm)/disease (e.g. blue mold)/curing-defect (e.g. barn rot) issue
4. **Maintains transparency** — audit ledger traces all decisions

The robot is **not** the decision-maker. The farmer/agronomist make all
decisions about agronomic practice, curing-barn temperature, pesticide
application, and economic choices. The robot **proposes** actions and
escalates when human input is needed.

## Operating the Actor

### Prerequisites

1. **Field Registration** — your field must be registered in the system
   before any operation can proceed
2. **Authorized User** — operator must be authenticated and authorized
3. **Clear Request Type** — specify what you're doing:
   - `:log-cultivation-record` — record planting/curing/grading batch and leaf-quality data
   - `:schedule-field-operation` — arrange a planting/topping/harvesting/curing/grading window
   - `:flag-crop-health-concern` — report a concern
   - `:order-supplies` — procurement request

### Workflow

1. **Submit Request**
   ```clojure
   {:field-id "field-001"
    :op :log-cultivation-record
    :acreage 40
    :tobacco-type "burley"
    :leaf-grade "grade-a"
    :record-type "curing"}
   ```

2. **Actor Processes** (`operation/run-operation store request context`)
   - `:advise` — `TobaccoOpsAdvisor` proposes an action (`tobaccoops.advisor`)
   - `:govern` — `TobaccoOperationsGovernor` checks hard invariants and escalation gates (`tobaccoops.governor`)
   - phase gate — rollout-phase constraints applied on top of the Governor's verdict (`tobaccoops.phase`)

3. **Outcomes** (`:disposition` on the return value)
   - **`:commit`** — operation logged, robot proceeds (`:record` is present)
   - **`:escalate`** — operation held pending human decision (audit fact `:t :approval-requested`)
   - **`:hold`** — operation blocked, hard violation (audit fact `:t :governor-hold`, cites `:violations`)

### Escalation Scenarios

**Automatic escalation (always human sign-off):**
- `:flag-crop-health-concern` — any pest (e.g. tobacco hornworm)/disease (e.g. blue mold)/curing-defect (e.g. barn rot) issue
- Supply orders over cost threshold (default 500 currency units)
- Low confidence operations (< 0.7)

**Hard blocks (no override):**
- `:operate-field-equipment` — direct machinery operation is the farmer's authority
- `:finalize-curing-barn-temperature-decision` — curing-barn temperature decisions are the farmer/curing-operator's authority
- `:finalize-pesticide-application` — pesticide-application decisions are agronomist/farmer authority
- Missing/unregistered field — must register first
- Non-positive acreage, or a leaf-grade code outside the recognized closed
  vocabulary (`tobaccoops.facts/leaf-quality-grades`)

### Resuming Escalated Operations

`tobaccoops.operation` is currently a synchronous stub (see its docstring):
one call to `(operation/run-operation store request context)` runs the full
`advise -> govern -> phase-gate` flow and returns immediately with a
`:disposition` of `:commit`, `:escalate`, or `:hold`. There is **no
persisted pause/resume yet** — that requires the deferred `langgraph-clj`
StateGraph integration (`interrupt-before` + checkpoint-based resume,
mirroring `cloud-itonami-isic-0116`). Until then, an `:escalate`
disposition means: **do not commit** — the caller (production
integration layer) is responsible for holding the proposal for human
review and re-submitting a follow-up operation once approved.

## Audit & Transparency

Every operation run returns an `:audit` vector containing an
advisor-proposal trace and a disposition fact (`:committed`,
`:governor-hold`, or `:approval-requested`). Production integration is
responsible for appending these facts to an append-only ledger (the
reference implementation does not include a ledger-writer — that's a
backend-integration concern, same seam point as the `Store`).

- Every proposal produces a trace, regardless of outcome
- Every hold cites the specific Governor rule(s) violated (`:violations`)
- Every escalation cites its `:reason` (always-escalate op / high cost / low confidence)

## Integration

The actor provides a standard protocol (`tobaccoops.store/Store`) for backend
integration:

- **Field lookup** — `(store/registered-field store field-id)`

Implementations include in-memory `MemStore` (testing, `tobaccoops.store`),
and future Datomic/kotoba-server backends (the same seam point all
cloud-itonami actors use). Record-commit and ledger-append are integration
responsibilities on top of `operation/run-operation`'s return value, not
part of the `Store` protocol itself.

## Safety Guarantees

- **No unsupervised decisions** — no agronomic, curing-barn temperature, or
  pesticide-application decision is made by the robot
- **No suppressed concerns** — crop-health/curing-defect concerns cannot be
  hidden or delayed
- **No unlogged operations** — every action is recorded in the audit ledger
- **No direct execution** — the governor gates every robot action

The robot is safe because:
1. It never decides — it proposes
2. It always escalates when needed
3. It never hides information
4. Every action is auditable
