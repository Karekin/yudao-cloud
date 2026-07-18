# CloudMold canonical customer service — first slice

This red-zone module is the canonical authority for tenant-scoped customer-service tickets, qualified Order/AfterSale links, message and attachment metadata, buyer-authored satisfaction feedback, internal quality reviews, claims, and an append-only service-compensation ledger. It does not import another module's DO, Mapper, or internal service.

## Persistent model and business keys

- `cloudmold_customer_service_ticket`: ticket aggregate; unique `(tenant_id, ticket_no)`, optimistic `version`.
- `cloudmold_customer_service_ticket_order_link`: immutable qualified link; unique `(tenant_id, ticket_id, reference_type, reference_id)`. `ORDER` is valid only with `cloudmold-order`; `AFTER_SALE` only with `cloudmold-aftersales`.
- `cloudmold_customer_service_message`: immutable message metadata; `content_token` accepts only a SHA-256 digest or opaque restricted-store token, never raw conversation text.
- `cloudmold_customer_service_attachment`: immutable file metadata; no file name, bucket, URL, customer address, or raw content is stored.
- `cloudmold_customer_service_buyer_feedback`: immutable buyer-authored post-resolution feedback fact; exactly one buyer/ticket/touchpoint response per first-slice path.
- `cloudmold_customer_service_quality_review`: immutable review fact with a 0–10000 basis-point score.
- `cloudmold_customer_service_claim`: claim aggregate; unique `(tenant_id, claim_code)`, optimistic `version`.
- `cloudmold_customer_service_compensation_entry`: append-only money entry; unique `(tenant_id, claim_id)` and `(tenant_id, operation_idempotency_key)` for the first-slice single compensation.
- `cloudmold_customer_service_operation`: tenant-scoped immutable idempotency result ledger; unique `(tenant_id, idempotency_key)`.
- `cloudmold_customer_service_status_history`: append-only Ticket/Claim transition audit.

Money is signed 64-bit minor units plus ISO-4217 alpha-3 currency. Claim payment appends the compensation entry and moves the claim from `APPROVED` to `PAID` in one transaction. It is a customer-service compensation fact, not proof that Pay/Trade executed a provider refund.

## Versioned events

All six events use schema v1, source `cloudmold-customer-service`, the shared transactional Outbox, and PII-safe payloads:

- `customer_service.ticket.status_changed`
- `customer_service.message.recorded`
- `customer_service.attachment.recorded`
- `customer_service.buyer_feedback.recorded`
- `customer_service.quality_review.recorded`
- `customer_service.claim.status_changed`

Buyer feedback and quality review are intentionally separate:

- buyer feedback is customer-authored experience evidence and may feed CSAT metrics;
- quality review is an internal sampled audit and must never be used as a CSAT proxy.

## Explicitly unsupported prototype gaps

This is an honest first slice. It does not yet implement live-chat session routing, agent queues and SLA escalation, telephony/provider adapters, customer-visible notification delivery, search over restricted message content, attachment blob retention or malware-scanner execution, automatic Pay/Trade refund or payout, risk/model-result automation, appeal/arbitration, multi-stage or partial compensation, buyer survey invitation/delivery orchestration, legacy customer-service history import, shadow reads, tenant cutover, or production backfill. Those remain separate migration and integration gates.
