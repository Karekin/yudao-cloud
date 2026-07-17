# CloudMold Risk

CloudMold-owned red-zone module for explainable risk evidence and human review. It is intentionally separate from
upstream Member, System, Trade, Pay, CRM, and AI internals.

## Authority

- Owns versioned `RiskPolicy` and immutable policy version/rule snapshots.
- Owns versioned `IntelligenceEventTaxonomy` definitions, exact source lineage
  and retirement. Corrections publish a new effective version; they never
  rewrite a definition already referenced by an observation.
- Records explainable `RiskSignal` evidence against a published policy version.
- Records tenant-scoped relationship observations using only keyed-HMAC `medium_token`, `medium_type`, and
  `key_version`. Raw phone numbers, IP addresses, postal addresses, device identifiers, and payment identifiers are
  forbidden from this module and its events.
- Owns `RiskCluster` membership, `RiskReviewCase`, immutable human `Decision`, and immutable `Feedback`.
- Emits nine versioned event types through the shared transactional Outbox with `source_system=cloudmold-risk`.

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
- A classification observation must reference the exact effective taxonomy
  version and a member of its ordered level set. Retirement blocks new use but
  preserves pre-retirement history. Categorical source levels are never
  inferred as severity or automatic enforcement.

## Events (schema v1)

- `risk.policy.version_published`
- `risk.signal.detected`
- `risk.relationship.observed`
- `risk.cluster.status_changed`
- `risk.review.status_changed`
- `risk.decision.recorded`
- `risk.feedback.recorded`
- `risk.intelligence_event_taxonomy.version_published`
- `risk.intelligence_event_taxonomy.retired`

Module migration: `cloudmold-module-risk-server/src/main/resources/db/migration/`.
The module is registered in the root reactor and the monolith. Operations
Intelligence consumes its public query contract for exact taxonomy validation.
