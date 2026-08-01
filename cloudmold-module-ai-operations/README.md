# CloudMold AI Operations

This red-zone module is the canonical authority for privacy-safe AI operational telemetry. It is intentionally separate from the upstream `yudao-module-ai`, which remains an optional provider/UI capability and is not an operational evidence authority.

## First-slice boundary

- Tenant-scoped Application, Workflow Definition and immutable Workflow Version.
- CAS-governed Workflow Run state with append-only status history.
- Append-only model Invocation Attempt with exact token conservation and optional evidence-backed cost.
- Append-only Outcome Feedback.
- Operation idempotency and transactional shared Outbox events.

The public contracts contain only identifiers, bounded tokens, opaque references, structured status/error codes, counts, durations and optional integer minor-unit cost. Prompt text, response text, workflow graphs, API keys, email addresses, IP addresses and error stacks are deliberately absent from API, persistence and event payloads.

## Event contract

All events use `source_system=cloudmold-ai-operations`, schema version 1, and aggregate types:

| Event | Aggregate type |
|---|---|
| `ai.application.status_changed` | `ai_application` |
| `ai.workflow.version.published` | `ai_workflow` |
| `ai.workflow.run.status_changed` | `ai_workflow_run` |
| `ai.model.invocation.recorded` | `ai_model_invocation` |
| `ai.outcome.feedback.recorded` | `ai_outcome_feedback` |

## Explicit gaps

This slice does not enable the upstream AI module, call a model provider, ingest Dify traffic, store raw content, calculate unknown cost, backfill history, or prove business effects for intelligence, customer service, community red-team, or metadata automation. Provider integration must be added through a typed adapter and non-empty reconciliation evidence.

## Agent-maintained workflow governance

The workflow registry is the tenant-scoped source of truth for Agent-generated workflow revisions. DeerFlow can read an attested stable definition, submit an immutable candidate bundle, append redacted evidence, and request validation. It cannot submit evaluator verdicts, approvals, releases, rollbacks, kill-switch changes, or business execution commands.

The governed lifecycle is:

`ACTIVE stable -> SUBMITTED candidate -> VALIDATING -> READY_FOR_REVIEW/CANARY -> ACTIVE | REJECTED | ROLLED_BACK`

- Candidate registration uses stable/candidate pointer CAS, canonical JSON hashes, proposal hashes, a fresh signed base attestation, immutable semantic versions, and tenant-scoped foreign keys.
- Validation start mints a one-time server-side validation request and challenge. Evaluation is accepted only with a fresh HMAC attestation from `cloudmold-independent-workflow-validator`, bound to the tenant, request, candidate, pointer version, dataset hash, complete replay/shadow/canary result, evidence references, and metrics. Configure `CLOUDMOLD_WORKFLOW_VALIDATOR_ATTESTATION_SECRET` independently from DeerFlow credentials.
- E0/E1 may auto-promote only after every attested gate passes. E2/E3 require an approver who is distinct from both proposer and evaluator.
- Release and rollback use pointer CAS. The persistent kill switch is included in the pointer and is enforced by the SkillTask dynamic runtime for both `ACTIVE` and explicit semantic-version submissions.
- The evidence hub stores append-only observations, problems, user feedback, and external-web snapshots. Model-visible text and references reject raw PII, secrets, and prompt instructions. External-web evidence cannot authorize release without a non-web corroborating source.
- SkillTask resolves `ACTIVE` from the tenant registry, re-runs capability/composition/idempotency validation, freezes the semantic version and definition closure, and records registry lineage. File definitions remain a development/disaster-recovery fallback when there is no managed registry version.

Main APIs:

- `POST /cloudmold/ai-operations/workflow-proposals`
- `GET /cloudmold/ai-operations/workflow-proposals/{workflowId}`
- `GET /cloudmold/ai-operations/workflow-registry/governance/workflows`
- `GET /cloudmold/ai-operations/workflow-registry/governance/workflows/{workflowId}`
- `POST /cloudmold/ai-operations/workflow-registry/governance/validation-requests`
- `POST /cloudmold/ai-operations/workflow-registry/governance/evaluations`
- `POST /cloudmold/ai-operations/workflow-registry/governance/approvals`
- `POST /cloudmold/ai-operations/workflow-registry/governance/retirements`
- `POST /cloudmold/ai-operations/workflow-evidence/{ingest|problem|feedback}`
- `GET /cloudmold/ai-operations/workflow-evidence/{query|digests/daily|digests/weekly}`

Migrations `V20260801_123` through `V20260801_126` install the immutable registry, validation/approval/release ledger, evidence hub, persistent kill switch, and SkillTask runtime lineage.
