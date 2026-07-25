package cn.iocoder.yudao.module.cloudmold.quality.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.service.query.QualityWorkItem;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QualityMapper {
    @Insert("""
            INSERT INTO cloudmold_quality_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_type,aggregate_id,result_json
            FROM cloudmold_quality_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_quality_operation
            SET status=10,aggregate_type=#{aggregateType},aggregate_id=#{aggregateId},
                result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId,
                               @Param("tenantId") Long tenantId,
                               @Param("aggregateType") String aggregateType,
                               @Param("aggregateId") String aggregateId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_quality_standard
              (standard_id,tenant_id,standard_code,category_code,brand_code,applicable_sku_id,
               draft_content_sha256,status,current_version,aggregate_version,created_at,updated_at)
            VALUES (#{standardId},#{tenantId},#{standardCode},#{categoryCode},#{brandCode},
                    #{applicableSkuId},#{draftContentSha256},#{status},#{currentVersion},
                    #{aggregateVersion},#{createdAt},#{updatedAt})
            """)
    int insertStandard(Standard value);

    @Select("""
            SELECT standard_id,tenant_id,standard_code,category_code,brand_code,applicable_sku_id,
                   draft_content_sha256,status,current_version,aggregate_version,created_at,updated_at
            FROM cloudmold_quality_standard
            WHERE tenant_id=#{tenantId} AND standard_id=#{standardId} FOR UPDATE
            """)
    Standard selectStandardForUpdate(@Param("tenantId") Long tenantId,
                                     @Param("standardId") String standardId);

    @Update("""
            UPDATE cloudmold_quality_standard
            SET status='PUBLISHED',current_version=current_version+1,
                aggregate_version=aggregate_version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND standard_id=#{standardId}
              AND status='DRAFT' AND aggregate_version=#{expectedVersion}
            """)
    int publishStandard(@Param("tenantId") Long tenantId,
                        @Param("standardId") String standardId,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_quality_standard_version
              (standard_version_id,tenant_id,standard_id,standard_version,content_sha256,
               approver_principal_id,effective_at,created_at)
            VALUES (#{standardVersionId},#{tenantId},#{standardId},#{standardVersion},#{contentSha256},
                    #{approverPrincipalId},#{effectiveAt},#{createdAt})
            """)
    int insertStandardVersion(StandardVersion value);

    @Select("""
            SELECT standard_version_id,tenant_id,standard_id,standard_version,content_sha256,
                   approver_principal_id,effective_at,created_at
            FROM cloudmold_quality_standard_version
            WHERE tenant_id=#{tenantId} AND standard_id=#{standardId}
              AND standard_version=#{standardVersion}
            """)
    StandardVersion selectStandardVersion(@Param("tenantId") Long tenantId,
                                          @Param("standardId") String standardId,
                                          @Param("standardVersion") Long standardVersion);

    @Insert("""
            INSERT INTO cloudmold_authenticator_certification
              (certification_id,tenant_id,authenticator_principal_id,standard_id,certification_level,
               effective_from,effective_to,evidence_sha256,status,version,created_at,updated_at)
            VALUES (#{certificationId},#{tenantId},#{authenticatorPrincipalId},#{standardId},
                    #{certificationLevel},#{effectiveFrom},#{effectiveTo},#{evidenceSha256},
                    #{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertCertification(Certification value);

    @Select("""
            SELECT certification_id,tenant_id,authenticator_principal_id,standard_id,certification_level,
                   effective_from,effective_to,evidence_sha256,status,version,revoked_at,revoke_reason_code,
                   created_at,updated_at
            FROM cloudmold_authenticator_certification
            WHERE tenant_id=#{tenantId} AND certification_id=#{certificationId} FOR UPDATE
            """)
    Certification selectCertificationForUpdate(@Param("tenantId") Long tenantId,
                                                @Param("certificationId") String certificationId);

    @Select("""
            SELECT certification_id,tenant_id,authenticator_principal_id,standard_id,certification_level,
                   effective_from,effective_to,evidence_sha256,status,version,revoked_at,revoke_reason_code,
                   created_at,updated_at
            FROM cloudmold_authenticator_certification
            WHERE tenant_id=#{tenantId} AND authenticator_principal_id=#{principalId}
              AND standard_id=#{standardId} AND status='ACTIVE'
              AND effective_from <= #{businessDate} AND effective_to >= #{businessDate}
            ORDER BY version DESC LIMIT 1
            """)
    Certification selectActiveCertification(@Param("tenantId") Long tenantId,
                                             @Param("principalId") String principalId,
                                             @Param("standardId") String standardId,
                                             @Param("businessDate") LocalDate businessDate);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_authenticator_certification
            WHERE tenant_id=#{tenantId}
              AND authenticator_principal_id=#{principalId}
              AND standard_id=#{standardId}
              AND status='ACTIVE'
              AND effective_from <= #{effectiveTo}
              AND effective_to >= #{effectiveFrom}
            """)
    long countOverlappingActiveCertification(@Param("tenantId") Long tenantId,
                                             @Param("principalId") String principalId,
                                             @Param("standardId") String standardId,
                                             @Param("effectiveFrom") LocalDate effectiveFrom,
                                             @Param("effectiveTo") LocalDate effectiveTo);

    @Update("""
            UPDATE cloudmold_authenticator_certification
            SET status='REVOKED',revoke_reason_code=#{reasonCode},revoked_at=#{now},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND certification_id=#{certificationId}
              AND status='ACTIVE' AND version=#{expectedVersion}
            """)
    int revokeCertification(@Param("tenantId") Long tenantId,
                            @Param("certificationId") String certificationId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("reasonCode") String reasonCode,
                            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inspection_task
              (task_id,tenant_id,standard_id,standard_version,standard_version_id,subject_type,subject_ref,
               canonical_sku_id,lot_id,warehouse_id,priority,status,version,created_at,updated_at)
            VALUES (#{taskId},#{tenantId},#{standardId},#{standardVersion},#{standardVersionId},#{subjectType},
                    #{subjectRef},#{canonicalSkuId},#{lotId},#{warehouseId},#{priority},#{status},#{version},
                    #{createdAt},#{updatedAt})
            """)
    int insertInspectionTask(InspectionTask value);

    @Select("""
            SELECT task_id,tenant_id,standard_id,standard_version,standard_version_id,subject_type,subject_ref,
                   canonical_sku_id,lot_id,warehouse_id,priority,status,authenticator_principal_id,decision,
                   defect_code,evidence_ref,recheck_reason_code,secondary_authenticator_principal_id,
                   secondary_decision,secondary_defect_code,secondary_evidence_ref,
                   adjudicator_principal_id,ground_truth_decision,ground_truth_defect_code,
                   ground_truth_evidence_ref,version,assigned_at,started_at,decided_at,rechecked_at,
                   adjudicated_at,completed_at,created_at,updated_at
            FROM cloudmold_inspection_task
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} FOR UPDATE
            """)
    InspectionTask selectInspectionTaskForUpdate(@Param("tenantId") Long tenantId,
                                                 @Param("taskId") String taskId);

    @Update("""
            UPDATE cloudmold_inspection_task
            SET status=#{afterStatus},authenticator_principal_id=#{authenticatorPrincipalId},
                decision=#{decision},defect_code=#{defectCode},evidence_ref=#{evidenceRef},
                recheck_reason_code=#{recheckReasonCode},assigned_at=COALESCE(assigned_at,#{assignedAt}),
                started_at=COALESCE(started_at,#{startedAt}),decided_at=COALESCE(decided_at,#{decidedAt}),
                completed_at=COALESCE(completed_at,#{completedAt}),version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId}
              AND status=#{beforeStatus} AND version=#{expectedVersion}
            """)
    int transitionInspectionTask(@Param("tenantId") Long tenantId,
                                 @Param("taskId") String taskId,
                                 @Param("expectedVersion") Long expectedVersion,
                                 @Param("beforeStatus") String beforeStatus,
                                 @Param("afterStatus") String afterStatus,
                                 @Param("authenticatorPrincipalId") String authenticatorPrincipalId,
                                 @Param("decision") String decision,
                                 @Param("defectCode") String defectCode,
                                 @Param("evidenceRef") String evidenceRef,
                                 @Param("recheckReasonCode") String recheckReasonCode,
                                 @Param("assignedAt") LocalDateTime assignedAt,
                                 @Param("startedAt") LocalDateTime startedAt,
                                 @Param("decidedAt") LocalDateTime decidedAt,
                                 @Param("completedAt") LocalDateTime completedAt,
                                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inspection_task
            SET status='RECHECK_REQUIRED',
                secondary_authenticator_principal_id=#{secondaryPrincipalId},
                recheck_reason_code=#{reasonCode},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId}
              AND status='DECIDED' AND version=#{expectedVersion}
            """)
    int assignRecheckReviewer(@Param("tenantId") Long tenantId,
                              @Param("taskId") String taskId,
                              @Param("expectedVersion") Long expectedVersion,
                              @Param("secondaryPrincipalId") String secondaryPrincipalId,
                              @Param("reasonCode") String reasonCode,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inspection_task
            SET status=#{afterStatus},secondary_decision=#{secondaryDecision},
                secondary_defect_code=#{secondaryDefectCode},
                secondary_evidence_ref=#{secondaryEvidenceRef},
                ground_truth_decision=#{groundTruthDecision},
                ground_truth_defect_code=#{groundTruthDefectCode},
                ground_truth_evidence_ref=#{groundTruthEvidenceRef},
                rechecked_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId}
              AND status='RECHECK_REQUIRED' AND version=#{expectedVersion}
            """)
    int submitRecheckDecision(@Param("tenantId") Long tenantId,
                              @Param("taskId") String taskId,
                              @Param("expectedVersion") Long expectedVersion,
                              @Param("afterStatus") String afterStatus,
                              @Param("secondaryDecision") String secondaryDecision,
                              @Param("secondaryDefectCode") String secondaryDefectCode,
                              @Param("secondaryEvidenceRef") String secondaryEvidenceRef,
                              @Param("groundTruthDecision") String groundTruthDecision,
                              @Param("groundTruthDefectCode") String groundTruthDefectCode,
                              @Param("groundTruthEvidenceRef") String groundTruthEvidenceRef,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inspection_task
            SET status='DECIDED',adjudicator_principal_id=#{adjudicatorPrincipalId},
                ground_truth_decision=#{groundTruthDecision},
                ground_truth_defect_code=#{groundTruthDefectCode},
                ground_truth_evidence_ref=#{groundTruthEvidenceRef},
                adjudicated_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId}
              AND status='CONFLICTED' AND version=#{expectedVersion}
            """)
    int adjudicateInspectionTask(@Param("tenantId") Long tenantId,
                                 @Param("taskId") String taskId,
                                 @Param("expectedVersion") Long expectedVersion,
                                 @Param("adjudicatorPrincipalId") String adjudicatorPrincipalId,
                                 @Param("groundTruthDecision") String groundTruthDecision,
                                 @Param("groundTruthDefectCode") String groundTruthDefectCode,
                                 @Param("groundTruthEvidenceRef") String groundTruthEvidenceRef,
                                 @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inspection_task_history
              (tenant_id,task_id,task_version,previous_status,current_status,actor_principal_id,
               reason_code,operation_id,occurred_at,created_at)
            VALUES (#{tenantId},#{taskId},#{taskVersion},#{previousStatus},#{currentStatus},
                    #{actorPrincipalId},#{reasonCode},#{operationId},#{occurredAt},#{createdAt})
            """)
    int insertTaskHistory(TaskHistory value);

    @Insert("""
            INSERT INTO cloudmold_quality_capa
              (capa_id,tenant_id,inspection_task_id,root_cause_code,owner_principal_id,due_date,
               status,version,opened_at,created_at,updated_at)
            VALUES (#{capaId},#{tenantId},#{inspectionTaskId},#{rootCauseCode},#{ownerPrincipalId},
                    #{dueDate},#{status},#{version},#{openedAt},#{createdAt},#{updatedAt})
            """)
    int insertCapa(Capa value);

    @Select("""
            SELECT capa_id,tenant_id,inspection_task_id,root_cause_code,owner_principal_id,due_date,
                   status,effectiveness_evidence_ref,version,opened_at,resolved_at,created_at,updated_at
            FROM cloudmold_quality_capa
            WHERE tenant_id=#{tenantId} AND capa_id=#{capaId} FOR UPDATE
            """)
    Capa selectCapaForUpdate(@Param("tenantId") Long tenantId,
                             @Param("capaId") String capaId);

    @Update("""
            UPDATE cloudmold_quality_capa
            SET status='VERIFIED',effectiveness_evidence_ref=#{evidenceRef},resolved_at=#{now},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND capa_id=#{capaId}
              AND status='OPEN' AND version=#{expectedVersion}
            """)
    int resolveCapa(@Param("tenantId") Long tenantId,
                    @Param("capaId") String capaId,
                    @Param("expectedVersion") Long expectedVersion,
                    @Param("evidenceRef") String evidenceRef,
                    @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_quality_recall_action
              (recall_action_id,tenant_id,inspection_task_id,canonical_sku_id,lot_id,warehouse_id,
               reason_code,status,owner_principal_id,version,opened_at,created_at,updated_at)
            VALUES (#{recallActionId},#{tenantId},#{inspectionTaskId},#{canonicalSkuId},#{lotId},
                    #{warehouseId},#{reasonCode},#{status},#{ownerPrincipalId},#{version},
                    #{openedAt},#{createdAt},#{updatedAt})
            """)
    int insertRecallAction(RecallAction value);

    @Select("""
            SELECT recall_action_id,tenant_id,inspection_task_id,canonical_sku_id,lot_id,warehouse_id,
                   reason_code,status,owner_principal_id,resolution_code,version,opened_at,
                   acknowledged_at,resolved_at,created_at,updated_at
            FROM cloudmold_quality_recall_action
            WHERE tenant_id=#{tenantId} AND recall_action_id=#{recallActionId} FOR UPDATE
            """)
    RecallAction selectRecallActionForUpdate(@Param("tenantId") Long tenantId,
                                             @Param("recallActionId") String recallActionId);

    @Update("""
            UPDATE cloudmold_quality_recall_action
            SET status='ACKNOWLEDGED',owner_principal_id=#{ownerPrincipalId},
                acknowledged_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND recall_action_id=#{recallActionId}
              AND status='OPEN' AND version=#{expectedVersion}
            """)
    int acknowledgeRecallAction(@Param("tenantId") Long tenantId,
                                @Param("recallActionId") String recallActionId,
                                @Param("expectedVersion") Long expectedVersion,
                                @Param("ownerPrincipalId") String ownerPrincipalId,
                                @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_quality_recall_action
            SET status='RESOLVED',resolution_code=#{resolutionCode},resolved_at=#{now},
                owner_principal_id=COALESCE(owner_principal_id,#{ownerPrincipalId}),
                acknowledged_at=COALESCE(acknowledged_at,#{now}),
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND recall_action_id=#{recallActionId}
              AND status IN ('OPEN','ACKNOWLEDGED') AND version=#{expectedVersion}
            """)
    int resolveRecallAction(@Param("tenantId") Long tenantId,
                            @Param("recallActionId") String recallActionId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("ownerPrincipalId") String ownerPrincipalId,
                            @Param("resolutionCode") String resolutionCode,
                            @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT COUNT(*) FROM (
                SELECT 'STANDARD' item_type,status FROM cloudmold_quality_standard WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'CERTIFICATION',status FROM cloudmold_authenticator_certification WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'INSPECTION_TASK',status FROM cloudmold_inspection_task WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'CAPA',status FROM cloudmold_quality_capa WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'RECALL_ACTION',status FROM cloudmold_quality_recall_action WHERE tenant_id=#{tenantId}
            ) work_item WHERE 1=1
            <if test="itemType != null">AND work_item.item_type=#{itemType}</if>
            <if test="status != null">AND work_item.status=#{status}</if>
            </script>
            """)
    long countWorkItems(@Param("tenantId") Long tenantId,
                        @Param("itemType") String itemType,
                        @Param("status") String status);

    @Select("""
            <script>
            SELECT * FROM (
                SELECT standard_id aggregate_id,'STANDARD' item_type,standard_code code,category_code related_ref,
                       status,aggregate_version,NULL due_date,updated_at
                FROM cloudmold_quality_standard WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT certification_id,'CERTIFICATION',certification_level,authenticator_principal_id,
                       status,version,effective_to,updated_at
                FROM cloudmold_authenticator_certification WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT task_id,'INSPECTION_TASK',priority,subject_ref,status,version,NULL,updated_at
                FROM cloudmold_inspection_task WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT capa_id,'CAPA',root_cause_code,inspection_task_id,status,version,due_date,updated_at
                FROM cloudmold_quality_capa WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT recall_action_id,'RECALL_ACTION',reason_code,inspection_task_id,status,version,
                       DATE(opened_at),updated_at
                FROM cloudmold_quality_recall_action WHERE tenant_id=#{tenantId}
            ) work_item WHERE 1=1
            <if test="itemType != null">AND work_item.item_type=#{itemType}</if>
            <if test="status != null">AND work_item.status=#{status}</if>
            ORDER BY work_item.updated_at DESC,work_item.aggregate_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<QualityWorkItem> selectWorkItems(@Param("tenantId") Long tenantId,
                                          @Param("itemType") String itemType,
                                          @Param("status") String status,
                                          @Param("offset") long offset,
                                          @Param("limit") int limit);

    @Select("""
            SELECT canonical_sku_id,
                   CASE
                       WHEN EXISTS (
                           SELECT 1
                           FROM cloudmold_quality_recall_action recall_action
                           WHERE recall_action.tenant_id=cloudmold_inspection_task.tenant_id
                             AND recall_action.inspection_task_id=cloudmold_inspection_task.task_id
                             AND recall_action.status IN ('OPEN','ACKNOWLEDGED')
                       ) THEN 'RECALLED'
                       WHEN COALESCE(ground_truth_decision,secondary_decision,decision)='PASS' THEN 'VERIFIED'
                       WHEN COALESCE(ground_truth_decision,secondary_decision,decision)='FAIL' THEN 'REJECTED'
                       ELSE 'UNVERIFIED'
                   END AS status,
                   task_id AS inspection_task_id,
                   COALESCE(ground_truth_decision,secondary_decision,decision) AS decision,
                   COALESCE(ground_truth_evidence_ref,secondary_evidence_ref,evidence_ref) AS evidence_token,
                   COALESCE(adjudicated_at,rechecked_at,decided_at,completed_at) AS inspected_at,
                   version AS aggregate_version
            FROM cloudmold_inspection_task
            WHERE tenant_id=#{tenantId} AND canonical_sku_id=#{canonicalSkuId}
              AND status IN ('DECIDED','COMPLETED')
            ORDER BY COALESCE(adjudicated_at,rechecked_at,decided_at,completed_at) DESC,task_id DESC
            LIMIT 1
            """)
    cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView selectLatestConsumerEvidence(
            @Param("tenantId") Long tenantId,
            @Param("canonicalSkuId") String canonicalSkuId);
}
