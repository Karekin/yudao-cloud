package cn.iocoder.yudao.module.cloudmold.catalog.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningCommand;
import cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningCommandApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningCommandResult;
import cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningQueryApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningWorkflowView;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentCandidateDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentPlanningOperationDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentWaveDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentWaveHistoryDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.AssortmentCandidateMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.AssortmentPlanningOperationMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.AssortmentWaveMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AssortmentPlanningServiceImpl
        implements AssortmentPlanningCommandApi, AssortmentPlanningQueryApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final String AGGREGATE_TYPE = "assortment_wave";
    static final String EVENT_TYPE = "catalog.assortment_wave_state_changed";
    static final String SOURCE_SYSTEM = "cloudmold-catalog";

    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> SEASONS =
            Set.of("SPRING", "SUMMER", "AUTUMN", "WINTER", "ALL_SEASON");
    private static final Set<String> PRICE_BANDS = Set.of("ENTRY", "CORE", "PREMIUM");
    private static final Set<String> SIGNAL_TYPES =
            Set.of("SEARCH_TREND", "SOCIAL_TREND", "SALES_GAP", "CUSTOMER_RESEARCH", "COMPETITOR");

    private final AssortmentPlanningOperationMapper operationMapper;
    private final AssortmentWaveMapper waveMapper;
    private final AssortmentCandidateMapper candidateMapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssortmentPlanningCommandResult execute(AssortmentPlanningCommand command) {
        validateEnvelope(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(
                tenantId + "\n" + command.getActorPrincipalId() + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(),
                command.getOperation().name(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve assortment planning operation");
        AssortmentPlanningOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "assortment planning operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different assortment planning payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing assortment planning operation is incomplete");
            AssortmentPlanningCommandResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), AssortmentPlanningCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_WAVE -> createWave(tenantId, command, now);
            case ADD_CANDIDATE -> addCandidate(tenantId, command, now);
            case EVALUATE_CANDIDATE -> evaluateCandidate(tenantId, command, now);
            case SELECT_PORTFOLIO -> selectPortfolio(tenantId, command, now);
            case APPROVE_WAVE -> approveWave(tenantId, command, now);
            case PUBLISH_WAVE -> publishWave(tenantId, command, now);
        };
        appendEvent(tenantId, command, outcome);
        AssortmentPlanningCommandResult result = result(operationId, outcome);
        require(operationMapper.markSucceeded(operationId, tenantId, AGGREGATE_TYPE,
                outcome.wave().getWaveId(), JsonUtils.toJsonString(result), now) == 1,
                "assortment planning operation completion conflict");
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AssortmentPlanningWorkflowView getWorkflow(String waveId) {
        requireRef(waveId, "waveId", 128);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        AssortmentWaveDO wave = nonNull(waveMapper.selectOneById(tenantId, waveId.trim()),
                "assortment wave not found");
        List<AssortmentCandidateDO> candidates = safe(candidateMapper.selectByWave(tenantId, wave.getWaveId()));
        boolean terminal = "PUBLISHED".equals(wave.getStatus());
        List<AssortmentPlanningWorkflowView.Blocker> blockers = blockers(wave, candidates);
        String phase = switch (wave.getStatus()) {
            case "DRAFT", "BUILDING" -> "CANDIDATE_RESEARCH";
            case "SELECTED" -> "WAITING_APPROVAL";
            case "APPROVED" -> "WAITING_PUBLICATION";
            case "PUBLISHED" -> "COMPLETED";
            default -> "UNKNOWN";
        };
        String actionRequired = switch (wave.getStatus()) {
            case "DRAFT", "BUILDING" -> "补齐候选、AI 评分并形成满足价格带和毛利门槛的组合";
            case "SELECTED" -> "完成独立 R3 审批";
            case "APPROVED" -> "发布上新日历并形成下游商品开发交接";
            case "PUBLISHED" -> "无";
            default -> "人工检查状态";
        };
        return AssortmentPlanningWorkflowView.builder()
                .workflowType("AssortmentWaveWorkflow")
                .businessKey(wave.getWaveCode())
                .waveId(wave.getWaveId())
                .waveCode(wave.getWaveCode())
                .status(wave.getStatus())
                .aggregateVersion(wave.getVersion())
                .terminal(terminal)
                .phase(phase)
                .summary(summary(wave))
                .actionRequired(actionRequired)
                .planningYear(wave.getPlanningYear())
                .seasonCode(wave.getSeasonCode())
                .categoryCode(wave.getCategoryCode())
                .trendBrief(wave.getTrendBrief())
                .targetAudience(wave.getTargetAudience())
                .targetStyleCount(wave.getTargetStyleCount())
                .candidateCount(wave.getCandidateCount())
                .evaluatedCandidateCount(wave.getEvaluatedCandidateCount())
                .selectedStyleCount(wave.getSelectedStyleCount())
                .targetGrossMarginBps(wave.getTargetGrossMarginBps())
                .maxReturnRateBps(wave.getMaxReturnRateBps())
                .launchStartDate(wave.getLaunchStartDate())
                .launchEndDate(wave.getLaunchEndDate())
                .currencyCode(wave.getCurrencyCode())
                .decisionPolicyVersion(wave.getDecisionPolicyVersion())
                .decisionSummary(wave.getDecisionSummary())
                .launchCalendarRef(wave.getLaunchCalendarRef())
                .downstreamHandoffRef(wave.getDownstreamHandoffRef())
                .candidates(candidates.stream().map(AssortmentPlanningServiceImpl::candidateView).toList())
                .artifacts(artifacts(wave, candidates))
                .blockers(blockers)
                .build();
    }

    private Outcome createWave(Long tenantId, AssortmentPlanningCommand command, LocalDateTime now) {
        AssortmentPlanningCommand.WaveDefinition definition =
                nonNull(command.getWave(), "wave definition is required");
        requireRef(definition.getWaveId(), "wave.waveId", 128);
        requireCode(definition.getWaveCode(), "wave.waveCode");
        require(definition.getPlanningYear() != null
                        && definition.getPlanningYear() >= 2020
                        && definition.getPlanningYear() <= 2100,
                "wave.planningYear is invalid");
        String season = upper(definition.getSeasonCode());
        require(SEASONS.contains(season), "wave.seasonCode is invalid");
        requireCode(definition.getCategoryCode(), "wave.categoryCode");
        requireText(definition.getTrendBrief(), "wave.trendBrief", 1000);
        requireText(definition.getTargetAudience(), "wave.targetAudience", 512);
        requireRange(definition.getTargetStyleCount(), 2, 20, "wave.targetStyleCount");
        requirePositive(definition.getTargetPriceFloorMinor(), "wave.targetPriceFloorMinor");
        requirePositive(definition.getTargetPriceCeilingMinor(), "wave.targetPriceCeilingMinor");
        require(definition.getTargetPriceCeilingMinor() > definition.getTargetPriceFloorMinor(),
                "wave target price ceiling must exceed floor");
        requireRange(definition.getTargetGrossMarginBps(), 1, 9999, "wave.targetGrossMarginBps");
        requireRange(definition.getMaxReturnRateBps(), 1, 9999, "wave.maxReturnRateBps");
        require(definition.getLaunchStartDate() != null && definition.getLaunchEndDate() != null
                        && !definition.getLaunchEndDate().isBefore(definition.getLaunchStartDate()),
                "wave launch date range is invalid");
        requireCurrency(definition.getCurrencyCode());
        require(waveMapper.selectOneById(tenantId, definition.getWaveId().trim()) == null,
                "assortment wave already exists");

        AssortmentWaveDO wave = new AssortmentWaveDO()
                .setWaveId(definition.getWaveId().trim())
                .setTenantId(tenantId)
                .setWaveCode(upper(definition.getWaveCode()))
                .setRunId(command.getRunId().trim())
                .setPlanningYear(definition.getPlanningYear())
                .setSeasonCode(season)
                .setCategoryCode(upper(definition.getCategoryCode()))
                .setTrendBrief(definition.getTrendBrief().trim())
                .setTargetAudience(definition.getTargetAudience().trim())
                .setTargetStyleCount(definition.getTargetStyleCount())
                .setTargetPriceFloorMinor(definition.getTargetPriceFloorMinor())
                .setTargetPriceCeilingMinor(definition.getTargetPriceCeilingMinor())
                .setTargetGrossMarginBps(definition.getTargetGrossMarginBps())
                .setMaxReturnRateBps(definition.getMaxReturnRateBps())
                .setLaunchStartDate(definition.getLaunchStartDate())
                .setLaunchEndDate(definition.getLaunchEndDate())
                .setCurrencyCode(upper(definition.getCurrencyCode()))
                .setCandidateCount(0)
                .setEvaluatedCandidateCount(0)
                .setSelectedStyleCount(0)
                .setStatus("DRAFT")
                .setReasonCode(reason(definition.getReasonCode()))
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(waveMapper.insert(wave) == 1, "failed to persist assortment wave");
        insertHistory(command, wave, null, "DRAFT", evidence(command), now);
        return new Outcome(wave, null, List.of(), "创建波段企划并冻结趋势、价格带、款量和毛利目标",
                evidence(command));
    }

    private Outcome addCandidate(Long tenantId, AssortmentPlanningCommand command, LocalDateTime now) {
        AssortmentPlanningCommand.CandidateDefinition definition =
                nonNull(command.getCandidate(), "candidate definition is required");
        AssortmentWaveDO wave = requireWaveForUpdate(tenantId, definition.getWaveId());
        require(Set.of("DRAFT", "BUILDING").contains(wave.getStatus()),
                "candidates can only be added while the wave is building");
        requireExpectedVersion(definition.getExpectedWaveVersion(), wave.getVersion());
        requireRef(definition.getCandidateId(), "candidate.candidateId", 128);
        requireCode(definition.getCandidateCode(), "candidate.candidateCode");
        requireText(definition.getProductConcept(), "candidate.productConcept", 512);
        String signalType = upper(definition.getSourceSignalType());
        require(SIGNAL_TYPES.contains(signalType), "candidate.sourceSignalType is invalid");
        requireRef(definition.getSourceSignalRef(), "candidate.sourceSignalRef", 128);
        String priceBand = upper(definition.getPriceBandCode());
        require(PRICE_BANDS.contains(priceBand), "candidate.priceBandCode is invalid");
        requirePositive(definition.getTargetPriceMinor(), "candidate.targetPriceMinor");
        requirePositive(definition.getExpectedUnitCostMinor(), "candidate.expectedUnitCostMinor");
        require(definition.getTargetPriceMinor() >= wave.getTargetPriceFloorMinor()
                        && definition.getTargetPriceMinor() <= wave.getTargetPriceCeilingMinor(),
                "candidate target price is outside the wave price range");
        require(definition.getExpectedUnitCostMinor() < definition.getTargetPriceMinor(),
                "candidate expected unit cost must be below target price");
        require(candidateMapper.selectForUpdate(tenantId, definition.getCandidateId().trim()) == null,
                "assortment candidate already exists");
        int marginBps = Math.toIntExact(
                (definition.getTargetPriceMinor() - definition.getExpectedUnitCostMinor()) * 10_000L
                        / definition.getTargetPriceMinor());
        AssortmentCandidateDO candidate = new AssortmentCandidateDO()
                .setCandidateId(definition.getCandidateId().trim())
                .setTenantId(tenantId)
                .setWaveId(wave.getWaveId())
                .setCandidateCode(upper(definition.getCandidateCode()))
                .setProductConcept(definition.getProductConcept().trim())
                .setSourceSignalType(signalType)
                .setSourceSignalRef(definition.getSourceSignalRef().trim())
                .setPriceBandCode(priceBand)
                .setTargetPriceMinor(definition.getTargetPriceMinor())
                .setExpectedUnitCostMinor(definition.getExpectedUnitCostMinor())
                .setExpectedGrossMarginBps(marginBps)
                .setStatus("DISCOVERED")
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(candidateMapper.insert(candidate) == 1, "failed to persist assortment candidate");
        String fromStatus = wave.getStatus();
        wave.setStatus("BUILDING")
                .setCandidateCount(wave.getCandidateCount() + 1)
                .setReasonCode(reason(definition.getReasonCode()));
        advanceWave(tenantId, command, wave, fromStatus, evidence(command), now);
        return new Outcome(wave, candidate.getCandidateId(), List.of(),
                "新增候选 " + candidate.getCandidateCode() + "，等待 AI 评分", evidence(command));
    }

    private Outcome evaluateCandidate(Long tenantId, AssortmentPlanningCommand command, LocalDateTime now) {
        AssortmentPlanningCommand.EvaluationDefinition definition =
                nonNull(command.getEvaluation(), "evaluation definition is required");
        AssortmentWaveDO wave = requireWaveForUpdate(tenantId, definition.getWaveId());
        require("BUILDING".equals(wave.getStatus()), "candidate evaluation requires a building wave");
        requireExpectedVersion(definition.getExpectedWaveVersion(), wave.getVersion());
        AssortmentCandidateDO candidate = requireCandidateForUpdate(
                tenantId, definition.getCandidateId(), wave.getWaveId());
        require("DISCOVERED".equals(candidate.getStatus()), "candidate has already been evaluated");
        requireRange(definition.getTrendScore(), 0, 100, "evaluation.trendScore");
        requireRange(definition.getDemandScore(), 0, 100, "evaluation.demandScore");
        requireRange(definition.getAudienceFitScore(), 0, 100, "evaluation.audienceFitScore");
        requireRange(definition.getSupplyRiskScore(), 0, 100, "evaluation.supplyRiskScore");
        requireRange(definition.getPredictedReturnRateBps(), 0, 10_000,
                "evaluation.predictedReturnRateBps");
        requireSha256(definition.getEvidenceSha256(), "evaluation.evidenceSha256");
        requireText(definition.getRationale(), "evaluation.rationale", 1000);
        int weightedScore = (30 * definition.getTrendScore()
                + 35 * definition.getDemandScore()
                + 25 * definition.getAudienceFitScore()
                + 10 * (100 - definition.getSupplyRiskScore())) / 100;
        long candidateVersion = candidate.getVersion();
        candidate.setTrendScore(definition.getTrendScore())
                .setDemandScore(definition.getDemandScore())
                .setAudienceFitScore(definition.getAudienceFitScore())
                .setSupplyRiskScore(definition.getSupplyRiskScore())
                .setPredictedReturnRateBps(definition.getPredictedReturnRateBps())
                .setWeightedScore(weightedScore)
                .setEvidenceSha256(definition.getEvidenceSha256())
                .setRationale(definition.getRationale().trim())
                .setStatus("EVALUATED")
                .setVersion(candidateVersion + 1)
                .setEvaluatedAt(now)
                .setUpdatedAt(now);
        require(candidateMapper.updateEvaluation(tenantId, candidate, candidateVersion) == 1,
                "assortment candidate evaluation version conflict");
        wave.setEvaluatedCandidateCount(wave.getEvaluatedCandidateCount() + 1)
                .setReasonCode(reason(definition.getReasonCode()));
        advanceWave(tenantId, command, wave, "BUILDING", definition.getEvidenceSha256(), now);
        return new Outcome(wave, candidate.getCandidateId(), List.of(),
                "完成候选 " + candidate.getCandidateCode() + " 的 AI 多因子评分 " + weightedScore,
                definition.getEvidenceSha256());
    }

    private Outcome selectPortfolio(Long tenantId, AssortmentPlanningCommand command, LocalDateTime now) {
        AssortmentPlanningCommand.PortfolioDefinition definition =
                nonNull(command.getPortfolio(), "portfolio definition is required");
        AssortmentWaveDO wave = requireWaveForUpdate(tenantId, definition.getWaveId());
        require("BUILDING".equals(wave.getStatus()), "portfolio selection requires a building wave");
        requireExpectedVersion(definition.getExpectedWaveVersion(), wave.getVersion());
        requireRef(definition.getDecisionPolicyVersion(), "portfolio.decisionPolicyVersion", 128);
        requireSha256(definition.getDecisionEvidenceSha256(), "portfolio.decisionEvidenceSha256");
        List<AssortmentCandidateDO> candidates = safe(
                candidateMapper.selectByWave(tenantId, wave.getWaveId()));
        require(candidates.size() >= wave.getTargetStyleCount(),
                "candidate pool is smaller than the target style count");
        require(candidates.stream().allMatch(candidate -> "EVALUATED".equals(candidate.getStatus())),
                "every candidate must be evaluated before portfolio selection");

        List<AssortmentCandidateDO> eligible = candidates.stream()
                .filter(candidate -> candidate.getExpectedGrossMarginBps() >= wave.getTargetGrossMarginBps())
                .filter(candidate -> candidate.getPredictedReturnRateBps() <= wave.getMaxReturnRateBps())
                .sorted(Comparator.comparing(AssortmentCandidateDO::getWeightedScore,
                                Comparator.reverseOrder())
                        .thenComparing(AssortmentCandidateDO::getExpectedGrossMarginBps,
                                Comparator.reverseOrder())
                        .thenComparing(AssortmentCandidateDO::getCandidateCode))
                .toList();
        List<AssortmentCandidateDO> selected = diversifiedSelection(
                eligible, wave.getTargetStyleCount());
        require(selected.size() == wave.getTargetStyleCount(),
                "eligible candidate pool cannot satisfy target style count");
        if (wave.getTargetStyleCount() > 1) {
            require(selected.stream().map(AssortmentCandidateDO::getPriceBandCode).distinct().count() >= 2,
                    "selected portfolio must cover at least two price bands");
        }
        Set<String> selectedIds = selected.stream().map(AssortmentCandidateDO::getCandidateId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        for (AssortmentCandidateDO candidate : candidates) {
            String status = selectedIds.contains(candidate.getCandidateId()) ? "SELECTED" : "REJECTED";
            require(candidateMapper.updateSelection(tenantId, wave.getWaveId(),
                    candidate.getCandidateId(), status, now) == 1,
                    "assortment candidate selection conflict");
        }
        String decisionSummary = selected.stream()
                .map(candidate -> candidate.getCandidateCode() + "/" + candidate.getPriceBandCode()
                        + "/score=" + candidate.getWeightedScore()
                        + "/margin=" + candidate.getExpectedGrossMarginBps()
                        + "/return=" + candidate.getPredictedReturnRateBps())
                .reduce((left, right) -> left + "; " + right)
                .orElseThrow();
        wave.setStatus("SELECTED")
                .setSelectedStyleCount(selected.size())
                .setDecisionPolicyVersion(definition.getDecisionPolicyVersion().trim())
                .setDecisionEvidenceSha256(definition.getDecisionEvidenceSha256())
                .setDecisionSummary(decisionSummary)
                .setSelectedAt(now)
                .setReasonCode(reason(definition.getReasonCode()));
        advanceWave(tenantId, command, wave, "BUILDING",
                definition.getDecisionEvidenceSha256(), now);
        return new Outcome(wave, null, List.copyOf(selectedIds),
                "AI 已按趋势、需求、受众、供给风险、毛利、退货率和价格带约束选出组合",
                definition.getDecisionEvidenceSha256());
    }

    private Outcome approveWave(Long tenantId, AssortmentPlanningCommand command, LocalDateTime now) {
        AssortmentPlanningCommand.ApprovalDefinition definition =
                nonNull(command.getApproval(), "approval definition is required");
        AssortmentWaveDO wave = requireWaveForUpdate(tenantId, definition.getWaveId());
        require("SELECTED".equals(wave.getStatus()), "only a selected wave can be approved");
        requireExpectedVersion(definition.getExpectedWaveVersion(), wave.getVersion());
        requireText(definition.getApprovalRef(), "approval.approvalRef", 2048);
        requireText(definition.getApprovalNote(), "approval.approvalNote", 512);
        require(wave.getSelectedStyleCount() != null
                        && Objects.equals(wave.getSelectedStyleCount(), wave.getTargetStyleCount()),
                "approved wave must meet the target style count");
        String approvalEvidence = DigestUtil.sha256Hex(definition.getApprovalRef().trim());
        wave.setStatus("APPROVED")
                .setApprovedByPrincipalId(command.getActorPrincipalId().trim())
                .setApprovalEvidenceSha256(approvalEvidence)
                .setApprovedAt(now)
                .setReasonCode(reason(definition.getReasonCode()));
        advanceWave(tenantId, command, wave, "SELECTED", approvalEvidence, now);
        return new Outcome(wave, null, selectedCandidateIds(tenantId, wave.getWaveId()),
                "波段组合已通过独立审批并冻结", approvalEvidence);
    }

    private Outcome publishWave(Long tenantId, AssortmentPlanningCommand command, LocalDateTime now) {
        AssortmentPlanningCommand.PublicationDefinition definition =
                nonNull(command.getPublication(), "publication definition is required");
        AssortmentWaveDO wave = requireWaveForUpdate(tenantId, definition.getWaveId());
        require("APPROVED".equals(wave.getStatus()), "only an approved wave can be published");
        requireExpectedVersion(definition.getExpectedWaveVersion(), wave.getVersion());
        requireRef(definition.getLaunchCalendarRef(), "publication.launchCalendarRef", 128);
        requireRef(definition.getDownstreamHandoffRef(), "publication.downstreamHandoffRef", 128);
        requireSha256(definition.getPublicationEvidenceSha256(),
                "publication.publicationEvidenceSha256");
        List<String> selectedIds = selectedCandidateIds(tenantId, wave.getWaveId());
        require(selectedIds.size() == wave.getTargetStyleCount(),
                "published wave must retain the approved selected portfolio");
        wave.setStatus("PUBLISHED")
                .setLaunchCalendarRef(definition.getLaunchCalendarRef().trim())
                .setDownstreamHandoffRef(definition.getDownstreamHandoffRef().trim())
                .setPublicationEvidenceSha256(definition.getPublicationEvidenceSha256())
                .setPublishedAt(now)
                .setReasonCode(reason(definition.getReasonCode()));
        advanceWave(tenantId, command, wave, "APPROVED",
                definition.getPublicationEvidenceSha256(), now);
        return new Outcome(wave, null, selectedIds,
                "波段企划、上新日历和商品开发交接已发布", definition.getPublicationEvidenceSha256());
    }

    private void advanceWave(Long tenantId, AssortmentPlanningCommand command,
                             AssortmentWaveDO wave, String fromStatus,
                             String evidenceSha256, LocalDateTime now) {
        long expectedVersion = wave.getVersion();
        wave.setVersion(expectedVersion + 1).setUpdatedAt(now);
        require(waveMapper.updateWorkflowState(tenantId, wave, expectedVersion) == 1,
                "assortment wave version conflict");
        insertHistory(command, wave, fromStatus, wave.getStatus(), evidenceSha256, now);
    }

    private void insertHistory(AssortmentPlanningCommand command, AssortmentWaveDO wave,
                               String fromStatus, String toStatus,
                               String evidenceSha256, LocalDateTime now) {
        AssortmentWaveHistoryDO history = new AssortmentWaveHistoryDO()
                .setWaveId(wave.getWaveId())
                .setTenantId(wave.getTenantId())
                .setAggregateVersion(wave.getVersion())
                .setCommandType(command.getOperation().name())
                .setFromStatus(fromStatus)
                .setToStatus(toStatus)
                .setActorPrincipalId(command.getActorPrincipalId())
                .setReasonCode(wave.getReasonCode())
                .setEvidenceSha256(evidenceSha256)
                .setCreatedAt(now);
        require(waveMapper.insertHistory(history) == 1, "failed to persist assortment wave history");
    }

    private void appendEvent(Long tenantId, AssortmentPlanningCommand command, Outcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("wave_id", outcome.wave().getWaveId());
        payload.put("wave_code", outcome.wave().getWaveCode());
        payload.put("planning_year", outcome.wave().getPlanningYear());
        payload.put("season_code", outcome.wave().getSeasonCode());
        payload.put("category_code", outcome.wave().getCategoryCode());
        payload.put("current_status", outcome.wave().getStatus());
        payload.put("aggregate_version", outcome.wave().getVersion());
        payload.put("candidate_count", outcome.wave().getCandidateCount());
        payload.put("evaluated_candidate_count", outcome.wave().getEvaluatedCandidateCount());
        payload.put("selected_style_count", outcome.wave().getSelectedStyleCount());
        payload.put("summary", outcome.summary());
        if (outcome.candidateId() != null) payload.put("candidate_id", outcome.candidateId());
        if (!outcome.selectedCandidateIds().isEmpty()) {
            payload.put("selected_candidate_ids", outcome.selectedCandidateIds());
        }
        if (StringUtils.hasText(outcome.evidenceSha256())) {
            payload.put("evidence_sha256", outcome.evidenceSha256());
        }
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EVENT_TYPE)
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(outcome.wave().getWaveId())
                .aggregateVersion(outcome.wave().getVersion())
                .eventSequence((short) 1)
                .occurredAt(command.getOccurredAt())
                .traceId(command.getRunId())
                .correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey())
                .payload(payload)
                .headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse.canonical.assortment")
                .maxAttempts(16)
                .build());
    }

    private static AssortmentPlanningCommandResult result(Long operationId, Outcome outcome) {
        AssortmentWaveDO wave = outcome.wave();
        return AssortmentPlanningCommandResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(wave.getWaveId())
                .aggregateVersion(wave.getVersion())
                .status(wave.getStatus())
                .waveId(wave.getWaveId())
                .candidateId(outcome.candidateId())
                .candidateCount(wave.getCandidateCount())
                .evaluatedCandidateCount(wave.getEvaluatedCandidateCount())
                .selectedStyleCount(wave.getSelectedStyleCount())
                .selectedCandidateIds(outcome.selectedCandidateIds())
                .decisionSummary(wave.getDecisionSummary())
                .build();
    }

    private static List<AssortmentCandidateDO> diversifiedSelection(
            List<AssortmentCandidateDO> eligible, int targetStyleCount) {
        List<AssortmentCandidateDO> selected = new ArrayList<>();
        Set<String> usedBands = new LinkedHashSet<>();
        for (AssortmentCandidateDO candidate : eligible) {
            if (selected.size() >= targetStyleCount) break;
            if (usedBands.add(candidate.getPriceBandCode())) selected.add(candidate);
        }
        for (AssortmentCandidateDO candidate : eligible) {
            if (selected.size() >= targetStyleCount) break;
            if (!selected.contains(candidate)) selected.add(candidate);
        }
        return selected;
    }

    private List<String> selectedCandidateIds(Long tenantId, String waveId) {
        return safe(candidateMapper.selectByWave(tenantId, waveId)).stream()
                .filter(candidate -> "SELECTED".equals(candidate.getStatus()))
                .map(AssortmentCandidateDO::getCandidateId)
                .toList();
    }

    private AssortmentWaveDO requireWaveForUpdate(Long tenantId, String waveId) {
        requireRef(waveId, "waveId", 128);
        return nonNull(waveMapper.selectForUpdate(tenantId, waveId.trim()),
                "assortment wave not found");
    }

    private AssortmentCandidateDO requireCandidateForUpdate(
            Long tenantId, String candidateId, String waveId) {
        requireRef(candidateId, "candidateId", 128);
        AssortmentCandidateDO candidate = nonNull(
                candidateMapper.selectForUpdate(tenantId, candidateId.trim()),
                "assortment candidate not found");
        require(Objects.equals(candidate.getWaveId(), waveId),
                "candidate does not belong to the assortment wave");
        return candidate;
    }

    private static AssortmentPlanningWorkflowView.Candidate candidateView(AssortmentCandidateDO row) {
        return AssortmentPlanningWorkflowView.Candidate.builder()
                .candidateId(row.getCandidateId())
                .candidateCode(row.getCandidateCode())
                .productConcept(row.getProductConcept())
                .priceBandCode(row.getPriceBandCode())
                .targetPriceMinor(row.getTargetPriceMinor())
                .expectedUnitCostMinor(row.getExpectedUnitCostMinor())
                .expectedGrossMarginBps(row.getExpectedGrossMarginBps())
                .trendScore(row.getTrendScore())
                .demandScore(row.getDemandScore())
                .audienceFitScore(row.getAudienceFitScore())
                .supplyRiskScore(row.getSupplyRiskScore())
                .predictedReturnRateBps(row.getPredictedReturnRateBps())
                .weightedScore(row.getWeightedScore())
                .status(row.getStatus())
                .rationale(row.getRationale())
                .build();
    }

    private static List<AssortmentPlanningWorkflowView.Artifact> artifacts(
            AssortmentWaveDO wave, List<AssortmentCandidateDO> candidates) {
        List<AssortmentPlanningWorkflowView.Artifact> values = new ArrayList<>();
        values.add(AssortmentPlanningWorkflowView.Artifact.builder()
                .type("ASSORTMENT_WAVE")
                .referenceId(wave.getWaveId())
                .status(wave.getStatus())
                .build());
        candidates.stream().filter(candidate -> "SELECTED".equals(candidate.getStatus()))
                .forEach(candidate -> values.add(AssortmentPlanningWorkflowView.Artifact.builder()
                        .type("SELECTED_PRODUCT_CONCEPT")
                        .referenceId(candidate.getCandidateId())
                        .status(candidate.getStatus())
                        .build()));
        if (StringUtils.hasText(wave.getLaunchCalendarRef())) {
            values.add(AssortmentPlanningWorkflowView.Artifact.builder()
                    .type("LAUNCH_CALENDAR")
                    .referenceId(wave.getLaunchCalendarRef())
                    .status("PUBLISHED")
                    .build());
        }
        if (StringUtils.hasText(wave.getDownstreamHandoffRef())) {
            values.add(AssortmentPlanningWorkflowView.Artifact.builder()
                    .type("PRODUCT_DEVELOPMENT_HANDOFF")
                    .referenceId(wave.getDownstreamHandoffRef())
                    .status("READY")
                    .build());
        }
        return values;
    }

    private static List<AssortmentPlanningWorkflowView.Blocker> blockers(
            AssortmentWaveDO wave, List<AssortmentCandidateDO> candidates) {
        if ("PUBLISHED".equals(wave.getStatus())) return List.of();
        List<AssortmentPlanningWorkflowView.Blocker> blockers = new ArrayList<>();
        if (wave.getCandidateCount() < wave.getTargetStyleCount()) {
            blockers.add(blocker("CANDIDATE_POOL_TOO_SMALL", "候选池不足以满足目标款量"));
        }
        if (!Objects.equals(wave.getCandidateCount(), wave.getEvaluatedCandidateCount())) {
            blockers.add(blocker("CANDIDATE_EVALUATION_INCOMPLETE", "仍有候选未完成 AI 多因子评分"));
        }
        if (Set.of("DRAFT", "BUILDING").contains(wave.getStatus())) {
            blockers.add(blocker("PORTFOLIO_NOT_SELECTED", "尚未形成满足毛利、退货率与价格带约束的组合"));
        }
        if ("SELECTED".equals(wave.getStatus())) {
            blockers.add(blocker("APPROVAL_REQUIRED", "已选组合尚未完成独立 R3 审批"));
        }
        if ("APPROVED".equals(wave.getStatus())) {
            blockers.add(blocker("PUBLICATION_REQUIRED", "上新日历与商品开发交接尚未发布"));
        }
        long selected = candidates.stream().filter(candidate -> "SELECTED".equals(candidate.getStatus())).count();
        if (wave.getSelectedStyleCount() != null && selected != wave.getSelectedStyleCount()) {
            blockers.add(blocker("PORTFOLIO_DRIFT", "候选明细与波段选中数量发生漂移"));
        }
        return blockers;
    }

    private static AssortmentPlanningWorkflowView.Blocker blocker(String code, String message) {
        return AssortmentPlanningWorkflowView.Blocker.builder().code(code).message(message).build();
    }

    private static String summary(AssortmentWaveDO wave) {
        return switch (wave.getStatus()) {
            case "DRAFT" -> "波段企划已创建，等待建立候选池";
            case "BUILDING" -> "正在完成候选发现、AI 评分和组合选择";
            case "SELECTED" -> "AI 已形成 " + wave.getSelectedStyleCount() + " 款组合，等待独立审批";
            case "APPROVED" -> "波段组合已批准，等待发布上新日历和下游交接";
            case "PUBLISHED" -> "波段企划已发布：" + wave.getDecisionSummary();
            default -> "波段企划状态未知";
        };
    }

    private static void validateEnvelope(AssortmentPlanningCommand command) {
        require(command != null, "assortment planning command is required");
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

    private static String evidence(AssortmentPlanningCommand command) {
        return DigestUtil.sha256Hex(command.getOperation().name() + "\n"
                + command.getIdempotencyKey() + "\n" + command.getRunId());
    }

    private static String reason(String value) {
        if (!StringUtils.hasText(value)) return null;
        require(value.trim().length() <= 64, "reasonCode exceeds max length 64");
        return upper(value);
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireExpectedVersion(Long expected, Long actual) {
        require(expected != null && Objects.equals(expected, actual),
                "expectedWaveVersion does not match current aggregate version");
    }

    private static void requireCode(String value, String field) {
        require(value != null && SAFE_CODE.matcher(upper(value)).matches(), field + " is invalid");
    }

    private static void requireCurrency(String value) {
        require(value != null && upper(value).matches("^[A-Z]{3}$"), "currencyCode is invalid");
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
        require(value != null && SHA256.matcher(value).matches(),
                field + " must be a 64-char lowercase sha256");
    }

    private static void requirePositive(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
    }

    private static void requireRange(Integer value, int min, int max, String field) {
        require(value != null && value >= min && value <= max,
                field + " must be between " + min + " and " + max);
    }

    private static void requireUuid(String value, String field) {
        try {
            require(value != null, field + " is required");
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private static <T> T nonNull(T value, String message) {
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private static List<AssortmentCandidateDO> safe(List<AssortmentCandidateDO> values) {
        return values == null ? List.of() : values;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Outcome(AssortmentWaveDO wave, String candidateId,
                           List<String> selectedCandidateIds,
                           String summary, String evidenceSha256) {
    }
}
