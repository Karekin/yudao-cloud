# DreamPlant control plane

DreamPlant is the AI-facing world-map control plane. It owns normalized knowledge assets and relations, published
world-map projections, exploration intents, evidence-backed outcomes, observations, and synchronization state. It
does not duplicate ERP transaction execution: orders, inventory, procurement, and other business facts remain in
their authoritative domain services and lakehouse; DreamPlant stores references, relationships, plans, and evidence.

## Runtime flow

- Browser: public read projection or authenticated intent submission over REST.
- AI/Skill: allowlisted `DreamPlantCommandApi`, `DreamPlantQueryApi`, `DreamPlantKnowledgeCommandApi`, and
  `DreamPlantKnowledgeQueryApi` over Dubbo/Nacos.
- AI workers poll tenant-scoped `QUEUED` explorations, claim them with the versioned `RUNNING` transition, and write
  back a published map version, terminal outcome, evidence hash, and step audit. Failed leases can be recovered and
  transient failures retry with bounded exponential delay; concurrent claims fail through optimistic locking.
- The built-in `deterministic-world-map-composition.v1` planner is the safe baseline and verifies the complete
  orchestration path. `DreamPlantExplorationPlanner` is the replacement boundary for a governed model/tool planner;
  AI Operations remains execution telemetry and is not treated as an LLM provider.
- Both transports call domain services; Mapper methods are never exported as capabilities.
- Writes require a tenant context, idempotency key, run trace, optimistic version, and governed evidence reference.

## Persistence model

The normalized tables are the source of truth for fine-grained AI reads and writes. The immutable snapshot is a
versioned frontend projection, not the primary write model.

- Assets: capability, product, operation agent, solution, agent role, and phase roadmap.
- History and graph: immutable asset revisions plus product-capability, operation-agent-product, and
  solution-operation-agent relations.
- Learning loop: evidence, metric observations, synchronization checkpoints, and drift records.
- Autonomous execution: exploration runs, exploration steps, operation idempotency audit, map registration, and
  immutable published snapshots.

There are 19 DreamPlant tables after migrations `V20260718_58`, `V20260718_65`, and the historical-state backfill
in `V20260718_66`. Snapshot publication imports the
projection into typed tables, while `rebuildProjection` can regenerate the existing frontend JSON shape from the
normalized model without changing page design.

Splitting `payload_json` removes domain coupling and enables independent versioning/querying, but it does not by
itself make hashes stable. Every JSON document is recursively canonicalized before persistence; hashes are computed
over that canonical text and guarded by `canonical_dreamplant_normalized_dqc.sql`.

## Initial data

1. Apply `sql/cloudmold/V20260718_58__cloudmold_dreamplant_control_plane.sql`, followed by
   `sql/cloudmold/V20260718_65__dreamplant_normalized_knowledge_and_worker.sql` and
   `sql/cloudmold/V20260718_66__dreamplant_exploration_history_backfill.sql`.
2. Start the backend once with `--cloudmold.dreamplant.seed.enabled=true` to publish the bundled
   `dreamplant.bootstrap.v1` snapshot for tenant `1` and map key `dreamplant`.
3. Later snapshots should be published by an approved AI Skill through `DreamPlantCommandApi`, not edited in a UI.

The frontend reads `/admin-api/cloudmold/dreamplant/public/world-map/dreamplant`. Set
`VITE_DREAMPLANT_API_BASE_URL` when the backend is hosted on another origin. Intent submission remains authenticated
and requires `cloudmold:dreamplant:explore`.
