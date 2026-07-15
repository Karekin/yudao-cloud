package cn.iocoder.yudao.module.cloudmold.risk.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

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

    @Insert("""
            INSERT INTO cloudmold_risk_status_history
              (tenant_id,aggregate_type,aggregate_id,aggregate_version,previous_status,current_status,operation_id,
               reason_code,occurred_at,created_at)
            VALUES (#{tenantId},#{aggregateType},#{aggregateId},#{aggregateVersion},#{previousStatus},#{currentStatus},
                    #{operationId},#{reasonCode},#{occurredAt},#{createdAt})
            """) int insertHistory(StatusHistory value);
}
