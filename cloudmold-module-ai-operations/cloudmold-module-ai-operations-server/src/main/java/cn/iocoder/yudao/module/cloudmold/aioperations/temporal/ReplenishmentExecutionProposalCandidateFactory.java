package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionProposalView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Materializes governed replenishment execution proposals as durable Temporal
 * candidates. The proposal is the source of truth; the SkillTask re-reads and
 * validates it before converting the approved recommendation.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal",
        name = "enabled", havingValue = "true")
public class ReplenishmentExecutionProposalCandidateFactory {

    static final String SKILL_ID = "skill.cloudmold.supply-planning.prepare.v1";
    private static final String ROUTE_VERSION = "replenishment-proposal-v1";

    private final SupplyPlanningQueryApi supplyPlanningQueryApi;
    private final AiOperationsTemporalMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public int materialize(TemporalDailyDispatchRequest request) {
        if (!SKILL_ID.equals(request.getSkillId())) {
            return 0;
        }
        int limit = Math.max(1,
                Math.min(request.getMaxFanOut() == null ? 100 : request.getMaxFanOut(), 500));
        List<ReplenishmentExecutionProposalView> proposals =
                supplyPlanningQueryApi.listReadyReplenishmentExecutionProposals(limit);
        int created = 0;
        for (ReplenishmentExecutionProposalView proposal : proposals) {
            String businessKey = "replenishment-execution-proposal/"
                    + proposal.getProposalId() + "/recommendation-version/"
                    + proposal.getExpectedRecommendationVersion();
            String executionKey = DigestUtil.sha256Hex(
                    request.getTenantId() + ":" + request.getSkillId() + ":"
                            + request.getSkillVersion() + ":" + ROUTE_VERSION + ":"
                            + businessKey + ":" + proposal.getPolicySha256());
            String conversionId = UUID.nameUUIDFromBytes(
                    ("cloudmold:" + executionKey).getBytes(StandardCharsets.UTF_8)).toString();
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime occurredAt = proposal.getProposedAt() == null
                    ? now : proposal.getProposedAt();
            TemporalAutomationCandidateRecord record =
                    new TemporalAutomationCandidateRecord()
                            .setTenantId(request.getTenantId())
                            .setCandidateId("mac-" + executionKey.substring(0, 32))
                            .setClientRequestKey("execution:" + executionKey)
                            .setSkillId(request.getSkillId())
                            .setSkillVersion(request.getSkillVersion())
                            .setBusinessKey(businessKey)
                            .setInputJson(JsonUtils.toJsonString(Map.of(
                                    "proposalId", proposal.getProposalId(),
                                    "recommendationId", proposal.getRecommendationId(),
                                    "conversionId", conversionId,
                                    "occurredAt", occurredAt.toInstant(ZoneOffset.UTC).toString())))
                            .setDueAt(occurredAt)
                            .setStatus("PENDING")
                            .setVersion(1L)
                            .setCreatedAt(now)
                            .setUpdatedAt(now);
            created += mapper.insertAutomationCandidate(record);
        }
        return created;
    }
}
