# CloudMold Risk

CloudMold-owned red-zone module for explainable risk evidence and human review. It is intentionally separate from
upstream Member, System, Trade, Pay, CRM, and AI internals.

## Authority

- Owns versioned `RiskPolicy` and immutable policy version/rule snapshots.
- Records explainable `RiskSignal` evidence against a published policy version.
- Records tenant-scoped relationship observations using only keyed-HMAC `medium_token`, `medium_type`, and
  `key_version`. Raw phone numbers, IP addresses, postal addresses, device identifiers, and payment identifiers are
  forbidden from this module and its events.
- Owns `RiskCluster` membership, `RiskReviewCase`, immutable human `Decision`, and immutable `Feedback`.
- Emits seven versioned event types through the shared transactional Outbox with `source_system=cloudmold-risk`.

This first slice does **not** own authentication, identity profiles, payments, account status, penalties, refunds,
or merchant/member enforcement. Its decision vocabulary is evidence-only (`DISMISS`, `MONITOR`, `ESCALATE`,
`CONFIRM_RISK`). It has no API, column, event field, or worker for automatic blocking, charging, withholding,
suspension, or account closure.

## Invariants

- Every business key and lookup is tenant-scoped.
- Commands are immutable-replay idempotent; reusing a key with a different payload fails closed.
- Policy, cluster, and review mutation uses optimistic versions and appends immutable status history.
- Policy versions and rules are append-only snapshots with a canonical SHA-256 rule-set digest.
- Relationship endpoints are canonicalized, self-loops and reverse duplicates are rejected, and media are lowercase
  64-character keyed-HMAC tokens with a positive key version.
- Decisions require the assigned human reviewer. Feedback must reference a same-tenant decision and its exact case.

## Events (schema v1)

- `risk.policy.version_published`
- `risk.signal.detected`
- `risk.relationship.observed`
- `risk.cluster.status_changed`
- `risk.review.status_changed`
- `risk.decision.recorded`
- `risk.feedback.recorded`

Module migration: `cloudmold-module-risk-server/src/main/resources/db/migration/`.
The module is not registered in the root reactor or monolith in this isolated change; callers must wire it separately.
