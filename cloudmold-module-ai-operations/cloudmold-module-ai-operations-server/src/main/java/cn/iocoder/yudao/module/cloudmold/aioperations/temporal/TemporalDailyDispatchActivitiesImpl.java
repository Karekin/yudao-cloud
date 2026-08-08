package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class TemporalDailyDispatchActivitiesImpl implements TemporalDailyDispatchActivities {

    private static final long CLAIM_LEASE_MINUTES = 15L;

    private final AiOperationsTemporalMapper mapper;
    private final TemporalAutomationCandidateMaterializer candidateMaterializer;
    private final ReplenishmentExecutionProposalCandidateFactory replenishmentProposalCandidateFactory;
    private final RotatingBusinessScenarioInputFactory rotatingScenarioInputFactory;
    private final WorkflowClient workflowClient;

    @Override
    public List<TemporalAutomationCandidate> claimCandidates(
            TemporalDailyDispatchRequest request, String businessDate, String leaseOwner) {
        return TenantUtils.execute(request.getTenantId(),
                () -> claimCandidatesInTenant(request, businessDate, leaseOwner));
    }

    @Override
    public void markDispatched(TemporalDailyDispatchRequest request, String candidateId,
                               String leaseOwner, String workflowId) {
        if (candidateId == null) {
            return;
        }
        TenantUtils.execute(request.getTenantId(), () -> {
            int updated = mapper.markAutomationCandidateDispatched(
                    request.getTenantId(), candidateId, leaseOwner, workflowId, now());
            require(updated == 1, "Automation candidate dispatch acknowledgement lost its lease");
            return null;
        });
    }

    @Override
    public void releaseCandidate(TemporalDailyDispatchRequest request, String candidateId,
                                 String leaseOwner, String error) {
        if (candidateId == null) {
            return;
        }
        TenantUtils.execute(request.getTenantId(), () -> {
            mapper.releaseAutomationCandidate(
                    request.getTenantId(), candidateId, leaseOwner, truncate(error), now());
            return null;
        });
    }

    @Override
    public boolean workflowExists(String workflowId) {
        try {
            workflowClient.newUntypedWorkflowStub(workflowId).describe();
            return true;
        } catch (WorkflowNotFoundException notFound) {
            return false;
        }
    }

    @Override
    public void recordDispatchResult(TemporalDailyDispatchRequest request,
                                     TemporalDailyDispatchResult result,
                                     String temporalRunId) {
        TenantUtils.execute(request.getTenantId(), () -> {
            mapper.insertDispatchObservation(new TemporalDispatchObservationRecord()
                    .setTenantId(request.getTenantId())
                    .setScheduleId(request.getScheduleId())
                    .setTemporalRunId(temporalRunId)
                    .setBusinessDate(result.getBusinessDate())
                    .setOutcomeCode(result.getOutcomeCode())
                    .setCandidateCount(result.getCandidateCount())
                    .setDispatchedCount(result.getDispatchedCount())
                    .setFailedCount(result.getFailedCount())
                    .setWorkflowIdsJson(JsonUtils.toJsonString(result.getWorkflowIds()))
                    .setObservedAt(now()));
            return null;
        });
    }

    private List<TemporalAutomationCandidate> claimCandidatesInTenant(
            TemporalDailyDispatchRequest request, String businessDate, String leaseOwner) {
        if ("TENANT_AGGREGATE".equals(request.getInputStrategy())) {
            return List.of(TemporalAutomationCandidate.builder()
                    .candidateId(null)
                    .businessKey("business-date/" + businessDate)
                    .inputJson("{}")
                    .synthetic(true)
                    .build());
        }
        if ("ROTATING_BUSINESS_SCENARIO".equals(request.getInputStrategy())) {
            String occurrenceToken = DigestUtil.sha256Hex(leaseOwner).substring(0, 16);
            return rotatingScenarioInputFactory.build(
                            request.getTenantId(), request.getSkillId(), businessDate, leaseOwner,
                            request.getOperatorUserId())
                    .map(input -> List.of(TemporalAutomationCandidate.builder()
                            .candidateId(null)
                            .businessKey("rotating-scenario/" + request.getSkillId() + "/"
                                    + businessDate + "/" + occurrenceToken)
                            .inputJson(input)
                            .synthetic(true)
                            .build()))
                    .orElseGet(List::of);
        }
        if ("DOMAIN_BACKLOG".equals(request.getInputStrategy())) {
            replenishmentProposalCandidateFactory.materialize(request);
        } else {
            candidateMaterializer.materialize(request);
        }
        int limit = Math.max(1, Math.min(request.getMaxFanOut() == null ? 100 : request.getMaxFanOut(), 500));
        LocalDateTime now = now();
        List<TemporalAutomationCandidateRecord> due = mapper.selectDueAutomationCandidates(
                request.getTenantId(), request.getSkillId(), request.getSkillVersion(), now, limit * 2);
        List<TemporalAutomationCandidate> claimed = new ArrayList<>();
        for (TemporalAutomationCandidateRecord candidate : due) {
            if (claimed.size() >= limit) {
                break;
            }
            int updated = mapper.claimAutomationCandidate(
                    request.getTenantId(), candidate.getCandidateId(), leaseOwner,
                    now.plusMinutes(CLAIM_LEASE_MINUTES), now);
            if (updated != 1) {
                continue;
            }
            claimed.add(TemporalAutomationCandidate.builder()
                    .candidateId(candidate.getCandidateId())
                    .businessKey(candidate.getBusinessKey())
                    .inputJson(candidate.getInputJson())
                    .synthetic(false)
                    .build());
        }
        return claimed;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "UNKNOWN_DISPATCH_ERROR";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
