package cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.merchant.api.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MerchantStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_merchant_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_merchant_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    MerchantOperationDO selectOperationForUpdate(@Param("operationId") Long operationId,
                                                   @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_merchant_operation
            SET status=10,aggregate_id=#{aggregateId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_merchant_legal_entity
              (legal_entity_id,tenant_id,legal_name,registration_hash_token,business_license_token,status,version,
               created_at,updated_at)
            VALUES (#{legalEntityId},#{tenantId},#{legalName},#{registrationHashToken},#{businessLicenseToken},
                    #{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertLegalEntity(MerchantLegalEntityDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_onboarding_application
              (application_id,tenant_id,run_id,legal_entity_id,owner_principal_id,channel_code,external_shop_id,status,
               decision_reason,merchant_id,shop_id,owner_assignment_id,version,created_at,updated_at)
            VALUES (#{applicationId},#{tenantId},#{runId},#{legalEntityId},#{ownerPrincipalId},#{channelCode},
                    #{externalShopId},#{status},#{decisionReason},#{merchantId},#{shopId},#{ownerAssignmentId},
                    #{version},#{createdAt},#{updatedAt})
            """)
    int insertApplication(MerchantOnboardingApplicationDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_account
              (merchant_id,tenant_id,merchant_code,legal_entity_id,status,version,created_at,updated_at)
            VALUES (#{merchantId},#{tenantId},#{merchantCode},#{legalEntityId},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertMerchant(MerchantAccountDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_shop
              (shop_id,tenant_id,merchant_id,channel_code,external_shop_id,status,version,created_at,updated_at)
            VALUES (#{shopId},#{tenantId},#{merchantId},#{channelCode},#{externalShopId},#{status},#{version},
                    #{createdAt},#{updatedAt})
            """)
    int insertShop(MerchantShopDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_operator_assignment
              (assignment_id,tenant_id,merchant_id,shop_id,principal_id,role_code,status,version,valid_from,valid_to,
               created_at,updated_at)
            VALUES (#{assignmentId},#{tenantId},#{merchantId},#{shopId},#{principalId},#{roleCode},#{status},#{version},
                    #{validFrom},#{validTo},#{createdAt},#{updatedAt})
            """)
    int insertAssignment(MerchantOperatorAssignmentDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_status_history
              (tenant_id,aggregate_type,aggregate_id,aggregate_version,previous_status,current_status,operation_id,
               reason,occurred_at,created_at)
            VALUES (#{tenantId},#{aggregateType},#{aggregateId},#{aggregateVersion},#{previousStatus},#{currentStatus},
                    #{operationId},#{reason},#{occurredAt},#{createdAt})
            """)
    int insertHistory(MerchantStatusHistoryDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_source_mapping
              (mapping_id,tenant_id,source_system,source_type,source_id,target_type,target_id,legal_entity_id,
               merchant_id,shop_id,valid_from,valid_to,verification_ref,migration_run_id,status,version,
               created_at,updated_at)
            VALUES (#{mappingId},#{tenantId},#{sourceSystem},#{sourceType},#{sourceId},#{targetType},#{targetId},
                    #{legalEntityId},#{merchantId},#{shopId},#{validFrom},#{validTo},#{verificationRef},
                    #{migrationRunId},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertSourceMapping(MerchantSourceMappingDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_managed_admission
              (admission_id,tenant_id,application_id,merchant_id,shop_id,status,attribution_channel_code,
               attribution_source_system,attribution_source_type,attribution_source_id,attribution_reference,
               attribution_evidence_ref,diagnostic_id,inspection_task_id,final_review_id,version,created_at,updated_at)
            VALUES (#{admissionId},#{tenantId},#{applicationId},#{merchantId},#{shopId},#{status},
                    #{attributionChannelCode},#{attributionSourceSystem},#{attributionSourceType},
                    #{attributionSourceId},#{attributionReference},#{attributionEvidenceRef},#{diagnosticId},
                    #{inspectionTaskId},#{finalReviewId},#{version},#{createdAt},#{updatedAt})
            """)
    int insertManagedAdmission(MerchantManagedAdmissionDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_managed_invitation
              (invitation_id,tenant_id,invitation_code,recruiter_principal_id,attribution_source_system,
               attribution_source_type,attribution_source_id,attribution_reference,evidence_ref,used_admission_id,
               used_at,status,version,created_at,updated_at)
            VALUES (#{invitationId},#{tenantId},#{invitationCode},#{recruiterPrincipalId},
                    #{attributionSourceSystem},#{attributionSourceType},#{attributionSourceId},
                    #{attributionReference},#{evidenceRef},#{usedAdmissionId},#{usedAt},#{status},#{version},
                    #{createdAt},#{updatedAt})
            """)
    int insertManagedInvitation(MerchantManagedInvitationDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_managed_evidence_package
              (evidence_package_id,tenant_id,admission_id,package_ref,items_json,status,version,created_at,updated_at)
            VALUES (#{evidencePackageId},#{tenantId},#{admissionId},#{packageRef},CAST(#{itemsJson} AS JSON),
                    #{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertManagedEvidencePackage(MerchantManagedEvidencePackageDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_ai_diagnostic
              (diagnostic_id,tenant_id,admission_id,evidence_package_id,recommendation_code,recommendation_summary,
               evidence_ref,status,reviewer_principal_id,review_note,version,created_at,updated_at)
            VALUES (#{diagnosticId},#{tenantId},#{admissionId},#{evidencePackageId},#{recommendationCode},
                    #{recommendationSummary},#{evidenceRef},#{status},#{reviewerPrincipalId},#{reviewNote},
                    #{version},#{createdAt},#{updatedAt})
            """)
    int insertAiDiagnostic(MerchantAiDiagnosticDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_factory_inspection_task
              (inspection_task_id,tenant_id,admission_id,diagnostic_id,status,actor_principal_id,scheduled_at,
               evidence_ref,note,version,created_at,updated_at)
            VALUES (#{inspectionTaskId},#{tenantId},#{admissionId},#{diagnosticId},#{status},#{actorPrincipalId},
                    #{scheduledAt},#{evidenceRef},#{note},#{version},#{createdAt},#{updatedAt})
            """)
    int insertFactoryInspectionTask(MerchantFactoryInspectionTaskDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_buyer_assignment
              (buyer_assignment_id,tenant_id,admission_id,inspection_task_id,merchant_id,shop_id,buyer_tl_principal_id,
               buyer_principal_id,evidence_ref,status,version,created_at,updated_at)
            VALUES (#{buyerAssignmentId},#{tenantId},#{admissionId},#{inspectionTaskId},#{merchantId},#{shopId},
                    #{buyerTlPrincipalId},#{buyerPrincipalId},#{evidenceRef},#{status},#{version},#{createdAt},
                    #{updatedAt})
            """)
    int insertBuyerAssignment(MerchantBuyerAssignmentDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_grade_decision
              (grade_decision_id,tenant_id,merchant_id,shop_id,probation_assessment_id,scorecard_id,grade_code,
               transition_decision,decision_status,thresholds_config_ref,evidence_ref,entitlements_json,version,
               created_at,updated_at)
            VALUES (#{gradeDecisionId},#{tenantId},#{merchantId},#{shopId},#{probationAssessmentId},#{scorecardId},
                    #{gradeCode},#{transitionDecision},#{decisionStatus},#{thresholdsConfigRef},#{evidenceRef},
                    CAST(#{entitlementsJson} AS JSON),#{version},#{createdAt},#{updatedAt})
            """)
    int insertGradeDecision(MerchantGradeDecisionDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_probation_assessment
              (probation_assessment_id,tenant_id,admission_id,merchant_id,shop_id,assessment_status,
               thresholds_config_ref,gates_json,gate_count,evidence_ref,version,created_at,updated_at)
            VALUES (#{probationAssessmentId},#{tenantId},#{admissionId},#{merchantId},#{shopId},#{assessmentStatus},
                    #{thresholdsConfigRef},CAST(#{gatesJson} AS JSON),#{gateCount},#{evidenceRef},#{version},
                    #{createdAt},#{updatedAt})
            """)
    int insertProbationAssessment(MerchantProbationAssessmentDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_monthly_scorecard
              (scorecard_id,tenant_id,merchant_id,shop_id,scorecard_month,scorecard_status,transition_recommendation,
               thresholds_config_ref,items_json,item_count,red_line_count,remediation_failure_count,evidence_ref,
               version,created_at,updated_at)
            VALUES (#{scorecardId},#{tenantId},#{merchantId},#{shopId},#{scorecardMonth},#{scorecardStatus},
                    #{transitionRecommendation},#{thresholdsConfigRef},CAST(#{itemsJson} AS JSON),#{itemCount},
                    #{redLineCount},#{remediationFailureCount},#{evidenceRef},#{version},#{createdAt},#{updatedAt})
            """)
    int insertMonthlyScorecard(MerchantMonthlyScorecardDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_exit_decision
              (exit_decision_id,tenant_id,merchant_id,shop_id,admission_id,scorecard_id,reason_type,decision_status,
               evidence_ref,note,version,created_at,updated_at)
            VALUES (#{exitDecisionId},#{tenantId},#{merchantId},#{shopId},#{admissionId},#{scorecardId},
                    #{reasonType},#{decisionStatus},#{evidenceRef},#{note},#{version},#{createdAt},#{updatedAt})
            """)
    int insertExitDecision(MerchantExitDecisionDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_managed_final_review
              (final_review_id,tenant_id,admission_id,inspection_task_id,decision,evidence_ref,reviewer_principal_id,
               review_note,status,version,created_at,updated_at)
            VALUES (#{finalReviewId},#{tenantId},#{admissionId},#{inspectionTaskId},#{decision},#{evidenceRef},
                    #{reviewerPrincipalId},#{reviewNote},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertManagedFinalReview(MerchantManagedFinalReviewDO value);

    @Select("""
            SELECT application_id,tenant_id,run_id,legal_entity_id,owner_principal_id,channel_code,external_shop_id,
                   status,decision_reason,merchant_id,shop_id,owner_assignment_id,version,created_at,updated_at
            FROM cloudmold_merchant_onboarding_application
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}
            FOR UPDATE
            """)
    MerchantOnboardingApplicationDO selectApplicationForUpdate(@Param("tenantId") Long tenantId,
                                                                @Param("applicationId") String applicationId);

    @Select("""
            SELECT legal_entity_id,tenant_id,legal_name,registration_hash_token,business_license_token,status,version,
                   created_at,updated_at
            FROM cloudmold_merchant_legal_entity
            WHERE tenant_id=#{tenantId} AND legal_entity_id=#{legalEntityId}
            FOR UPDATE
            """)
    MerchantLegalEntityDO selectLegalEntityForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("legalEntityId") String legalEntityId);

    @Select("""
            SELECT merchant_id,tenant_id,merchant_code,legal_entity_id,status,version,created_at,updated_at
            FROM cloudmold_merchant_account
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId}
            FOR UPDATE
            """)
    MerchantAccountDO selectMerchantForUpdate(@Param("tenantId") Long tenantId,
                                               @Param("merchantId") String merchantId);

    @Select("""
            SELECT shop_id,tenant_id,merchant_id,channel_code,external_shop_id,status,version,created_at,updated_at
            FROM cloudmold_merchant_shop
            WHERE tenant_id=#{tenantId} AND shop_id=#{shopId}
            FOR UPDATE
            """)
    MerchantShopDO selectShopForUpdate(@Param("tenantId") Long tenantId, @Param("shopId") String shopId);

    @Select("""
            SELECT mapping_id,tenant_id,source_system,source_type,source_id,target_type,target_id,legal_entity_id,
                   merchant_id,shop_id,valid_from,valid_to,verification_ref,migration_run_id,status,version,
                   created_at,updated_at
            FROM cloudmold_merchant_source_mapping
            WHERE tenant_id=#{tenantId} AND mapping_id=#{mappingId}
            FOR UPDATE
            """)
    MerchantSourceMappingDO selectSourceMappingForUpdate(@Param("tenantId") Long tenantId,
                                                          @Param("mappingId") String mappingId);

    @Select("""
            SELECT admission_id,tenant_id,application_id,merchant_id,shop_id,status,attribution_channel_code,
                   attribution_source_system,attribution_source_type,attribution_source_id,attribution_reference,
                   attribution_evidence_ref,diagnostic_id,inspection_task_id,final_review_id,version,
                   created_at,updated_at
            FROM cloudmold_merchant_managed_admission
            WHERE tenant_id=#{tenantId} AND admission_id=#{admissionId}
            FOR UPDATE
            """)
    MerchantManagedAdmissionDO selectManagedAdmissionForUpdate(@Param("tenantId") Long tenantId,
                                                               @Param("admissionId") String admissionId);

    @Select("""
            SELECT admission_id,tenant_id,application_id,merchant_id,shop_id,status,attribution_channel_code,
                   attribution_source_system,attribution_source_type,attribution_source_id,attribution_reference,
                   attribution_evidence_ref,diagnostic_id,inspection_task_id,final_review_id,version,
                   created_at,updated_at
            FROM cloudmold_merchant_managed_admission
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}
            FOR UPDATE
            """)
    MerchantManagedAdmissionDO selectManagedAdmissionByApplicationForUpdate(@Param("tenantId") Long tenantId,
                                                                            @Param("applicationId") String applicationId);

    @Select("""
            SELECT invitation_id,tenant_id,invitation_code,recruiter_principal_id,attribution_source_system,
                   attribution_source_type,attribution_source_id,attribution_reference,evidence_ref,used_admission_id,
                   used_at,status,version,created_at,updated_at
            FROM cloudmold_merchant_managed_invitation
            WHERE tenant_id=#{tenantId} AND invitation_code=#{invitationCode}
            FOR UPDATE
            """)
    MerchantManagedInvitationDO selectManagedInvitationByCodeForUpdate(@Param("tenantId") Long tenantId,
                                                                       @Param("invitationCode") String invitationCode);

    @Select("""
            SELECT evidence_package_id,tenant_id,admission_id,package_ref,items_json,status,version,created_at,updated_at
            FROM cloudmold_merchant_managed_evidence_package
            WHERE tenant_id=#{tenantId} AND evidence_package_id=#{evidencePackageId}
            FOR UPDATE
            """)
    MerchantManagedEvidencePackageDO selectManagedEvidencePackageForUpdate(@Param("tenantId") Long tenantId,
                                                                           @Param("evidencePackageId") String evidencePackageId);

    @Select("""
            SELECT evidence_package_id,tenant_id,admission_id,package_ref,items_json,status,version,created_at,updated_at
            FROM cloudmold_merchant_managed_evidence_package
            WHERE tenant_id=#{tenantId} AND admission_id=#{admissionId}
            ORDER BY version DESC
            LIMIT 1
            FOR UPDATE
            """)
    MerchantManagedEvidencePackageDO selectLatestManagedEvidencePackageForUpdate(@Param("tenantId") Long tenantId,
                                                                                 @Param("admissionId") String admissionId);

    @Select("""
            SELECT diagnostic_id,tenant_id,admission_id,evidence_package_id,recommendation_code,recommendation_summary,
                   evidence_ref,status,reviewer_principal_id,review_note,version,created_at,updated_at
            FROM cloudmold_merchant_ai_diagnostic
            WHERE tenant_id=#{tenantId} AND diagnostic_id=#{diagnosticId}
            FOR UPDATE
            """)
    MerchantAiDiagnosticDO selectAiDiagnosticForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("diagnosticId") String diagnosticId);

    @Select("""
            SELECT inspection_task_id,tenant_id,admission_id,diagnostic_id,status,actor_principal_id,scheduled_at,
                   evidence_ref,note,version,created_at,updated_at
            FROM cloudmold_merchant_factory_inspection_task
            WHERE tenant_id=#{tenantId} AND inspection_task_id=#{inspectionTaskId}
            FOR UPDATE
            """)
    MerchantFactoryInspectionTaskDO selectFactoryInspectionTaskForUpdate(@Param("tenantId") Long tenantId,
                                                                         @Param("inspectionTaskId") String inspectionTaskId);

    @Select("""
            SELECT buyer_assignment_id,tenant_id,admission_id,inspection_task_id,merchant_id,shop_id,buyer_tl_principal_id,
                   buyer_principal_id,evidence_ref,status,version,created_at,updated_at
            FROM cloudmold_merchant_buyer_assignment
            WHERE tenant_id=#{tenantId} AND buyer_assignment_id=#{buyerAssignmentId}
            FOR UPDATE
            """)
    MerchantBuyerAssignmentDO selectBuyerAssignmentForUpdate(@Param("tenantId") Long tenantId,
                                                             @Param("buyerAssignmentId") String buyerAssignmentId);

    @Select("""
            SELECT grade_decision_id,tenant_id,merchant_id,shop_id,probation_assessment_id,scorecard_id,grade_code,
                   transition_decision,decision_status,thresholds_config_ref,evidence_ref,entitlements_json,version,
                   created_at,updated_at
            FROM cloudmold_merchant_grade_decision
            WHERE tenant_id=#{tenantId} AND grade_decision_id=#{gradeDecisionId}
            FOR UPDATE
            """)
    MerchantGradeDecisionDO selectGradeDecisionForUpdate(@Param("tenantId") Long tenantId,
                                                         @Param("gradeDecisionId") String gradeDecisionId);

    @Select("""
            SELECT probation_assessment_id,tenant_id,admission_id,merchant_id,shop_id,assessment_status,
                   thresholds_config_ref,gates_json,gate_count,evidence_ref,version,created_at,updated_at
            FROM cloudmold_merchant_probation_assessment
            WHERE tenant_id=#{tenantId} AND probation_assessment_id=#{probationAssessmentId}
            FOR UPDATE
            """)
    MerchantProbationAssessmentDO selectProbationAssessmentForUpdate(@Param("tenantId") Long tenantId,
                                                                     @Param("probationAssessmentId") String probationAssessmentId);

    @Select("""
            SELECT scorecard_id,tenant_id,merchant_id,shop_id,scorecard_month,scorecard_status,transition_recommendation,
                   thresholds_config_ref,items_json,item_count,red_line_count,remediation_failure_count,evidence_ref,
                   version,created_at,updated_at
            FROM cloudmold_merchant_monthly_scorecard
            WHERE tenant_id=#{tenantId} AND scorecard_id=#{scorecardId}
            FOR UPDATE
            """)
    MerchantMonthlyScorecardDO selectMonthlyScorecardForUpdate(@Param("tenantId") Long tenantId,
                                                               @Param("scorecardId") String scorecardId);

    @Select("""
            SELECT exit_decision_id,tenant_id,merchant_id,shop_id,admission_id,scorecard_id,reason_type,
                   decision_status,evidence_ref,note,version,created_at,updated_at
            FROM cloudmold_merchant_exit_decision
            WHERE tenant_id=#{tenantId} AND exit_decision_id=#{exitDecisionId}
            FOR UPDATE
            """)
    MerchantExitDecisionDO selectExitDecisionForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("exitDecisionId") String exitDecisionId);

    @Select("""
            SELECT final_review_id,tenant_id,admission_id,inspection_task_id,decision,evidence_ref,reviewer_principal_id,
                   review_note,status,version,created_at,updated_at
            FROM cloudmold_merchant_managed_final_review
            WHERE tenant_id=#{tenantId} AND final_review_id=#{finalReviewId}
            FOR UPDATE
            """)
    MerchantManagedFinalReviewDO selectManagedFinalReviewForUpdate(@Param("tenantId") Long tenantId,
                                                                   @Param("finalReviewId") String finalReviewId);

    @Select("""
            SELECT mapping_id,tenant_id,source_system,source_type,source_id,target_type,target_id,legal_entity_id,
                   merchant_id,shop_id,valid_from,valid_to,verification_ref,migration_run_id,status,version,
                   created_at,updated_at
            FROM cloudmold_merchant_source_mapping
            WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem} AND source_type=#{sourceType}
              AND source_id=#{sourceId} AND status='ACTIVE'
            FOR UPDATE
            """)
    List<MerchantSourceMappingDO> selectActiveSourceMappingsForUpdate(@Param("tenantId") Long tenantId,
                                                                      @Param("sourceSystem") String sourceSystem,
                                                                      @Param("sourceType") String sourceType,
                                                                      @Param("sourceId") String sourceId);

    @Select("""
            SELECT mapping_id,tenant_id,source_system,source_type,source_id,target_type,target_id,legal_entity_id,
                   merchant_id,shop_id,valid_from,valid_to,verification_ref,migration_run_id,status,version,
                   created_at,updated_at
            FROM cloudmold_merchant_source_mapping
            WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem} AND source_type=#{sourceType}
              AND source_id=#{sourceId} AND status='ACTIVE' AND valid_from<=#{effectiveAt}
              AND (valid_to IS NULL OR valid_to>#{effectiveAt})
            """)
    List<MerchantSourceMappingDO> selectActiveSourceMappings(@Param("tenantId") Long tenantId,
                                                             @Param("sourceSystem") String sourceSystem,
                                                             @Param("sourceType") String sourceType,
                                                             @Param("sourceId") String sourceId,
                                                             @Param("effectiveAt") LocalDateTime effectiveAt);

    @Select("""
            SELECT merchant_id,status AS merchant_status,legal_entity_id
            FROM cloudmold_merchant_account
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND status='ACTIVE'
            """)
    MerchantOwnerView selectActiveMerchant(@Param("tenantId") Long tenantId,
                                           @Param("merchantId") String merchantId);

    @Update("""
            UPDATE cloudmold_merchant_onboarding_application
            SET status=#{after},decision_reason=#{reason},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionApplication(@Param("tenantId") Long tenantId, @Param("applicationId") String applicationId,
                              @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                              @Param("after") String after, @Param("reason") String reason,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_onboarding_application
            SET merchant_id=#{merchantId},shop_id=#{shopId},owner_assignment_id=#{assignmentId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND version=#{approvedVersion}
              AND status='APPROVED' AND merchant_id IS NULL AND shop_id IS NULL AND owner_assignment_id IS NULL
            """)
    int attachApprovedEntities(@Param("tenantId") Long tenantId, @Param("applicationId") String applicationId,
                               @Param("approvedVersion") Long approvedVersion, @Param("merchantId") String merchantId,
                               @Param("shopId") String shopId, @Param("assignmentId") String assignmentId,
                               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_legal_entity
            SET status=#{after},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND legal_entity_id=#{legalEntityId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionLegalEntity(@Param("tenantId") Long tenantId, @Param("legalEntityId") String legalEntityId,
                              @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                              @Param("after") String after, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_account
            SET status=#{after},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionMerchant(@Param("tenantId") Long tenantId, @Param("merchantId") String merchantId,
                           @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                           @Param("after") String after, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_shop
            SET status=#{after},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND shop_id=#{shopId} AND version=#{expectedVersion} AND status=#{before}
            """)
    int transitionShop(@Param("tenantId") Long tenantId, @Param("shopId") String shopId,
                       @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                       @Param("after") String after, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_source_mapping
            SET status='REVOKED',valid_to=#{validTo},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND mapping_id=#{mappingId} AND version=#{expectedVersion}
              AND status='ACTIVE'
            """)
    int revokeSourceMapping(@Param("tenantId") Long tenantId, @Param("mappingId") String mappingId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("validTo") LocalDateTime validTo, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_managed_admission
            SET status=#{after},attribution_channel_code=#{attributionChannelCode},
                attribution_source_system=#{attributionSourceSystem},attribution_source_type=#{attributionSourceType},
                attribution_source_id=#{attributionSourceId},attribution_reference=#{attributionReference},
                attribution_evidence_ref=#{attributionEvidenceRef},diagnostic_id=#{diagnosticId},
                inspection_task_id=#{inspectionTaskId},final_review_id=#{finalReviewId},version=version+1,
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND admission_id=#{admissionId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionManagedAdmission(@Param("tenantId") Long tenantId, @Param("admissionId") String admissionId,
                                   @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                                   @Param("after") String after,
                                   @Param("attributionChannelCode") String attributionChannelCode,
                                   @Param("attributionSourceSystem") String attributionSourceSystem,
                                   @Param("attributionSourceType") String attributionSourceType,
                                   @Param("attributionSourceId") String attributionSourceId,
                                   @Param("attributionReference") String attributionReference,
                                   @Param("attributionEvidenceRef") String attributionEvidenceRef,
                                   @Param("diagnosticId") String diagnosticId,
                                   @Param("inspectionTaskId") String inspectionTaskId,
                                   @Param("finalReviewId") String finalReviewId,
                                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_managed_evidence_package
            SET package_ref=#{packageRef},items_json=CAST(#{itemsJson} AS JSON),status=#{status},version=version+1,
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND evidence_package_id=#{evidencePackageId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionManagedEvidencePackage(@Param("tenantId") Long tenantId,
                                         @Param("evidencePackageId") String evidencePackageId,
                                         @Param("expectedVersion") Long expectedVersion,
                                         @Param("before") String before,
                                         @Param("status") String status,
                                         @Param("packageRef") String packageRef,
                                         @Param("itemsJson") String itemsJson,
                                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_ai_diagnostic
            SET status=#{status},reviewer_principal_id=#{reviewerPrincipalId},review_note=#{reviewNote},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND diagnostic_id=#{diagnosticId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionAiDiagnostic(@Param("tenantId") Long tenantId, @Param("diagnosticId") String diagnosticId,
                               @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                               @Param("status") String status,
                               @Param("reviewerPrincipalId") String reviewerPrincipalId,
                               @Param("reviewNote") String reviewNote,
                               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_managed_invitation
            SET used_admission_id=#{usedAdmissionId},used_at=#{usedAt},status=#{status},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND invitation_id=#{invitationId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionManagedInvitation(@Param("tenantId") Long tenantId, @Param("invitationId") String invitationId,
                                    @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                                    @Param("status") String status, @Param("usedAdmissionId") String usedAdmissionId,
                                    @Param("usedAt") LocalDateTime usedAt, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_factory_inspection_task
            SET status=#{status},actor_principal_id=#{actorPrincipalId},scheduled_at=#{scheduledAt},
                evidence_ref=#{evidenceRef},note=#{note},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND inspection_task_id=#{inspectionTaskId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionFactoryInspectionTask(@Param("tenantId") Long tenantId,
                                        @Param("inspectionTaskId") String inspectionTaskId,
                                        @Param("expectedVersion") Long expectedVersion,
                                        @Param("before") String before,
                                        @Param("status") String status,
                                        @Param("actorPrincipalId") String actorPrincipalId,
                                        @Param("scheduledAt") LocalDateTime scheduledAt,
                                        @Param("evidenceRef") String evidenceRef,
                                        @Param("note") String note,
                                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_buyer_assignment
            SET status=#{status},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND buyer_assignment_id=#{buyerAssignmentId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionBuyerAssignment(@Param("tenantId") Long tenantId,
                                  @Param("buyerAssignmentId") String buyerAssignmentId,
                                  @Param("expectedVersion") Long expectedVersion,
                                  @Param("before") String before,
                                  @Param("status") String status,
                                  @Param("now") LocalDateTime now);

    @Select("""
            SELECT m.merchant_id,m.status AS merchant_status,m.legal_entity_id,
                   s.shop_id,s.status AS shop_status,s.channel_code
            FROM cloudmold_merchant_account m
            JOIN cloudmold_merchant_shop s ON s.tenant_id=m.tenant_id AND s.merchant_id=m.merchant_id
            WHERE m.tenant_id=#{tenantId} AND m.merchant_id=#{merchantId} AND s.shop_id=#{shopId}
              AND m.status='ACTIVE' AND s.status='ACTIVE'
            """)
    MerchantReferenceView selectActiveReference(@Param("tenantId") Long tenantId,
                                                @Param("merchantId") String merchantId,
                                                @Param("shopId") String shopId);

    @Select("""
            SELECT assignment_id,merchant_id,shop_id,principal_id,role_code,status AS assignment_status
            FROM cloudmold_merchant_operator_assignment
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND shop_id=#{shopId}
              AND principal_id=#{principalId} AND role_code=#{roleCode} AND status='ACTIVE'
              AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6))
            LIMIT 1
            """)
    MerchantOperatorAuthorizationView selectActiveAssignment(@Param("tenantId") Long tenantId,
                                                              @Param("merchantId") String merchantId,
                                                              @Param("shopId") String shopId,
                                                              @Param("principalId") String principalId,
                                                              @Param("roleCode") String roleCode);

    @Select("""
            SELECT application_id,tenant_id,run_id,legal_entity_id,owner_principal_id,channel_code,external_shop_id,
                   status,decision_reason,merchant_id,shop_id,owner_assignment_id,version,created_at,updated_at
            FROM cloudmold_merchant_onboarding_application
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}
            """)
    MerchantOnboardingApplicationDO selectApplication(@Param("tenantId") Long tenantId,
                                                       @Param("applicationId") String applicationId);

    @Select("""
            SELECT invitation_id,tenant_id,invitation_code,recruiter_principal_id,attribution_source_system,
                   attribution_source_type,attribution_source_id,attribution_reference,evidence_ref,used_admission_id,
                   used_at,status,version,created_at,updated_at
            FROM cloudmold_merchant_managed_invitation
            WHERE tenant_id=#{tenantId} AND invitation_code=#{invitationCode}
            """)
    MerchantManagedInvitationDO selectManagedInvitationByCode(@Param("tenantId") Long tenantId,
                                                              @Param("invitationCode") String invitationCode);

    @Select("""
            SELECT admission_id,tenant_id,application_id,merchant_id,shop_id,status,attribution_channel_code,
                   attribution_source_system,attribution_source_type,attribution_source_id,attribution_reference,
                   attribution_evidence_ref,diagnostic_id,inspection_task_id,final_review_id,version,
                   created_at,updated_at
            FROM cloudmold_merchant_managed_admission
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}
            """)
    MerchantManagedAdmissionDO selectManagedAdmissionByApplication(@Param("tenantId") Long tenantId,
                                                                   @Param("applicationId") String applicationId);

    @Select("""
            SELECT evidence_package_id,tenant_id,admission_id,package_ref,items_json,status,version,created_at,updated_at
            FROM cloudmold_merchant_managed_evidence_package
            WHERE tenant_id=#{tenantId} AND admission_id=#{admissionId}
            ORDER BY version DESC
            LIMIT 1
            """)
    MerchantManagedEvidencePackageDO selectLatestManagedEvidencePackage(@Param("tenantId") Long tenantId,
                                                                        @Param("admissionId") String admissionId);

    @Select("""
            SELECT diagnostic_id,tenant_id,admission_id,evidence_package_id,recommendation_code,recommendation_summary,
                   evidence_ref,status,reviewer_principal_id,review_note,version,created_at,updated_at
            FROM cloudmold_merchant_ai_diagnostic
            WHERE tenant_id=#{tenantId} AND admission_id=#{admissionId}
            ORDER BY version DESC
            LIMIT 1
            """)
    MerchantAiDiagnosticDO selectLatestAiDiagnostic(@Param("tenantId") Long tenantId,
                                                    @Param("admissionId") String admissionId);

    @Select("""
            SELECT inspection_task_id,tenant_id,admission_id,diagnostic_id,status,actor_principal_id,scheduled_at,
                   evidence_ref,note,version,created_at,updated_at
            FROM cloudmold_merchant_factory_inspection_task
            WHERE tenant_id=#{tenantId} AND admission_id=#{admissionId}
            ORDER BY version DESC
            LIMIT 1
            """)
    MerchantFactoryInspectionTaskDO selectLatestFactoryInspectionTask(@Param("tenantId") Long tenantId,
                                                                      @Param("admissionId") String admissionId);

    @Select("""
            SELECT final_review_id,tenant_id,admission_id,inspection_task_id,decision,evidence_ref,reviewer_principal_id,
                   review_note,status,version,created_at,updated_at
            FROM cloudmold_merchant_managed_final_review
            WHERE tenant_id=#{tenantId} AND admission_id=#{admissionId}
            ORDER BY version DESC
            LIMIT 1
            """)
    MerchantManagedFinalReviewDO selectLatestManagedFinalReview(@Param("tenantId") Long tenantId,
                                                                @Param("admissionId") String admissionId);

    @Select("""
            SELECT buyer_assignment_id,tenant_id,admission_id,inspection_task_id,merchant_id,shop_id,buyer_tl_principal_id,
                   buyer_principal_id,evidence_ref,status,version,created_at,updated_at
            FROM cloudmold_merchant_buyer_assignment
            WHERE tenant_id=#{tenantId} AND admission_id=#{admissionId}
            ORDER BY version DESC
            LIMIT 1
            """)
    MerchantBuyerAssignmentDO selectLatestBuyerAssignment(@Param("tenantId") Long tenantId,
                                                          @Param("admissionId") String admissionId);

    @Select("""
            SELECT grade_decision_id,tenant_id,merchant_id,shop_id,probation_assessment_id,scorecard_id,grade_code,
                   transition_decision,decision_status,thresholds_config_ref,evidence_ref,entitlements_json,version,
                   created_at,updated_at
            FROM cloudmold_merchant_grade_decision
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId}
            ORDER BY version DESC
            LIMIT 1
            """)
    MerchantGradeDecisionDO selectLatestGradeDecision(@Param("tenantId") Long tenantId,
                                                      @Param("merchantId") String merchantId);

    @Select("""
            SELECT probation_assessment_id,tenant_id,admission_id,merchant_id,shop_id,assessment_status,
                   thresholds_config_ref,gates_json,gate_count,evidence_ref,version,created_at,updated_at
            FROM cloudmold_merchant_probation_assessment
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId}
            ORDER BY version DESC
            LIMIT 1
            """)
    MerchantProbationAssessmentDO selectLatestProbationAssessment(@Param("tenantId") Long tenantId,
                                                                  @Param("merchantId") String merchantId);

    @Select("""
            SELECT scorecard_id,tenant_id,merchant_id,shop_id,scorecard_month,scorecard_status,transition_recommendation,
                   thresholds_config_ref,items_json,item_count,red_line_count,remediation_failure_count,evidence_ref,
                   version,created_at,updated_at
            FROM cloudmold_merchant_monthly_scorecard
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND scorecard_month=#{scorecardMonth}
            """)
    MerchantMonthlyScorecardDO selectMonthlyScorecardByMonth(@Param("tenantId") Long tenantId,
                                                             @Param("merchantId") String merchantId,
                                                             @Param("scorecardMonth") String scorecardMonth);

    @Select("""
            SELECT exit_decision_id,tenant_id,merchant_id,shop_id,admission_id,scorecard_id,reason_type,
                   decision_status,evidence_ref,note,version,created_at,updated_at
            FROM cloudmold_merchant_exit_decision
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND decision_status='APPROVED'
            ORDER BY version DESC
            LIMIT 1
            """)
    MerchantExitDecisionDO selectLatestApprovedExitDecision(@Param("tenantId") Long tenantId,
                                                            @Param("merchantId") String merchantId);

    @Select("""
            SELECT merchant_id,tenant_id,merchant_code,legal_entity_id,status,version,created_at,updated_at
            FROM cloudmold_merchant_account
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId}
            """)
    MerchantAccountDO selectMerchant(@Param("tenantId") Long tenantId, @Param("merchantId") String merchantId);

    @Select("""
            SELECT shop_id,tenant_id,merchant_id,channel_code,external_shop_id,status,version,created_at,updated_at
            FROM cloudmold_merchant_shop
            WHERE tenant_id=#{tenantId} AND shop_id=#{shopId}
            """)
    MerchantShopDO selectShop(@Param("tenantId") Long tenantId, @Param("shopId") String shopId);

    @Select("""
            SELECT assignment_id,tenant_id,merchant_id,shop_id,principal_id,role_code,status,version,valid_from,
                   valid_to,created_at,updated_at
            FROM cloudmold_merchant_operator_assignment
            WHERE tenant_id=#{tenantId} AND assignment_id=#{assignmentId}
            """)
    MerchantOperatorAssignmentDO selectAssignment(@Param("tenantId") Long tenantId,
                                                   @Param("assignmentId") String assignmentId);
}
