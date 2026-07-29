package cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingDecisionView;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.Quote;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.SampleEvaluation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.SourcingCase;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.SupplierProfile;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface SupplierSourcingMapper {
    @Insert("""
            INSERT INTO cloudmold_supplier_operation
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
            FROM cloudmold_supplier_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_supplier_operation
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
            INSERT INTO cloudmold_supplier_profile
              (supplier_id,tenant_id,supplier_code,supplier_name,country_code,capability_summary,risk_level,
               status,admission_status,created_by_principal_id,reason_code,version,created_at,updated_at)
            VALUES (#{supplierId},#{tenantId},#{supplierCode},#{supplierName},#{countryCode},
                    #{capabilitySummary},#{riskLevel},#{status},#{admissionStatus},#{createdByPrincipalId},
                    #{reasonCode},#{version},#{createdAt},#{updatedAt})
            """)
    int insertSupplier(SupplierProfile value);

    @Select("""
            SELECT supplier_id,tenant_id,supplier_code,supplier_name,country_code,capability_summary,risk_level,
                   status,admission_status,qualification_evidence_sha256,risk_evidence_sha256,
                   created_by_principal_id,submitted_by_principal_id,admitted_by_principal_id,reason_code,
                   version,created_at,updated_at,admission_submitted_at,admitted_at
            FROM cloudmold_supplier_profile
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} FOR UPDATE
            """)
    SupplierProfile selectSupplierForUpdate(@Param("tenantId") Long tenantId,
                                            @Param("supplierId") String supplierId);

    @Update("""
            UPDATE cloudmold_supplier_profile
            SET admission_status='UNDER_REVIEW',submitted_by_principal_id=#{actorPrincipalId},
                qualification_evidence_sha256=#{qualificationEvidenceSha256},
                risk_evidence_sha256=#{riskEvidenceSha256},reason_code=#{reasonCode},
                admission_submitted_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}
              AND status='CANDIDATE' AND admission_status='DRAFT' AND version=#{expectedVersion}
            """)
    int submitAdmission(@Param("tenantId") Long tenantId,
                        @Param("supplierId") String supplierId,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("actorPrincipalId") String actorPrincipalId,
                        @Param("qualificationEvidenceSha256") String qualificationEvidenceSha256,
                        @Param("riskEvidenceSha256") String riskEvidenceSha256,
                        @Param("reasonCode") String reasonCode,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_supplier_profile
            SET status='ACTIVE',admission_status='ADMITTED',admitted_by_principal_id=#{actorPrincipalId},
                reason_code=#{reasonCode},admitted_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}
              AND status='CANDIDATE' AND admission_status='UNDER_REVIEW' AND version=#{expectedVersion}
            """)
    int approveAdmission(@Param("tenantId") Long tenantId,
                         @Param("supplierId") String supplierId,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("actorPrincipalId") String actorPrincipalId,
                         @Param("reasonCode") String reasonCode,
                         @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_supplier_sourcing_case
              (sourcing_case_id,tenant_id,rfq_code,request_ref,canonical_sku_id,target_quantity,uom_code,
               currency_code,max_unit_cost_minor,required_delivery_date,requirements,status,
               created_by_principal_id,version,created_at,updated_at)
            VALUES (#{sourcingCaseId},#{tenantId},#{rfqCode},#{requestRef},#{canonicalSkuId},
                    #{targetQuantity},#{uomCode},#{currencyCode},#{maxUnitCostMinor},#{requiredDeliveryDate},
                    #{requirements},#{status},#{createdByPrincipalId},#{version},#{createdAt},#{updatedAt})
            """)
    int insertSourcingCase(SourcingCase value);

    @Select("""
            SELECT sourcing_case_id,tenant_id,rfq_code,request_ref,canonical_sku_id,target_quantity,uom_code,
                   currency_code,max_unit_cost_minor,required_delivery_date,requirements,status,
                   awarded_supplier_id,awarded_quote_id,decision_rationale,created_by_principal_id,
                   awarded_by_principal_id,version,created_at,updated_at,awarded_at
            FROM cloudmold_supplier_sourcing_case
            WHERE tenant_id=#{tenantId} AND sourcing_case_id=#{sourcingCaseId} FOR UPDATE
            """)
    SourcingCase selectCaseForUpdate(@Param("tenantId") Long tenantId,
                                     @Param("sourcingCaseId") String sourcingCaseId);

    @Insert("""
            INSERT INTO cloudmold_supplier_quote
              (quote_id,tenant_id,sourcing_case_id,supplier_id,quote_version,unit_cost_minor,moq,
               lead_time_days,capacity_quantity,valid_until,terms_summary,status,
               submitted_by_principal_id,created_at,updated_at)
            VALUES (#{quoteId},#{tenantId},#{sourcingCaseId},#{supplierId},#{quoteVersion},
                    #{unitCostMinor},#{moq},#{leadTimeDays},#{capacityQuantity},#{validUntil},
                    #{termsSummary},#{status},#{submittedByPrincipalId},#{createdAt},#{updatedAt})
            """)
    int insertQuote(Quote value);

    @Select("""
            SELECT quote_id,tenant_id,sourcing_case_id,supplier_id,quote_version,unit_cost_minor,moq,
                   lead_time_days,capacity_quantity,valid_until,terms_summary,status,
                   submitted_by_principal_id,created_at,updated_at
            FROM cloudmold_supplier_quote
            WHERE tenant_id=#{tenantId} AND quote_id=#{quoteId}
            """)
    Quote selectQuote(@Param("tenantId") Long tenantId,
                      @Param("quoteId") String quoteId);

    @Update("""
            UPDATE cloudmold_supplier_sourcing_case
            SET status='QUOTING',version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND sourcing_case_id=#{sourcingCaseId}
              AND status IN ('OPEN','QUOTING') AND version=#{expectedVersion}
            """)
    int markQuoteSubmitted(@Param("tenantId") Long tenantId,
                           @Param("sourcingCaseId") String sourcingCaseId,
                           @Param("expectedVersion") Long expectedVersion,
                           @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_supplier_sample_evaluation
              (evaluation_id,tenant_id,sourcing_case_id,supplier_id,quote_id,quality_score,fit_score,
               delivery_score,risk_score,result,notes,evaluated_by_principal_id,evaluated_at)
            VALUES (#{evaluationId},#{tenantId},#{sourcingCaseId},#{supplierId},#{quoteId},
                    #{qualityScore},#{fitScore},#{deliveryScore},#{riskScore},#{result},#{notes},
                    #{evaluatedByPrincipalId},#{evaluatedAt})
            """)
    int insertSampleEvaluation(SampleEvaluation value);

    @Update("""
            UPDATE cloudmold_supplier_sourcing_case
            SET status='EVALUATING',version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND sourcing_case_id=#{sourcingCaseId}
              AND status IN ('QUOTING','EVALUATING') AND version=#{expectedVersion}
            """)
    int markSampleEvaluated(@Param("tenantId") Long tenantId,
                            @Param("sourcingCaseId") String sourcingCaseId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(DISTINCT supplier_id)
            FROM cloudmold_supplier_quote
            WHERE tenant_id=#{tenantId} AND sourcing_case_id=#{sourcingCaseId}
              AND status='SUBMITTED'
            """)
    int countQuotedSuppliers(@Param("tenantId") Long tenantId,
                             @Param("sourcingCaseId") String sourcingCaseId);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_supplier_sample_evaluation
            WHERE tenant_id=#{tenantId} AND sourcing_case_id=#{sourcingCaseId}
              AND supplier_id=#{supplierId} AND quote_id=#{quoteId} AND result='PASS'
            """)
    int countPassingSample(@Param("tenantId") Long tenantId,
                           @Param("sourcingCaseId") String sourcingCaseId,
                           @Param("supplierId") String supplierId,
                           @Param("quoteId") String quoteId);

    @Update("""
            UPDATE cloudmold_supplier_sourcing_case
            SET status='AWARDED',awarded_supplier_id=#{supplierId},awarded_quote_id=#{quoteId},
                decision_rationale=#{decisionRationale},awarded_by_principal_id=#{actorPrincipalId},
                awarded_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND sourcing_case_id=#{sourcingCaseId}
              AND status='EVALUATING' AND version=#{expectedVersion}
            """)
    int awardCase(@Param("tenantId") Long tenantId,
                  @Param("sourcingCaseId") String sourcingCaseId,
                  @Param("expectedVersion") Long expectedVersion,
                  @Param("supplierId") String supplierId,
                  @Param("quoteId") String quoteId,
                  @Param("decisionRationale") String decisionRationale,
                  @Param("actorPrincipalId") String actorPrincipalId,
                  @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_supplier_quote
            SET status=CASE WHEN quote_id=#{quoteId} THEN 'AWARDED' ELSE 'REJECTED' END,
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND sourcing_case_id=#{sourcingCaseId}
              AND status='SUBMITTED'
            """)
    int resolveQuotes(@Param("tenantId") Long tenantId,
                      @Param("sourcingCaseId") String sourcingCaseId,
                      @Param("quoteId") String quoteId,
                      @Param("now") LocalDateTime now);

    @Select("""
            SELECT supplier_id supplierId,supplier_code supplierCode,supplier_name supplierName,
                   country_code countryCode,capability_summary capabilitySummary,risk_level riskLevel,
                   status,admission_status admissionStatus,
                   qualification_evidence_sha256 qualificationEvidenceSha256,
                   risk_evidence_sha256 riskEvidenceSha256,version,
                   created_by_principal_id createdByPrincipalId,
                   admitted_by_principal_id admittedByPrincipalId,
                   created_at createdAt,admitted_at admittedAt
            FROM cloudmold_supplier_profile
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}
            """)
    SupplierProfileView selectSupplier(@Param("tenantId") Long tenantId,
                                       @Param("supplierId") String supplierId);

    @Select("""
            SELECT c.sourcing_case_id sourcingCaseId,c.rfq_code rfqCode,c.request_ref requestRef,
                   c.canonical_sku_id canonicalSkuId,c.target_quantity targetQuantity,c.uom_code uomCode,
                   c.currency_code currencyCode,c.max_unit_cost_minor maxUnitCostMinor,
                   c.required_delivery_date requiredDeliveryDate,c.requirements,c.status,
                   (SELECT COUNT(DISTINCT q0.supplier_id) FROM cloudmold_supplier_quote q0
                     WHERE q0.tenant_id=c.tenant_id AND q0.sourcing_case_id=c.sourcing_case_id)
                     quoteSupplierCount,
                   c.awarded_supplier_id awardedSupplierId,s.supplier_name awardedSupplierName,
                   c.awarded_quote_id awardedQuoteId,q.unit_cost_minor awardedUnitCostMinor,
                   q.moq awardedMoq,q.lead_time_days awardedLeadTimeDays,
                   q.capacity_quantity awardedCapacityQuantity,e.result sampleResult,
                   e.quality_score sampleQualityScore,e.fit_score sampleFitScore,
                   e.delivery_score sampleDeliveryScore,e.risk_score sampleRiskScore,
                   c.decision_rationale decisionRationale,c.version,c.created_at createdAt,c.awarded_at awardedAt
            FROM cloudmold_supplier_sourcing_case c
            LEFT JOIN cloudmold_supplier_profile s
              ON s.tenant_id=c.tenant_id AND s.supplier_id=c.awarded_supplier_id
            LEFT JOIN cloudmold_supplier_quote q
              ON q.tenant_id=c.tenant_id AND q.quote_id=c.awarded_quote_id
            LEFT JOIN cloudmold_supplier_sample_evaluation e
              ON e.tenant_id=c.tenant_id AND e.sourcing_case_id=c.sourcing_case_id
             AND e.quote_id=c.awarded_quote_id
            WHERE c.tenant_id=#{tenantId} AND c.sourcing_case_id=#{sourcingCaseId}
            """)
    SupplierSourcingDecisionView selectDecision(@Param("tenantId") Long tenantId,
                                                @Param("sourcingCaseId") String sourcingCaseId);
}
