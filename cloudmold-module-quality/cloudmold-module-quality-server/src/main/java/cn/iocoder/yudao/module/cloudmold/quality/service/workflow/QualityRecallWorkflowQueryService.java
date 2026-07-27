package cn.iocoder.yudao.module.cloudmold.quality.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.api.workflow.QualityRecallWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.quality.api.workflow.QualityRecallWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.quality.api.workflow.QualityRecallWorkflowResult.Artifact;
import cn.iocoder.yudao.module.cloudmold.quality.api.workflow.QualityRecallWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.Capa;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.InspectionTask;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.RecallAction;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.QualityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QualityRecallWorkflowQueryService implements QualityRecallWorkflowQueryPort {

    private final QualityMapper mapper;
    private final InventoryLotQueryApi lotQueryApi;
    private final InventoryV3AvailabilityQueryApi availabilityQueryApi;

    @Override
    public QualityRecallWorkflowResult inspect(String recallActionId) {
        if (!StringUtils.hasText(recallActionId)) {
            throw new IllegalArgumentException("recallActionId is required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String key = recallActionId.trim();
        RecallAction recall = mapper.selectRecallAction(tenantId, key);
        if (recall == null) {
            return result(key, Status.PREPARE, "召回准备", false, "尚未创建质量召回动作", 0L,
                    List.of("Quality SoR 中不存在召回动作"), List.of("基于不合格质检结果创建召回"), List.of());
        }
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("RECALL_ACTION", recall.getRecallActionId(), recall.getStatus(), recall.getVersion(), "质量召回"));
        InspectionTask task = StringUtils.hasText(recall.getInspectionTaskId())
                ? mapper.selectInspectionTask(tenantId, recall.getInspectionTaskId()) : null;
        List<Capa> capas = task == null ? List.of() : safe(mapper.selectCapasByInspectionTask(tenantId, task.getTaskId()));
        if (task != null) {
            artifacts.add(artifact("INSPECTION_TASK", task.getTaskId(), task.getStatus(), task.getVersion(), "质量检验"));
        }
        capas.forEach(capa -> artifacts.add(artifact("CAPA", capa.getCapaId(), capa.getStatus(), capa.getVersion(), "纠正预防措施")));

        List<String> blockers = new ArrayList<>();
        List<String> next = new ArrayList<>();
        if (task == null) {
            blockers.add("召回未关联真实质检任务");
            next.add("关联质量检验任务");
        } else if (!"COMPLETED".equals(task.getStatus())) {
            blockers.add("质量检验尚未完成");
            next.add("完成质检结论");
        }
        if (capas.isEmpty()) {
            blockers.add("尚未建立纠正预防措施 CAPA");
            next.add("创建并验证 CAPA");
        } else if (capas.stream().anyMatch(capa -> !"VERIFIED".equals(capa.getStatus()))) {
            blockers.add("仍有 CAPA 未验证完成");
            next.add("验证 CAPA 有效性");
        }
        if (!"RESOLVED".equals(recall.getStatus())) {
            blockers.add("召回动作尚未解决");
            next.add("确认召回并完成处置");
        }

        InventoryLotView lot = null;
        List<InventoryV3AvailabilityView> balances = List.of();
        if (!StringUtils.hasText(recall.getLotId())) {
            blockers.add("召回未绑定规范库存批次");
            next.add("补齐批次引用");
        } else {
            try {
                lot = lotQueryApi.requireCurrent(recall.getLotId(), Instant.now());
                artifacts.add(artifact("INVENTORY_LOT", lot.getLotId(), lot.getStatus(), lot.getVersion(), "库存批次"));
                balances = safe(availabilityQueryApi.listByLot(recall.getLotId(), Instant.now()));
                balances.forEach(balance -> artifacts.add(artifact("INVENTORY_BALANCE", balance.getBalanceId(),
                        balance.getAllocationEligibility(), balance.getAggregateVersion(), "批次库存余额")));
                if (!"RECALLED".equals(lot.getStatus())) {
                    blockers.add("库存批次尚未进入召回状态");
                    next.add("将批次标记为召回");
                }
                if (balances.isEmpty()) {
                    blockers.add("未找到批次库存余额，无法证明已隔离");
                    next.add("同步库存批次余额");
                } else if (balances.stream().anyMatch(balance -> balance.getAllocatableQuantity() == null
                        || balance.getAllocatableQuantity().compareTo(BigDecimal.ZERO) != 0)) {
                    blockers.add("召回批次仍存在可分配库存");
                    next.add("隔离全部召回库存");
                }
            } catch (IllegalArgumentException missingLot) {
                blockers.add("Inventory SoR 中不存在召回批次");
                next.add("建立规范库存批次");
            }
        }
        long version = artifacts.stream().map(Artifact::getVersion).filter(v -> v != null)
                .mapToLong(Long::longValue).max().orElse(recall.getVersion());
        if (!blockers.isEmpty()) {
            return result(key, Status.WAITING, "召回处置与库存隔离", false,
                    "质量召回尚未形成可验证闭环", version, blockers, next, artifacts);
        }
        return result(key, Status.SUCCEEDED, "质量召回闭环", true,
                "SKU " + recall.getCanonicalSkuId() + " 的批次 " + lot.getLotCode()
                        + " 已完成质检、CAPA、召回和库存隔离",
                version, List.of(), List.of(), artifacts);
    }

    private static QualityRecallWorkflowResult result(String key, Status status, String phase, boolean terminal,
                                                       String summary, Long version, List<String> blockers,
                                                       List<String> next, List<Artifact> artifacts) {
        return QualityRecallWorkflowResult.builder().workflowType("QualityRecallWorkflow")
                .workflowInstanceKey("quality-recall:" + key).businessKey(key).status(status).phase(phase)
                .terminal(terminal).actionRequired(!next.isEmpty()).summary(summary).aggregateVersion(version)
                .blockers(blockers).nextActions(next).artifacts(artifacts).build();
    }

    private static Artifact artifact(String type, String id, String status, Long version, String label) {
        return Artifact.builder().type(type).id(id).status(status).version(version).label(label).build();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
