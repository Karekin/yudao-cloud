package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.ExplorationRun;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DreamPlantExplorationWorkerTest {

    @Test
    void shouldPlanPublishAndCompleteQueuedExploration() {
        InMemoryGateway gateway = new InMemoryGateway();
        gateway.candidates.add(DreamPlantExplorationGateway.ExplorationCandidate.builder()
                .tenantId(1L).explorationRunId("exp-001").version(1L).attemptCount(0).build());
        gateway.runs.put("exp-001", new ExplorationRun().setTenantId(1L).setExplorationRunId("exp-001")
                .setMapKey("dreamplant").setIntent("生成一个服饰供应链小单快反解决方案").setRequestedByPrincipalId("principal-ai")
                .setContextJson("{}").setVersion(1L));
        InMemoryWorldMapPort worldMapPort = new InMemoryWorldMapPort();
        worldMapPort.current = DreamPlantWorldMapPort.PublishedWorldMap.builder()
                .tenantId(1L).mapKey("dreamplant").version(2L).schemaVersion("dreamplant.bootstrap.v2")
                .payloadJson("""
                        {"capabilities":[],"products":[{"id":"trade-core"},{"id":"inventory-core"}],
                         "operationAgents":[],"solutions":[],"agentRoles":[],"phaseRoadmap":[]}
                        """)
                .publiclyReadable(true)
                .sourceRef("sha256:" + "a".repeat(64))
                .build();

        DreamPlantExplorationWorker worker = new DreamPlantExplorationWorker(
                gateway, worldMapPort, new DeterministicWorldMapCompositionPlanner());

        DreamPlantExplorationRunResult result = worker.runBatch("lease-1", 10, LocalDateTime.of(2026, 7, 18, 12, 0));

        assertThat(result).isEqualTo(new DreamPlantExplorationRunResult(1, 1, 1, 0, 0));
        assertThat(worldMapPort.publishRequests).hasSize(1);
        assertThat(worldMapPort.publishRequests.get(0).getIdempotencyKey())
                .isEqualTo("dreamplant-publish-exp-001-v1")
                .matches("[A-Za-z0-9][A-Za-z0-9_.-]{1,127}");
        assertThat(gateway.terminalDecisions).hasSize(1);
        assertThat(gateway.terminalDecisions.get(0).getStatus()).isEqualTo("SUCCEEDED");
        assertThat(gateway.steps).extracting(DreamPlantExplorationGateway.ExplorationStep::getStepCode)
                .containsExactly("PLANNING", "PUBLISH_WORLD_MAP", "COMPLETE");
    }

    @Test
    void shouldScheduleRetryForTransientFailure() {
        InMemoryGateway gateway = new InMemoryGateway();
        gateway.candidates.add(DreamPlantExplorationGateway.ExplorationCandidate.builder()
                .tenantId(1L).explorationRunId("exp-002").version(3L).attemptCount(1).build());
        gateway.runs.put("exp-002", new ExplorationRun().setTenantId(1L).setExplorationRunId("exp-002")
                .setMapKey("dreamplant").setIntent("生成一个服饰供应链小单快反解决方案").setRequestedByPrincipalId("principal-ai")
                .setContextJson("{}").setVersion(3L));
        InMemoryWorldMapPort worldMapPort = new InMemoryWorldMapPort();
        worldMapPort.current = DreamPlantWorldMapPort.PublishedWorldMap.builder()
                .tenantId(1L).mapKey("dreamplant").version(2L).schemaVersion("dreamplant.bootstrap.v2")
                .payloadJson("{\"capabilities\":[],\"products\":[],\"operationAgents\":[],\"solutions\":[],\"agentRoles\":[],\"phaseRoadmap\":[]}")
                .publiclyReadable(true)
                .sourceRef("sha256:" + "a".repeat(64))
                .build();

        DreamPlantExplorationWorker worker = new DreamPlantExplorationWorker(gateway, worldMapPort,
                input -> { throw new RuntimeException("registry timeout"); });

        assertThatCode(() -> worker.runBatch("lease-2", 10, LocalDateTime.of(2026, 7, 18, 12, 0)))
                .doesNotThrowAnyException();

        assertThat(gateway.retryDecisions).hasSize(1);
        assertThat(gateway.retryDecisions.get(0).getFailureCode()).isEqualTo("TRANSIENT_FAILURE");
        assertThat(gateway.terminalDecisions).isEmpty();
    }

    @Test
    void shouldEscalateInvalidInputToNeedsReview() {
        InMemoryGateway gateway = new InMemoryGateway();
        gateway.candidates.add(DreamPlantExplorationGateway.ExplorationCandidate.builder()
                .tenantId(1L).explorationRunId("exp-003").version(2L).attemptCount(0).build());
        gateway.runs.put("exp-003", new ExplorationRun().setTenantId(1L).setExplorationRunId("exp-003")
                .setMapKey("dreamplant").setIntent("bad").setRequestedByPrincipalId("principal-ai")
                .setContextJson("{}").setVersion(2L));
        InMemoryWorldMapPort worldMapPort = new InMemoryWorldMapPort();
        worldMapPort.current = DreamPlantWorldMapPort.PublishedWorldMap.builder()
                .tenantId(1L).mapKey("dreamplant").version(2L).schemaVersion("dreamplant.bootstrap.v2")
                .payloadJson("{\"capabilities\":[],\"products\":[],\"operationAgents\":[],\"solutions\":[],\"agentRoles\":[],\"phaseRoadmap\":[]}")
                .publiclyReadable(true)
                .sourceRef("sha256:" + "a".repeat(64))
                .build();

        DreamPlantExplorationWorker worker = new DreamPlantExplorationWorker(gateway, worldMapPort,
                input -> { throw new IllegalArgumentException("intent is too short"); });

        DreamPlantExplorationRunResult result = worker.runBatch("lease-3", 10, LocalDateTime.of(2026, 7, 18, 12, 0));

        assertThat(result).isEqualTo(new DreamPlantExplorationRunResult(1, 1, 0, 0, 1));
        assertThat(gateway.terminalDecisions).hasSize(1);
        assertThat(gateway.terminalDecisions.get(0).getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(gateway.retryDecisions).isEmpty();
    }

    private static final class InMemoryGateway implements DreamPlantExplorationGateway {
        private final List<ExplorationCandidate> candidates = new ArrayList<>();
        private final Map<String, ExplorationRun> runs = new HashMap<>();
        private final List<ExplorationStep> steps = new ArrayList<>();
        private final List<RetryDecision> retryDecisions = new ArrayList<>();
        private final List<TerminalDecision> terminalDecisions = new ArrayList<>();

        @Override
        public List<ExplorationCandidate> selectDueExplorations(LocalDateTime now, int limit) {
            return new ArrayList<>(candidates);
        }

        @Override
        public boolean claimExploration(Long tenantId, String explorationRunId, Long expectedVersion,
                                        String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now) {
            return true;
        }

        @Override
        public ExplorationRun loadExploration(Long tenantId, String explorationRunId) {
            return runs.get(explorationRunId);
        }

        @Override
        public void appendExplorationStep(ExplorationStep step) {
            steps.add(step);
        }

        @Override
        public void markExplorationRetry(RetryDecision decision) {
            retryDecisions.add(decision);
        }

        @Override
        public void markExplorationTerminal(TerminalDecision decision) {
            terminalDecisions.add(decision);
        }
    }

    private static final class InMemoryWorldMapPort implements DreamPlantWorldMapPort {
        private PublishedWorldMap current;
        private final List<PublishWorldMapRequest> publishRequests = new ArrayList<>();

        @Override
        public PublishedWorldMap loadPublishedWorldMap(Long tenantId, String mapKey) {
            return current;
        }

        @Override
        public void publishWorldMap(PublishWorldMapRequest request) {
            publishRequests.add(request);
        }
    }

}
