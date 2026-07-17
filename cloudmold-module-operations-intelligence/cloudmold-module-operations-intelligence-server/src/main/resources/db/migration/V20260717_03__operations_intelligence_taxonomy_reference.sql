-- Grandfather existing v1 observations while requiring all new v2 observations to carry
-- an exact, as-of-valid risk taxonomy definition and level snapshot.

ALTER TABLE cloudmold_intelligence_observation
    ADD COLUMN classification_contract_version TINYINT NOT NULL DEFAULT 1 AFTER observation_type,
    ADD COLUMN taxonomy_id CHAR(36) NULL AFTER classification_contract_version,
    ADD COLUMN taxonomy_version_id CHAR(36) NULL AFTER taxonomy_id,
    ADD COLUMN taxonomy_definition_version BIGINT NULL AFTER taxonomy_version_id,
    ADD COLUMN event_code VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER taxonomy_definition_version,
    ADD COLUMN intelligence_level_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER event_code,
    ADD KEY idx_int_observation_taxonomy (
        tenant_id, taxonomy_id, taxonomy_definition_version, intelligence_level_code),
    ADD CONSTRAINT ck_int_observation_class_contract CHECK (
        (classification_contract_version=1
         AND taxonomy_id IS NULL AND taxonomy_version_id IS NULL
         AND taxonomy_definition_version IS NULL AND event_code IS NULL
         AND intelligence_level_code IS NULL)
        OR
        (classification_contract_version=2
         AND taxonomy_id IS NOT NULL AND taxonomy_version_id IS NOT NULL
         AND taxonomy_definition_version > 0 AND event_code IS NOT NULL
         AND intelligence_level_code REGEXP '^[A-Z][A-Z0-9_-]{0,31}$'));

