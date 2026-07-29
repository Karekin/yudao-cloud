package cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject.PartnerMarketingCaseDO;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject.PartnerMarketingCaseHistoryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PartnerMarketingCaseMapper extends BaseMapperX<PartnerMarketingCaseDO> {

    @Select("""
        SELECT * FROM cloudmold_partner_marketing_case
        WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
        """)
    PartnerMarketingCaseDO selectOneById(@Param("tenantId") Long tenantId,
                                         @Param("caseId") String caseId);

    @Select("""
        SELECT * FROM cloudmold_partner_marketing_case
        WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
        FOR UPDATE
        """)
    PartnerMarketingCaseDO selectForUpdate(@Param("tenantId") Long tenantId,
                                           @Param("caseId") String caseId);

    @Update("""
        <script>
        UPDATE cloudmold_partner_marketing_case
        SET status=#{row.status},
            cooperation_model=#{row.cooperationModel},
            brief_budget_amount_minor=#{row.briefBudgetAmountMinor},
            currency_code=#{row.currencyCode},
            risk_level=#{row.riskLevel},
            risk_evidence_sha256=#{row.riskEvidenceSha256},
            qualification_note=#{row.qualificationNote},
            outreach_channel_code=#{row.outreachChannelCode},
            outreach_external_ref=#{row.outreachExternalRef},
            campaign_id=#{row.campaignId},
            listing_id=#{row.listingId},
            brief_summary=#{row.briefSummary},
            brief_evidence_sha256=#{row.briefEvidenceSha256},
            brief_submitted_by_principal_id=#{row.briefSubmittedByPrincipalId},
            brief_approved_by_principal_id=#{row.briefApprovedByPrincipalId},
            content_summary=#{row.contentSummary},
            content_evidence_sha256=#{row.contentEvidenceSha256},
            content_submitted_by_principal_id=#{row.contentSubmittedByPrincipalId},
            content_approved_by_principal_id=#{row.contentApprovedByPrincipalId},
            external_publish_ref=#{row.externalPublishRef},
            external_publish_url=#{row.externalPublishUrl},
            disclosure_label=#{row.disclosureLabel},
            publish_evidence_sha256=#{row.publishEvidenceSha256},
            disclosure_verified=#{row.disclosureVerified},
            publish_verified_by_principal_id=#{row.publishVerifiedByPrincipalId},
            attributed_order_count=#{row.attributedOrderCount},
            attributed_order_id=#{row.attributedOrderId},
            attributed_payment_id=#{row.attributedPaymentId},
            attribution_source_ref=#{row.attributionSourceRef},
            gross_settlement_amount_minor=#{row.grossSettlementAmountMinor},
            platform_fee_amount_minor=#{row.platformFeeAmountMinor},
            tax_withholding_amount_minor=#{row.taxWithholdingAmountMinor},
            net_payable_amount_minor=#{row.netPayableAmountMinor},
            attribution_evidence_sha256=#{row.attributionEvidenceSha256},
            settlement_requested_by_principal_id=#{row.settlementRequestedByPrincipalId},
            settlement_approved_by_principal_id=#{row.settlementApprovedByPrincipalId},
            settlement_paid_by_principal_id=#{row.settlementPaidByPrincipalId},
            settlement_reference=#{row.settlementReference},
            settlement_approval_evidence_sha256=#{row.settlementApprovalEvidenceSha256},
            settlement_payment_evidence_sha256=#{row.settlementPaymentEvidenceSha256},
            closed_by_principal_id=#{row.closedByPrincipalId},
            reason_code=#{row.reasonCode},
            version=#{row.version},
            qualified_at=#{row.qualifiedAt},
            outreach_started_at=#{row.outreachStartedAt},
            brief_submitted_at=#{row.briefSubmittedAt},
            brief_approved_at=#{row.briefApprovedAt},
            content_submitted_at=#{row.contentSubmittedAt},
            content_approved_at=#{row.contentApprovedAt},
            publish_verified_at=#{row.publishVerifiedAt},
            attribution_reconciled_at=#{row.attributionReconciledAt},
            settlement_requested_at=#{row.settlementRequestedAt},
            settlement_approved_at=#{row.settlementApprovedAt},
            settlement_paid_at=#{row.settlementPaidAt},
            closed_at=#{row.closedAt},
            updated_at=#{row.updatedAt}
        WHERE tenant_id=#{tenantId} AND case_id=#{row.caseId} AND version=#{expectedVersion}
        </script>
        """)
    int updateWorkflowState(@Param("tenantId") Long tenantId, @Param("row") PartnerMarketingCaseDO row,
                            @Param("expectedVersion") Long expectedVersion);

    @Insert("""
        INSERT INTO cloudmold_partner_marketing_case_history
          (case_id,tenant_id,aggregate_version,command_type,from_status,to_status,actor_principal_id,
           reason_code,evidence_sha256,created_at)
        VALUES (#{caseId},#{tenantId},#{aggregateVersion},#{commandType},#{fromStatus},#{toStatus},
                #{actorPrincipalId},#{reasonCode},#{evidenceSha256},#{createdAt})
        """)
    int insertHistory(PartnerMarketingCaseHistoryDO row);

    @Select("""
        SELECT * FROM cloudmold_partner_marketing_case_history
        WHERE tenant_id=#{tenantId} AND case_id=#{caseId}
        ORDER BY aggregate_version ASC, history_id ASC
        """)
    List<PartnerMarketingCaseHistoryDO> selectHistory(@Param("tenantId") Long tenantId,
                                                      @Param("caseId") String caseId);
}
