-- Persist content verification metadata for historical product evidence.
-- New qualifications must bind a content-addressed URI to the server snapshot hash.

ALTER TABLE `cloudmold_order_product_identity_qualification_request`
  ADD COLUMN `evidence_verification_status` varchar(32) DEFAULT NULL AFTER `source_evidence_uri`,
  ADD COLUMN `evidence_verifier_version` varchar(64) DEFAULT NULL AFTER `evidence_verification_status`,
  ADD COLUMN `evidence_content_length` bigint DEFAULT NULL AFTER `evidence_verifier_version`,
  ADD COLUMN `evidence_verified_at` datetime(6) DEFAULT NULL AFTER `evidence_content_length`;

UPDATE `cloudmold_order_product_identity_qualification_request`
SET `evidence_verification_status`='LEGACY_UNVERIFIED',
    `evidence_verifier_version`='legacy-v0',
    `evidence_content_length`=0,
    `evidence_verified_at`=`requested_at`
WHERE `evidence_verification_status` IS NULL;

ALTER TABLE `cloudmold_order_product_identity_qualification_request`
  MODIFY COLUMN `evidence_verification_status` varchar(32) NOT NULL,
  MODIFY COLUMN `evidence_verifier_version` varchar(64) NOT NULL,
  MODIFY COLUMN `evidence_content_length` bigint NOT NULL,
  MODIFY COLUMN `evidence_verified_at` datetime(6) NOT NULL,
  ADD CONSTRAINT `ck_cm_order_product_identity_request_verified_evidence` CHECK (
    (`evidence_verification_status`='VERIFIED'
      AND `evidence_verifier_version`='filesystem-content-addressed-sha256-v1'
      AND `evidence_content_length` BETWEEN 1 AND 65536
      AND `source_evidence_uri`=CONCAT('evidence://sha256/',`historical_product_snapshot_hash`))
    OR (`evidence_verification_status`='LEGACY_UNVERIFIED'
      AND `evidence_verifier_version`='legacy-v0' AND `evidence_content_length`=0));

ALTER TABLE `cloudmold_order_product_identity_qualification`
  ADD COLUMN `evidence_verification_status` varchar(32) DEFAULT NULL AFTER `source_evidence_uri`,
  ADD COLUMN `evidence_verifier_version` varchar(64) DEFAULT NULL AFTER `evidence_verification_status`,
  ADD COLUMN `evidence_content_length` bigint DEFAULT NULL AFTER `evidence_verifier_version`,
  ADD COLUMN `evidence_verified_at` datetime(6) DEFAULT NULL AFTER `evidence_content_length`;

UPDATE `cloudmold_order_product_identity_qualification`
SET `evidence_verification_status`='LEGACY_UNVERIFIED',
    `evidence_verifier_version`='legacy-v0',
    `evidence_content_length`=0,
    `evidence_verified_at`=`qualified_at`
WHERE `evidence_verification_status` IS NULL;

ALTER TABLE `cloudmold_order_product_identity_qualification`
  MODIFY COLUMN `evidence_verification_status` varchar(32) NOT NULL,
  MODIFY COLUMN `evidence_verifier_version` varchar(64) NOT NULL,
  MODIFY COLUMN `evidence_content_length` bigint NOT NULL,
  MODIFY COLUMN `evidence_verified_at` datetime(6) NOT NULL,
  ADD CONSTRAINT `ck_cm_order_product_identity_qualification_verified_evidence` CHECK (
    (`evidence_verification_status`='VERIFIED'
      AND `evidence_verifier_version`='filesystem-content-addressed-sha256-v1'
      AND `evidence_content_length` BETWEEN 1 AND 65536
      AND `source_evidence_uri`=CONCAT('evidence://sha256/',`historical_product_snapshot_hash`))
    OR (`evidence_verification_status`='LEGACY_UNVERIFIED'
      AND `evidence_verifier_version`='legacy-v0' AND `evidence_content_length`=0));
