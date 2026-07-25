DROP TABLE IF EXISTS cloudmold_engagement_community_reaction_state;

ALTER TABLE cloudmold_engagement_community_interaction
    DROP CONSTRAINT ck_engagement_comment_encrypted_payload,
    DROP COLUMN payload_digest_sha256,
    DROP COLUMN payload_ciphertext,
    DROP COLUMN payload_iv,
    DROP COLUMN payload_key_id;

ALTER TABLE cloudmold_engagement_community_content
    DROP CONSTRAINT ck_engagement_content_product_link,
    DROP CONSTRAINT ck_engagement_content_encrypted_body,
    DROP INDEX idx_engagement_content_listing_status,
    DROP COLUMN listing_offer_id,
    DROP COLUMN listing_id,
    DROP COLUMN canonical_sku_id,
    DROP COLUMN canonical_spu_id,
    DROP COLUMN body_digest_sha256,
    DROP COLUMN body_ciphertext,
    DROP COLUMN body_iv,
    DROP COLUMN body_key_id;

