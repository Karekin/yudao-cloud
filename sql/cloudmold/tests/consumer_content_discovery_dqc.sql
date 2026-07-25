SELECT 'published_content_missing_encrypted_body_or_product_link' AS check_name,
       COUNT(*) AS violations
FROM cloudmold_engagement_community_content
WHERE status = 'PUBLISHED'
  AND (
      content_type <> 'POST'
      OR body_key_id IS NULL
      OR body_iv IS NULL
      OR body_ciphertext IS NULL
      OR body_digest_sha256 IS NULL
      OR canonical_spu_id IS NULL
      OR canonical_sku_id IS NULL
      OR listing_id IS NULL
      OR listing_offer_id IS NULL
  )
UNION ALL
SELECT 'comment_missing_encrypted_payload' AS check_name,
       COUNT(*) AS violations
FROM cloudmold_engagement_community_interaction
WHERE interaction_type = 'COMMENT'
  AND (
      payload_key_id IS NULL
      OR payload_iv IS NULL
      OR payload_ciphertext IS NULL
      OR payload_digest_sha256 IS NULL
  )
UNION ALL
SELECT 'reaction_state_invalid' AS check_name,
       COUNT(*) AS violations
FROM cloudmold_engagement_community_reaction_state
WHERE reaction_type <> 'LIKE'
   OR target_type <> 'CONTENT'
   OR status NOT IN ('ACTIVE', 'REMOVED')
   OR version <= 0;

