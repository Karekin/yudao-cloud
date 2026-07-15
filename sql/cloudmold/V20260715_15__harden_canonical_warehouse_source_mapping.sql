-- Harden the canonical warehouse network hierarchy and qualified source mapping identity.
-- Additive only: source ERP/WMS/MES tables remain untouched and no default location is synthesized.

ALTER TABLE `cloudmold_warehouse_zone`
  ADD UNIQUE KEY `uk_cm_wh_zone_hierarchy` (`tenant_id`,`warehouse_id`,`zone_id`);

ALTER TABLE `cloudmold_warehouse_location`
  ADD UNIQUE KEY `uk_cm_wh_location_hierarchy` (`tenant_id`,`warehouse_id`,`zone_id`,`location_id`),
  ADD CONSTRAINT `fk_cm_wh_location_zone_hierarchy`
    FOREIGN KEY (`tenant_id`,`warehouse_id`,`zone_id`)
    REFERENCES `cloudmold_warehouse_zone` (`tenant_id`,`warehouse_id`,`zone_id`);

ALTER TABLE `cloudmold_warehouse_source_mapping`
  ADD CONSTRAINT `fk_cm_wh_mapping_zone_hierarchy`
    FOREIGN KEY (`tenant_id`,`warehouse_id`,`zone_id`)
    REFERENCES `cloudmold_warehouse_zone` (`tenant_id`,`warehouse_id`,`zone_id`),
  ADD CONSTRAINT `fk_cm_wh_mapping_location_hierarchy`
    FOREIGN KEY (`tenant_id`,`warehouse_id`,`zone_id`,`location_id`)
    REFERENCES `cloudmold_warehouse_location` (`tenant_id`,`warehouse_id`,`zone_id`,`location_id`),
  ADD CONSTRAINT `ck_cm_wh_mapping_source_identity` CHECK (
    CHAR_LENGTH(TRIM(`source_system`)) > 0
    AND CHAR_LENGTH(TRIM(`source_type`)) > 0
    AND CHAR_LENGTH(TRIM(`source_id`)) > 0
    AND CHAR_LENGTH(TRIM(`verification_ref`)) > 0
    AND BINARY `source_system` = BINARY UPPER(TRIM(`source_system`))
    AND BINARY `source_type` = BINARY UPPER(TRIM(`source_type`))
    AND BINARY `source_id` = BINARY TRIM(`source_id`)
  ),
  ADD CONSTRAINT `ck_cm_wh_mapping_distinct_id` CHECK (`canonical_id` <> `source_id`);

ALTER TABLE `cloudmold_warehouse_operator_assignment`
  ADD CONSTRAINT `fk_cm_wh_assignment_zone_hierarchy`
    FOREIGN KEY (`tenant_id`,`warehouse_id`,`zone_id`)
    REFERENCES `cloudmold_warehouse_zone` (`tenant_id`,`warehouse_id`,`zone_id`),
  ADD CONSTRAINT `fk_cm_wh_assignment_location_hierarchy`
    FOREIGN KEY (`tenant_id`,`warehouse_id`,`zone_id`,`location_id`)
    REFERENCES `cloudmold_warehouse_location` (`tenant_id`,`warehouse_id`,`zone_id`,`location_id`);
