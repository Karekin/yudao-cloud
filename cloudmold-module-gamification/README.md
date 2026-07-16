# CloudMold Gamification

This isolated red-zone module owns governed game definitions, player sessions and rounds,
game-only virtual currency, reward grants, draw pools/results, assist quotas, task progress,
fragment balances and gifts.

Season/series and GK/figure/collectible definitions are immutable and versioned. Player ownership
is an append-only signed change ledger plus an optimistic balance projection. A successful mall
redemption debits either the exact collectible version or the game's own virtual currency through
its balanced ledger, while storing only opaque adapter intent/result references and no mall-side
value; reward claims transition exactly once from claimable to claimed or expired.

It deliberately does **not** own or alias money, coupons, loyalty points, Token Platform quota,
Catalog products, or payment settlement. Cross-domain redemption requires a separate versioned
adapter and two-sided evidence.

All writes are tenant scoped, idempotent and transactional. Mutable aggregates use optimistic
versions; economic effects use immutable balanced ledger entries and transactional Outbox events.
Leaderboards are intentionally not a transactional write model here; they are derived in the
lakehouse from governed game events.
