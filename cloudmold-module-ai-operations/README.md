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
