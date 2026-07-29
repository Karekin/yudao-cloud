package cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.ComplianceAssessmentRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.StatusHistoryRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CrossBorderCaseMapper {
    @Insert("""
            INSERT INTO cloudmold_crossborder_operation
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
            FROM cloudmold_crossborder_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_crossborder_operation
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
            INSERT INTO cloudmold_crossborder_case
              (case_id,tenant_id,case_no,order_id,fulfillment_id,trade_mode,origin_country,destination_country,
               status,created_by_principal_id,version,created_at,updated_at)
            VALUES (#{caseId},#{tenantId},#{caseNo},#{orderId},#{fulfillmentId},#{tradeMode},#{originCountry},
                    #{destinationCountry},#{status},#{createdByPrincipalId},#{version},#{createdAt},#{updatedAt})
            """)
    int insertCase(CaseRecord value);

    @Select("""
            SELECT case_id,tenant_id,case_no,order_id,fulfillment_id,trade_mode,origin_country,destination_country,
                   status,assessment_id,route_code,route_carrier_code,route_service_level,route_sla_days,
                   approval_ref,declaration_id,hs_code,goods_description,quantity,declared_amount_minor,currency,
                   declaration_evidence_ref,document_order_ref,document_payment_ref,document_logistics_ref,
                   document_validation_evidence_ref,booking_ref,booking_carrier_code,booking_service_level,
                   label_ref,tracking_number,handover_ref,customs_declaration_ref,customs_release_ref,
                   delivery_evidence_ref,close_reason,created_by_principal_id,version,created_at,updated_at,closed_at
            FROM cloudmold_crossborder_case
            WHERE tenant_id=#{tenantId} AND case_id=#{caseId} FOR UPDATE
            """)
    CaseRecord selectCaseForUpdate(@Param("tenantId") Long tenantId,
                                   @Param("caseId") String caseId);

    @Select("""
            SELECT case_id,tenant_id,case_no,order_id,fulfillment_id,trade_mode,origin_country,destination_country,
                   status,assessment_id,route_code,route_carrier_code,route_service_level,route_sla_days,
                   approval_ref,declaration_id,hs_code,goods_description,quantity,declared_amount_minor,currency,
                   declaration_evidence_ref,document_order_ref,document_payment_ref,document_logistics_ref,
                   document_validation_evidence_ref,booking_ref,booking_carrier_code,booking_service_level,
                   label_ref,tracking_number,handover_ref,customs_declaration_ref,customs_release_ref,
                   delivery_evidence_ref,close_reason,created_by_principal_id,version,created_at,updated_at,closed_at
            FROM cloudmold_crossborder_case
            WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
            """)
    CaseRecord selectCase(@Param("tenantId") Long tenantId,
                          @Param("caseId") String caseId);

    @Update("""
            UPDATE cloudmold_crossborder_case
            SET status=#{value.status},
                assessment_id=#{value.assessmentId},
                route_code=#{value.routeCode},
                route_carrier_code=#{value.routeCarrierCode},
                route_service_level=#{value.routeServiceLevel},
                route_sla_days=#{value.routeSlaDays},
                approval_ref=#{value.approvalRef},
                declaration_id=#{value.declarationId},
                hs_code=#{value.hsCode},
                goods_description=#{value.goodsDescription},
                quantity=#{value.quantity},
                declared_amount_minor=#{value.declaredAmountMinor},
                currency=#{value.currency},
                declaration_evidence_ref=#{value.declarationEvidenceRef},
                document_order_ref=#{value.documentOrderRef},
                document_payment_ref=#{value.documentPaymentRef},
                document_logistics_ref=#{value.documentLogisticsRef},
                document_validation_evidence_ref=#{value.documentValidationEvidenceRef},
                booking_ref=#{value.bookingRef},
                booking_carrier_code=#{value.bookingCarrierCode},
                booking_service_level=#{value.bookingServiceLevel},
                label_ref=#{value.labelRef},
                tracking_number=#{value.trackingNumber},
                handover_ref=#{value.handoverRef},
                customs_declaration_ref=#{value.customsDeclarationRef},
                customs_release_ref=#{value.customsReleaseRef},
                delivery_evidence_ref=#{value.deliveryEvidenceRef},
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
            INSERT INTO cloudmold_crossborder_compliance_assessment
              (assessment_id,tenant_id,case_id,facts_json,options_json,recommendation,risks_json,confidence,
               missing_facts_json,evidence_ref,assessed_by_principal_id,assessed_at,created_at)
            VALUES (#{assessmentId},#{tenantId},#{caseId},#{factsJson},#{optionsJson},#{recommendation},
                    #{risksJson},#{confidence},#{missingFactsJson},#{evidenceRef},
                    #{assessedByPrincipalId},#{assessedAt},#{createdAt})
            """)
    int insertAssessment(ComplianceAssessmentRecord value);

    @Select("""
            SELECT assessment_id,tenant_id,case_id,facts_json,options_json,recommendation,risks_json,confidence,
                   missing_facts_json,evidence_ref,assessed_by_principal_id,assessed_at,created_at
            FROM cloudmold_crossborder_compliance_assessment
            WHERE tenant_id=#{tenantId} AND assessment_id=#{assessmentId}
            """)
    ComplianceAssessmentRecord selectAssessment(@Param("tenantId") Long tenantId,
                                                @Param("assessmentId") String assessmentId);

    @Insert("""
            INSERT INTO cloudmold_crossborder_status_history
              (tenant_id,case_id,aggregate_version,operation,previous_status,current_status,actor_principal_id,
               occurred_at,detail_json,created_at)
            VALUES (#{tenantId},#{caseId},#{aggregateVersion},#{operation},#{previousStatus},#{currentStatus},
                    #{actorPrincipalId},#{occurredAt},#{detailJson},#{createdAt})
            """)
    int insertHistory(StatusHistoryRecord value);

    @Select("""
            SELECT history_id,tenant_id,case_id,aggregate_version,operation,previous_status,current_status,
                   actor_principal_id,occurred_at,detail_json,created_at
            FROM cloudmold_crossborder_status_history
            WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
            ORDER BY aggregate_version ASC, history_id ASC
            """)
    List<StatusHistoryRecord> selectHistory(@Param("tenantId") Long tenantId,
                                            @Param("caseId") String caseId);
}
