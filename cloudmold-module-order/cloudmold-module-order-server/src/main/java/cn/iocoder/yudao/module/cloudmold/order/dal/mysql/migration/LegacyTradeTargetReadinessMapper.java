package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration;

import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface LegacyTradeTargetReadinessMapper {

    @Select("""
            SELECT * FROM cloudmold_order_benefit_migration_run
            WHERE tenant_id=#{tenantId} AND migration_run_id=#{sourceRunId}
            """)
    LegacyTradeBenefitMigrationRunDO selectSourceRun(@Param("tenantId") Long tenantId,
                                                       @Param("sourceRunId") String sourceRunId);

    @Select("""
            WITH order_map AS (
              SELECT tenant_id,source_id,COUNT(*) mapping_count,MAX(mapping_plan_id) mapping_plan_id,
                     MAX(planned_target_id) planned_target_id,MAX(version) version,
                     MAX(source_snapshot_hash) source_snapshot_hash
              FROM cloudmold_order_migration_mapping_plan
              WHERE source_system='LOCAL_YUDAO_TRADE' AND source_type='ORDER' AND status='QUALIFIED'
              GROUP BY tenant_id,source_id
            ), lifecycle_map AS (
              SELECT tenant_id,source_status,COUNT(*) mapping_count,MAX(status_mapping_id) status_mapping_id,
                     MAX(canonical_status) canonical_status,MAX(version) version
              FROM cloudmold_order_legacy_status_mapping_policy
              WHERE source_system='LOCAL_YUDAO_TRADE' AND status='QUALIFIED'
              GROUP BY tenant_id,source_status
            )
            SELECT candidate.tenant_id,candidate.candidate_id,candidate.legacy_order_id,
                   candidate.legacy_order_status,candidate.legacy_snapshot_hash,candidate.is_deleted AS deleted,
                   candidate.buyer_identity_status,candidate.buyer_source_identity_id,
                   candidate.buyer_principal_id,candidate.buyer_identity_version,
                   candidate.negative_money,candidate.header_money_mismatch,candidate.header_item_mismatch,
                   candidate.invalid_item_money_count,
                   COALESCE(order_map.mapping_count,0) order_mapping_count,
                   CASE WHEN order_map.mapping_count=1 THEN order_map.mapping_plan_id END order_mapping_plan_id,
                   CASE WHEN order_map.mapping_count=1 THEN order_map.planned_target_id END planned_order_id,
                   CASE WHEN order_map.mapping_count=1 THEN order_map.version END order_mapping_version,
                   CASE WHEN order_map.mapping_count=1 THEN order_map.source_snapshot_hash END order_mapping_source_snapshot_hash,
                   COALESCE(lifecycle_map.mapping_count,0) lifecycle_mapping_count,
                   CASE WHEN lifecycle_map.mapping_count=1 THEN lifecycle_map.status_mapping_id END status_mapping_id,
                   CASE WHEN lifecycle_map.mapping_count=1 THEN lifecycle_map.canonical_status END canonical_order_status,
                   CASE WHEN lifecycle_map.mapping_count=1 THEN lifecycle_map.version END lifecycle_mapping_version
            FROM cloudmold_order_benefit_migration_candidate candidate
            LEFT JOIN order_map ON order_map.tenant_id=candidate.tenant_id
             AND order_map.source_id=CONVERT(CAST(candidate.legacy_order_id AS CHAR) USING utf8mb4)
               COLLATE utf8mb4_unicode_ci
            LEFT JOIN lifecycle_map ON lifecycle_map.tenant_id=candidate.tenant_id
             AND lifecycle_map.source_status=candidate.legacy_order_status
            WHERE candidate.tenant_id=#{tenantId} AND candidate.migration_run_id=#{sourceRunId}
            ORDER BY candidate.legacy_order_id
            """)
    List<LegacyTradeTargetReadinessOrderSourceDO> selectSourceOrders(@Param("tenantId") Long tenantId,
                                                                      @Param("sourceRunId") String sourceRunId);

    @Select("""
            WITH spu_map AS (
              SELECT tenant_id,source_id,COUNT(*) mapping_count,MAX(mapping_id) mapping_id,
                     MAX(target_id) target_id,MAX(version) version
              FROM cloudmold_catalog_migration_source_mapping
              WHERE source_system='LOCAL_YUDAO_TRADE' AND source_type='SPU' AND status='QUALIFIED'
              GROUP BY tenant_id,source_id
            ), sku_map AS (
              SELECT mapping.tenant_id,mapping.source_id,COUNT(*) mapping_count,MAX(mapping.mapping_id) mapping_id,
                     MAX(mapping.target_id) target_id,MAX(mapping.version) version,MAX(sku.spu_id) target_spu_id
              FROM cloudmold_catalog_migration_source_mapping mapping
              JOIN cloudmold_catalog_sku sku
                ON sku.tenant_id=mapping.tenant_id AND BINARY sku.sku_id=BINARY mapping.target_id
              WHERE mapping.source_system='LOCAL_YUDAO_TRADE'
                AND mapping.source_type='SKU' AND mapping.status='QUALIFIED'
              GROUP BY mapping.tenant_id,mapping.source_id
            ), item_plan AS (
              SELECT tenant_id,source_id,COUNT(*) mapping_count,MAX(mapping_plan_id) mapping_plan_id,
                     MAX(planned_target_id) planned_target_id,MAX(planned_target_order_id) planned_target_order_id,
                     MAX(version) version,MAX(source_snapshot_hash) source_snapshot_hash
              FROM cloudmold_order_migration_mapping_plan
              WHERE source_system='LOCAL_YUDAO_TRADE' AND source_type='ORDER_ITEM' AND status='QUALIFIED'
              GROUP BY tenant_id,source_id
            )
            SELECT item.tenant_id,item.candidate_id,item.legacy_order_id,item.legacy_order_item_id,
                   item.item_evidence_id,item.legacy_item_snapshot_hash,item.is_deleted AS deleted,
                   candidate.is_deleted AS order_deleted,item.legacy_spu_id,item.legacy_sku_id,
                   item.source_product_identity_status,item.item_quantity,item.unit_price_minor,
                   item.gross_amount_minor,item.generic_discount_amount_minor,item.coupon_amount_minor,
                   item.point_amount_minor,item.vip_amount_minor,item.delivery_amount_minor,
                   item.adjust_amount_minor,item.pay_amount_minor,
                   COALESCE(spu_map.mapping_count,0) spu_mapping_count,
                   CASE WHEN spu_map.mapping_count=1 THEN spu_map.mapping_id END spu_mapping_id,
                   CASE WHEN spu_map.mapping_count=1 THEN spu_map.target_id END canonical_spu_id,
                   CASE WHEN spu_map.mapping_count=1 THEN spu_map.version END spu_mapping_version,
                   COALESCE(sku_map.mapping_count,0) sku_mapping_count,
                   CASE WHEN sku_map.mapping_count=1 THEN sku_map.mapping_id END sku_mapping_id,
                   CASE WHEN sku_map.mapping_count=1 THEN sku_map.target_id END canonical_sku_id,
                   CASE WHEN sku_map.mapping_count=1 THEN sku_map.target_spu_id END canonical_sku_spu_id,
                   CASE WHEN sku_map.mapping_count=1 THEN sku_map.version END sku_mapping_version,
                   COALESCE(item_plan.mapping_count,0) order_item_mapping_count,
                   CASE WHEN item_plan.mapping_count=1 THEN item_plan.mapping_plan_id END order_item_mapping_plan_id,
                   CASE WHEN item_plan.mapping_count=1 THEN item_plan.planned_target_id END planned_order_item_id,
                   CASE WHEN item_plan.mapping_count=1 THEN item_plan.planned_target_order_id END planned_order_id,
                   CASE WHEN item_plan.mapping_count=1 THEN item_plan.version END order_item_mapping_version,
                   CASE WHEN item_plan.mapping_count=1 THEN item_plan.source_snapshot_hash END order_item_mapping_source_snapshot_hash
            FROM cloudmold_order_benefit_migration_item item
            JOIN cloudmold_order_benefit_migration_candidate candidate
              ON candidate.tenant_id=item.tenant_id
             AND BINARY candidate.migration_run_id=BINARY item.migration_run_id
             AND BINARY candidate.candidate_id=BINARY item.candidate_id
            LEFT JOIN spu_map ON spu_map.tenant_id=item.tenant_id
             AND spu_map.source_id=CONVERT(CAST(item.legacy_spu_id AS CHAR) USING utf8mb4)
               COLLATE utf8mb4_unicode_ci
            LEFT JOIN sku_map ON sku_map.tenant_id=item.tenant_id
             AND sku_map.source_id=CONVERT(CAST(item.legacy_sku_id AS CHAR) USING utf8mb4)
               COLLATE utf8mb4_unicode_ci
            LEFT JOIN item_plan ON item_plan.tenant_id=item.tenant_id
             AND item_plan.source_id=CONVERT(CAST(item.legacy_order_item_id AS CHAR) USING utf8mb4)
               COLLATE utf8mb4_unicode_ci
            WHERE item.tenant_id=#{tenantId} AND item.migration_run_id=#{sourceRunId}
            ORDER BY item.legacy_order_id,item.legacy_order_item_id
            """)
    List<LegacyTradeTargetReadinessItemSourceDO> selectSourceItems(@Param("tenantId") Long tenantId,
                                                                    @Param("sourceRunId") String sourceRunId);

    @Insert("""
            INSERT INTO cloudmold_order_target_readiness_operation
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
            SELECT * FROM cloudmold_order_target_readiness_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    LegacyTradeTargetReadinessOperationDO selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                                    @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_order_target_readiness_operation
            SET status=10,target_readiness_run_id=#{runId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId, @Param("operationId") Long operationId,
                               @Param("runId") String runId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_order_target_readiness_run
              (target_readiness_run_id,tenant_id,source_migration_run_id,policy_version,evidence_ref,
               target_mapping_evidence_hash,source_order_count,active_order_count,excluded_order_count,
               source_item_count,active_item_count,excluded_item_count,buyer_resolved_order_count,
               order_mapping_qualified_count,lifecycle_mapping_qualified_count,fully_mapped_item_count,
               mapping_admitted_order_count,mapping_blocked_order_count,canonical_import_allowed_order_count,
               production_migration_enabled,status,version,assessed_at,created_at,updated_at)
            VALUES
              (#{targetReadinessRunId},#{tenantId},#{sourceMigrationRunId},#{policyVersion},#{evidenceRef},
               #{targetMappingEvidenceHash},#{sourceOrderCount},#{activeOrderCount},#{excludedOrderCount},
               #{sourceItemCount},#{activeItemCount},#{excludedItemCount},#{buyerResolvedOrderCount},
               #{orderMappingQualifiedCount},#{lifecycleMappingQualifiedCount},#{fullyMappedItemCount},
               #{mappingAdmittedOrderCount},#{mappingBlockedOrderCount},#{canonicalImportAllowedOrderCount},
               #{productionMigrationEnabled},#{status},#{version},#{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertRun(LegacyTradeTargetReadinessRunDO value);

    @Insert("""
            INSERT INTO cloudmold_order_target_readiness_order
              (order_readiness_id,tenant_id,target_readiness_run_id,source_migration_run_id,candidate_id,
               legacy_order_id,legacy_snapshot_hash,buyer_identity_status,buyer_source_identity_id,
               buyer_principal_id,buyer_identity_version,order_mapping_plan_id,planned_order_id,
               order_mapping_version,order_mapping_status,status_mapping_id,canonical_order_status,
               lifecycle_mapping_version,lifecycle_mapping_status,money_reconciliation_status,
               active_item_count,fully_mapped_item_count,mapping_readiness_status,blocker_codes,
               mapping_admission_allowed,canonical_import_allowed,evidence_hash,version,assessed_at,created_at,updated_at)
            VALUES
              (#{orderReadinessId},#{tenantId},#{targetReadinessRunId},#{sourceMigrationRunId},#{candidateId},
               #{legacyOrderId},#{legacySnapshotHash},#{buyerIdentityStatus},#{buyerSourceIdentityId},
               #{buyerPrincipalId},#{buyerIdentityVersion},#{orderMappingPlanId},#{plannedOrderId},
               #{orderMappingVersion},#{orderMappingStatus},#{statusMappingId},#{canonicalOrderStatus},
               #{lifecycleMappingVersion},#{lifecycleMappingStatus},#{moneyReconciliationStatus},
               #{activeItemCount},#{fullyMappedItemCount},#{mappingReadinessStatus},CAST(#{blockerCodes} AS JSON),
               #{mappingAdmissionAllowed},#{canonicalImportAllowed},#{evidenceHash},#{version},
               #{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertOrder(LegacyTradeTargetReadinessOrderDO value);

    @Insert("""
            INSERT INTO cloudmold_order_target_readiness_item
              (item_readiness_id,tenant_id,target_readiness_run_id,order_readiness_id,legacy_order_id,
               legacy_order_item_id,item_evidence_id,legacy_item_snapshot_hash,spu_mapping_id,canonical_spu_id,
               spu_mapping_version,spu_mapping_status,sku_mapping_id,canonical_sku_id,sku_mapping_version,
               sku_mapping_status,order_item_mapping_plan_id,planned_order_item_id,planned_order_id,
               order_item_mapping_version,order_item_mapping_status,money_reconciliation_status,
               mapping_readiness_status,blocker_codes,mapping_admission_allowed,canonical_import_allowed,
               evidence_hash,version,assessed_at,created_at,updated_at)
            VALUES
              (#{itemReadinessId},#{tenantId},#{targetReadinessRunId},#{orderReadinessId},#{legacyOrderId},
               #{legacyOrderItemId},#{itemEvidenceId},#{legacyItemSnapshotHash},#{spuMappingId},#{canonicalSpuId},
               #{spuMappingVersion},#{spuMappingStatus},#{skuMappingId},#{canonicalSkuId},#{skuMappingVersion},
               #{skuMappingStatus},#{orderItemMappingPlanId},#{plannedOrderItemId},#{plannedOrderId},
               #{orderItemMappingVersion},#{orderItemMappingStatus},#{moneyReconciliationStatus},
               #{mappingReadinessStatus},CAST(#{blockerCodes} AS JSON),#{mappingAdmissionAllowed},
               #{canonicalImportAllowed},#{evidenceHash},#{version},#{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertItem(LegacyTradeTargetReadinessItemDO value);

    @Select("""
            SELECT * FROM cloudmold_order_target_readiness_run
            WHERE tenant_id=#{tenantId} AND target_readiness_run_id=#{runId}
            """)
    LegacyTradeTargetReadinessRunDO selectRun(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            SELECT * FROM cloudmold_order_target_readiness_order
            WHERE tenant_id=#{tenantId} AND target_readiness_run_id=#{runId} ORDER BY legacy_order_id
            """)
    List<LegacyTradeTargetReadinessOrderDO> selectOrders(@Param("tenantId") Long tenantId,
                                                          @Param("runId") String runId);

    @Select("""
            SELECT * FROM cloudmold_order_target_readiness_item
            WHERE tenant_id=#{tenantId} AND target_readiness_run_id=#{runId}
            ORDER BY legacy_order_id,legacy_order_item_id
            """)
    List<LegacyTradeTargetReadinessItemDO> selectItems(@Param("tenantId") Long tenantId,
                                                        @Param("runId") String runId);
}
