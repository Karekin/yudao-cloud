package cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql.bonded;

import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.EligibilityAssessmentRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.StatusHistoryRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BondedCustomsCaseMapper {
    @Insert("""
            INSERT INTO cloudmold_bonded_customs_operation
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
            FROM cloudmold_bonded_customs_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_bonded_customs_operation
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
            INSERT INTO cloudmold_bonded_customs_case
              (case_id,tenant_id,case_no,mode,canonical_order_id,status,triple_match_status,customs_status,
               bonded_release_status,delivery_status,order_ref,payment_ref,logistics_ref,created_by_principal_id,
               version,created_at,updated_at)
            VALUES (#{caseId},#{tenantId},#{caseNo},#{mode},#{canonicalOrderId},#{status},#{tripleMatchStatus},
                    #{customsStatus},#{bondedReleaseStatus},#{deliveryStatus},#{orderRef},#{paymentRef},
                    #{logisticsRef},#{createdByPrincipalId},#{version},#{createdAt},#{updatedAt})
            """)
    int insertCase(CaseRecord value);

    @Select("""
            SELECT case_id,tenant_id,case_no,mode,canonical_order_id,status,triple_match_status,customs_status,
                   bonded_release_status,delivery_status,order_ref,payment_ref,logistics_ref,order_amount_minor,
                   payment_amount_minor,logistics_amount_minor,currency,buyer_identity_hash,receiver_identity_hash,
                   declarant_identity_hash,order_snapshot_ref,payment_snapshot_ref,logistics_snapshot_ref,
                   assessment_id,hs_code,positive_list_code,goods_name,goods_evidence_ref,dutiable_amount_minor,
                   consumption_tax_minor,value_added_tax_minor,total_tax_minor,tax_currency,tax_evidence_ref,
                   approval_ref,declaration_ref,customs_acceptance_ref,bonded_release_ref,delivery_confirmation_ref,
                   close_reason,created_by_principal_id,version,created_at,updated_at,closed_at
            FROM cloudmold_bonded_customs_case
            WHERE tenant_id=#{tenantId} AND case_id=#{caseId} FOR UPDATE
            """)
    CaseRecord selectCaseForUpdate(@Param("tenantId") Long tenantId,
                                   @Param("caseId") String caseId);

    @Select("""
            SELECT case_id,tenant_id,case_no,mode,canonical_order_id,status,triple_match_status,customs_status,
                   bonded_release_status,delivery_status,order_ref,payment_ref,logistics_ref,order_amount_minor,
                   payment_amount_minor,logistics_amount_minor,currency,buyer_identity_hash,receiver_identity_hash,
                   declarant_identity_hash,order_snapshot_ref,payment_snapshot_ref,logistics_snapshot_ref,
                   assessment_id,hs_code,positive_list_code,goods_name,goods_evidence_ref,dutiable_amount_minor,
                   consumption_tax_minor,value_added_tax_minor,total_tax_minor,tax_currency,tax_evidence_ref,
                   approval_ref,declaration_ref,customs_acceptance_ref,bonded_release_ref,delivery_confirmation_ref,
                   close_reason,created_by_principal_id,version,created_at,updated_at,closed_at
            FROM cloudmold_bonded_customs_case
            WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
            """)
    CaseRecord selectCase(@Param("tenantId") Long tenantId,
                          @Param("caseId") String caseId);

    @Update("""
            UPDATE cloudmold_bonded_customs_case
            SET status=#{value.status},
                triple_match_status=#{value.tripleMatchStatus},
                customs_status=#{value.customsStatus},
                bonded_release_status=#{value.bondedReleaseStatus},
                delivery_status=#{value.deliveryStatus},
                order_ref=#{value.orderRef},
                payment_ref=#{value.paymentRef},
                logistics_ref=#{value.logisticsRef},
                order_amount_minor=#{value.orderAmountMinor},
                payment_amount_minor=#{value.paymentAmountMinor},
                logistics_amount_minor=#{value.logisticsAmountMinor},
                currency=#{value.currency},
                buyer_identity_hash=#{value.buyerIdentityHash},
                receiver_identity_hash=#{value.receiverIdentityHash},
                declarant_identity_hash=#{value.declarantIdentityHash},
                order_snapshot_ref=#{value.orderSnapshotRef},
                payment_snapshot_ref=#{value.paymentSnapshotRef},
                logistics_snapshot_ref=#{value.logisticsSnapshotRef},
                assessment_id=#{value.assessmentId},
                hs_code=#{value.hsCode},
                positive_list_code=#{value.positiveListCode},
                goods_name=#{value.goodsName},
                goods_evidence_ref=#{value.goodsEvidenceRef},
                dutiable_amount_minor=#{value.dutiableAmountMinor},
                consumption_tax_minor=#{value.consumptionTaxMinor},
                value_added_tax_minor=#{value.valueAddedTaxMinor},
                total_tax_minor=#{value.totalTaxMinor},
                tax_currency=#{value.taxCurrency},
                tax_evidence_ref=#{value.taxEvidenceRef},
                approval_ref=#{value.approvalRef},
                declaration_ref=#{value.declarationRef},
                customs_acceptance_ref=#{value.customsAcceptanceRef},
                bonded_release_ref=#{value.bondedReleaseRef},
                delivery_confirmation_ref=#{value.deliveryConfirmationRef},
                close_reason=#{value.closeReason},
                version=#{value.version},
                updated_at=#{value.updatedAt},
                closed_at=#{value.closedAt}
            WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
              AND version=#{expectedVersion} AND status=#{expectedStatus}
            """)
    int updateCase(@Param("value") CaseRecord value,
                   @Param("tenantId") Long tenantId,
                   @Param("caseId") String caseId,
                   @Param("expectedVersion") Long expectedVersion,
                   @Param("expectedStatus") String expectedStatus);

    @Insert("""
            INSERT INTO cloudmold_bonded_customs_eligibility_assessment
              (assessment_id,tenant_id,case_id,facts_json,options_json,recommendation,risks_json,confidence,
               missing_facts_json,evidence_ref,assessed_by_principal_id,assessed_at,created_at)
            VALUES (#{assessmentId},#{tenantId},#{caseId},#{factsJson},#{optionsJson},#{recommendation},
                    #{risksJson},#{confidence},#{missingFactsJson},#{evidenceRef},#{assessedByPrincipalId},
                    #{assessedAt},#{createdAt})
            """)
    int insertAssessment(EligibilityAssessmentRecord value);

    @Select("""
            SELECT assessment_id,tenant_id,case_id,facts_json,options_json,recommendation,risks_json,confidence,
                   missing_facts_json,evidence_ref,assessed_by_principal_id,assessed_at,created_at
            FROM cloudmold_bonded_customs_eligibility_assessment
            WHERE tenant_id=#{tenantId} AND assessment_id=#{assessmentId}
            """)
    EligibilityAssessmentRecord selectAssessment(@Param("tenantId") Long tenantId,
                                                 @Param("assessmentId") String assessmentId);

    @Insert("""
            INSERT INTO cloudmold_bonded_customs_status_history
              (tenant_id,case_id,aggregate_version,operation,previous_status,current_status,actor_principal_id,
               occurred_at,detail_json,created_at)
            VALUES (#{tenantId},#{caseId},#{aggregateVersion},#{operation},#{previousStatus},#{currentStatus},
                    #{actorPrincipalId},#{occurredAt},#{detailJson},#{createdAt})
            """)
    int insertHistory(StatusHistoryRecord value);

    @Select("""
            SELECT history_id,tenant_id,case_id,aggregate_version,operation,previous_status,current_status,
                   actor_principal_id,occurred_at,detail_json,created_at
            FROM cloudmold_bonded_customs_status_history
            WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
            ORDER BY aggregate_version ASC, history_id ASC
            """)
    List<StatusHistoryRecord> selectHistory(@Param("tenantId") Long tenantId,
                                            @Param("caseId") String caseId);
}
