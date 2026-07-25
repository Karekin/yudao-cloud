-- All statements must return zero rows.

SELECT review_id
FROM cloudmold_app_product_review
WHERE product_score NOT BETWEEN 1 AND 5
   OR service_score NOT BETWEEN 1 AND 5
   OR logistics_score NOT BETWEEN 1 AND 5
   OR overall_score NOT BETWEEN 1 AND 5
   OR content_digest_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR OCTET_LENGTH(content_iv) <> 12
   OR OCTET_LENGTH(content_ciphertext) <= 16
   OR moderation_status NOT IN ('APPROVED','PENDING_MODERATION','HIDDEN','REJECTED')
   OR (moderation_status = 'APPROVED' AND approved_at IS NULL);

SELECT tenant_id, order_item_id, COUNT(*) AS duplicate_count
FROM cloudmold_app_product_review
GROUP BY tenant_id, order_item_id
HAVING COUNT(*) > 1;
