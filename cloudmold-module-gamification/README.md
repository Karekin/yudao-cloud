# CloudMold Gamification

This isolated red-zone module owns governed game definitions, player sessions and rounds,
game-only virtual currency, reward grants, draw pools/results, assist quotas, task progress,
fragment balances and gifts.

It deliberately does **not** own or alias money, coupons, loyalty points, Token Platform quota,
Catalog products, or payment settlement. Cross-domain redemption requires a separate versioned
adapter and two-sided evidence.

All writes are tenant scoped, idempotent and transactional. Mutable aggregates use optimistic
versions; economic effects use immutable balanced ledger entries and transactional Outbox events.
