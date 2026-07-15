-- Controlled, repeatable warehouse-network fixture for tenant 991501.
-- The ERP/WMS rows are explicit test sources. Canonical IDs are stable UUIDs and never reuse source primary keys.
-- Zone/location are explicit canonical fixture inputs; they are not inferred from a legacy default location.

SET @fixture_tenant := 991501;
SET @fixture_at := TIMESTAMP('2026-07-15 00:00:00.000000');
SET @warehouse_id := 'f1500000-0000-4000-8000-000000000001';
SET @zone_id := 'f1500000-0000-4000-8000-000000000002';
SET @location_id := 'f1500000-0000-4000-8000-000000000003';
SET @erp_source_id := '99150101';
SET @wms_source_id := '99150102';

INSERT IGNORE INTO erp_warehouse
  (id,name,address,sort,remark,principal,warehouse_price,truckage_price,status,default_status,
   creator,create_time,updater,update_time,deleted,tenant_id)
VALUES
  (99150101,'CM-WH-FIXTURE-9915','fixture-only',1,'controlled canonical warehouse source fixture',NULL,NULL,NULL,
   0,b'0','warehouse-fixture',@fixture_at,'warehouse-fixture',@fixture_at,b'0',@fixture_tenant);

INSERT IGNORE INTO wms_warehouse
  (id,code,name,remark,sort,creator,create_time,updater,update_time,deleted,tenant_id)
VALUES
  (99150102,'CM-WH-FIX-9915','CM Warehouse Fixture','controlled canonical warehouse source fixture',1,
   'warehouse-fixture',@fixture_at,'warehouse-fixture',@fixture_at,b'0',@fixture_tenant);

INSERT IGNORE INTO cloudmold_warehouse
  (warehouse_id,tenant_id,warehouse_code,name,warehouse_type,timezone,status,version,created_at,updated_at)
VALUES
  (@warehouse_id,@fixture_tenant,'CM-WH-FIX-9915','CM Warehouse Fixture','FULFILLMENT','Asia/Shanghai',
   'ACTIVE',2,@fixture_at,@fixture_at);

INSERT IGNORE INTO cloudmold_warehouse_zone
  (zone_id,tenant_id,warehouse_id,zone_code,name,zone_type,status,version,created_at,updated_at)
VALUES
  (@zone_id,@fixture_tenant,@warehouse_id,'STORAGE-A','Fixture Storage Zone','STORAGE',
   'ACTIVE',2,@fixture_at,@fixture_at);

INSERT IGNORE INTO cloudmold_warehouse_location
  (location_id,tenant_id,warehouse_id,zone_id,location_code,name,location_type,
   aisle_code,rack_code,bay_code,level_code,allow_item_mixing,allow_lot_mixing,
   capacity_quantity,capacity_uom_code,status,version,created_at,updated_at)
VALUES
  (@location_id,@fixture_tenant,@warehouse_id,@zone_id,'A-01-01-01','Fixture Addressable Location','STORAGE',
   'A','01','01','01',0,0,1000.000000,'PCS','ACTIVE',2,@fixture_at,@fixture_at);

INSERT IGNORE INTO cloudmold_warehouse_source_mapping
  (mapping_id,tenant_id,source_system,source_type,source_id,canonical_type,canonical_id,
   warehouse_id,zone_id,location_id,valid_from,valid_to,verification_ref,status,version,created_at,updated_at)
VALUES
  ('f1500000-0000-4000-8000-000000000011',@fixture_tenant,'ERP','WAREHOUSE',@erp_source_id,
   'WAREHOUSE',@warehouse_id,@warehouse_id,NULL,NULL,@fixture_at,NULL,
   'controlled-fixture:erp_warehouse:99150101','ACTIVE',1,@fixture_at,@fixture_at),
  ('f1500000-0000-4000-8000-000000000012',@fixture_tenant,'WMS','WAREHOUSE',@wms_source_id,
   'WAREHOUSE',@warehouse_id,@warehouse_id,NULL,NULL,@fixture_at,NULL,
   'controlled-fixture:wms_warehouse:99150102','ACTIVE',1,@fixture_at,@fixture_at);

SET @warehouse_draft_payload := JSON_OBJECT(
  'entity_type','WAREHOUSE','entity_id',@warehouse_id,'warehouse_id',@warehouse_id,
  'warehouse_code','CM-WH-FIX-9915','name','CM Warehouse Fixture','warehouse_type','FULFILLMENT',
  'previous_status',NULL,'current_status','DRAFT');
SET @warehouse_active_payload := JSON_OBJECT(
  'entity_type','WAREHOUSE','entity_id',@warehouse_id,'warehouse_id',@warehouse_id,
  'previous_status','DRAFT','current_status','ACTIVE');
SET @zone_draft_payload := JSON_OBJECT(
  'entity_type','ZONE','entity_id',@zone_id,'warehouse_id',@warehouse_id,'zone_id',@zone_id,
  'zone_code','STORAGE-A','zone_type','STORAGE','previous_status',NULL,'current_status','DRAFT');
SET @zone_active_payload := JSON_OBJECT(
  'entity_type','ZONE','entity_id',@zone_id,'warehouse_id',@warehouse_id,'zone_id',@zone_id,
  'previous_status','DRAFT','current_status','ACTIVE');
SET @location_draft_payload := JSON_OBJECT(
  'entity_type','LOCATION','entity_id',@location_id,'warehouse_id',@warehouse_id,'zone_id',@zone_id,
  'location_id',@location_id,'location_code','A-01-01-01','location_type','STORAGE',
  'previous_status',NULL,'current_status','DRAFT');
SET @location_active_payload := JSON_OBJECT(
  'entity_type','LOCATION','entity_id',@location_id,'warehouse_id',@warehouse_id,'zone_id',@zone_id,
  'location_id',@location_id,'location_type','STORAGE','previous_status','DRAFT','current_status','ACTIVE');
SET @erp_mapping_payload := JSON_OBJECT(
  'mapping_id','f1500000-0000-4000-8000-000000000011','source_system','ERP','source_type','WAREHOUSE',
  'source_id',@erp_source_id,'canonical_type','WAREHOUSE','canonical_id',@warehouse_id,
  'warehouse_id',@warehouse_id,'current_status','ACTIVE');
SET @wms_mapping_payload := JSON_OBJECT(
  'mapping_id','f1500000-0000-4000-8000-000000000012','source_system','WMS','source_type','WAREHOUSE',
  'source_id',@wms_source_id,'canonical_type','WAREHOUSE','canonical_id',@warehouse_id,
  'warehouse_id',@warehouse_id,'current_status','ACTIVE');

INSERT IGNORE INTO cloudmold_event_outbox
  (event_id,event_type,schema_version,source_system,tenant_id,aggregate_type,aggregate_id,
   aggregate_version,event_sequence,occurred_at,recorded_at,trace_id,correlation_id,causation_id,
   idempotency_key,payload,headers,payload_hash,destination,status,available_at,attempt_count,max_attempts,published_at)
VALUES
  ('f1500000-0000-4000-8000-000000000021','warehouse.entity.status_changed',1,'cloudmold-warehouse',
   @fixture_tenant,'warehouse',@warehouse_id,1,1,@fixture_at,@fixture_at,NULL,
   'f1500000-0000-4000-8000-000000000099',NULL,'warehouse-fixture:warehouse:draft',
   @warehouse_draft_payload,JSON_OBJECT('fixture',TRUE),SHA2(CAST(@warehouse_draft_payload AS CHAR),256),
   'lakehouse',20,@fixture_at,0,20,@fixture_at),
  ('f1500000-0000-4000-8000-000000000022','warehouse.entity.status_changed',1,'cloudmold-warehouse',
   @fixture_tenant,'warehouse',@warehouse_id,2,1,@fixture_at,@fixture_at,NULL,
   'f1500000-0000-4000-8000-000000000099','f1500000-0000-4000-8000-000000000021',
   'warehouse-fixture:warehouse:active',@warehouse_active_payload,JSON_OBJECT('fixture',TRUE),
   SHA2(CAST(@warehouse_active_payload AS CHAR),256),'lakehouse',20,@fixture_at,0,20,@fixture_at),
  ('f1500000-0000-4000-8000-000000000023','warehouse.entity.status_changed',1,'cloudmold-warehouse',
   @fixture_tenant,'warehouse_zone',@zone_id,1,1,@fixture_at,@fixture_at,NULL,
   'f1500000-0000-4000-8000-000000000099',NULL,'warehouse-fixture:zone:draft',
   @zone_draft_payload,JSON_OBJECT('fixture',TRUE),SHA2(CAST(@zone_draft_payload AS CHAR),256),
   'lakehouse',20,@fixture_at,0,20,@fixture_at),
  ('f1500000-0000-4000-8000-000000000024','warehouse.entity.status_changed',1,'cloudmold-warehouse',
   @fixture_tenant,'warehouse_zone',@zone_id,2,1,@fixture_at,@fixture_at,NULL,
   'f1500000-0000-4000-8000-000000000099','f1500000-0000-4000-8000-000000000023',
   'warehouse-fixture:zone:active',@zone_active_payload,JSON_OBJECT('fixture',TRUE),
   SHA2(CAST(@zone_active_payload AS CHAR),256),'lakehouse',20,@fixture_at,0,20,@fixture_at),
  ('f1500000-0000-4000-8000-000000000025','warehouse.entity.status_changed',1,'cloudmold-warehouse',
   @fixture_tenant,'warehouse_location',@location_id,1,1,@fixture_at,@fixture_at,NULL,
   'f1500000-0000-4000-8000-000000000099',NULL,'warehouse-fixture:location:draft',
   @location_draft_payload,JSON_OBJECT('fixture',TRUE),SHA2(CAST(@location_draft_payload AS CHAR),256),
   'lakehouse',20,@fixture_at,0,20,@fixture_at),
  ('f1500000-0000-4000-8000-000000000026','warehouse.entity.status_changed',1,'cloudmold-warehouse',
   @fixture_tenant,'warehouse_location',@location_id,2,1,@fixture_at,@fixture_at,NULL,
   'f1500000-0000-4000-8000-000000000099','f1500000-0000-4000-8000-000000000025',
   'warehouse-fixture:location:active',@location_active_payload,JSON_OBJECT('fixture',TRUE),
   SHA2(CAST(@location_active_payload AS CHAR),256),'lakehouse',20,@fixture_at,0,20,@fixture_at),
  ('f1500000-0000-4000-8000-000000000031','warehouse.source_mapping.changed',1,'cloudmold-warehouse',
   @fixture_tenant,'warehouse_source_mapping','f1500000-0000-4000-8000-000000000011',1,1,
   @fixture_at,@fixture_at,NULL,'f1500000-0000-4000-8000-000000000099',NULL,
   'warehouse-fixture:mapping:erp',@erp_mapping_payload,JSON_OBJECT('fixture',TRUE),
   SHA2(CAST(@erp_mapping_payload AS CHAR),256),'lakehouse',20,@fixture_at,0,20,@fixture_at),
  ('f1500000-0000-4000-8000-000000000032','warehouse.source_mapping.changed',1,'cloudmold-warehouse',
   @fixture_tenant,'warehouse_source_mapping','f1500000-0000-4000-8000-000000000012',1,1,
   @fixture_at,@fixture_at,NULL,'f1500000-0000-4000-8000-000000000099',NULL,
   'warehouse-fixture:mapping:wms',@wms_mapping_payload,JSON_OBJECT('fixture',TRUE),
   SHA2(CAST(@wms_mapping_payload AS CHAR),256),'lakehouse',20,@fixture_at,0,20,@fixture_at);

SELECT
  (SELECT COUNT(*) FROM cloudmold_warehouse WHERE tenant_id=@fixture_tenant) AS warehouse_count,
  (SELECT COUNT(*) FROM cloudmold_warehouse_zone WHERE tenant_id=@fixture_tenant) AS zone_count,
  (SELECT COUNT(*) FROM cloudmold_warehouse_location WHERE tenant_id=@fixture_tenant) AS location_count,
  (SELECT COUNT(*) FROM cloudmold_warehouse_source_mapping WHERE tenant_id=@fixture_tenant) AS mapping_count,
  (SELECT COUNT(*) FROM cloudmold_event_outbox
    WHERE tenant_id=@fixture_tenant AND source_system='cloudmold-warehouse') AS event_count;
