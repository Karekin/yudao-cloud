package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.ExplorationRun;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DeterministicWorldMapCompositionPlanner implements DreamPlantExplorationPlanner {

    private static final String PLANNER_CODE = "deterministic-world-map-composition.v1";
    private static final String SOLUTION_ID = "fashion-small-order-fast-response";
    private static final String SOLUTION_NAME = "服饰供应链小单快反解决方案";
    private static final String AGENT_ID = "ops-agent-fashion-small-order-fast-response";
    private static final String AGENT_NAME = "服饰小单快反运营Agent";

    @Override
    public DreamPlantPlanResult plan(DreamPlantPlannerInput input) {
        require(input != null, "input is required");
        ExplorationRun run = input.getExplorationRun();
        require(run != null, "explorationRun is required");
        DreamPlantWorldMapPort.PublishedWorldMap worldMap = input.getPublishedWorldMap();
        require(worldMap != null, "publishedWorldMap is required");

        Map<String, Object> root = rootObject(worldMap.getPayloadJson());
        List<Map<String, Object>> products = objectList(root, "products");
        List<String> productIds = preferredProductIds(products);

        String intent = run.getIntent().trim();
        upsertCanonical(objectList(root, "operationAgents"), AGENT_ID, AGENT_NAME,
                operationAgent(AGENT_ID, intent, productIds));
        upsertCanonical(objectList(root, "solutions"), SOLUTION_ID, SOLUTION_NAME,
                solution(SOLUTION_ID, AGENT_ID, intent));

        String canonicalWorldMap = DreamPlantJsonCanonicalizer.canonicalize(root);
        String worldMapSha = DigestUtil.sha256Hex(canonicalWorldMap);
        String outcomeJson = DreamPlantJsonCanonicalizer.canonicalize(outcome(run, SOLUTION_ID, AGENT_ID, productIds));
        String evidenceRef = "sha256:" + DigestUtil.sha256Hex(outcomeJson);

        return DreamPlantPlanResult.builder()
                .plannerCode(PLANNER_CODE)
                .solutionId(SOLUTION_ID)
                .operationAgentId(AGENT_ID)
                .worldMapSchemaVersion(worldMap.getSchemaVersion())
                .worldMapJson(canonicalWorldMap)
                .worldMapSha256(worldMapSha)
                .outcomeStatus("SUCCEEDED")
                .outcomeJson(outcomeJson)
                .evidenceRef(evidenceRef)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rootObject(String payloadJson) {
        Object parsed = DreamPlantJsonCanonicalizer.parse(payloadJson);
        require(parsed instanceof Map<?, ?>, "world map payload must be a JSON object");
        Map<String, Object> root = new LinkedHashMap<>((Map<String, Object>) parsed);
        root.computeIfAbsent("capabilities", ignored -> new ArrayList<>());
        root.computeIfAbsent("products", ignored -> new ArrayList<>());
        root.computeIfAbsent("operationAgents", ignored -> new ArrayList<>());
        root.computeIfAbsent("solutions", ignored -> new ArrayList<>());
        root.computeIfAbsent("agentRoles", ignored -> new ArrayList<>());
        root.computeIfAbsent("phaseRoadmap", ignored -> new ArrayList<>());
        return root;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objectList(Map<String, Object> root, String key) {
        Object value = root.get(key);
        require(value instanceof List<?>, key + " must be an array");
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object element : (List<Object>) value) {
            require(element instanceof Map<?, ?>, key + " elements must be objects");
            list.add(new LinkedHashMap<>((Map<String, Object>) element));
        }
        root.put(key, list);
        return list;
    }

    private static void upsertCanonical(List<Map<String, Object>> list, String id, String name,
                                        Map<String, Object> value) {
        int insertionIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> candidate = list.get(i);
            if (Objects.equals(id, candidate.get("id")) || Objects.equals(name, candidate.get("name"))) {
                if (insertionIndex < 0) insertionIndex = i;
            }
        }
        list.removeIf(candidate -> Objects.equals(id, candidate.get("id")) || Objects.equals(name, candidate.get("name")));
        if (insertionIndex < 0) {
            list.add(value);
        } else {
            list.add(Math.min(insertionIndex, list.size()), value);
        }
    }

    private static Map<String, Object> operationAgent(String agentId, String intent, List<String> productIds) {
        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("id", agentId);
        agent.put("name", AGENT_NAME);
        agent.put("mission", "围绕“" + intent + "”持续编排需求感知、补单、排产与履约纠偏");
        agent.put("kpi", "首单交期缩短 30%，补单命中率 >= 85%，滞销率下降 20%");
        agent.put("productIds", productIds);
        agent.put("lifecycleStage", "编码验证");
        agent.put("owner", "AI托管");
        return agent;
    }

    private static Map<String, Object> solution(String solutionId, String agentId, String intent) {
        Map<String, Object> solution = new LinkedHashMap<>();
        solution.put("id", solutionId);
        solution.put("name", SOLUTION_NAME);
        solution.put("valueNarrative", "面向服饰行业的小单试产、快速补单与柔性排产闭环，意图来源：" + intent);
        solution.put("operationAgentIds", List.of(agentId));
        solution.put("kpi", "试销到补单决策 < 6 小时，首返单周期 < 7 天");
        solution.put("status", "灰度中");
        return solution;
    }

    private static Map<String, Object> outcome(ExplorationRun run, String solutionId, String agentId,
                                               List<String> productIds) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("planner", PLANNER_CODE);
        metadata.put("explorationRunId", run.getExplorationRunId());
        metadata.put("mapKey", run.getMapKey());
        metadata.put("requestedByPrincipalId", run.getRequestedByPrincipalId());
        metadata.put("status", "SUCCEEDED");

        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(stage("需求感知", "聚合近 7 天销量、退货、加购与询单信号，识别值得追单的爆款雏形"));
        stages.add(stage("款色码企划", "拆解款式、颜色、尺码组合，先锁定高命中尺码带"));
        stages.add(stage("小批首单", "控制首单 MOQ，优先验证版型、面料与渠道反馈"));
        stages.add(stage("面辅料齐套", "根据可替代料与供应商交期做齐套校验与替代建议"));
        stages.add(stage("柔性排产", "将小单插单到空档产能，避免影响主生产节拍"));
        stages.add(stage("履约质检", "把首单质量问题回写到返单阈值与供应商评分"));
        stages.add(stage("动态补单", "依据售罄速度和退货风险，自动触发二次补单窗口"));
        stages.add(stage("经营反馈回流世界地图", "把实际表现、异常和有效做法沉淀为可复用方案"));

        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("mode", "AI托管");
        governance.put("humanApprovals", List.of("大额采购", "生产切单", "资金放量"));
        governance.put("nonGoals", List.of("不复制 ERP 订单、库存和采购事实", "不绕过领域权威系统直接改业务账"));

        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("metadata", metadata);
        outcome.put("solution", Map.of("solutionId", solutionId, "operationAgentId", agentId, "productIds", productIds));
        outcome.put("problemStatement", run.getIntent());
        outcome.put("stages", stages);
        outcome.put("kpis", List.of(
                "首单交付周期 <= 7 天",
                "爆款补单决策时延 <= 6 小时",
                "返单命中率 >= 85%",
                "面辅料齐套异常提前 24 小时暴露"
        ));
        outcome.put("nextExperiments", List.of(
                "接入真实销量与退货波动数据，校准补单阈值",
                "接入供应商交期可信度评分，动态调优排产优先级",
                "接入库存老化数据，约束快反补单的 SKU 范围",
                "把首单质检问题自动映射到下一轮款色码建议"
        ));
        outcome.put("governance", governance);
        return outcome;
    }

    private static Map<String, Object> stage(String name, String objective) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("name", name);
        stage.put("objective", objective);
        return stage;
    }

    private static List<String> preferredProductIds(List<Map<String, Object>> products) {
        List<String> preferred = new ArrayList<>();
        for (String keyword : List.of("inventory", "trade", "marketing", "listing", "order")) {
            for (Map<String, Object> product : products) {
                Object id = product.get("id");
                if (!(id instanceof String text)) continue;
                if (text.contains(keyword) && !preferred.contains(text)) preferred.add(text);
                if (preferred.size() >= 3) return preferred;
            }
        }
        for (Map<String, Object> product : products) {
            Object id = product.get("id");
            if (id instanceof String text && !preferred.contains(text)) preferred.add(text);
            if (preferred.size() >= 3) return preferred;
        }
        return preferred;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

}
