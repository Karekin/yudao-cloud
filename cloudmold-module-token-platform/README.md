# CloudMold Token Platform

This red-zone module is the canonical first slice for internal AI usage and quota accounting.

## Authority boundary

- Identity owns the canonical `principal_id` and System/Member retain authentication and profile authority.
- Pay retains money collection and refund facts. This slice does not implement recharge or payment.
- Token Platform owns model offerings and immutable pricing versions, credential metadata, quota accounts and
  append-only quota ledger entries, and immutable invocation usage.
- A secret manager owns credential values. The database stores only a SHA-256 fingerprint, a secret locator,
  `last4`, and a key version. Commands, results, query views, events, and logs never contain the secret locator
  or credential value.

Quota is an integer `int64` microunit. Invocation cost uses the selected immutable pricing version and computes:

```text
ceil(((input - cached_input) * input_rate
    + cached_input * cached_input_rate
    + output * output_rate) / 1_000_000)
```

`cached_input_tokens` must be a subset of `input_tokens`, and `total_tokens = input_tokens + output_tokens`.
Recording one invocation atomically appends its negative quota ledger entry, updates the account with CAS, stores
the immutable usage row, and emits both quota and usage Outbox events.

## Event contracts

- `token_platform.model_offering.status_changed` v1
- `token_platform.access_credential.status_changed` v1
- `token_platform.quota.ledger_posted` v1
- `token_platform.invocation.usage_recorded` v1

The first slice intentionally excludes payment/recharge, AI canvas, and Skill marketplace capabilities.
