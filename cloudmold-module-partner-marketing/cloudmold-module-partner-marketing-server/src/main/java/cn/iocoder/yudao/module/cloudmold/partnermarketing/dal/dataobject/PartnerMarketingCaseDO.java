package cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_partner_marketing_case")
@Data
@Accessors(chain = true)
public class PartnerMarketingCaseDO {
    @TableId(type = IdType.INPUT)
    private String caseId;
    private Long tenantId;
    private String caseCode;
    private String creatorPrincipalId;
    private String candidateHandle;
    private String platformCode;
    private String regionCode;
    private String categoryCode;
    private String cooperationModel;
    private Long briefBudgetAmountMinor;
    private String currencyCode;
    private String riskLevel;
    private String riskEvidenceSha256;
    private String qualificationNote;
    private String outreachChannelCode;
    private String outreachExternalRef;
    private String campaignId;
    private String listingId;
    private String briefSummary;
    private String briefEvidenceSha256;
    private String briefSubmittedByPrincipalId;
    private String briefApprovedByPrincipalId;
    private String contentSummary;
    private String contentEvidenceSha256;
    private String contentSubmittedByPrincipalId;
    private String contentApprovedByPrincipalId;
    private String externalPublishRef;
    private String externalPublishUrl;
    private String disclosureLabel;
    private String publishEvidenceSha256;
    private Boolean disclosureVerified;
    private String publishVerifiedByPrincipalId;
    private Integer attributedOrderCount;
    private String attributedOrderId;
    private String attributedPaymentId;
    private String attributionSourceRef;
    private Long grossSettlementAmountMinor;
    private Long platformFeeAmountMinor;
    private Long taxWithholdingAmountMinor;
    private Long netPayableAmountMinor;
    private String attributionEvidenceSha256;
    private String settlementRequestedByPrincipalId;
    private String settlementApprovedByPrincipalId;
    private String settlementPaidByPrincipalId;
    private String settlementReference;
    private String settlementApprovalEvidenceSha256;
    private String settlementPaymentEvidenceSha256;
    private String closedByPrincipalId;
    private String status;
    private String reasonCode;
    private Long version;
    private LocalDateTime qualifiedAt;
    private LocalDateTime outreachStartedAt;
    private LocalDateTime briefSubmittedAt;
    private LocalDateTime briefApprovedAt;
    private LocalDateTime contentSubmittedAt;
    private LocalDateTime contentApprovedAt;
    private LocalDateTime publishVerifiedAt;
    private LocalDateTime attributionReconciledAt;
    private LocalDateTime settlementRequestedAt;
    private LocalDateTime settlementApprovedAt;
    private LocalDateTime settlementPaidAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
