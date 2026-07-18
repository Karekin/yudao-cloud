package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.ExplorationRun;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicWorldMapCompositionPlannerTest {

    private final DeterministicWorldMapCompositionPlanner planner = new DeterministicWorldMapCompositionPlanner();

    @Test
    void shouldDeterministicallyComposeSolutionAndOperationAgent() {
        ExplorationRun run = new ExplorationRun().setTenantId(1L).setExplorationRunId("exp-001").setMapKey("dreamplant")
                .setIntent("生成一个服饰供应链小单快反解决方案").setRequestedByPrincipalId("principal-ai")
                .setContextJson("{}").setVersion(1L).setCreatedAt(LocalDateTime.of(2026, 7, 18, 0, 0));
        DreamPlantWorldMapPort.PublishedWorldMap worldMap = DreamPlantWorldMapPort.PublishedWorldMap.builder()
                .tenantId(1L)
                .mapKey("dreamplant")
                .version(2L)
                .schemaVersion("dreamplant.bootstrap.v2")
                .payloadJson("""
                        {"capabilities":[],"products":[
                          {"id":"trade-core","name":"交易核心"},
                          {"id":"inventory-core","name":"库存核心"},
                          {"id":"marketing-core","name":"营销核心"}],
                         "operationAgents":[
                           {"id":"old-run-agent","name":"服饰小单快反运营Agent"},
                           {"id":"ops-agent-fashion-small-order-fast-response","name":"服饰小单快反运营Agent"}],
                         "solutions":[
                           {"id":"old-run-solution","name":"服饰供应链小单快反解决方案"},
                           {"id":"fashion-small-order-fast-response","name":"服饰供应链小单快反解决方案"}],
                         "agentRoles":[],"phaseRoadmap":[]}
                        """)
                .publiclyReadable(true)
                .sourceRef("sha256:" + "a".repeat(64))
                .build();

        DreamPlantPlanResult first = planner.plan(DreamPlantPlannerInput.builder()
                .explorationRun(run)
                .publishedWorldMap(worldMap)
                .build());
        DreamPlantPlanResult second = planner.plan(DreamPlantPlannerInput.builder()
                .explorationRun(run)
                .publishedWorldMap(worldMap)
                .build());

        assertThat(first).isEqualTo(second);
        assertThat(first.getWorldMapSchemaVersion()).isEqualTo("dreamplant.bootstrap.v2");
        assertThat(first.getEvidenceRef()).startsWith("sha256:");

        Map<?, ?> worldMapPayload = JsonUtils.parseObject(first.getWorldMapJson(), Map.class);
        List<?> agents = (List<?>) worldMapPayload.get("operationAgents");
        List<?> solutions = (List<?>) worldMapPayload.get("solutions");
        assertThat(agents).hasSize(1);
        assertThat(solutions).hasSize(1);
        assertThat(((Map<?, ?>) agents.get(0)).get("id"))
                .isEqualTo("ops-agent-fashion-small-order-fast-response");
        assertThat(((Map<?, ?>) solutions.get(0)).get("id"))
                .isEqualTo("fashion-small-order-fast-response");
        assertThat(((Map<?, ?>) agents.get(0)).get("name")).isEqualTo("服饰小单快反运营Agent");
        assertThat(((Map<?, ?>) solutions.get(0)).get("name")).isEqualTo("服饰供应链小单快反解决方案");
    }

}
