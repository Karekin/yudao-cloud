package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryMigrationPilotMapper {

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_pilot_batch
              (batch_id,tenant_id,migration_run_id,environment,source_system,source_type,source_classification,
               policy_version,policy_hash,manifest_hash,expected_item_count,expected_on_hand_quantity,
               warehouse_id,base_uom_code,source_watermark_kind,source_watermark_value,source_watermark_captured_at,
               target_watermark_kind,target_watermark_value,target_watermark_applied_at,max_lag_seconds,
               execution_window_start,execution_window_end,change_ticket,purpose,requester_id,approval_count,
               status,version,frozen_at,created_at,updated_at)
            VALUES
              (#{batchId},#{tenantId},#{migrationRunId},#{environment},#{sourceSystem},#{sourceType},#{sourceClassification},
               #{policyVersion},#{policyHash},#{manifestHash},#{expectedItemCount},#{expectedOnHandQuantity},
               #{warehouseId},#{baseUomCode},#{sourceWatermarkKind},#{sourceWatermarkValue},#{sourceWatermarkCapturedAt},
               #{targetWatermarkKind},#{targetWatermarkValue},#{targetWatermarkAppliedAt},#{maxLagSeconds},
               #{executionWindowStart},#{executionWindowEnd},#{changeTicket},#{purpose},#{requesterId},0,
               'FROZEN',1,#{frozenAt},#{createdAt},#{updatedAt})
            """)
    int insertBatch(InventoryMigrationPilotBatchDO value);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_pilot_item
              (item_id,tenant_id,batch_id,ordinal,candidate_id,legacy_balance_id,source_version,source_updated_at,
               source_snapshot_hash,source_on_hand_quantity,source_reserved_quantity,source_in_transit_quantity,
               active_reservation_count,active_reservation_quantity,owner_type,owner_id,owner_source_system,
               owner_source_type,owner_source_id,owner_mapping_id,owner_mapping_version,owner_mapping_evidence_ref,
               canonical_sku_id,sku_mapping_id,sku_mapping_version,sku_mapping_evidence_ref,
               source_uom_code,base_uom_code,uom_conversion_ratio,uom_evidence_ref,
               warehouse_source_system,warehouse_source_type,warehouse_source_id,warehouse_source_mapping_id,
               warehouse_mapping_version,warehouse_mapping_evidence_ref,warehouse_id,
               location_source_system,location_source_type,location_source_id,location_source_mapping_id,
               location_mapping_version,location_mapping_evidence_ref,location_id,zone_id,
               lot_tracking_policy,lot_id,lot_mapping_id,lot_mapping_version,lot_evidence_ref,
               stock_status,quality_status,authoritative_record_ref,quantity_evidence_ref,source_cdc_position,
               source_extracted_at,target_balance_absent,bridge_absent,item_scope_hash,status,version,created_at,updated_at)
            VALUES
              (#{itemId},#{tenantId},#{batchId},#{ordinal},#{candidateId},#{legacyBalanceId},#{sourceVersion},#{sourceUpdatedAt},
               #{sourceSnapshotHash},#{sourceOnHandQuantity},#{sourceReservedQuantity},#{sourceInTransitQuantity},
               #{activeReservationCount},#{activeReservationQuantity},#{ownerType},#{ownerId},#{ownerSourceSystem},
               #{ownerSourceType},#{ownerSourceId},#{ownerMappingId},#{ownerMappingVersion},#{ownerMappingEvidenceRef},
               #{canonicalSkuId},#{skuMappingId},#{skuMappingVersion},#{skuMappingEvidenceRef},
               #{sourceUomCode},#{baseUomCode},#{uomConversionRatio},#{uomEvidenceRef},
               #{warehouseSourceSystem},#{warehouseSourceType},#{warehouseSourceId},#{warehouseSourceMappingId},
               #{warehouseMappingVersion},#{warehouseMappingEvidenceRef},#{warehouseId},
               #{locationSourceSystem},#{locationSourceType},#{locationSourceId},#{locationSourceMappingId},
               #{locationMappingVersion},#{locationMappingEvidenceRef},#{locationId},#{zoneId},
               #{lotTrackingPolicy},#{lotId},#{lotMappingId},#{lotMappingVersion},#{lotEvidenceRef},
               #{stockStatus},#{qualityStatus},#{authoritativeRecordRef},#{quantityEvidenceRef},#{sourceCdcPosition},
               #{sourceExtractedAt},#{targetBalanceAbsent},#{bridgeAbsent},#{itemScopeHash},'FROZEN',1,#{createdAt},#{updatedAt})
            """)
    int insertItem(InventoryMigrationPilotItemDO value);

    @Select("SELECT * FROM cloudmold_inventory_migration_pilot_batch WHERE tenant_id=#{tenantId} AND batch_id=#{batchId}")
    InventoryMigrationPilotBatchDO selectBatch(@Param("tenantId") Long tenantId,
                                                 @Param("batchId") String batchId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_pilot_batch
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} FOR UPDATE
            """)
    InventoryMigrationPilotBatchDO selectBatchForUpdate(@Param("tenantId") Long tenantId,
                                                          @Param("batchId") String batchId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_pilot_item
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} ORDER BY ordinal
            """)
    List<InventoryMigrationPilotItemDO> selectItems(@Param("tenantId") Long tenantId,
                                                     @Param("batchId") String batchId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_pilot_item
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} ORDER BY ordinal FOR UPDATE
            """)
    List<InventoryMigrationPilotItemDO> selectItemsForUpdate(@Param("tenantId") Long tenantId,
                                                              @Param("batchId") String batchId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_pilot_approval
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} ORDER BY approval_role
            """)
    List<InventoryMigrationPilotApprovalDO> selectApprovals(@Param("tenantId") Long tenantId,
                                                             @Param("batchId") String batchId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_pilot_approval
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} ORDER BY approval_role FOR UPDATE
            """)
    List<InventoryMigrationPilotApprovalDO> selectApprovalsForUpdate(@Param("tenantId") Long tenantId,
                                                                      @Param("batchId") String batchId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_pilot_checkpoint
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} AND checkpoint_type='ADMISSION_PASSED'
            ORDER BY batch_version DESC LIMIT 1
            """)
    InventoryMigrationPilotCheckpointDO selectAdmissionCheckpoint(@Param("tenantId") Long tenantId,
                                                                    @Param("batchId") String batchId);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_pilot_approval
              (approval_id,tenant_id,batch_id,approval_role,approver_id,scope_hash,policy_hash,evidence_ref,
               idempotency_key,request_hash,status,version,approved_at,expires_at,created_at)
            VALUES
              (#{approvalId},#{tenantId},#{batchId},#{approvalRole},#{approverId},#{scopeHash},#{policyHash},
               #{evidenceRef},#{idempotencyKey},#{requestHash},'APPROVED',1,#{approvedAt},#{expiresAt},#{createdAt})
            """)
    int insertApproval(InventoryMigrationPilotApprovalDO value);

    @Update("""
            UPDATE cloudmold_inventory_migration_pilot_batch
            SET approval_count=#{approvalCount},status=#{status},version=version+1,
                approved_at=#{approvedAt},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} AND version=#{expectedVersion}
              AND status IN ('FROZEN','PARTIALLY_APPROVED')
            """)
    int markApproved(@Param("tenantId") Long tenantId,
                     @Param("batchId") String batchId,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("approvalCount") int approvalCount,
                     @Param("status") String status,
                     @Param("approvedAt") LocalDateTime approvedAt,
                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_migration_pilot_item
            SET status='APPROVED',version=2,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} AND status='FROZEN' AND version=1
            """)
    int markItemsApproved(@Param("tenantId") Long tenantId,
                          @Param("batchId") String batchId,
                          @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_migration_pilot_batch
            SET status='ADMISSION_PASSED',executor_id=#{executorId},version=version+1,
                admitted_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} AND status='APPROVED' AND version=#{expectedVersion}
            """)
    int markAdmitted(@Param("tenantId") Long tenantId,
                     @Param("batchId") String batchId,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("executorId") Long executorId,
                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_migration_pilot_item
            SET status='ADMISSION_PASSED',version=3,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} AND status='APPROVED' AND version=2
            """)
    int markItemsAdmitted(@Param("tenantId") Long tenantId,
                          @Param("batchId") String batchId,
                          @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_pilot_checkpoint
              (checkpoint_id,tenant_id,batch_id,checkpoint_type,actor_id,batch_version,scope_hash,policy_hash,
               item_count,details_json,created_at)
            VALUES
              (#{checkpointId},#{tenantId},#{batchId},#{checkpointType},#{actorId},#{batchVersion},#{scopeHash},
               #{policyHash},#{itemCount},CAST(#{detailsJson} AS JSON),#{now})
            """)
    int insertCheckpoint(@Param("checkpointId") String checkpointId,
                         @Param("tenantId") Long tenantId,
                         @Param("batchId") String batchId,
                         @Param("checkpointType") String checkpointType,
                         @Param("actorId") Long actorId,
                         @Param("batchVersion") Long batchVersion,
                         @Param("scopeHash") String scopeHash,
                         @Param("policyHash") String policyHash,
                         @Param("itemCount") int itemCount,
                         @Param("detailsJson") String detailsJson,
                         @Param("now") LocalDateTime now);
}
