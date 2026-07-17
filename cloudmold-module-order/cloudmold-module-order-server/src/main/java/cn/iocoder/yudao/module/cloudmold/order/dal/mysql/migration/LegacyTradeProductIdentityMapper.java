package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration;

import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface LegacyTradeProductIdentityMapper {

    @Select("""
            SELECT * FROM cloudmold_order_benefit_migration_run
            WHERE tenant_id=#{tenantId} AND migration_run_id=#{sourceRunId}
            """)
    LegacyTradeBenefitMigrationRunDO selectSourceRun(@Param("tenantId") Long tenantId,
                                                       @Param("sourceRunId") String sourceRunId);

    @Select("""
            WITH parent_cardinality AS (
              SELECT item.tenant_id,item.migration_run_id,item.legacy_sku_id,
                     COUNT(DISTINCT item.legacy_spu_id) source_parent_cardinality
              FROM cloudmold_order_benefit_migration_item item
              JOIN cloudmold_order_benefit_migration_candidate candidate
                ON candidate.tenant_id=item.tenant_id
               AND BINARY candidate.migration_run_id=BINARY item.migration_run_id
               AND BINARY candidate.candidate_id=BINARY item.candidate_id
              WHERE item.tenant_id=#{tenantId} AND item.migration_run_id=#{sourceRunId}
                AND item.is_deleted=0 AND candidate.is_deleted=0
                AND item.legacy_spu_id IS NOT NULL AND item.legacy_sku_id IS NOT NULL
              GROUP BY item.tenant_id,item.migration_run_id,item.legacy_sku_id
            ), qualification AS (
              SELECT tenant_id,source_migration_run_id,item_evidence_id,COUNT(*) qualification_count,
                     MAX(qualification_id) qualification_id,
                     MAX(legacy_order_item_id) qualified_legacy_order_item_id,
                     MAX(historical_spu_id) historical_spu_id,MAX(historical_sku_id) historical_sku_id,
                     MAX(source_item_evidence_hash) qualification_source_item_evidence_hash,
                     MAX(historical_product_snapshot_hash) historical_product_snapshot_hash
              FROM cloudmold_order_product_identity_qualification
              WHERE tenant_id=#{tenantId} AND source_migration_run_id=#{sourceRunId}
                AND status='QUALIFIED'
              GROUP BY tenant_id,source_migration_run_id,item_evidence_id
            )
            SELECT item.tenant_id,item.migration_run_id source_migration_run_id,item.candidate_id,
                   item.item_evidence_id,item.legacy_order_id,item.legacy_order_item_id,
                   item.legacy_spu_id,item.legacy_sku_id,
                   item.legacy_item_snapshot_hash source_item_evidence_hash,
                   item.is_deleted deleted,candidate.is_deleted order_deleted,
                   COALESCE(parent.source_parent_cardinality,0) source_parent_cardinality,
                   current_spu.id current_spu_id,current_spu.status current_spu_status,
                   current_spu.deleted current_spu_deleted,current_spu.create_time current_spu_created_at,
                   current_spu.update_time current_spu_updated_at,
                   current_sku.id current_sku_id,current_sku.spu_id current_sku_spu_id,
                   current_sku.deleted current_sku_deleted,current_sku.create_time current_sku_created_at,
                   current_sku.update_time current_sku_updated_at,
                   COALESCE(qualification.qualification_count,0) qualification_count,
                   CASE WHEN qualification.qualification_count=1 THEN qualification.qualification_id END qualification_id,
                   CASE WHEN qualification.qualification_count=1 THEN qualification.qualified_legacy_order_item_id END
                     qualified_legacy_order_item_id,
                   CASE WHEN qualification.qualification_count=1 THEN qualification.historical_spu_id END historical_spu_id,
                   CASE WHEN qualification.qualification_count=1 THEN qualification.historical_sku_id END historical_sku_id,
                   CASE WHEN qualification.qualification_count=1
                     THEN qualification.qualification_source_item_evidence_hash END qualification_source_item_evidence_hash,
                   CASE WHEN qualification.qualification_count=1
                     THEN qualification.historical_product_snapshot_hash END historical_product_snapshot_hash
            FROM cloudmold_order_benefit_migration_item item
            JOIN cloudmold_order_benefit_migration_candidate candidate
              ON candidate.tenant_id=item.tenant_id
             AND BINARY candidate.migration_run_id=BINARY item.migration_run_id
             AND BINARY candidate.candidate_id=BINARY item.candidate_id
            LEFT JOIN parent_cardinality parent
              ON parent.tenant_id=item.tenant_id
             AND BINARY parent.migration_run_id=BINARY item.migration_run_id
             AND parent.legacy_sku_id=item.legacy_sku_id
            LEFT JOIN product_spu current_spu
              ON current_spu.tenant_id=item.tenant_id AND current_spu.id=item.legacy_spu_id
            LEFT JOIN product_sku current_sku
              ON current_sku.tenant_id=item.tenant_id AND current_sku.id=item.legacy_sku_id
            LEFT JOIN qualification
              ON qualification.tenant_id=item.tenant_id
             AND BINARY qualification.source_migration_run_id=BINARY item.migration_run_id
             AND BINARY qualification.item_evidence_id=BINARY item.item_evidence_id
            WHERE item.tenant_id=#{tenantId} AND item.migration_run_id=#{sourceRunId}
            ORDER BY item.legacy_order_id,item.legacy_order_item_id
            """)
    List<LegacyTradeProductIdentityItemSourceDO> selectSourceItems(@Param("tenantId") Long tenantId,
                                                                    @Param("sourceRunId") String sourceRunId);

    @Insert("""
            INSERT INTO cloudmold_order_product_identity_operation
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
            SELECT * FROM cloudmold_order_product_identity_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    LegacyTradeProductIdentityOperationDO selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                                    @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_order_product_identity_operation
            SET status=10,identity_run_id=#{runId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId, @Param("operationId") Long operationId,
                               @Param("runId") String runId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_order_product_identity_run
              (identity_run_id,tenant_id,source_migration_run_id,policy_version,evidence_ref,
               governance_evidence_hash,source_item_count,active_item_count,excluded_item_count,
               source_pair_unambiguous_count,source_parent_conflict_item_count,current_relation_observed_count,
               historical_identity_qualified_count,identity_admitted_item_count,target_mapping_enabled,
               status,version,assessed_at,created_at,updated_at)
            VALUES
              (#{identityRunId},#{tenantId},#{sourceMigrationRunId},#{policyVersion},#{evidenceRef},
               #{governanceEvidenceHash},#{sourceItemCount},#{activeItemCount},#{excludedItemCount},
               #{sourcePairUnambiguousCount},#{sourceParentConflictItemCount},#{currentRelationObservedCount},
               #{historicalIdentityQualifiedCount},#{identityAdmittedItemCount},#{targetMappingEnabled},
               #{status},#{version},#{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertRun(LegacyTradeProductIdentityRunDO value);

    @Insert("""
            INSERT INTO cloudmold_order_product_identity_item
              (identity_item_id,tenant_id,identity_run_id,source_migration_run_id,candidate_id,item_evidence_id,
               legacy_order_id,legacy_order_item_id,legacy_spu_id,legacy_sku_id,source_item_evidence_hash,
               source_parent_cardinality,source_pair_status,current_reference_status,current_product_snapshot_hash,
               qualification_id,historical_identity_status,blocker_codes,identity_admission_allowed,
               target_mapping_allowed,evidence_hash,version,assessed_at,created_at,updated_at)
            VALUES
              (#{identityItemId},#{tenantId},#{identityRunId},#{sourceMigrationRunId},#{candidateId},#{itemEvidenceId},
               #{legacyOrderId},#{legacyOrderItemId},#{legacySpuId},#{legacySkuId},#{sourceItemEvidenceHash},
               #{sourceParentCardinality},#{sourcePairStatus},#{currentReferenceStatus},#{currentProductSnapshotHash},
               #{qualificationId},#{historicalIdentityStatus},CAST(#{blockerCodes} AS JSON),
               #{identityAdmissionAllowed},#{targetMappingAllowed},#{evidenceHash},#{version},
               #{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertItem(LegacyTradeProductIdentityItemDO value);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_run
            WHERE tenant_id=#{tenantId} AND identity_run_id=#{runId}
            """)
    LegacyTradeProductIdentityRunDO selectRun(@Param("tenantId") Long tenantId,
                                               @Param("runId") String runId);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_item
            WHERE tenant_id=#{tenantId} AND identity_run_id=#{runId}
            ORDER BY legacy_order_id,legacy_order_item_id
            """)
    List<LegacyTradeProductIdentityItemDO> selectItems(@Param("tenantId") Long tenantId,
                                                        @Param("runId") String runId);
}
