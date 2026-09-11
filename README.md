# cloud-itonami-isco-2131

**ISCO-08 Unit Group 2131: Biologists, Botanists, Zoologists and Related Professionals**

A field and laboratory research support actor implementing the itonami actor pattern (independent `BiologyGovernor`, langgraph-clj StateGraph, append-only audit ledger) for biodiversity research, ecological surveys, and species monitoring operations.

## Architecture

The actor is decomposed into:

1. **FieldAdvisor** (`biosciences.advisor`) — proposes field/lab operations:
   - `:analyze-specimen-data` — pipeline analysis proposal over recorded specimens
   - `:draft-report` — prepare an ecological report for review
   - `:flag-species-anomaly` — surface anomalous findings (invasive species, disease outbreak, population change)
   - `:request-field-equipment` — propose field equipment allocation

2. **BiologyGovernor** (`biosciences.governor`) — independent verification layer:
   - HARD invariants (`:hold`, never overridable):
     - Project provenance (registered project)
     - Specimen registration (required for analysis)
     - Site registration (if site context provided)
     - Proposal effect must be `:propose` (no direct actuation)
     - No finalized claims in draft reports
   - ESCALATION (always human sign-off):
     - `:flag-species-anomaly` always escalates
     - `:draft-report` with significant ecological findings
     - Low confidence (< 0.6)

3. **FieldActor** (`biosciences.actor`) — langgraph StateGraph:
   ```text
   :intake → :advise → :govern → :decide ─┬─→ :commit           (ok)
                                             ├─→ :request-approval  (escalate)
                                             └─→ :hold              (hard violation)
   ```

4. **Store** (`biosciences.store`) — single source of truth protocol:
   - `project`, `specimen`, `site`, `equipment`, `record`, `ledger`
   - `MemStore` (in-memory, default); swappable for Datomic/kotoba-server

## Operations

All proposals carry:
- `:op` — operation type
- `:effect :propose` — always (no direct writes)
- `:stake` — `:low`, `:medium`, or `:high`
- `:confidence` — 0.0–1.0 (LLM advisor)
- `:rationale` — operation description

## Testing

```bash
kbb -M:test
```

Deterministic mock advisor (`:mock-advisor`, default) or real LLM-backed (`llm-advisor`).

## Graph State

| Channel | Type | Role |
|---------|------|------|
| `:request` | map | incoming operation request |
| `:context` | map | execution context |
| `:proposal` | map | advisor's recommendation |
| `:verdict` | map | governor's assessment |
| `:disposition` | keyword | `:commit`, `:request-approval`, or `:hold` |
| `:record` | map | committed operation record |
| `:audit` | vector | per-node ledger entries |

## Checkpointing & Human Approval

Escalated proposals interrupt before `:request-approval` node; resume after human sign-off via `approve!`:

```clojure
(let [graph (build-graph {:store st})
      interrupted (run-request! graph request {} "thread-1")]
  ;; ... human review ...
  (approve! graph "thread-1"))  ;; advance to :commit
```

All outcomes (commit, hold, escalation) logged to store's append-only ledger.

## License

AGPL-3.0-or-later. See LICENSE.
