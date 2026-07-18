package cn.iocoder.yudao.module.cloudmold.risk.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RiskStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_risk_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()") Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_risk_operation WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_risk_operation SET status=10,aggregate_id=#{aggregateId},
              result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_risk_policy
              (policy_id,tenant_id,policy_code,policy_name,status,current_version,created_at,updated_at)
            VALUES (#{policyId},#{tenantId},#{policyCode},#{policyName},#{status},#{currentVersion},#{createdAt},#{updatedAt})
            """) int insertPolicy(Policy value);

    @Select("""
            SELECT policy_id,tenant_id,policy_code,policy_name,status,current_version,created_at,updated_at
            FROM cloudmold_risk_policy WHERE tenant_id=#{tenantId} AND policy_id=#{policyId} FOR UPDATE
            """) Policy selectPolicyForUpdate(@Param("tenantId") Long tenantId, @Param("policyId") String policyId);

    @Select("""
            SELECT policy_id,tenant_id,policy_code,policy_name,status,current_version,created_at,updated_at
            FROM cloudmold_risk_policy WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}
            """) Policy selectPolicy(@Param("tenantId") Long tenantId, @Param("policyId") String policyId);

    @Update("""
            UPDATE cloudmold_risk_policy SET status='PUBLISHED',current_version=current_version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND policy_id=#{policyId} AND current_version=#{expectedVersion}
            """) int publishPolicy(@Param("tenantId") Long tenantId, @Param("policyId") String policyId,
                                     @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_risk_policy_version
              (policy_version_id,tenant_id,policy_id,policy_version,rules_sha256,approved_by_principal_id,
               effective_from,published_at)
            VALUES (#{policyVersionId},#{tenantId},#{policyId},#{policyVersion},#{rulesSha256},
                    #{approvedByPrincipalId},#{effectiveFrom},#{publishedAt})
            """) int insertPolicyVersion(PolicyVersion value);

    @Insert("""
            INSERT INTO cloudmold_risk_policy_rule
              (rule_id,tenant_id,policy_version_id,policy_id,policy_version,rule_sequence,rule_code,signal_type,
               operator_code,threshold_value,outcome_code,explanation_template,created_at)
            VALUES (#{ruleId},#{tenantId},#{policyVersionId},#{policyId},#{policyVersion},#{ruleSequence},#{ruleCode},
                    #{signalType},#{operatorCode},#{thresholdValue},#{outcomeCode},#{explanationTemplate},#{createdAt})
            """) int insertPolicyRule(PolicyRule value);

    @Insert("""
            INSERT INTO cloudmold_risk_intelligence_taxonomy
              (taxonomy_id,tenant_id,event_code,status,current_definition_version,version,retired_at,created_at,updated_at)
            VALUES (#{taxonomyId},#{tenantId},#{eventCode},#{status},#{currentDefinitionVersion},#{version},#{retiredAt},
                    #{createdAt},#{updatedAt})
            """) int insertIntelligenceTaxonomy(IntelligenceTaxonomy value);

    @Select("""
            SELECT taxonomy_id,tenant_id,event_code,status,current_definition_version,version,retired_at,
                   created_at,updated_at
            FROM cloudmold_risk_intelligence_taxonomy
            WHERE tenant_id=#{tenantId} AND taxonomy_id=#{taxonomyId} FOR UPDATE
            """) IntelligenceTaxonomy selectIntelligenceTaxonomyForUpdate(
            @Param("tenantId") Long tenantId, @Param("taxonomyId") String taxonomyId);

    @Select("""
            SELECT taxonomy_id,tenant_id,event_code,status,current_definition_version,version,retired_at,
                   created_at,updated_at
            FROM cloudmold_risk_intelligence_taxonomy
            WHERE tenant_id=#{tenantId} AND taxonomy_id=#{taxonomyId}
            """) IntelligenceTaxonomy selectIntelligenceTaxonomy(
            @Param("tenantId") Long tenantId, @Param("taxonomyId") String taxonomyId);

    @Insert("""
            INSERT INTO cloudmold_risk_intelligence_taxonomy_version
              (taxonomy_version_id,tenant_id,taxonomy_id,definition_version,level_count,levels_sha256,
               approved_by_principal_id,source_system,source_table,source_record_key,source_version,
               source_observed_at,source_evidence_ref,source_evidence_sha256,effective_from,published_at)
            VALUES (#{taxonomyVersionId},#{tenantId},#{taxonomyId},#{definitionVersion},#{levelCount},#{levelsSha256},
                    #{approvedByPrincipalId},#{sourceSystem},#{sourceTable},#{sourceRecordKey},#{sourceVersion},
                    #{sourceObservedAt},#{sourceEvidenceRef},#{sourceEvidenceSha256},#{effectiveFrom},#{publishedAt})
            """) int insertIntelligenceTaxonomyVersion(IntelligenceTaxonomyVersion value);

    @Select("""
            SELECT taxonomy_version_id,tenant_id,taxonomy_id,definition_version,level_count,levels_sha256,
                   approved_by_principal_id,source_system,source_table,source_record_key,source_version,
                   source_observed_at,source_evidence_ref,source_evidence_sha256,effective_from,published_at
            FROM cloudmold_risk_intelligence_taxonomy_version
            WHERE tenant_id=#{tenantId} AND taxonomy_id=#{taxonomyId} AND definition_version=#{definitionVersion}
            """) IntelligenceTaxonomyVersion selectIntelligenceTaxonomyVersion(
            @Param("tenantId") Long tenantId, @Param("taxonomyId") String taxonomyId,
            @Param("definitionVersion") Long definitionVersion);

    @Insert("""
            INSERT INTO cloudmold_risk_intelligence_taxonomy_level
              (level_definition_id,tenant_id,taxonomy_version_id,taxonomy_id,definition_version,level_sequence,
               level_code,created_at)
            VALUES (#{levelDefinitionId},#{tenantId},#{taxonomyVersionId},#{taxonomyId},#{definitionVersion},
                    #{levelSequence},#{levelCode},#{createdAt})
            """) int insertIntelligenceTaxonomyLevel(IntelligenceTaxonomyLevel value);

    @Select("""
            SELECT level_code FROM cloudmold_risk_intelligence_taxonomy_level
            WHERE tenant_id=#{tenantId} AND taxonomy_id=#{taxonomyId} AND definition_version=#{definitionVersion}
            ORDER BY level_sequence
            """) List<String> selectIntelligenceTaxonomyLevelCodes(
            @Param("tenantId") Long tenantId, @Param("taxonomyId") String taxonomyId,
            @Param("definitionVersion") Long definitionVersion);

    @Update("""
            UPDATE cloudmold_risk_intelligence_taxonomy
            SET status='PUBLISHED',current_definition_version=current_definition_version+1,
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND taxonomy_id=#{taxonomyId} AND version=#{expectedVersion}
              AND status IN ('DRAFT','PUBLISHED')
            """) int publishIntelligenceTaxonomy(
            @Param("tenantId") Long tenantId, @Param("taxonomyId") String taxonomyId,
            @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_risk_intelligence_taxonomy
            SET status='RETIRED',version=version+1,retired_at=#{retiredAt},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND taxonomy_id=#{taxonomyId} AND version=#{expectedVersion}
              AND status='PUBLISHED'
            """) int retireIntelligenceTaxonomy(
            @Param("tenantId") Long tenantId, @Param("taxonomyId") String taxonomyId,
            @Param("expectedVersion") Long expectedVersion, @Param("retiredAt") LocalDateTime retiredAt,
            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_risk_intelligence_taxonomy_retirement
              (retirement_id,tenant_id,taxonomy_id,taxonomy_version,retired_by_principal_id,reason_code,
               source_system,source_table,source_record_key,source_version,source_observed_at,source_evidence_ref,
               source_evidence_sha256,retired_at,created_at)
            VALUES (#{retirementId},#{tenantId},#{taxonomyId},#{taxonomyVersion},#{retiredByPrincipalId},#{reasonCode},
                    #{sourceSystem},#{sourceTable},#{sourceRecordKey},#{sourceVersion},#{sourceObservedAt},
                    #{sourceEvidenceRef},#{sourceEvidenceSha256},#{retiredAt},#{createdAt})
            """) int insertIntelligenceTaxonomyRetirement(IntelligenceTaxonomyRetirement value);

    @Select("""
            SELECT t.taxonomy_id,v.taxonomy_version_id,v.definition_version,t.event_code,l.level_code,
                   v.levels_sha256,v.effective_from
            FROM cloudmold_risk_intelligence_taxonomy t
            JOIN cloudmold_risk_intelligence_taxonomy_version v
              ON v.tenant_id=t.tenant_id AND v.taxonomy_id=t.taxonomy_id
            JOIN cloudmold_risk_intelligence_taxonomy_level l
              ON l.tenant_id=v.tenant_id AND l.taxonomy_version_id=v.taxonomy_version_id
            WHERE t.tenant_id=#{tenantId} AND t.taxonomy_id=#{taxonomyId}
              AND v.definition_version=#{definitionVersion} AND t.event_code=#{eventCode}
              AND l.level_code=#{levelCode} AND v.effective_from <= #{observedAt}
              AND (t.retired_at IS NULL OR #{observedAt} < t.retired_at)
              AND NOT EXISTS (
                  SELECT 1 FROM cloudmold_risk_intelligence_taxonomy_version newer
                  WHERE newer.tenant_id=v.tenant_id AND newer.taxonomy_id=v.taxonomy_id
                    AND newer.effective_from <= #{observedAt}
                    AND (newer.effective_from > v.effective_from
                         OR (newer.effective_from=v.effective_from
                             AND newer.definition_version > v.definition_version))
              )
            FOR SHARE
            """) IntelligenceTaxonomyReferenceRow selectEffectiveIntelligenceTaxonomyReference(
            @Param("tenantId") Long tenantId, @Param("taxonomyId") String taxonomyId,
            @Param("definitionVersion") Long definitionVersion, @Param("eventCode") String eventCode,
            @Param("levelCode") String levelCode, @Param("observedAt") LocalDateTime observedAt);

    @Insert("""
            INSERT INTO cloudmold_risk_signal
              (signal_id,tenant_id,subject_principal_id,policy_id,policy_version,signal_type,severity,evidence_ref,
               occurred_at,created_at)
            VALUES (#{signalId},#{tenantId},#{subjectPrincipalId},#{policyId},#{policyVersion},#{signalType},#{severity},
                    #{evidenceRef},#{occurredAt},#{createdAt})
            """) int insertSignal(Signal value);

    @Select("""
            SELECT relation_id,tenant_id,subject_principal_id,related_principal_id,medium_type,medium_token,key_version,
                   first_seen_at,last_seen_at,confidence_basis_points,created_at
            FROM cloudmold_risk_relation_edge
            WHERE tenant_id=#{tenantId} AND subject_principal_id=#{subjectPrincipalId}
              AND related_principal_id=#{relatedPrincipalId} AND medium_type=#{mediumType}
              AND medium_token=#{mediumToken} AND key_version=#{keyVersion}
            """)
    Relation selectRelationByKey(@Param("tenantId") Long tenantId,
                                 @Param("subjectPrincipalId") String subjectPrincipalId,
                                 @Param("relatedPrincipalId") String relatedPrincipalId,
                                 @Param("mediumType") String mediumType, @Param("mediumToken") String mediumToken,
                                 @Param("keyVersion") Integer keyVersion);

    @Select("""
            SELECT relation_id,tenant_id,subject_principal_id,related_principal_id,medium_type,medium_token,key_version,
                   first_seen_at,last_seen_at,confidence_basis_points,created_at
            FROM cloudmold_risk_relation_edge WHERE tenant_id=#{tenantId} AND relation_id=#{relationId}
            """) Relation selectRelation(@Param("tenantId") Long tenantId, @Param("relationId") String relationId);

    @Insert("""
            INSERT INTO cloudmold_risk_relation_edge
              (relation_id,tenant_id,subject_principal_id,related_principal_id,medium_type,medium_token,key_version,
               first_seen_at,last_seen_at,confidence_basis_points,created_at)
            VALUES (#{relationId},#{tenantId},#{subjectPrincipalId},#{relatedPrincipalId},#{mediumType},#{mediumToken},
                    #{keyVersion},#{firstSeenAt},#{lastSeenAt},#{confidenceBasisPoints},#{createdAt})
            """) int insertRelation(Relation value);

    @Insert("""
            INSERT INTO cloudmold_risk_cluster
              (cluster_id,tenant_id,cluster_code,status,risk_level,member_count,edge_count,version,created_at,updated_at)
            VALUES (#{clusterId},#{tenantId},#{clusterCode},#{status},#{riskLevel},#{memberCount},#{edgeCount},#{version},
                    #{createdAt},#{updatedAt})
            """) int insertCluster(Cluster value);

    @Select("""
            SELECT cluster_id,tenant_id,cluster_code,status,risk_level,member_count,edge_count,version,created_at,updated_at
            FROM cloudmold_risk_cluster WHERE tenant_id=#{tenantId} AND cluster_id=#{clusterId} FOR UPDATE
            """) Cluster selectClusterForUpdate(@Param("tenantId") Long tenantId, @Param("clusterId") String clusterId);

    @Select("""
            SELECT cluster_id,tenant_id,cluster_code,status,risk_level,member_count,edge_count,version,created_at,updated_at
            FROM cloudmold_risk_cluster WHERE tenant_id=#{tenantId} AND cluster_id=#{clusterId}
            """) Cluster selectCluster(@Param("tenantId") Long tenantId, @Param("clusterId") String clusterId);

    @Insert("""
            INSERT INTO cloudmold_risk_cluster_member
              (cluster_member_id,tenant_id,cluster_id,member_type,member_ref,created_at)
            VALUES (#{clusterMemberId},#{tenantId},#{clusterId},#{memberType},#{memberRef},#{createdAt})
            """) int insertClusterMember(ClusterMember value);

    @Update("""
            UPDATE cloudmold_risk_cluster SET member_count=member_count+1,
              edge_count=edge_count+#{edgeDelta},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND cluster_id=#{clusterId} AND version=#{expectedVersion}
            """) int addClusterMember(@Param("tenantId") Long tenantId, @Param("clusterId") String clusterId,
                                        @Param("expectedVersion") Long expectedVersion,
                                        @Param("edgeDelta") Integer edgeDelta, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_risk_cluster SET status=#{after},risk_level=#{riskLevel},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND cluster_id=#{clusterId} AND status=#{before} AND version=#{expectedVersion}
            """) int transitionCluster(@Param("tenantId") Long tenantId, @Param("clusterId") String clusterId,
                                         @Param("expectedVersion") Long expectedVersion,
                                         @Param("before") String before, @Param("after") String after,
                                         @Param("riskLevel") String riskLevel, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_risk_review_case
              (case_id,tenant_id,cluster_id,status,reviewer_principal_id,version,created_at,updated_at)
            VALUES (#{caseId},#{tenantId},#{clusterId},#{status},#{reviewerPrincipalId},#{version},#{createdAt},#{updatedAt})
            """) int insertReviewCase(ReviewCase value);

    @Select("""
            SELECT case_id,tenant_id,cluster_id,status,reviewer_principal_id,version,created_at,updated_at
            FROM cloudmold_risk_review_case WHERE tenant_id=#{tenantId} AND case_id=#{caseId} FOR UPDATE
            """) ReviewCase selectReviewCaseForUpdate(@Param("tenantId") Long tenantId, @Param("caseId") String caseId);

    @Select("""
            SELECT case_id,tenant_id,cluster_id,status,reviewer_principal_id,version,created_at,updated_at
            FROM cloudmold_risk_review_case WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
            """) ReviewCase selectReviewCase(@Param("tenantId") Long tenantId, @Param("caseId") String caseId);

    @Update("""
            UPDATE cloudmold_risk_review_case SET status=#{after},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND case_id=#{caseId} AND status=#{before} AND version=#{expectedVersion}
            """) int transitionReviewCase(@Param("tenantId") Long tenantId, @Param("caseId") String caseId,
                                            @Param("expectedVersion") Long expectedVersion,
                                            @Param("before") String before, @Param("after") String after,
                                            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_risk_decision
              (decision_id,tenant_id,case_id,cluster_id,decision_type,reason_code,decided_by_principal_id,
               occurred_at,created_at)
            VALUES (#{decisionId},#{tenantId},#{caseId},#{clusterId},#{decisionType},#{reasonCode},
                    #{decidedByPrincipalId},#{occurredAt},#{createdAt})
            """) int insertDecision(Decision value);

    @Select("""
            SELECT decision_id,tenant_id,case_id,cluster_id,decision_type,reason_code,decided_by_principal_id,
                   occurred_at,created_at
            FROM cloudmold_risk_decision WHERE tenant_id=#{tenantId} AND decision_id=#{decisionId}
            """) Decision selectDecision(@Param("tenantId") Long tenantId, @Param("decisionId") String decisionId);

    @Insert("""
            INSERT INTO cloudmold_risk_feedback
              (feedback_id,tenant_id,decision_id,case_id,feedback_type,reason_code,recorded_by_principal_id,
               occurred_at,created_at)
            VALUES (#{feedbackId},#{tenantId},#{decisionId},#{caseId},#{feedbackType},#{reasonCode},
                    #{recordedByPrincipalId},#{occurredAt},#{createdAt})
            """) int insertFeedback(Feedback value);

    @Select("""
            SELECT order_id,tenant_id,payment_id,status,payable_amount_minor,currency_code
            FROM cloudmold_order_header WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            """)
    OrderReference selectOrderReference(@Param("tenantId") Long tenantId, @Param("orderId") String orderId);

    @Select("""
            SELECT payment_id,tenant_id,order_id,status,captured_amount_minor,refunded_amount_minor,currency_code,
                   provider_code,test_mode
            FROM cloudmold_payment WHERE tenant_id=#{tenantId} AND payment_id=#{paymentId}
            """)
    PaymentReference selectPaymentReference(@Param("tenantId") Long tenantId, @Param("paymentId") String paymentId);

    @Insert("""
            INSERT INTO cloudmold_risk_order_case
              (order_risk_case_id,tenant_id,case_id,order_id,payment_id,risk_type,reason_code,created_at)
            VALUES (#{orderRiskCaseId},#{tenantId},#{caseId},#{orderId},#{paymentId},#{riskType},#{reasonCode},
                    #{createdAt})
            """)
    int insertOrderRiskCase(OrderRiskCase value);

    @Select("""
            SELECT order_risk_case_id,tenant_id,case_id,order_id,payment_id,risk_type,reason_code,created_at
            FROM cloudmold_risk_order_case WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
            """)
    OrderRiskCase selectOrderRiskCaseByCaseId(@Param("tenantId") Long tenantId, @Param("caseId") String caseId);

    @Insert("""
            INSERT INTO cloudmold_risk_payment_dispute
              (dispute_id,tenant_id,order_id,payment_id,case_id,decision_id,dispute_type,status,reason_code,
               amount_minor,currency_code,external_ref,version,opened_at,resolved_at,created_at,updated_at)
            VALUES (#{disputeId},#{tenantId},#{orderId},#{paymentId},#{caseId},#{decisionId},#{disputeType},
                    #{status},#{reasonCode},#{amountMinor},#{currencyCode},#{externalRef},#{version},#{openedAt},
                    #{resolvedAt},#{createdAt},#{updatedAt})
            """)
    int insertPaymentDispute(PaymentDispute value);

    @Select("""
            SELECT dispute_id,tenant_id,order_id,payment_id,case_id,decision_id,dispute_type,status,reason_code,
                   amount_minor,currency_code,external_ref,version,opened_at,resolved_at,created_at,updated_at
            FROM cloudmold_risk_payment_dispute WHERE tenant_id=#{tenantId} AND dispute_id=#{disputeId} FOR UPDATE
            """)
    PaymentDispute selectPaymentDisputeForUpdate(@Param("tenantId") Long tenantId, @Param("disputeId") String disputeId);

    @Select("""
            SELECT dispute_id,tenant_id,order_id,payment_id,case_id,decision_id,dispute_type,status,reason_code,
                   amount_minor,currency_code,external_ref,version,opened_at,resolved_at,created_at,updated_at
            FROM cloudmold_risk_payment_dispute WHERE tenant_id=#{tenantId} AND dispute_id=#{disputeId}
            """)
    PaymentDispute selectPaymentDispute(@Param("tenantId") Long tenantId, @Param("disputeId") String disputeId);

    @Update("""
            UPDATE cloudmold_risk_payment_dispute
            SET decision_id=#{decisionId},status=#{after},reason_code=#{reasonCode},resolved_at=#{resolvedAt},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND dispute_id=#{disputeId} AND status=#{before}
              AND version=#{expectedVersion}
            """)
    int resolvePaymentDispute(@Param("tenantId") Long tenantId, @Param("disputeId") String disputeId,
                              @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                              @Param("after") String after, @Param("reasonCode") String reasonCode,
                              @Param("decisionId") String decisionId, @Param("resolvedAt") LocalDateTime resolvedAt,
                              @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_risk_loss_entry
              (loss_entry_id,tenant_id,order_id,payment_id,dispute_id,decision_id,entry_type,signed_amount_minor,
               currency_code,external_ref,occurred_at,created_at)
            VALUES (#{lossEntryId},#{tenantId},#{orderId},#{paymentId},#{disputeId},#{decisionId},#{entryType},
                    #{signedAmountMinor},#{currencyCode},#{externalRef},#{occurredAt},#{createdAt})
            """)
    int insertLossEntry(LossEntry value);

    @Insert("""
            INSERT INTO cloudmold_risk_status_history
              (tenant_id,aggregate_type,aggregate_id,aggregate_version,previous_status,current_status,operation_id,
               reason_code,occurred_at,created_at)
            VALUES (#{tenantId},#{aggregateType},#{aggregateId},#{aggregateVersion},#{previousStatus},#{currentStatus},
                    #{operationId},#{reasonCode},#{occurredAt},#{createdAt})
            """) int insertHistory(StatusHistory value);
}
