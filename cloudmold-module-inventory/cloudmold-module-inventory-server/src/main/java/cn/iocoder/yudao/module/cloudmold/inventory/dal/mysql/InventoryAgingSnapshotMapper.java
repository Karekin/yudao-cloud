package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotLineView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotView;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotLineDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotOperationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryAgingSnapshotCaptureItem;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryAgingSnapshotWatermark;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryAgingSnapshotMapper {

    @Insert("""
            INSERT INTO cloudmold_inventory_aging_snapshot_operation
              (tenant_id,idempotency_key,source_event_id,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{sourceEventId},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("sourceEventId") String sourceEventId,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,source_event_id,request_hash,attempt_token,status,
                   result_json,created_at,updated_at
            FROM cloudmold_inventory_aging_snapshot_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    InventoryAgingSnapshotOperationDO selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                               @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_inventory_aging_snapshot_operation
            SET status=10,result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId,
                               @Param("operationId") Long operationId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inventory_aging_snapshot
              (snapshot_id,tenant_id,snapshot_code,owner_type,owner_id,warehouse_id,
               bucket_policy_code,bucket_policy_version,bucket_policy_hash,
               age_fresh_max_days,age_aging_max_days,age_stale_max_days,
               expiry_warning_max_days,expiry_critical_max_days,
               ledger_watermark_ref,ledger_watermark_occurred_at,snapshot_date,
               line_count,unknown_age_count,unknown_expiry_count,status,version,created_at)
            VALUES
              (#{snapshotId},#{tenantId},#{snapshotCode},#{ownerType},#{ownerId},#{warehouseId},
               #{bucketPolicyCode},#{bucketPolicyVersion},#{bucketPolicyHash},
               #{ageFreshMaxDays},#{ageAgingMaxDays},#{ageStaleMaxDays},
               #{expiryWarningMaxDays},#{expiryCriticalMaxDays},
               #{ledgerWatermarkRef},#{ledgerWatermarkOccurredAt},#{snapshotDate},
               #{lineCount},#{unknownAgeCount},#{unknownExpiryCount},#{status},#{version},#{createdAt})
            """)
    int insertSnapshot(InventoryAgingSnapshotDO snapshot);

    @Select("""
            SELECT snapshot_id,tenant_id,snapshot_code,owner_type,owner_id,warehouse_id,
                   bucket_policy_code,bucket_policy_version,bucket_policy_hash,
                   age_fresh_max_days,age_aging_max_days,age_stale_max_days,
                   expiry_warning_max_days,expiry_critical_max_days,
                   ledger_watermark_ref,ledger_watermark_occurred_at,snapshot_date,
                   line_count,unknown_age_count,unknown_expiry_count,status,version,created_at
            FROM cloudmold_inventory_aging_snapshot
            WHERE tenant_id=#{tenantId} AND snapshot_id=#{snapshotId}
            FOR UPDATE
            """)
    InventoryAgingSnapshotDO selectSnapshotHeadForUpdate(@Param("tenantId") Long tenantId,
                                                         @Param("snapshotId") String snapshotId);

    @Insert("""
            INSERT INTO cloudmold_inventory_aging_snapshot_line
              (tenant_id,snapshot_id,balance_id,owner_type,owner_id,canonical_sku_id,warehouse_id,
               location_id,lot_id,lot_code,stock_status,quality_status,base_uom_code,on_hand_quantity,
               reserved_quantity,in_transit_quantity,available_quantity,balance_version,manufactured_on,
               expires_on,age_basis_type,age_basis_at,age_days,age_bucket,expiry_days_remaining,
               expiry_status,expiry_bucket,risk_classification,created_at)
            VALUES
              (#{tenantId},#{snapshotId},#{balanceId},#{ownerType},#{ownerId},#{canonicalSkuId},#{warehouseId},
               #{locationId},#{lotId},#{lotCode},#{stockStatus},#{qualityStatus},#{baseUomCode},
               #{onHandQuantity},#{reservedQuantity},#{inTransitQuantity},#{availableQuantity},
               #{balanceVersion},#{manufacturedOn},#{expiresOn},#{ageBasisType},#{ageBasisAt},#{ageDays},
               #{ageBucket},#{expiryDaysRemaining},#{expiryStatus},#{expiryBucket},#{riskClassification},#{createdAt})
            """)
    int insertSnapshotLine(InventoryAgingSnapshotLineDO line);

    @Select("""
            <script>
            SELECT COUNT(*) ledgerEntryCount,
                   MAX(e.ledger_entry_id) maxLedgerEntryId,
                   MAX(e.aggregate_version) maxBalanceVersion,
                   MAX(e.created_at) maxLedgerEntryAt
            FROM cloudmold_inventory_ledger_entry_v3 e
            JOIN cloudmold_inventory_balance_v3 b
              ON b.tenant_id=e.tenant_id AND b.balance_id=e.balance_id
            WHERE e.tenant_id=#{tenantId}
            <if test="ownerType != null">AND b.owner_type=#{ownerType}</if>
            <if test="ownerId != null">AND b.owner_id=#{ownerId}</if>
            <if test="warehouseId != null">AND b.warehouse_id=#{warehouseId}</if>
            </script>
            """)
    InventoryAgingSnapshotWatermark selectWatermark(@Param("tenantId") Long tenantId,
                                                    @Param("ownerType") String ownerType,
                                                    @Param("ownerId") String ownerId,
                                                    @Param("warehouseId") String warehouseId);

    @Select("""
            <script>
            SELECT b.balance_id balanceId,
                   b.owner_type ownerType,
                   b.owner_id ownerId,
                   b.canonical_sku_id canonicalSkuId,
                   b.warehouse_id warehouseId,
                   b.location_id locationId,
                   b.lot_id lotId,
                   l.lot_code lotCode,
                   b.stock_status stockStatus,
                   b.quality_status qualityStatus,
                   b.base_uom_code baseUomCode,
                   b.on_hand_quantity onHandQuantity,
                   b.reserved_quantity reservedQuantity,
                   b.in_transit_quantity inTransitQuantity,
                   b.version balanceVersion,
                   l.manufactured_on manufacturedOn,
                   l.expires_on expiresOn,
                   l.received_at lotReceivedAt,
                   ledger.first_ledger_entry_at firstLedgerEntryAt
            FROM cloudmold_inventory_balance_v3 b
            LEFT JOIN cloudmold_inventory_lot l
              ON l.tenant_id=b.tenant_id AND l.lot_id=b.lot_id
            LEFT JOIN (
                SELECT tenant_id,balance_id,MIN(created_at) first_ledger_entry_at
                FROM cloudmold_inventory_ledger_entry_v3
                GROUP BY tenant_id,balance_id
            ) ledger
              ON ledger.tenant_id=b.tenant_id AND ledger.balance_id=b.balance_id
            WHERE b.tenant_id=#{tenantId}
              AND (b.on_hand_quantity&lt;&gt;0 OR b.reserved_quantity&lt;&gt;0 OR b.in_transit_quantity&lt;&gt;0)
            <if test="ownerType != null">AND b.owner_type=#{ownerType}</if>
            <if test="ownerId != null">AND b.owner_id=#{ownerId}</if>
            <if test="warehouseId != null">AND b.warehouse_id=#{warehouseId}</if>
            ORDER BY b.owner_type,b.owner_id,b.canonical_sku_id,b.warehouse_id,b.location_id,
                     CASE WHEN b.lot_id IS NULL THEN 1 ELSE 0 END,b.lot_id,b.stock_status,b.quality_status,b.balance_id
            </script>
            """)
    List<InventoryAgingSnapshotCaptureItem> selectCaptureItems(@Param("tenantId") Long tenantId,
                                                               @Param("ownerType") String ownerType,
                                                               @Param("ownerId") String ownerId,
                                                               @Param("warehouseId") String warehouseId);

    @Select("""
            SELECT snapshot_id snapshotId,snapshot_code snapshotCode,status,version snapshotVersion,
                   owner_type ownerType,owner_id ownerId,warehouse_id warehouseId,
                   bucket_policy_code bucketPolicyCode,bucket_policy_version bucketPolicyVersion,
                   bucket_policy_hash bucketPolicyHash,age_fresh_max_days ageFreshMaxDays,
                   age_aging_max_days ageAgingMaxDays,age_stale_max_days ageStaleMaxDays,
                   expiry_warning_max_days expiryWarningMaxDays,
                   expiry_critical_max_days expiryCriticalMaxDays,
                   ledger_watermark_ref ledgerWatermarkRef,
                   ledger_watermark_occurred_at ledgerWatermarkOccurredAt,
                   snapshot_date snapshotDate,line_count lineCount,unknown_age_count unknownAgeCount,
                   unknown_expiry_count unknownExpiryCount,created_at createdAt
            FROM cloudmold_inventory_aging_snapshot
            WHERE tenant_id=#{tenantId} AND snapshot_id=#{snapshotId}
            """)
    InventoryAgingSnapshotView selectSnapshot(@Param("tenantId") Long tenantId,
                                              @Param("snapshotId") String snapshotId);

    @Select("""
            SELECT line_id lineId,balance_id balanceId,owner_type ownerType,owner_id ownerId,
                   canonical_sku_id canonicalSkuId,warehouse_id warehouseId,location_id locationId,
                   lot_id lotId,lot_code lotCode,stock_status stockStatus,quality_status qualityStatus,
                   base_uom_code baseUomCode,on_hand_quantity onHandQuantity,reserved_quantity reservedQuantity,
                   in_transit_quantity inTransitQuantity,available_quantity availableQuantity,
                   balance_version balanceVersion,manufactured_on manufacturedOn,expires_on expiresOn,
                   age_basis_type ageBasisType,age_basis_at ageBasisAt,age_days ageDays,age_bucket ageBucket,
                   expiry_days_remaining expiryDaysRemaining,expiry_status expiryStatus,expiry_bucket expiryBucket,
                   risk_classification riskClassification
            FROM cloudmold_inventory_aging_snapshot_line
            WHERE tenant_id=#{tenantId} AND snapshot_id=#{snapshotId}
            ORDER BY line_id ASC
            """)
    List<InventoryAgingSnapshotLineView> selectSnapshotLines(@Param("tenantId") Long tenantId,
                                                             @Param("snapshotId") String snapshotId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_inventory_aging_snapshot
            WHERE tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (
                snapshot_code LIKE CONCAT('%',#{keyword},'%')
                OR bucket_policy_version LIKE CONCAT('%',#{keyword},'%')
                OR ledger_watermark_ref LIKE CONCAT('%',#{keyword},'%')
              )
            </if>
            </script>
            """)
    long countSnapshotPage(@Param("tenantId") Long tenantId, @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT snapshot_id snapshotId,snapshot_code snapshotCode,status,version snapshotVersion,
                   owner_type ownerType,owner_id ownerId,warehouse_id warehouseId,
                   bucket_policy_code bucketPolicyCode,bucket_policy_version bucketPolicyVersion,
                   bucket_policy_hash bucketPolicyHash,age_fresh_max_days ageFreshMaxDays,
                   age_aging_max_days ageAgingMaxDays,age_stale_max_days ageStaleMaxDays,
                   expiry_warning_max_days expiryWarningMaxDays,
                   expiry_critical_max_days expiryCriticalMaxDays,
                   ledger_watermark_ref ledgerWatermarkRef,
                   ledger_watermark_occurred_at ledgerWatermarkOccurredAt,
                   snapshot_date snapshotDate,line_count lineCount,unknown_age_count unknownAgeCount,
                   unknown_expiry_count unknownExpiryCount,created_at createdAt
            FROM cloudmold_inventory_aging_snapshot
            WHERE tenant_id=#{tenantId}
            <if test="keyword != null">
              AND (
                snapshot_code LIKE CONCAT('%',#{keyword},'%')
                OR bucket_policy_version LIKE CONCAT('%',#{keyword},'%')
                OR ledger_watermark_ref LIKE CONCAT('%',#{keyword},'%')
              )
            </if>
            ORDER BY created_at DESC,snapshot_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<InventoryAgingSnapshotView> selectSnapshotPage(@Param("tenantId") Long tenantId,
                                                        @Param("keyword") String keyword,
                                                        @Param("offset") long offset,
                                                        @Param("limit") int limit);
}
