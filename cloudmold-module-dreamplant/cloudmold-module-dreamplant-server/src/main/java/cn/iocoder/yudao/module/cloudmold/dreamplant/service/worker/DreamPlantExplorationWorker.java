package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.ExplorationRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DreamPlantExplorationWorker {

    static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final DreamPlantExplorationGateway gateway;
    private final DreamPlantWorldMapPort worldMapPort;
    private final DreamPlantExplorationPlanner planner;

    public DreamPlantExplorationRunResult runBatch(String leaseOwner, int batchSize, LocalDateTime now) {
        require(leaseOwner != null && !leaseOwner.isBlank(), "leaseOwner is required");
        require(batchSize > 0 && batchSize <= 1000, "batchSize must be between 1 and 1000");
        require(now != null, "now is required");
        List<DreamPlantExplorationGateway.ExplorationCandidate> candidates =
                gateway.selectDueExplorations(now, batchSize);
        int claimed = 0;
        int completed = 0;
        int retried = 0;
        int needsReview = 0;
        for (DreamPlantExplorationGateway.ExplorationCandidate candidate : candidates) {
            if (!gateway.claimExploration(candidate.getTenantId(), candidate.getExplorationRunId(), candidate.getVersion(),
                    leaseOwner, now.plus(LEASE_DURATION), now)) {
                continue;
            }
            claimed++;
            try {
                TenantUtils.execute(candidate.getTenantId(), () -> process(candidate, leaseOwner, now));
                completed++;
            } catch (RuntimeException ex) {
                if (isReviewRequired(ex, candidate)) {
                    needsReview++;
                } else {
                    retried++;
                }
            }
        }
        return new DreamPlantExplorationRunResult(candidates.size(), claimed, completed, retried, needsReview);
    }

    void process(DreamPlantExplorationGateway.ExplorationCandidate candidate, String leaseOwner, LocalDateTime floor) {
        LocalDateTime now = checkpointNow(floor);
        try {
            ExplorationRun run = requireRun(candidate);
            appendStep(run, "PLANNING", "RUNNING", Map.of("attempt", attempt(candidate), "leaseOwner", leaseOwner), now);
            DreamPlantWorldMapPort.PublishedWorldMap worldMap = requireWorldMap(run);
            DreamPlantPlanResult plan = planner.plan(DreamPlantPlannerInput.builder()
                    .explorationRun(run)
                    .publishedWorldMap(worldMap)
                    .build());
            appendStep(run, "PUBLISH_WORLD_MAP", "RUNNING",
                    Map.of("solutionId", plan.getSolutionId(), "operationAgentId", plan.getOperationAgentId()), checkpointNow(now));
            worldMapPort.publishWorldMap(DreamPlantWorldMapPort.PublishWorldMapRequest.builder()
                    .tenantId(run.getTenantId())
                    .mapKey(run.getMapKey())
                    .expectedVersion(worldMap.getVersion())
                    .schemaVersion(plan.getWorldMapSchemaVersion())
                    .payloadJson(plan.getWorldMapJson())
                    .payloadSha256(plan.getWorldMapSha256())
                    .sourceRef(plan.getEvidenceRef())
                    .publiclyReadable(Boolean.TRUE.equals(worldMap.getPubliclyReadable()))
                    .idempotencyKey("dreamplant-publish-" + run.getExplorationRunId() + "-v" + run.getVersion())
                    .runTraceId(run.getExplorationRunId())
                    .build());
            appendStep(run, "COMPLETE", "SUCCEEDED",
                    Map.of("evidenceRef", plan.getEvidenceRef(), "planner", plan.getPlannerCode()), checkpointNow(now));
            gateway.markExplorationTerminal(DreamPlantExplorationGateway.TerminalDecision.builder()
                    .tenantId(run.getTenantId())
                    .explorationRunId(run.getExplorationRunId())
                    .expectedVersion(run.getVersion())
                    .status(plan.getOutcomeStatus())
                    .outcomeJson(plan.getOutcomeJson())
                    .evidenceRef(plan.getEvidenceRef())
                    .now(checkpointNow(now))
                    .build());
        } catch (Throwable failure) {
            handleFailure(candidate, leaseOwner, failure, checkpointNow(now));
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("DreamPlant exploration worker failed", failure);
        }
    }

    private ExplorationRun requireRun(DreamPlantExplorationGateway.ExplorationCandidate candidate) {
        ExplorationRun run = gateway.loadExploration(candidate.getTenantId(), candidate.getExplorationRunId());
        require(run != null, "DreamPlant exploration does not exist");
        require(Objects.equals(run.getVersion(), candidate.getVersion()),
                "DreamPlant exploration version changed before execution");
        return run;
    }

    private DreamPlantWorldMapPort.PublishedWorldMap requireWorldMap(ExplorationRun run) {
        DreamPlantWorldMapPort.PublishedWorldMap worldMap =
                worldMapPort.loadPublishedWorldMap(run.getTenantId(), run.getMapKey());
        require(worldMap != null, "DreamPlant world map does not exist");
        require(worldMap.getVersion() != null && worldMap.getVersion() > 0, "DreamPlant world map is unpublished");
        return worldMap;
    }

    private void handleFailure(DreamPlantExplorationGateway.ExplorationCandidate candidate, String leaseOwner,
                               Throwable failure, LocalDateTime now) {
        int attempt = attempt(candidate);
        String failureCode = classify(failure);
        boolean terminal = isPermanentFailure(failure) || attempt >= DEFAULT_MAX_ATTEMPTS;
        String outcomeJson = failureOutcomeJson(candidate, attempt, failureCode, terminal, failure);
        String evidenceRef = "sha256:" + DigestUtil.sha256Hex(outcomeJson);
        appendFailureStep(candidate, failureCode, terminal, failure, now);
        if (terminal) {
            gateway.markExplorationTerminal(DreamPlantExplorationGateway.TerminalDecision.builder()
                    .tenantId(candidate.getTenantId())
                    .explorationRunId(candidate.getExplorationRunId())
                    .expectedVersion(candidate.getVersion())
                    .status("NEEDS_REVIEW")
                    .outcomeJson(outcomeJson)
                    .evidenceRef(evidenceRef)
                    .now(now)
                    .build());
            return;
        }
        gateway.markExplorationRetry(DreamPlantExplorationGateway.RetryDecision.builder()
                .tenantId(candidate.getTenantId())
                .explorationRunId(candidate.getExplorationRunId())
                .expectedVersion(candidate.getVersion())
                .attemptCount(attempt)
                .leaseOwner(leaseOwner)
                .failureCode(failureCode)
                .failureMessage(messageOf(failure))
                .outcomeJson(outcomeJson)
                .evidenceRef(evidenceRef)
                .nextRunAt(now.plus(backoffForAttempt(attempt)))
                .now(now)
                .build());
    }

    private void appendStep(ExplorationRun run, String stepCode, String status, Map<String, Object> detail,
                            LocalDateTime now) {
        gateway.appendExplorationStep(DreamPlantExplorationGateway.ExplorationStep.builder()
                .tenantId(run.getTenantId())
                .explorationRunId(run.getExplorationRunId())
                .expectedVersion(run.getVersion())
                .stepCode(stepCode)
                .stepStatus(status)
                .detailJson(DreamPlantJsonCanonicalizer.canonicalize(detail))
                .occurredAt(now)
                .build());
    }

    private void appendFailureStep(DreamPlantExplorationGateway.ExplorationCandidate candidate, String failureCode,
                                   boolean terminal, Throwable failure, LocalDateTime now) {
        gateway.appendExplorationStep(DreamPlantExplorationGateway.ExplorationStep.builder()
                .tenantId(candidate.getTenantId())
                .explorationRunId(candidate.getExplorationRunId())
                .expectedVersion(candidate.getVersion())
                .stepCode("FAILED")
                .stepStatus(terminal ? "NEEDS_REVIEW" : "RETRYING")
                .detailJson(DreamPlantJsonCanonicalizer.canonicalize(Map.of(
                        "failureCode", failureCode,
                        "terminal", terminal,
                        "message", messageOf(failure)
                )))
                .occurredAt(now)
                .build());
    }

    private static int attempt(DreamPlantExplorationGateway.ExplorationCandidate candidate) {
        return Optional.ofNullable(candidate.getAttemptCount()).orElse(0) + 1;
    }

    private static Duration backoffForAttempt(int attempt) {
        long seconds = Math.min(300L, 15L * (1L << Math.min(attempt - 1, 4)));
        return Duration.ofSeconds(seconds);
    }

    private static String failureOutcomeJson(DreamPlantExplorationGateway.ExplorationCandidate candidate, int attempt,
                                             String failureCode, boolean terminal, Throwable failure) {
        return DreamPlantJsonCanonicalizer.canonicalize(Map.of(
                "status", terminal ? "NEEDS_REVIEW" : "FAILED",
                "failureCode", failureCode,
                "retryable", !terminal,
                "attempt", attempt,
                "explorationRunId", candidate.getExplorationRunId(),
                "message", messageOf(failure)
        ));
    }

    private static boolean isReviewRequired(RuntimeException ex, DreamPlantExplorationGateway.ExplorationCandidate candidate) {
        return isPermanentFailure(ex) || attempt(candidate) >= DEFAULT_MAX_ATTEMPTS;
    }

    private static boolean isPermanentFailure(Throwable failure) {
        return failure instanceof IllegalArgumentException;
    }

    private static String classify(Throwable failure) {
        if (failure instanceof IllegalArgumentException) {
            return "INVALID_INPUT";
        }
        if (failure instanceof IllegalStateException) {
            return "STATE_CONFLICT";
        }
        return "TRANSIENT_FAILURE";
    }

    private static String messageOf(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static LocalDateTime checkpointNow(LocalDateTime floor) {
        LocalDateTime wallClock = LocalDateTime.now(ZoneOffset.UTC);
        return wallClock.isAfter(floor) ? wallClock : floor.plusNanos(1);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

}
