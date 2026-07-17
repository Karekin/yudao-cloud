package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration;

import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LegacyTradeBenefitMigrationMapper {

    @Select("""
            WITH item_rollup AS (
              SELECT tenant_id,order_id,COUNT(*) item_row_count,SUM(`count`) item_quantity,
                     SUM(price*`count`) item_gross_amount_minor,
                     SUM(discount_price) item_generic_discount_amount_minor,
                     SUM(coupon_price) item_coupon_amount_minor,
                     SUM(point_price) item_point_amount_minor,
                     SUM(vip_price) item_vip_amount_minor,
                     SUM(delivery_price) item_delivery_amount_minor,
                     SUM(adjust_price) item_adjust_amount_minor,
                     SUM(pay_price) item_pay_amount_minor,
                     SUM(CASE WHEN price<0 OR `count`<=0 OR discount_price<0 OR coupon_price<0
                               OR point_price<0 OR vip_price<0 OR delivery_price<0 OR pay_price<0
                               OR price*`count`+delivery_price+adjust_price
                                  <>discount_price+coupon_price+point_price+vip_price+pay_price
                              THEN 1 ELSE 0 END) invalid_item_money_count
              FROM trade_order_item WHERE tenant_id=#{tenantId} AND deleted=0
              GROUP BY tenant_id,order_id
            )
            SELECT o.tenant_id,o.id legacy_order_id,o.no legacy_order_no,o.update_time source_updated_at,
                   o.deleted,o.product_count header_quantity,
                   COALESCE(i.item_row_count,0) item_row_count,COALESCE(i.item_quantity,0) item_quantity,
                   CAST(o.total_price AS SIGNED) header_gross_amount_minor,
                   CAST(o.discount_price AS SIGNED) header_generic_discount_amount_minor,
                   CAST(o.coupon_price AS SIGNED) header_coupon_amount_minor,
                   CAST(o.point_price AS SIGNED) header_point_amount_minor,
                   CAST(o.vip_price AS SIGNED) header_vip_amount_minor,
                   CAST(o.delivery_price AS SIGNED) header_delivery_amount_minor,
                   CAST(o.adjust_price AS SIGNED) header_adjust_amount_minor,
                   CAST(o.pay_price AS SIGNED) header_pay_amount_minor,
                   COALESCE(i.item_gross_amount_minor,0) item_gross_amount_minor,
                   COALESCE(i.item_generic_discount_amount_minor,0) item_generic_discount_amount_minor,
                   COALESCE(i.item_coupon_amount_minor,0) item_coupon_amount_minor,
                   COALESCE(i.item_point_amount_minor,0) item_point_amount_minor,
                   COALESCE(i.item_vip_amount_minor,0) item_vip_amount_minor,
                   COALESCE(i.item_delivery_amount_minor,0) item_delivery_amount_minor,
                   COALESCE(i.item_adjust_amount_minor,0) item_adjust_amount_minor,
                   COALESCE(i.item_pay_amount_minor,0) item_pay_amount_minor,
                   COALESCE(i.invalid_item_money_count,0) invalid_item_money_count,
                   o.coupon_id legacy_coupon_id,o.use_point legacy_used_point_quantity,
                   o.seckill_activity_id legacy_seckill_activity_id,
                   o.bargain_activity_id legacy_bargain_activity_id,
                   o.combination_activity_id legacy_combination_activity_id,
                   o.point_activity_id legacy_point_activity_id
            FROM trade_order o
            LEFT JOIN item_rollup i ON i.tenant_id=o.tenant_id AND i.order_id=o.id
            WHERE o.tenant_id=#{tenantId}
            ORDER BY o.id
            """)
    List<LegacyTradeOrderAssessmentSourceDO> selectSourceOrders(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT tenant_id,id legacy_order_item_id,order_id legacy_order_id,update_time source_updated_at,
                   deleted,spu_id legacy_spu_id,sku_id legacy_sku_id,`count` item_quantity,
                   CAST(price AS SIGNED) unit_price_minor,CAST(price*`count` AS SIGNED) gross_amount_minor,
                   CAST(discount_price AS SIGNED) generic_discount_amount_minor,
                   CAST(coupon_price AS SIGNED) coupon_amount_minor,
                   CAST(point_price AS SIGNED) point_amount_minor,CAST(vip_price AS SIGNED) vip_amount_minor,
                   CAST(delivery_price AS SIGNED) delivery_amount_minor,
                   CAST(adjust_price AS SIGNED) adjust_amount_minor,CAST(pay_price AS SIGNED) pay_amount_minor,
                   CAST(COALESCE(use_point,0) AS SIGNED) used_point_quantity
            FROM trade_order_item
            WHERE tenant_id=#{tenantId}
            ORDER BY order_id,id
            """)
    List<LegacyTradeOrderItemAssessmentSourceDO> selectSourceOrderItems(@Param("tenantId") Long tenantId);

    @Insert("""
            INSERT INTO cloudmold_order_benefit_migration_operation
              (tenant_id,idempotency_key,source_event_id,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{sourceEventId},'ASSESS_LEGACY_TRADE_V1',
                    #{requestHash},#{attemptToken},0,#{now},#{now})
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
            SELECT * FROM cloudmold_order_benefit_migration_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    LegacyTradeBenefitMigrationOperationDO selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                                     @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_order_benefit_migration_operation
            SET status=10,migration_run_id=#{migrationRunId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId,
                               @Param("operationId") Long operationId,
                               @Param("migrationRunId") String migrationRunId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_order_benefit_migration_run
              (migration_run_id,tenant_id,source_scope,policy_version,evidence_ref,source_snapshot_hash,
               source_watermark,source_order_count,non_deleted_order_count,deleted_excluded_count,
               no_benefit_order_count,benefit_evidence_pending_order_count,quarantined_order_count,
               benefit_component_count,source_benefit_amount_minor,component_amount_minor,
               source_item_count,active_item_count,excluded_item_count,item_evidence_hash,
               item_evidence_benefit_amount_minor,item_evidence_complete,
               unresolved_identity_count,unresolved_funding_count,import_allowed_component_count,
               production_migration_enabled,status,version,assessed_at,created_at,updated_at)
            VALUES
              (#{migrationRunId},#{tenantId},#{sourceScope},#{policyVersion},#{evidenceRef},#{sourceSnapshotHash},
               #{sourceWatermark},#{sourceOrderCount},#{nonDeletedOrderCount},#{deletedExcludedCount},
               #{noBenefitOrderCount},#{benefitEvidencePendingOrderCount},#{quarantinedOrderCount},
               #{benefitComponentCount},#{sourceBenefitAmountMinor},#{componentAmountMinor},
               #{sourceItemCount},#{activeItemCount},#{excludedItemCount},#{itemEvidenceHash},
               #{itemEvidenceBenefitAmountMinor},#{itemEvidenceComplete},
               #{unresolvedIdentityCount},#{unresolvedFundingCount},#{importAllowedComponentCount},
               #{productionMigrationEnabled},#{status},#{version},#{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertRun(LegacyTradeBenefitMigrationRunDO value);

    @Insert("""
            INSERT INTO cloudmold_order_benefit_migration_candidate
              (candidate_id,tenant_id,migration_run_id,legacy_order_id,legacy_order_no,legacy_snapshot_hash,
               source_updated_at,is_deleted,header_quantity,item_row_count,item_quantity,
               header_gross_amount_minor,header_generic_discount_amount_minor,header_coupon_amount_minor,
               header_point_amount_minor,header_vip_amount_minor,header_delivery_amount_minor,
               header_adjust_amount_minor,header_pay_amount_minor,item_gross_amount_minor,
               item_generic_discount_amount_minor,item_coupon_amount_minor,item_point_amount_minor,
               item_vip_amount_minor,item_delivery_amount_minor,item_adjust_amount_minor,item_pay_amount_minor,
               invalid_item_money_count,negative_money,header_money_mismatch,header_item_mismatch,
               assessment_status,reason_codes,canonical_import_allowed,version,assessed_at,created_at,updated_at)
            VALUES
              (#{candidateId},#{tenantId},#{migrationRunId},#{legacyOrderId},#{legacyOrderNo},#{legacySnapshotHash},
               #{sourceUpdatedAt},#{deleted},#{headerQuantity},#{itemRowCount},#{itemQuantity},
               #{headerGrossAmountMinor},#{headerGenericDiscountAmountMinor},#{headerCouponAmountMinor},
               #{headerPointAmountMinor},#{headerVipAmountMinor},#{headerDeliveryAmountMinor},
               #{headerAdjustAmountMinor},#{headerPayAmountMinor},#{itemGrossAmountMinor},
               #{itemGenericDiscountAmountMinor},#{itemCouponAmountMinor},#{itemPointAmountMinor},
               #{itemVipAmountMinor},#{itemDeliveryAmountMinor},#{itemAdjustAmountMinor},#{itemPayAmountMinor},
               #{invalidItemMoneyCount},#{negativeMoney},#{headerMoneyMismatch},#{headerItemMismatch},
               #{assessmentStatus},CAST(#{reasonCodes} AS JSON),#{canonicalImportAllowed},#{version},
               #{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertCandidate(LegacyTradeBenefitMigrationCandidateDO value);

    @Insert("""
            INSERT INTO cloudmold_order_benefit_migration_component
              (component_id,tenant_id,migration_run_id,candidate_id,legacy_order_id,component_type,
               component_amount_minor,source_reference,identity_resolution_status,funding_resolution_status,
               canonical_import_allowed,version,created_at,updated_at)
            VALUES
              (#{componentId},#{tenantId},#{migrationRunId},#{candidateId},#{legacyOrderId},#{componentType},
               #{componentAmountMinor},#{sourceReference},#{identityResolutionStatus},#{fundingResolutionStatus},
               #{canonicalImportAllowed},#{version},#{createdAt},#{updatedAt})
            """)
    int insertComponent(LegacyTradeBenefitMigrationComponentDO value);

    @Insert("""
            INSERT INTO cloudmold_order_benefit_migration_item
              (item_evidence_id,tenant_id,migration_run_id,candidate_id,legacy_order_id,legacy_order_item_id,
               legacy_item_snapshot_hash,source_updated_at,is_deleted,legacy_spu_id,legacy_sku_id,
               source_product_identity_status,item_quantity,unit_price_minor,gross_amount_minor,
               generic_discount_amount_minor,coupon_amount_minor,point_amount_minor,vip_amount_minor,
               delivery_amount_minor,adjust_amount_minor,pay_amount_minor,used_point_quantity,
               canonical_import_allowed,version,created_at,updated_at)
            VALUES
              (#{itemEvidenceId},#{tenantId},#{migrationRunId},#{candidateId},#{legacyOrderId},#{legacyOrderItemId},
               #{legacyItemSnapshotHash},#{sourceUpdatedAt},#{deleted},#{legacySpuId},#{legacySkuId},
               #{sourceProductIdentityStatus},#{itemQuantity},#{unitPriceMinor},#{grossAmountMinor},
               #{genericDiscountAmountMinor},#{couponAmountMinor},#{pointAmountMinor},#{vipAmountMinor},
               #{deliveryAmountMinor},#{adjustAmountMinor},#{payAmountMinor},#{usedPointQuantity},
               #{canonicalImportAllowed},#{version},#{createdAt},#{updatedAt})
            """)
    int insertItem(LegacyTradeBenefitMigrationItemDO value);

    @Select("SELECT * FROM cloudmold_order_benefit_migration_run WHERE tenant_id=#{tenantId} AND migration_run_id=#{runId}")
    LegacyTradeBenefitMigrationRunDO selectRun(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            SELECT * FROM cloudmold_order_benefit_migration_candidate
            WHERE tenant_id=#{tenantId} AND migration_run_id=#{runId} ORDER BY legacy_order_id
            """)
    List<LegacyTradeBenefitMigrationCandidateDO> selectCandidates(@Param("tenantId") Long tenantId,
                                                                   @Param("runId") String runId);

    @Select("""
            SELECT * FROM cloudmold_order_benefit_migration_component
            WHERE tenant_id=#{tenantId} AND migration_run_id=#{runId} ORDER BY legacy_order_id,component_type
            """)
    List<LegacyTradeBenefitMigrationComponentDO> selectComponents(@Param("tenantId") Long tenantId,
                                                                   @Param("runId") String runId);

    @Select("""
            SELECT * FROM cloudmold_order_benefit_migration_item
            WHERE tenant_id=#{tenantId} AND migration_run_id=#{runId}
            ORDER BY legacy_order_id,legacy_order_item_id
            """)
    List<LegacyTradeBenefitMigrationItemDO> selectItems(@Param("tenantId") Long tenantId,
                                                         @Param("runId") String runId);

    @Select("""
            SELECT * FROM cloudmold_order_benefit_migration_component_reconciliation
            WHERE tenant_id=#{tenantId} AND migration_run_id=#{runId}
            ORDER BY legacy_order_id,component_type
            """)
    List<LegacyTradeBenefitComponentReconciliationDO> selectComponentReconciliations(
            @Param("tenantId") Long tenantId, @Param("runId") String runId);
}
