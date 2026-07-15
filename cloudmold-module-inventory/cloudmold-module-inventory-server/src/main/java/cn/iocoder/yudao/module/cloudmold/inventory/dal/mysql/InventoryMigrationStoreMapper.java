package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface InventoryMigrationStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_operation
              (tenant_id,idempotency_key,source_event_id,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{sourceEventId},'ASSESS_V1',#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("sourceEventId") String sourceEventId,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_operation
              (tenant_id,idempotency_key,source_event_id,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{sourceEventId},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveCommand(@Param("tenantId") Long tenantId,
                               @Param("idempotencyKey") String idempotencyKey,
                               @Param("sourceEventId") String sourceEventId,
                               @Param("commandType") String commandType,
                               @Param("requestHash") String requestHash,
                               @Param("attemptToken") String attemptToken,
                               @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    InventoryMigrationOperationDO selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                            @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_inventory_migration_operation
            SET status=10,migration_run_id=#{migrationRunId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId,
                               @Param("operationId") Long operationId,
                               @Param("migrationRunId") String migrationRunId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_inventory_balance
            WHERE tenant_id=#{tenantId}
            ORDER BY balance_id
            FOR UPDATE
            """)
    List<InventoryBalanceDO> selectLegacyBalancesForUpdate(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT * FROM cloudmold_inventory_balance
            WHERE tenant_id=#{tenantId} AND balance_id=#{balanceId}
            FOR UPDATE
            """)
    InventoryBalanceDO selectLegacyBalanceForUpdate(@Param("tenantId") Long tenantId,
                                                      @Param("balanceId") String balanceId);

    @Select("""
            SELECT t.business_type,o.source_event_id
            FROM cloudmold_inventory_ledger_entry e
            JOIN cloudmold_inventory_ledger_transaction t
              ON t.tenant_id=e.tenant_id AND t.ledger_transaction_id=e.ledger_transaction_id
            JOIN cloudmold_inventory_operation o
              ON o.tenant_id=t.tenant_id AND o.operation_id=t.operation_id
            WHERE e.tenant_id=#{tenantId} AND e.balance_id=#{balanceId} AND e.aggregate_version=1
            ORDER BY e.ledger_entry_id
            LIMIT 1
            """)
    InventoryLegacySourceFactDO selectInitialSourceFact(@Param("tenantId") Long tenantId,
                                                         @Param("balanceId") String balanceId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_inventory_reservation
            WHERE tenant_id=#{tenantId} AND balance_id=#{balanceId} AND status=10
            """)
    int countActiveReservations(@Param("tenantId") Long tenantId,
                                @Param("balanceId") String balanceId);

    @Select("""
            SELECT COALESCE(SUM(quantity),0) FROM cloudmold_inventory_reservation
            WHERE tenant_id=#{tenantId} AND balance_id=#{balanceId} AND status=10
            """)
    java.math.BigDecimal sumActiveReservationQuantity(@Param("tenantId") Long tenantId,
                                                       @Param("balanceId") String balanceId);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_run
              (migration_run_id,tenant_id,source_scope,policy_version,evidence_ref,source_snapshot_hash,
               candidate_count,eligible_count,blocked_count,rejected_count,status,version,
               assessed_at,created_at,updated_at)
            VALUES
              (#{migrationRunId},#{tenantId},#{sourceScope},#{policyVersion},#{evidenceRef},#{sourceSnapshotHash},
               #{candidateCount},#{eligibleCount},#{blockedCount},#{rejectedCount},#{status},#{version},
               #{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertRun(InventoryMigrationRunDO value);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_candidate
              (candidate_id,tenant_id,migration_run_id,legacy_balance_id,legacy_balance_version,
               legacy_snapshot_hash,source_system,source_type,source_id,source_classification,source_updated_at,
               legacy_owner_id,legacy_canonical_sku_id,legacy_warehouse_id,
               stock_status,quality_status,base_uom_code,source_on_hand_quantity,source_reserved_quantity,
               source_in_transit_quantity,initial_business_type,initial_source_event_id,active_reservation_count,
               active_reservation_quantity,lot_tracking_policy,
               decision_status,reason_codes,verification_ref,version,assessed_at,created_at,updated_at)
            VALUES
              (#{candidateId},#{tenantId},#{migrationRunId},#{legacyBalanceId},#{legacyBalanceVersion},
               #{legacySnapshotHash},#{sourceSystem},#{sourceType},#{sourceId},#{sourceClassification},#{sourceUpdatedAt},
               #{legacyOwnerId},#{legacyCanonicalSkuId},#{legacyWarehouseId},
               #{stockStatus},#{qualityStatus},#{baseUomCode},#{sourceOnHandQuantity},#{sourceReservedQuantity},
               #{sourceInTransitQuantity},#{initialBusinessType},#{initialSourceEventId},#{activeReservationCount},
               #{activeReservationQuantity},#{lotTrackingPolicy},
               #{decisionStatus},CAST(#{reasonCodes} AS JSON),#{verificationRef},#{version},#{assessedAt},
               #{createdAt},#{updatedAt})
            """)
    int insertCandidate(InventoryMigrationCandidateDO value);

    @Select("SELECT * FROM cloudmold_inventory_migration_run WHERE tenant_id=#{tenantId} AND migration_run_id=#{migrationRunId}")
    InventoryMigrationRunDO selectRun(@Param("tenantId") Long tenantId,
                                      @Param("migrationRunId") String migrationRunId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_candidate
            WHERE tenant_id=#{tenantId} AND migration_run_id=#{migrationRunId}
            ORDER BY legacy_balance_id
            """)
    List<InventoryMigrationCandidateDO> selectCandidates(@Param("tenantId") Long tenantId,
                                                          @Param("migrationRunId") String migrationRunId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_candidate
            WHERE tenant_id=#{tenantId} AND candidate_id=#{candidateId}
            FOR UPDATE
            """)
    InventoryMigrationCandidateDO selectCandidateForUpdate(@Param("tenantId") Long tenantId,
                                                            @Param("candidateId") String candidateId);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_qualification
              (qualification_id,tenant_id,migration_run_id,candidate_id,qualification_operation_id,
               source_system,source_type,source_id,source_snapshot_hash,source_version,source_updated_at,
               source_on_hand_quantity,source_reserved_quantity,source_in_transit_quantity,
               owner_type,owner_id,canonical_sku_id,warehouse_source_mapping_id,warehouse_id,location_id,
               lot_tracking_policy,lot_id,stock_status,quality_status,base_uom_code,resolved_blocker_codes,
               policy_version,verification_ref,status,version,qualified_at,created_at,updated_at)
            VALUES
              (#{qualificationId},#{tenantId},#{migrationRunId},#{candidateId},#{qualificationOperationId},
               #{sourceSystem},#{sourceType},#{sourceId},#{sourceSnapshotHash},#{sourceVersion},#{sourceUpdatedAt},
               #{sourceOnHandQuantity},#{sourceReservedQuantity},#{sourceInTransitQuantity},
               #{ownerType},#{ownerId},#{canonicalSkuId},#{warehouseSourceMappingId},#{warehouseId},#{locationId},
               #{lotTrackingPolicy},#{lotId},#{stockStatus},#{qualityStatus},#{baseUomCode},
               CAST(#{resolvedBlockerCodes} AS JSON),#{policyVersion},#{verificationRef},#{status},#{version},
               #{qualifiedAt},#{createdAt},#{updatedAt})
            """)
    int insertQualification(InventoryMigrationQualificationDO value);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_qualification
            WHERE tenant_id=#{tenantId} AND qualification_id=#{qualificationId}
            """)
    InventoryMigrationQualificationDO selectQualification(@Param("tenantId") Long tenantId,
                                                            @Param("qualificationId") String qualificationId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_qualification
            WHERE tenant_id=#{tenantId} AND qualification_id=#{qualificationId}
            FOR UPDATE
            """)
    InventoryMigrationQualificationDO selectQualificationForUpdate(@Param("tenantId") Long tenantId,
                                                                     @Param("qualificationId") String qualificationId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_qualification
            WHERE tenant_id=#{tenantId} AND source_system='CLOUDMOLD_INVENTORY_V1'
              AND source_type='BALANCE' AND source_id=#{sourceId}
            FOR UPDATE
            """)
    InventoryMigrationQualificationDO selectQualificationBySourceForUpdate(@Param("tenantId") Long tenantId,
                                                                             @Param("sourceId") String sourceId);

    @Update("""
            UPDATE cloudmold_inventory_migration_qualification
            SET status='MIGRATED',opening_operation_id=#{openingOperationId},target_balance_id=#{targetBalanceId},
                ledger_transaction_id=#{ledgerTransactionId},bridge_id=#{bridgeId},version=2,
                migrated_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND qualification_id=#{qualificationId}
              AND status='QUALIFIED' AND version=1
            """)
    int markQualificationMigrated(@Param("tenantId") Long tenantId,
                                   @Param("qualificationId") String qualificationId,
                                   @Param("openingOperationId") Long openingOperationId,
                                   @Param("targetBalanceId") String targetBalanceId,
                                   @Param("ledgerTransactionId") Long ledgerTransactionId,
                                   @Param("bridgeId") String bridgeId,
                                   @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_bridge
              (bridge_id,tenant_id,legacy_balance_id,target_balance_id,source_system,source_type,source_id,
               resolution_status,verification_ref,source_on_hand_quantity,source_reserved_quantity,
               source_in_transit_quantity,target_on_hand_quantity,target_reserved_quantity,
               target_in_transit_quantity,version,created_at,updated_at)
            VALUES
              (#{bridgeId},#{tenantId},#{legacyBalanceId},#{targetBalanceId},'CLOUDMOLD_INVENTORY_V1','BALANCE',
               #{legacyBalanceId},'RESOLVED',#{verificationRef},#{sourceOnHand},#{sourceReserved},#{sourceInTransit},
               #{targetOnHand},#{targetReserved},#{targetInTransit},1,#{now},#{now})
            """)
    int insertResolvedBridge(@Param("bridgeId") String bridgeId,
                             @Param("tenantId") Long tenantId,
                             @Param("legacyBalanceId") String legacyBalanceId,
                             @Param("targetBalanceId") String targetBalanceId,
                             @Param("verificationRef") String verificationRef,
                             @Param("sourceOnHand") BigDecimal sourceOnHand,
                             @Param("sourceReserved") BigDecimal sourceReserved,
                             @Param("sourceInTransit") BigDecimal sourceInTransit,
                             @Param("targetOnHand") BigDecimal targetOnHand,
                             @Param("targetReserved") BigDecimal targetReserved,
                             @Param("targetInTransit") BigDecimal targetInTransit,
                             @Param("now") LocalDateTime now);

    @Select("""
            SELECT bridge_id
            FROM cloudmold_inventory_migration_bridge
            WHERE tenant_id=#{tenantId} AND legacy_balance_id=#{legacyBalanceId}
              AND resolution_status='RESOLVED'
            FOR UPDATE
            """)
    String selectResolvedBridgeIdForLegacyBalanceForUpdate(@Param("tenantId") Long tenantId,
                                                            @Param("legacyBalanceId") String legacyBalanceId);
}
