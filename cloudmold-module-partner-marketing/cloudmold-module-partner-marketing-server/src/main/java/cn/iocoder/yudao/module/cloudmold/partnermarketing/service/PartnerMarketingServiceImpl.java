package cn.iocoder.yudao.module.cloudmold.partnermarketing.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingCommand;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingCommandApi;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingCommandResult;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingOperation;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingQueryApi;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingWorkflowView;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject.PartnerMarketingCaseDO;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject.PartnerMarketingCaseHistoryDO;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject.PartnerMarketingOperationDO;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.mysql.PartnerMarketingCaseMapper;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.mysql.PartnerMarketingOperationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PartnerMarketingServiceImpl implements PartnerMarketingCommandApi, PartnerMarketingQueryApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final String AGGREGATE_TYPE = "partner_marketing_case";
    static final String EVENT_TYPE = "partner_marketing.case_state_changed";
    static final String SOURCE_SYSTEM = "cloudmold-partner-marketing";

    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> CASE_STATUSES = Set.of(
            "CANDIDATE", "QUALIFIED", "OUTREACH_ACTIVE", "BRIEF_PENDING_APPROVAL", "BRIEF_APPROVED",
            "CONTENT_PENDING_APPROVAL", "CONTENT_APPROVED", "PUBLISHED_DISCLOSURE_VERIFIED",
            "SETTLEMENT_PENDING_APPROVAL", "SETTLEMENT_APPROVED", "SETTLEMENT_PAID", "CLOSED");
    private static final Set<String> COOPERATION_MODELS = Set.of("FIXED_FEE", "CPS");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    private final PartnerMarketingOperationMapper operationMapper;
    private final PartnerMarketingCaseMapper caseMapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PartnerMarketingCommandResult execute(PartnerMarketingCommand command) {
        validateEnvelope(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(tenantId + "\n" + command.getActorPrincipalId() + "\n"
                + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve partner marketing operation");
        PartnerMarketingOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "partner marketing operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different partner marketing payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing partner marketing operation is incomplete");
            PartnerMarketingCommandResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), PartnerMarketingCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case OPEN_CANDIDATE_CASE -> openCandidateCase(tenantId, command, now);
            case QUALIFY_CANDIDATE -> qualifyCandidate(tenantId, command, now);
            case START_OUTREACH -> startOutreach(tenantId, command, now);
            case SUBMIT_BRIEF -> submitBrief(tenantId, command, now);
            case APPROVE_BRIEF -> approveBrief(tenantId, command, now);
            case SUBMIT_CONTENT -> submitContent(tenantId, command, now);
            case APPROVE_CONTENT -> approveContent(tenantId, command, now);
            case VERIFY_PUBLICATION_DISCLOSURE -> verifyPublicationDisclosure(tenantId, command, now);
            case RECONCILE_ATTRIBUTION -> reconcileAttribution(tenantId, command, now);
            case APPROVE_SETTLEMENT -> approveSettlement(tenantId, command, now);
            case MARK_SETTLEMENT_PAID -> markSettlementPaid(tenantId, command, now);
            case CLOSE_CASE -> closeCase(tenantId, command, now);
        };
        appendEvent(tenantId, command, outcome);
        PartnerMarketingCommandResult result = PartnerMarketingCommandResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(outcome.aggregateType)
                .aggregateId(outcome.aggregateId)
                .aggregateVersion(outcome.aggregateVersion)
                .status(outcome.currentStatus)
                .caseId(outcome.aggregateId)
                .build();
        require(operationMapper.markSucceeded(operationId, tenantId, outcome.aggregateType, outcome.aggregateId,
                JsonUtils.toJsonString(result), now) == 1, "partner marketing operation completion conflict");
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerMarketingWorkflowView getWorkflow(String caseId) {
        requireText(caseId, "caseId", 128);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PartnerMarketingCaseDO row = caseMapper.selectOneById(tenantId, caseId.trim());
        if (row == null) {
            return PartnerMarketingWorkflowView.builder()
                    .workflowType("PartnerMarketingWorkflow")
                    .workflowInstanceKey("partner-marketing:" + caseId.trim())
                    .businessKey(caseId.trim())
                    .status(PartnerMarketingWorkflowView.Status.PREPARE)
                    .currentStatus("NOT_FOUND")
                    .phase("候选建档")
                    .terminal(false)
                    .actionRequired(true)
                    .summary("尚未创建达人合作主流程")
                    .aggregateVersion(0L)
                    .blockers(List.of("Partner Marketing SoR 中不存在该 case"))
                    .nextActions(List.of("执行 OPEN_CANDIDATE_CASE 建立候选合作档案"))
                    .artifacts(List.of())
                    .build();
        }
        List<PartnerMarketingCaseHistoryDO> history = safe(caseMapper.selectHistory(tenantId, row.getCaseId()));
        List<PartnerMarketingWorkflowView.Artifact> artifacts = artifacts(row, history);
        if ("CLOSED".equals(row.getStatus())) {
            List<String> blockers = terminalBlockers(row, history);
            if (blockers.isEmpty()) {
                return view(row, PartnerMarketingWorkflowView.Status.SUCCEEDED, true, false,
                        "已关单", "达人合作已完成候选、内容、归因、结算与付款闭环", blockers, List.of(), artifacts);
            }
            return view(row, PartnerMarketingWorkflowView.Status.FAILED, false, true,
                    "终态校验失败", "case 已关闭，但终态严格校验仍存在缺口", blockers,
                    List.of("补齐终态缺口后重新执行 CLOSE_CASE"), artifacts);
        }
        return switch (row.getStatus()) {
            case "CANDIDATE" -> view(row, PartnerMarketingWorkflowView.Status.WAITING, false, true,
                    "候选准入", "候选已建档，等待风控与资质确认", List.of("风险/资质尚未确认"),
                    List.of("执行 QUALIFY_CANDIDATE"), artifacts);
            case "QUALIFIED" -> view(row, PartnerMarketingWorkflowView.Status.WAITING, false, true,
                    "商务接洽", "候选已通过风控，等待外联接洽", List.of("尚未发起商务接触"),
                    List.of("执行 START_OUTREACH"), artifacts);
            case "OUTREACH_ACTIVE" -> view(row, PartnerMarketingWorkflowView.Status.RUNNING, false, true,
                    "Brief 提交", "商务接触已发起，等待合作 brief 提交", List.of("合作 brief 未提交"),
                    List.of("执行 SUBMIT_BRIEF"), artifacts);
            case "BRIEF_PENDING_APPROVAL" -> view(row, PartnerMarketingWorkflowView.Status.RUNNING, false, true,
                    "Brief 审批", "合作 brief 已提交，等待审核", List.of("合作预算与玩法尚未批准"),
                    List.of("执行 APPROVE_BRIEF"), artifacts);
            case "BRIEF_APPROVED" -> view(row, PartnerMarketingWorkflowView.Status.RUNNING, false, true,
                    "内容交付", "合作 brief 已批准，等待内容提报", List.of("内容草稿尚未提交"),
                    List.of("执行 SUBMIT_CONTENT"), artifacts);
            case "CONTENT_PENDING_APPROVAL" -> view(row, PartnerMarketingWorkflowView.Status.RUNNING, false, true,
                    "内容审批", "内容草稿已提交，等待审核", List.of("内容尚未批准"),
                    List.of("执行 APPROVE_CONTENT"), artifacts);
            case "CONTENT_APPROVED" -> view(row, PartnerMarketingWorkflowView.Status.RUNNING, false, true,
                    "外部发布", "内容已批准，等待发布与披露核验", List.of("外部发布/广告披露尚未核验"),
                    List.of("执行 VERIFY_PUBLICATION_DISCLOSURE"), artifacts);
            case "PUBLISHED_DISCLOSURE_VERIFIED" -> view(row, PartnerMarketingWorkflowView.Status.RUNNING, false, true,
                    "归因对账", "发布与披露已核验，等待真实消费者归因对账", List.of("真实归因引用与金额尚未对齐"),
                    List.of("执行 RECONCILE_ATTRIBUTION"), artifacts);
            case "SETTLEMENT_PENDING_APPROVAL" -> view(row, PartnerMarketingWorkflowView.Status.RUNNING, false, true,
                    "结算审批", "归因已对齐，等待结算复核", List.of("结算尚未由独立复核人批准"),
                    List.of("执行 APPROVE_SETTLEMENT"), artifacts);
            case "SETTLEMENT_APPROVED" -> view(row, PartnerMarketingWorkflowView.Status.RUNNING, false, true,
                    "付款执行", "结算已批准，等待付款回执", List.of("结算款尚未支付"),
                    List.of("执行 MARK_SETTLEMENT_PAID"), artifacts);
            case "SETTLEMENT_PAID" -> view(row, PartnerMarketingWorkflowView.Status.RUNNING, false, true,
                    "案例关单", "付款已完成，等待最终关单", List.of("主流程尚未关单"),
                    List.of("执行 CLOSE_CASE"), artifacts);
            default -> view(row, PartnerMarketingWorkflowView.Status.FAILED, false, true,
                    "未知状态", "Partner Marketing case 处于未识别状态",
                    List.of("未知 case 状态: " + row.getStatus()), List.of(), artifacts);
        };
    }

    private Outcome openCandidateCase(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.CandidateCaseDefinition input =
                nonNull(command.getCandidateCase(), "candidateCase is required");
        requireRef(input.getCreatorPrincipalId(), "creatorPrincipalId", 128);
        require(input.getCreatorPrincipalId().equals(command.getActorPrincipalId()),
                "opening actor must equal creatorPrincipalId");
        requireText(input.getCandidateHandle(), "candidateHandle", 128);
        requireCode(input.getPlatformCode(), "platformCode");
        requireCode(input.getRegionCode(), "regionCode");
        requireText(input.getCategoryCode(), "categoryCode", 64);
        String caseId = valueOrUuid(input.getCaseId());
        requireText(input.getCaseCode(), "caseCode", 64);
        PartnerMarketingCaseDO row = new PartnerMarketingCaseDO()
                .setCaseId(caseId)
                .setTenantId(tenantId)
                .setCaseCode(input.getCaseCode())
                .setCreatorPrincipalId(input.getCreatorPrincipalId())
                .setCandidateHandle(input.getCandidateHandle().trim())
                .setPlatformCode(upper(input.getPlatformCode()))
                .setRegionCode(upper(input.getRegionCode()))
                .setCategoryCode(input.getCategoryCode().trim())
                .setStatus("CANDIDATE")
                .setReasonCode(reason(input.getReasonCode()))
                .setVersion(1L)
                .setDisclosureVerified(Boolean.FALSE)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(caseMapper.insert(row) == 1, "failed to create partner marketing case");
        insertHistory(tenantId, row.getCaseId(), 1L, command.getOperation().name(), null, row.getStatus(),
                command.getActorPrincipalId(), row.getReasonCode(), null, now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), 1L, row.getStatus(),
                payload(row, command, null, "候选合作档案已建立"));
    }

    private Outcome qualifyCandidate(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.QualificationDefinition input =
                nonNull(command.getQualification(), "qualification is required");
        requireSha256(input.getRiskEvidenceSha256(), "riskEvidenceSha256");
        String riskLevel = upper(input.getRiskLevel());
        require(RISK_LEVELS.contains(riskLevel), "riskLevel is invalid");
        require("PASS".equals(upper(input.getRiskDecision())), "only PASS riskDecision is supported");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "CANDIDATE");
        String fromStatus = row.getStatus();
        row.setRiskLevel(riskLevel)
                .setRiskEvidenceSha256(input.getRiskEvidenceSha256())
                .setQualificationNote(trimmed(input.getQualificationNote(), 512))
                .setStatus("QUALIFIED")
                .setReasonCode(reason(input.getReasonCode()))
                .setQualifiedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(), input.getRiskEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getRiskEvidenceSha256(), "候选已通过风控与资质校验"));
    }

    private Outcome startOutreach(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.OutreachDefinition input =
                nonNull(command.getOutreach(), "outreach is required");
        requireCode(input.getOutreachChannelCode(), "outreachChannelCode");
        requireText(input.getOutreachExternalRef(), "outreachExternalRef", 128);
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "QUALIFIED");
        String fromStatus = row.getStatus();
        row.setOutreachChannelCode(upper(input.getOutreachChannelCode()))
                .setOutreachExternalRef(input.getOutreachExternalRef().trim())
                .setStatus("OUTREACH_ACTIVE")
                .setReasonCode(reason(input.getReasonCode()))
                .setOutreachStartedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(), null, now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, null, "商务外联已发起"));
    }

    private Outcome submitBrief(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.BriefDefinition input = nonNull(command.getBrief(), "brief is required");
        requireText(input.getCampaignId(), "campaignId", 128);
        requireText(input.getListingId(), "listingId", 128);
        require(COOPERATION_MODELS.contains(upper(input.getCooperationModel())), "cooperationModel is invalid");
        requirePositive(input.getBudgetAmountMinor(), "budgetAmountMinor");
        requireCurrency(input.getCurrencyCode());
        requireText(input.getBriefSummary(), "briefSummary", 512);
        requireSha256(input.getBriefEvidenceSha256(), "briefEvidenceSha256");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "OUTREACH_ACTIVE");
        String fromStatus = row.getStatus();
        row.setCampaignId(input.getCampaignId().trim())
                .setListingId(input.getListingId().trim())
                .setCooperationModel(upper(input.getCooperationModel()))
                .setBriefBudgetAmountMinor(input.getBudgetAmountMinor())
                .setCurrencyCode(upper(input.getCurrencyCode()))
                .setBriefSummary(input.getBriefSummary().trim())
                .setBriefEvidenceSha256(input.getBriefEvidenceSha256())
                .setBriefSubmittedByPrincipalId(command.getActorPrincipalId())
                .setStatus("BRIEF_PENDING_APPROVAL")
                .setReasonCode(reason(input.getReasonCode()))
                .setBriefSubmittedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(), input.getBriefEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getBriefEvidenceSha256(), "合作 brief 已提交待审"));
    }

    private Outcome approveBrief(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.BriefDefinition input = nonNull(command.getBrief(), "brief is required");
        requireSha256(input.getApprovalEvidenceSha256(), "approvalEvidenceSha256");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "BRIEF_PENDING_APPROVAL");
        require(!command.getActorPrincipalId().equals(row.getBriefSubmittedByPrincipalId()),
                "brief approver must be independent from brief submitter");
        String fromStatus = row.getStatus();
        row.setBriefApprovedByPrincipalId(command.getActorPrincipalId())
                .setStatus("BRIEF_APPROVED")
                .setReasonCode(reason(input.getReasonCode()))
                .setBriefApprovedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(), input.getApprovalEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getApprovalEvidenceSha256(), "合作 brief 已批准"));
    }

    private Outcome submitContent(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.ContentDefinition input = nonNull(command.getContent(), "content is required");
        requireText(input.getContentSummary(), "contentSummary", 512);
        requireSha256(input.getContentEvidenceSha256(), "contentEvidenceSha256");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "BRIEF_APPROVED");
        String fromStatus = row.getStatus();
        row.setContentSummary(input.getContentSummary().trim())
                .setContentEvidenceSha256(input.getContentEvidenceSha256())
                .setContentSubmittedByPrincipalId(command.getActorPrincipalId())
                .setStatus("CONTENT_PENDING_APPROVAL")
                .setReasonCode(reason(input.getReasonCode()))
                .setContentSubmittedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(), input.getContentEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getContentEvidenceSha256(), "内容草稿已提交待审"));
    }

    private Outcome approveContent(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.ContentDefinition input = nonNull(command.getContent(), "content is required");
        requireSha256(input.getApprovalEvidenceSha256(), "approvalEvidenceSha256");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "CONTENT_PENDING_APPROVAL");
        require(!command.getActorPrincipalId().equals(row.getContentSubmittedByPrincipalId()),
                "content approver must be independent from content submitter");
        String fromStatus = row.getStatus();
        row.setContentApprovedByPrincipalId(command.getActorPrincipalId())
                .setStatus("CONTENT_APPROVED")
                .setReasonCode(reason(input.getReasonCode()))
                .setContentApprovedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(), input.getApprovalEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getApprovalEvidenceSha256(), "内容草稿已批准"));
    }

    private Outcome verifyPublicationDisclosure(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.PublicationDefinition input =
                nonNull(command.getPublication(), "publication is required");
        requireText(input.getExternalPublishRef(), "externalPublishRef", 128);
        requireUrl(input.getExternalPublishUrl(), "externalPublishUrl");
        requireText(input.getDisclosureLabel(), "disclosureLabel", 128);
        requireSha256(input.getPublishEvidenceSha256(), "publishEvidenceSha256");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "CONTENT_APPROVED");
        String fromStatus = row.getStatus();
        row.setExternalPublishRef(input.getExternalPublishRef().trim())
                .setExternalPublishUrl(input.getExternalPublishUrl().trim())
                .setDisclosureLabel(input.getDisclosureLabel().trim())
                .setPublishEvidenceSha256(input.getPublishEvidenceSha256())
                .setDisclosureVerified(Boolean.TRUE)
                .setPublishVerifiedByPrincipalId(command.getActorPrincipalId())
                .setStatus("PUBLISHED_DISCLOSURE_VERIFIED")
                .setReasonCode(reason(input.getReasonCode()))
                .setPublishVerifiedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(), input.getPublishEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getPublishEvidenceSha256(), "外部发布与广告披露已核验"));
    }

    private Outcome reconcileAttribution(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.AttributionDefinition input =
                nonNull(command.getAttribution(), "attribution is required");
        requirePositive(input.getAttributedOrderCount() == null ? null : input.getAttributedOrderCount().longValue(),
                "attributedOrderCount");
        requireText(input.getAttributedOrderId(), "attributedOrderId", 128);
        requireText(input.getAttributedPaymentId(), "attributedPaymentId", 128);
        requireText(input.getAttributionSourceRef(), "attributionSourceRef", 128);
        requirePositive(input.getGrossSettlementAmountMinor(), "grossSettlementAmountMinor");
        requireNonNegative(input.getPlatformFeeAmountMinor(), "platformFeeAmountMinor");
        requireNonNegative(input.getTaxWithholdingAmountMinor(), "taxWithholdingAmountMinor");
        requirePositive(input.getNetPayableAmountMinor(), "netPayableAmountMinor");
        requireSha256(input.getAttributionEvidenceSha256(), "attributionEvidenceSha256");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "PUBLISHED_DISCLOSURE_VERIFIED");
        require(Boolean.TRUE.equals(row.getDisclosureVerified())
                        && text(row.getExternalPublishRef()) && text(row.getExternalPublishUrl()),
                "publication disclosure must be verified before attribution reconciliation");
        require(text(row.getCooperationModel()) && row.getBriefBudgetAmountMinor() != null
                        && text(row.getCurrencyCode()) && text(row.getCampaignId()) && text(row.getListingId()),
                "approved brief must freeze campaign, listing, model, budget and currency before attribution");
        require(input.getGrossSettlementAmountMinor()
                        .equals(sum(input.getPlatformFeeAmountMinor(), input.getTaxWithholdingAmountMinor(),
                                input.getNetPayableAmountMinor())),
                "gross settlement must equal platform fee plus tax withholding plus net payable");
        if ("FIXED_FEE".equals(row.getCooperationModel())) {
            require(Objects.equals(input.getGrossSettlementAmountMinor(), row.getBriefBudgetAmountMinor()),
                    "fixed-fee reconciliation must exactly conserve approved brief budget");
        } else {
            require(input.getGrossSettlementAmountMinor() <= row.getBriefBudgetAmountMinor(),
                    "cps reconciliation cannot exceed approved brief budget cap");
        }
        String fromStatus = row.getStatus();
        row.setAttributedOrderCount(input.getAttributedOrderCount())
                .setAttributedOrderId(input.getAttributedOrderId().trim())
                .setAttributedPaymentId(input.getAttributedPaymentId().trim())
                .setAttributionSourceRef(input.getAttributionSourceRef().trim())
                .setGrossSettlementAmountMinor(input.getGrossSettlementAmountMinor())
                .setPlatformFeeAmountMinor(input.getPlatformFeeAmountMinor())
                .setTaxWithholdingAmountMinor(input.getTaxWithholdingAmountMinor())
                .setNetPayableAmountMinor(input.getNetPayableAmountMinor())
                .setAttributionEvidenceSha256(input.getAttributionEvidenceSha256())
                .setSettlementRequestedByPrincipalId(command.getActorPrincipalId())
                .setStatus("SETTLEMENT_PENDING_APPROVAL")
                .setReasonCode(reason(input.getReasonCode()))
                .setAttributionReconciledAt(now)
                .setSettlementRequestedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(),
                input.getAttributionEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getAttributionEvidenceSha256(), "真实消费者归因与结算金额已对齐"));
    }

    private Outcome approveSettlement(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.SettlementApprovalDefinition input =
                nonNull(command.getSettlementApproval(), "settlementApproval is required");
        requirePositive(input.getApprovedNetPayableAmountMinor(), "approvedNetPayableAmountMinor");
        requireSha256(input.getApprovalEvidenceSha256(), "approvalEvidenceSha256");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "SETTLEMENT_PENDING_APPROVAL");
        require(text(row.getAttributedOrderId()) && text(row.getAttributedPaymentId()) && text(row.getAttributionSourceRef()),
                "settlement approval requires immutable attributed order/payment/source references");
        require(Objects.equals(input.getApprovedNetPayableAmountMinor(), row.getNetPayableAmountMinor()),
                "approved net payable amount must exactly match reconciled amount");
        require(!command.getActorPrincipalId().equals(row.getCreatorPrincipalId()),
                "settlement confirmer must be independent from case creator");
        require(!command.getActorPrincipalId().equals(row.getSettlementRequestedByPrincipalId()),
                "settlement confirmer must be independent from settlement requester");
        String fromStatus = row.getStatus();
        row.setSettlementApprovedByPrincipalId(command.getActorPrincipalId())
                .setSettlementApprovalEvidenceSha256(input.getApprovalEvidenceSha256())
                .setStatus("SETTLEMENT_APPROVED")
                .setReasonCode(reason(input.getReasonCode()))
                .setSettlementApprovedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(),
                input.getApprovalEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getApprovalEvidenceSha256(), "结算已由独立复核人批准"));
    }

    private Outcome markSettlementPaid(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.SettlementPaymentDefinition input =
                nonNull(command.getSettlementPayment(), "settlementPayment is required");
        requirePositive(input.getPaidNetPayableAmountMinor(), "paidNetPayableAmountMinor");
        requireText(input.getSettlementReference(), "settlementReference", 128);
        requireSha256(input.getPaymentEvidenceSha256(), "paymentEvidenceSha256");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "SETTLEMENT_APPROVED");
        require(Objects.equals(input.getPaidNetPayableAmountMinor(), row.getNetPayableAmountMinor()),
                "paid net payable amount must exactly match approved amount");
        require(!command.getActorPrincipalId().equals(row.getSettlementApprovedByPrincipalId()),
                "settlement payer should be independent from settlement approver");
        String fromStatus = row.getStatus();
        row.setSettlementPaidByPrincipalId(command.getActorPrincipalId())
                .setSettlementReference(input.getSettlementReference().trim())
                .setSettlementPaymentEvidenceSha256(input.getPaymentEvidenceSha256())
                .setStatus("SETTLEMENT_PAID")
                .setReasonCode(reason(input.getReasonCode()))
                .setSettlementPaidAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(),
                input.getPaymentEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getPaymentEvidenceSha256(), "结算款已支付"));
    }

    private Outcome closeCase(Long tenantId, PartnerMarketingCommand command, LocalDateTime now) {
        PartnerMarketingCommand.CaseCloseDefinition input = nonNull(command.getCaseClose(), "caseClose is required");
        requireSha256(input.getClosureEvidenceSha256(), "closureEvidenceSha256");
        PartnerMarketingCaseDO row = requireCaseForUpdate(tenantId, input.getCaseId());
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireStatus(row, "SETTLEMENT_PAID");
        List<String> blockers = preCloseBlockers(row);
        require(blockers.isEmpty(), "close case requires complete publish, attribution and settlement closure");
        String fromStatus = row.getStatus();
        row.setClosedByPrincipalId(command.getActorPrincipalId())
                .setStatus("CLOSED")
                .setReasonCode(reason(input.getReasonCode()))
                .setClosedAt(now);
        persistTransition(tenantId, row, fromStatus, command, input.getReasonCode(), input.getClosureEvidenceSha256(), now);
        return new Outcome(AGGREGATE_TYPE, row.getCaseId(), row.getVersion(), row.getStatus(),
                payload(row, command, input.getClosureEvidenceSha256(), "达人合作主流程已关单"));
    }

    private void persistTransition(Long tenantId, PartnerMarketingCaseDO row, String fromStatus,
                                   PartnerMarketingCommand command, String reasonCode,
                                   String evidenceSha256, LocalDateTime now) {
        Long expectedVersion = row.getVersion();
        row.setVersion(expectedVersion + 1).setUpdatedAt(now);
        require(caseMapper.updateWorkflowState(tenantId, row, expectedVersion) == 1,
                "partner marketing case version conflict");
        insertHistory(tenantId, row.getCaseId(), row.getVersion(), command.getOperation().name(), fromStatus,
                row.getStatus(), command.getActorPrincipalId(), reason(reasonCode), evidenceSha256, now);
    }

    private void insertHistory(Long tenantId, String caseId, Long version, String commandType, String fromStatus,
                               String toStatus, String actorPrincipalId, String reasonCode,
                               String evidenceSha256, LocalDateTime now) {
        PartnerMarketingCaseHistoryDO history = new PartnerMarketingCaseHistoryDO()
                .setCaseId(caseId)
                .setTenantId(tenantId)
                .setAggregateVersion(version)
                .setCommandType(commandType)
                .setFromStatus(fromStatus)
                .setToStatus(toStatus)
                .setActorPrincipalId(actorPrincipalId)
                .setReasonCode(reasonCode)
                .setEvidenceSha256(evidenceSha256)
                .setCreatedAt(now);
        require(caseMapper.insertHistory(history) == 1, "failed to persist partner marketing history");
    }

    private void appendEvent(Long tenantId, PartnerMarketingCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EVENT_TYPE)
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType(outcome.aggregateType)
                .aggregateId(outcome.aggregateId)
                .aggregateVersion(outcome.aggregateVersion)
                .eventSequence((short) 1)
                .occurredAt(command.getOccurredAt())
                .traceId(command.getRunId())
                .correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey())
                .payload(outcome.payload)
                .headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse.canonical.partner_marketing")
                .maxAttempts(16)
                .build());
    }

    private PartnerMarketingCaseDO requireCaseForUpdate(Long tenantId, String caseId) {
        requireText(caseId, "caseId", 128);
        return nonNull(caseMapper.selectForUpdate(tenantId, caseId.trim()), "partner marketing case not found");
    }

    private static void validateEnvelope(PartnerMarketingCommand command) {
        require(command != null, "partner marketing command is required");
        require(command.getOperation() != null, "operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireRef(command.getRunId(), "runId", 128);
        requireRef(command.getActorPrincipalId(), "actorPrincipalId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (StringUtils.hasText(command.getCausationId())) {
            requireUuid(command.getCausationId(), "causationId");
        }
    }

    private static List<PartnerMarketingWorkflowView.Artifact> artifacts(PartnerMarketingCaseDO row,
                                                                         List<PartnerMarketingCaseHistoryDO> history) {
        List<PartnerMarketingWorkflowView.Artifact> values = new ArrayList<>();
        values.add(artifact("PARTNER_MARKETING_CASE", row.getCaseId(), row.getStatus(), row.getVersion(),
                "达人合作主流程"));
        if (text(row.getCampaignId()) || text(row.getListingId())) {
            values.add(artifact("BRIEF_SCOPE",
                    row.getCampaignId() == null ? row.getListingId() : row.getCampaignId(),
                    row.getStatus(), row.getVersion(),
                    "活动 " + blank(row.getCampaignId()) + " / 商品 " + blank(row.getListingId())));
        }
        if (text(row.getBriefEvidenceSha256())) {
            values.add(artifact("BRIEF", row.getCaseId(), row.getBriefApprovedAt() == null ? "SUBMITTED" : "APPROVED",
                    row.getVersion(), "合作 brief"));
        }
        if (text(row.getContentEvidenceSha256())) {
            values.add(artifact("CONTENT", row.getCaseId(),
                    row.getContentApprovedAt() == null ? "SUBMITTED" : "APPROVED", row.getVersion(), "内容草稿"));
        }
        if (text(row.getExternalPublishRef())) {
            values.add(artifact("PUBLICATION", row.getExternalPublishRef(),
                    Boolean.TRUE.equals(row.getDisclosureVerified()) ? "VERIFIED" : "PUBLISHED",
                    row.getVersion(), "外部发布与披露"));
        }
        if (text(row.getAttributedOrderId()) || text(row.getAttributedPaymentId())) {
            values.add(artifact("ATTRIBUTION", row.getAttributedOrderId(), row.getStatus(), row.getVersion(),
                    "归因订单 " + blank(row.getAttributedOrderId()) + " / 支付 " + blank(row.getAttributedPaymentId())));
        }
        if (text(row.getSettlementReference())) {
            values.add(artifact("SETTLEMENT", row.getSettlementReference(),
                    row.getClosedAt() == null ? row.getStatus() : "CLOSED", row.getVersion(), "结算与付款回执"));
        }
        if (!history.isEmpty()) {
            PartnerMarketingCaseHistoryDO last = history.get(history.size() - 1);
            values.add(artifact("STATUS_HISTORY", String.valueOf(last.getHistoryId()), last.getToStatus(),
                    last.getAggregateVersion(), "最新状态历史"));
        }
        return values;
    }

    private static PartnerMarketingWorkflowView view(PartnerMarketingCaseDO row, PartnerMarketingWorkflowView.Status status,
                                                     boolean terminal, boolean actionRequired, String phase,
                                                     String summary, List<String> blockers,
                                                     List<String> nextActions,
                                                     List<PartnerMarketingWorkflowView.Artifact> artifacts) {
        return PartnerMarketingWorkflowView.builder()
                .workflowType("PartnerMarketingWorkflow")
                .workflowInstanceKey("partner-marketing:" + row.getCaseId())
                .businessKey(row.getCaseId())
                .currentStatus(row.getStatus())
                .status(status)
                .phase(phase)
                .terminal(terminal)
                .actionRequired(actionRequired)
                .summary(summary)
                .aggregateVersion(row.getVersion())
                .blockers(blockers)
                .nextActions(nextActions)
                .artifacts(artifacts)
                .build();
    }

    private static List<String> preCloseBlockers(PartnerMarketingCaseDO row) {
        List<String> blockers = new ArrayList<>();
        if (!Boolean.TRUE.equals(row.getDisclosureVerified())
                || !text(row.getExternalPublishRef()) || !text(row.getExternalPublishUrl())
                || !text(row.getDisclosureLabel()) || row.getPublishVerifiedAt() == null) {
            blockers.add("外部发布与广告披露核验未完整冻结");
        }
        if (!text(row.getAttributedOrderId()) || !text(row.getAttributedPaymentId())
                || !text(row.getAttributionSourceRef()) || row.getAttributedOrderCount() == null
                || row.getAttributedOrderCount() <= 0 || row.getAttributionReconciledAt() == null) {
            blockers.add("真实消费者归因引用未完整落库");
        }
        if (row.getGrossSettlementAmountMinor() == null || row.getPlatformFeeAmountMinor() == null
                || row.getTaxWithholdingAmountMinor() == null || row.getNetPayableAmountMinor() == null
                || !Objects.equals(row.getGrossSettlementAmountMinor(),
                sum(row.getPlatformFeeAmountMinor(), row.getTaxWithholdingAmountMinor(), row.getNetPayableAmountMinor()))) {
            blockers.add("结算金额未满足 gross = fee + tax + net 守恒");
        }
        if ("FIXED_FEE".equals(row.getCooperationModel())
                && !Objects.equals(row.getBriefBudgetAmountMinor(), row.getGrossSettlementAmountMinor())) {
            blockers.add("固定费用模式未严格守恒 approved brief budget");
        }
        if ("CPS".equals(row.getCooperationModel()) && row.getBriefBudgetAmountMinor() != null
                && row.getGrossSettlementAmountMinor() != null
                && row.getGrossSettlementAmountMinor() > row.getBriefBudgetAmountMinor()) {
            blockers.add("CPS 模式结算超出 brief cap");
        }
        if (row.getSettlementApprovedAt() == null || row.getSettlementPaidAt() == null
                || !text(row.getSettlementReference()) || !text(row.getSettlementApprovedByPrincipalId())
                || !text(row.getSettlementPaidByPrincipalId())
                || !text(row.getSettlementApprovalEvidenceSha256())
                || !text(row.getSettlementPaymentEvidenceSha256())) {
            blockers.add("结算批准、付款回执或关单时间戳不完整");
        }
        if (text(row.getSettlementApprovedByPrincipalId())
                && row.getSettlementApprovedByPrincipalId().equals(row.getCreatorPrincipalId())) {
            blockers.add("结算复核人与创建人未隔离");
        }
        if (text(row.getSettlementApprovedByPrincipalId())
                && row.getSettlementApprovedByPrincipalId().equals(row.getSettlementRequestedByPrincipalId())) {
            blockers.add("结算复核人与结算请求人未隔离");
        }
        if (text(row.getSettlementPaidByPrincipalId())
                && row.getSettlementPaidByPrincipalId().equals(row.getSettlementApprovedByPrincipalId())) {
            blockers.add("结算付款人与结算复核人未隔离");
        }
        return blockers;
    }

    private static List<String> terminalBlockers(PartnerMarketingCaseDO row, List<PartnerMarketingCaseHistoryDO> history) {
        List<String> blockers = new ArrayList<>(preCloseBlockers(row));
        if (row.getClosedAt() == null) {
            blockers.add("关单时间戳缺失");
        }
        if (history.isEmpty()) {
            blockers.add("状态历史为空");
        } else {
            PartnerMarketingCaseHistoryDO last = history.get(history.size() - 1);
            if (!Objects.equals(last.getAggregateVersion(), row.getVersion()) || !"CLOSED".equals(last.getToStatus())) {
                blockers.add("状态历史未与 CLOSED 终态对齐");
            }
        }
        return blockers;
    }

    private static Map<String, Object> payload(PartnerMarketingCaseDO row, PartnerMarketingCommand command,
                                               String evidenceSha256, String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("case_id", row.getCaseId());
        payload.put("case_code", row.getCaseCode());
        payload.put("candidate_handle", row.getCandidateHandle());
        payload.put("platform_code", row.getPlatformCode());
        payload.put("region_code", row.getRegionCode());
        payload.put("category_code", row.getCategoryCode());
        payload.put("campaign_id", row.getCampaignId());
        payload.put("listing_id", row.getListingId());
        payload.put("current_status", row.getStatus());
        payload.put("aggregate_version", row.getVersion());
        payload.put("actor_principal_id", command.getActorPrincipalId());
        payload.put("summary", summary);
        if (text(row.getCooperationModel())) payload.put("cooperation_model", row.getCooperationModel());
        if (row.getBriefBudgetAmountMinor() != null) payload.put("brief_budget_amount_minor", row.getBriefBudgetAmountMinor());
        if (text(row.getCurrencyCode())) payload.put("currency_code", row.getCurrencyCode());
        if (text(row.getExternalPublishRef())) payload.put("external_publish_ref", row.getExternalPublishRef());
        if (text(row.getExternalPublishUrl())) payload.put("external_publish_url", row.getExternalPublishUrl());
        if (text(row.getDisclosureLabel())) payload.put("disclosure_label", row.getDisclosureLabel());
        if (Boolean.TRUE.equals(row.getDisclosureVerified())) payload.put("disclosure_verified", true);
        if (text(row.getAttributedOrderId())) payload.put("attributed_order_id", row.getAttributedOrderId());
        if (text(row.getAttributedPaymentId())) payload.put("attributed_payment_id", row.getAttributedPaymentId());
        if (text(row.getAttributionSourceRef())) payload.put("attribution_source_ref", row.getAttributionSourceRef());
        if (row.getAttributedOrderCount() != null) payload.put("attributed_order_count", row.getAttributedOrderCount());
        if (row.getGrossSettlementAmountMinor() != null) payload.put("gross_settlement_amount_minor", row.getGrossSettlementAmountMinor());
        if (row.getPlatformFeeAmountMinor() != null) payload.put("platform_fee_amount_minor", row.getPlatformFeeAmountMinor());
        if (row.getTaxWithholdingAmountMinor() != null) payload.put("tax_withholding_amount_minor", row.getTaxWithholdingAmountMinor());
        if (row.getNetPayableAmountMinor() != null) payload.put("net_payable_amount_minor", row.getNetPayableAmountMinor());
        if (text(row.getSettlementReference())) payload.put("settlement_reference", row.getSettlementReference());
        if (text(row.getSettlementApprovalEvidenceSha256())) {
            payload.put("settlement_approval_evidence_sha256", row.getSettlementApprovalEvidenceSha256());
        }
        if (text(row.getSettlementPaymentEvidenceSha256())) {
            payload.put("settlement_payment_evidence_sha256", row.getSettlementPaymentEvidenceSha256());
        }
        if (text(evidenceSha256)) payload.put("evidence_sha256", evidenceSha256);
        return payload;
    }

    private static PartnerMarketingWorkflowView.Artifact artifact(String type, String id, String status, Long version,
                                                                  String label) {
        return PartnerMarketingWorkflowView.Artifact.builder()
                .type(type).id(id).status(status).version(version).label(label).build();
    }

    private static List<PartnerMarketingCaseHistoryDO> safe(List<PartnerMarketingCaseHistoryDO> history) {
        return history == null ? List.of() : history;
    }

    private static void requireStatus(PartnerMarketingCaseDO row, String status) {
        require(status.equals(row.getStatus()), "expected case status " + status + " but found " + row.getStatus());
    }

    private static void requireExpectedVersion(Long expectedVersion, Long currentVersion) {
        require(expectedVersion != null, "expectedVersion is required");
        require(Objects.equals(expectedVersion, currentVersion),
                "expectedVersion does not match current aggregate version");
    }

    private static String reason(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String valueOrUuid(String value) {
        return StringUtils.hasText(value) ? value.trim() : UUID.randomUUID().toString();
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean text(String value) {
        return StringUtils.hasText(value);
    }

    private static String blank(String value) {
        return value == null ? "-" : value;
    }

    private static String trimmed(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        require(trimmed.length() <= max, "text exceeds max length " + max);
        return trimmed;
    }

    private static void requireUuid(String value, String field) {
        try {
            require(value != null, field + " is required");
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private static void requireCurrency(String value) {
        require(value != null && value.trim().matches("^[A-Z]{3}$"), "currencyCode is invalid");
    }

    private static void requireUrl(String value, String field) {
        requireText(value, field, 512);
        String trimmed = value.trim();
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://"), field + " must start with http(s)://");
    }

    private static void requireCode(String value, String field) {
        require(value != null && SAFE_CODE.matcher(upper(value)).matches(), field + " is invalid");
    }

    private static void requireRef(String value, String field, int maxLength) {
        requireText(value, field, maxLength);
        require(SAFE_REF.matcher(value.trim()).matches(), field + " is invalid");
    }

    private static void requireText(String value, String field, int maxLength) {
        require(StringUtils.hasText(value), field + " is required");
        require(value.trim().length() <= maxLength, field + " exceeds max length " + maxLength);
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(), field + " must be a 64-char lowercase sha256");
    }

    private static void requirePositive(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
    }

    private static void requireNonNegative(Long value, String field) {
        require(value != null && value >= 0, field + " must be non-negative");
    }

    private static Long sum(Long a, Long b, Long c) {
        require(a != null && b != null && c != null, "amount components are required");
        return Math.addExact(Math.addExact(a, b), c);
    }

    private static <T> T nonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean expression, String message) {
        if (!expression) {
            throw new IllegalStateException(message);
        }
    }

    private record Outcome(String aggregateType, String aggregateId, Long aggregateVersion,
                           String currentStatus, Map<String, Object> payload) {
    }
}
