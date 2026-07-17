package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration;

import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true") // Complex evidence CTEs are parser-incompatible; every statement binds tenant_id explicitly.
public interface LegacyTradeBenefitGovernanceMapper {

    @Select("SELECT * FROM cloudmold_order_benefit_migration_run " +
            "WHERE tenant_id=#{tenantId} AND migration_run_id=#{runId}")
    LegacyTradeBenefitMigrationRunDO selectSourceRun(@Param("tenantId") Long tenantId,
                                                       @Param("runId") String runId);

    @Select("""
            WITH current_ref AS (
              SELECT 'BARGAIN' reference_type,id reference_id,'promotion_bargain_activity' source_table,
                     create_time source_created_at,update_time source_updated_at,
                     CAST(status AS CHAR) source_status,deleted source_deleted,spu_id source_spu_id
              FROM promotion_bargain_activity WHERE tenant_id=#{tenantId}
              UNION ALL
              SELECT 'COMBINATION',id,'promotion_combination_activity',create_time,update_time,
                     CAST(status AS CHAR),deleted,spu_id
              FROM promotion_combination_activity WHERE tenant_id=#{tenantId}
              UNION ALL
              SELECT 'SECKILL',id,'promotion_seckill_activity',create_time,update_time,
                     CAST(status AS CHAR),deleted,spu_id
              FROM promotion_seckill_activity WHERE tenant_id=#{tenantId}
              UNION ALL
              SELECT 'POINT_ACTIVITY',id,'promotion_point_activity',create_time,update_time,
                     CAST(status AS CHAR),deleted,spu_id
              FROM promotion_point_activity WHERE tenant_id=#{tenantId}
              UNION ALL
              SELECT 'COUPON',id,'promotion_coupon',create_time,update_time,
                     CAST(status AS CHAR),deleted,NULL
              FROM promotion_coupon WHERE tenant_id=#{tenantId}
            ), identity_q AS (
              SELECT tenant_id,source_migration_run_id,component_id,COUNT(*) qualification_count,
                     MAX(qualification_id) qualification_id,
                     MAX(historical_benefit_type) historical_benefit_type,
                     MAX(source_component_evidence_hash) source_component_evidence_hash
              FROM cloudmold_order_benefit_identity_qualification
              WHERE tenant_id=#{tenantId} AND source_migration_run_id=#{runId} AND status='QUALIFIED'
              GROUP BY tenant_id,source_migration_run_id,component_id
            ), funding_q AS (
              SELECT tenant_id,source_migration_run_id,component_id,COUNT(*) funding_share_count,
                     SUM(amount_minor) funding_amount_minor,
                     COUNT(DISTINCT source_component_evidence_hash) source_evidence_hash_count,
                     MAX(source_component_evidence_hash) source_component_evidence_hash
              FROM cloudmold_order_benefit_funding_qualification
              WHERE tenant_id=#{tenantId} AND source_migration_run_id=#{runId} AND status='QUALIFIED'
              GROUP BY tenant_id,source_migration_run_id,component_id
            )
            SELECT component.tenant_id,component.migration_run_id source_migration_run_id,
                   component.component_id,component.candidate_id,component.legacy_order_id,
                   component.component_type,component.component_amount_minor,component.source_reference,
                   current_ref.source_table observed_source_table,
                   current_ref.reference_id observed_source_id,
                   current_ref.source_created_at observed_source_created_at,
                   current_ref.source_updated_at observed_source_updated_at,
                   current_ref.source_status observed_source_status,
                   current_ref.source_deleted observed_source_deleted,
                   current_ref.source_spu_id observed_source_spu_id,
                   COALESCE(identity_q.qualification_count,0) identity_qualification_count,
                   identity_q.qualification_id identity_qualification_id,
                   identity_q.source_component_evidence_hash identity_source_component_evidence_hash,
                   COALESCE(funding_q.funding_share_count,0) funding_share_count,
                   COALESCE(funding_q.funding_amount_minor,0) funding_amount_minor,
                   COALESCE(funding_q.source_evidence_hash_count,0) funding_source_evidence_hash_count,
                   funding_q.source_component_evidence_hash funding_source_component_evidence_hash
            FROM cloudmold_order_benefit_migration_component component
            LEFT JOIN current_ref
              ON current_ref.reference_type=SUBSTRING_INDEX(component.source_reference,':',1)
             AND current_ref.reference_id=CAST(SUBSTRING_INDEX(component.source_reference,':',-1) AS UNSIGNED)
            LEFT JOIN identity_q
              ON identity_q.tenant_id=component.tenant_id
             AND BINARY identity_q.source_migration_run_id=BINARY component.migration_run_id
             AND BINARY identity_q.component_id=BINARY component.component_id
             AND BINARY identity_q.historical_benefit_type=BINARY component.component_type
            LEFT JOIN funding_q
              ON funding_q.tenant_id=component.tenant_id
             AND BINARY funding_q.source_migration_run_id=BINARY component.migration_run_id
             AND BINARY funding_q.component_id=BINARY component.component_id
            WHERE component.tenant_id=#{tenantId} AND component.migration_run_id=#{runId}
            ORDER BY component.legacy_order_id,component.component_type
            """)
    List<LegacyTradeBenefitGovernanceComponentSourceDO> selectSourceComponents(
            @Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            WITH active_decision AS (
              SELECT tenant_id,source_migration_run_id,candidate_id,COUNT(*) decision_count,
                     MAX(decision_id) decision_id,
                     MAX(source_candidate_evidence_hash) source_candidate_evidence_hash
              FROM cloudmold_order_benefit_quarantine_decision
              WHERE tenant_id=#{tenantId} AND source_migration_run_id=#{runId} AND status='QUALIFIED'
              GROUP BY tenant_id,source_migration_run_id,candidate_id
            )
            SELECT candidate.tenant_id,candidate.migration_run_id source_migration_run_id,
                   candidate.candidate_id,candidate.legacy_order_id,candidate.legacy_order_no,
                   candidate.legacy_snapshot_hash,candidate.assessment_status,candidate.reason_codes,
                   COALESCE(active_decision.decision_count,0) decision_count,
                   active_decision.decision_id,
                   active_decision.source_candidate_evidence_hash decision_source_candidate_evidence_hash
            FROM cloudmold_order_benefit_migration_candidate candidate
            LEFT JOIN active_decision
              ON active_decision.tenant_id=candidate.tenant_id
             AND BINARY active_decision.source_migration_run_id=BINARY candidate.migration_run_id
             AND BINARY active_decision.candidate_id=BINARY candidate.candidate_id
            WHERE candidate.tenant_id=#{tenantId} AND candidate.migration_run_id=#{runId}
              AND candidate.assessment_status IN ('QUARANTINED_MONEY','QUARANTINED_HEADER_ITEM')
            ORDER BY candidate.legacy_order_id
            """)
    List<LegacyTradeBenefitGovernanceQuarantineSourceDO> selectSourceQuarantines(
            @Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Insert("""
            INSERT INTO cloudmold_order_benefit_governance_operation
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

    @Select("SELECT * FROM cloudmold_order_benefit_governance_operation " +
            "WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE")
    LegacyTradeBenefitGovernanceOperationDO selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                                      @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_order_benefit_governance_operation
            SET status=10,governance_run_id=#{runId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId, @Param("operationId") Long operationId,
                               @Param("runId") String runId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_order_benefit_governance_run
              (governance_run_id,tenant_id,source_migration_run_id,policy_version,evidence_ref,
               governance_evidence_hash,source_component_count,source_reference_present_count,
               current_reference_observed_count,historical_identity_qualified_count,identity_blocked_count,
               funding_qualified_count,funding_blocked_count,source_quarantine_count,
               quarantine_decided_count,quarantine_open_count,governance_admitted_component_count,
               production_migration_enabled,status,version,assessed_at,created_at,updated_at)
            VALUES
              (#{governanceRunId},#{tenantId},#{sourceMigrationRunId},#{policyVersion},#{evidenceRef},
               #{governanceEvidenceHash},#{sourceComponentCount},#{sourceReferencePresentCount},
               #{currentReferenceObservedCount},#{historicalIdentityQualifiedCount},#{identityBlockedCount},
               #{fundingQualifiedCount},#{fundingBlockedCount},#{sourceQuarantineCount},
               #{quarantineDecidedCount},#{quarantineOpenCount},#{governanceAdmittedComponentCount},
               #{productionMigrationEnabled},#{status},#{version},#{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertRun(LegacyTradeBenefitGovernanceRunDO value);

    @Insert("""
            INSERT INTO cloudmold_order_benefit_governance_component
              (component_governance_id,tenant_id,governance_run_id,source_migration_run_id,component_id,
               candidate_id,legacy_order_id,component_type,component_amount_minor,source_reference,
               source_component_evidence_hash,current_reference_status,observed_source_table,observed_source_id,
               observed_source_created_at,observed_source_updated_at,observed_source_status,
               observed_source_deleted,observed_source_spu_id,current_reference_snapshot_hash,
               identity_qualification_id,historical_identity_status,funding_share_count,funding_amount_minor,
               funding_resolution_status,governance_status,blocker_codes,governance_admission_allowed,
               canonical_import_allowed,evidence_hash,version,assessed_at,created_at,updated_at)
            VALUES
              (#{componentGovernanceId},#{tenantId},#{governanceRunId},#{sourceMigrationRunId},#{componentId},
               #{candidateId},#{legacyOrderId},#{componentType},#{componentAmountMinor},#{sourceReference},
               #{sourceComponentEvidenceHash},#{currentReferenceStatus},#{observedSourceTable},#{observedSourceId},
               #{observedSourceCreatedAt},#{observedSourceUpdatedAt},#{observedSourceStatus},
               #{observedSourceDeleted},#{observedSourceSpuId},#{currentReferenceSnapshotHash},
               #{identityQualificationId},#{historicalIdentityStatus},#{fundingShareCount},#{fundingAmountMinor},
               #{fundingResolutionStatus},#{governanceStatus},CAST(#{blockerCodes} AS JSON),
               #{governanceAdmissionAllowed},#{canonicalImportAllowed},#{evidenceHash},#{version},
               #{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertComponent(LegacyTradeBenefitGovernanceComponentDO value);

    @Insert("""
            INSERT INTO cloudmold_order_benefit_governance_quarantine
              (quarantine_governance_id,tenant_id,governance_run_id,source_migration_run_id,candidate_id,
               legacy_order_id,legacy_order_no,source_candidate_evidence_hash,source_assessment_status,
               source_reason_codes,decision_id,decision_status,recommended_action,blocker_codes,
               canonical_import_allowed,evidence_hash,version,assessed_at,created_at,updated_at)
            VALUES
              (#{quarantineGovernanceId},#{tenantId},#{governanceRunId},#{sourceMigrationRunId},#{candidateId},
               #{legacyOrderId},#{legacyOrderNo},#{sourceCandidateEvidenceHash},#{sourceAssessmentStatus},
               CAST(#{sourceReasonCodes} AS JSON),#{decisionId},#{decisionStatus},#{recommendedAction},
               CAST(#{blockerCodes} AS JSON),#{canonicalImportAllowed},#{evidenceHash},#{version},
               #{assessedAt},#{createdAt},#{updatedAt})
            """)
    int insertQuarantine(LegacyTradeBenefitGovernanceQuarantineDO value);

    @Select("SELECT * FROM cloudmold_order_benefit_governance_run " +
            "WHERE tenant_id=#{tenantId} AND governance_run_id=#{runId}")
    LegacyTradeBenefitGovernanceRunDO selectRun(@Param("tenantId") Long tenantId,
                                                 @Param("runId") String runId);

    @Select("SELECT * FROM cloudmold_order_benefit_governance_component " +
            "WHERE tenant_id=#{tenantId} AND governance_run_id=#{runId} ORDER BY legacy_order_id,component_type")
    List<LegacyTradeBenefitGovernanceComponentDO> selectComponents(@Param("tenantId") Long tenantId,
                                                                    @Param("runId") String runId);

    @Select("SELECT * FROM cloudmold_order_benefit_governance_quarantine " +
            "WHERE tenant_id=#{tenantId} AND governance_run_id=#{runId} ORDER BY legacy_order_id")
    List<LegacyTradeBenefitGovernanceQuarantineDO> selectQuarantines(@Param("tenantId") Long tenantId,
                                                                      @Param("runId") String runId);
}
