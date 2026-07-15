-- Every result column must be zero.
SELECT 'listing_unpublish_saga_bad_count' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_listing_unpublish_saga
WHERE unpublished_listing_count + skipped_listing_count > expected_listing_count;

SELECT 'listing_unpublish_saga_completed_incomplete' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_listing_unpublish_saga
WHERE status='COMPLETED'
  AND (unpublished_listing_count + skipped_listing_count <> expected_listing_count
       OR active_step <> 'NONE' OR completed_at IS NULL);

SELECT 'listing_unpublish_saga_source_shape' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_listing_unpublish_saga
WHERE (source_entity_type='MERCHANT' AND shop_id IS NOT NULL)
   OR (source_entity_type='SHOP' AND shop_id IS NULL);

SELECT 'listing_unpublish_saga_item_scope_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_listing_unpublish_saga_item i
JOIN cloudmold_listing_unpublish_saga s
  ON s.tenant_id=i.tenant_id AND s.saga_id=i.saga_id
JOIN cloudmold_listing_header l
  ON l.tenant_id=i.tenant_id AND l.listing_id=i.listing_id
WHERE l.merchant_id COLLATE utf8mb4_unicode_ci<>s.merchant_id
   OR (s.shop_id IS NOT NULL AND l.shop_id COLLATE utf8mb4_unicode_ci<>s.shop_id);

SELECT 'listing_unpublish_saga_item_terminal_shape' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_listing_unpublish_saga_item
WHERE (status IN ('UNPUBLISHED','SKIPPED')
       AND (final_listing_status IS NULL OR final_listing_version IS NULL OR completed_at IS NULL))
   OR (status NOT IN ('UNPUBLISHED','SKIPPED') AND completed_at IS NOT NULL);

SELECT 'listing_unpublish_saga_history_gap' AS check_name, COUNT(*) AS violation_count
FROM (
  SELECT tenant_id,saga_id,COUNT(*) AS history_count,MIN(aggregate_version) AS min_version,
         MAX(aggregate_version) AS max_version
  FROM cloudmold_listing_unpublish_saga_history
  GROUP BY tenant_id,saga_id
) h
JOIN cloudmold_listing_unpublish_saga s
  ON s.tenant_id=h.tenant_id AND s.saga_id=h.saga_id
WHERE h.min_version<>1 OR h.max_version<>s.version OR h.history_count<>s.version;

SELECT 'listing_unpublish_saga_missing_listing_event' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_listing_unpublish_saga_item i
JOIN cloudmold_listing_unpublish_saga s
  ON s.tenant_id=i.tenant_id AND s.saga_id=i.saga_id
LEFT JOIN cloudmold_event_outbox o
  ON o.tenant_id=i.tenant_id AND o.event_type='listing.status.changed'
 AND o.aggregate_id=i.listing_id AND o.causation_id=s.source_event_id
WHERE i.status='UNPUBLISHED' AND o.event_id IS NULL;

SELECT 'listing_unpublish_saga_terminal_listing_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_listing_unpublish_saga_item i
JOIN cloudmold_listing_header l
  ON l.tenant_id=i.tenant_id AND l.listing_id=i.listing_id
WHERE i.status='UNPUBLISHED' AND l.version=i.final_listing_version AND l.status<>'UNPUBLISHED';
