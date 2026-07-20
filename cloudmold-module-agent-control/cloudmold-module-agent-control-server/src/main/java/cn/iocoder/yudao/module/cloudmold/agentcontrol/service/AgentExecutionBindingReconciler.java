package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionBindingApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.MissionRuntimeApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ExecutionReconcileCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.MissionResolutionCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.agent-control.runtime", name = "enabled", havingValue = "true")
public class AgentExecutionBindingReconciler implements ApplicationListener<ApplicationReadyEvent> {

    private final AgentControlStoreMapper mapper;
    private final SkillTaskQueryApi skillTasks;
    private final AgentExecutionBindingApi bindings;
    private final MissionRuntimeApi missionRuntime;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        reconcile();
    }

    @Scheduled(fixedDelayString = "${cloudmold.agent-control.runtime.execution-reconcile-delay-ms:2000}")
    public synchronized void reconcile() {
        for (ExecutionReconcileCandidate candidate : mapper.selectExecutionReconcileCandidates(100)) {
            TenantUtils.execute(candidate.getTenantId(), () -> reconcileCandidate(candidate));
        }
        for (MissionResolutionCandidate candidate : mapper.selectMissionResolutionCandidates(100)) {
            TenantUtils.execute(candidate.getTenantId(), () -> resolveCompletedMissionWork(candidate));
        }
    }

    private void reconcileCandidate(ExecutionReconcileCandidate candidate) {
        try {
            CloudMoldRpcCallContext context = new CloudMoldRpcCallContext(candidate.getTenantId(),
                    candidate.getOperatorUserId(), UserTypeEnum.ADMIN.getValue(), candidate.getSkillId(),
                    candidate.getRunId());
            SkillTaskView task;
            try (CloudMoldRpcCallContext.Scope ignored = CloudMoldRpcCallContext.open(context)) {
                task = skillTasks.get(candidate.getSkillTaskId());
            }
            if ("SUCCEEDED".equals(task.getStatus())) {
                bindings.reconcile(candidate.getBindingId(), candidate.getOperatorUserId());
            } else if ("NEEDS_REVIEW".equals(task.getStatus())) {
                log.warn("Agent execution awaits review tenant={} binding={} task={}", candidate.getTenantId(),
                        candidate.getBindingId(), candidate.getSkillTaskId());
            }
        } catch (RuntimeException exception) {
            log.warn("Agent execution reconciliation deferred tenant={} binding={}", candidate.getTenantId(),
                    candidate.getBindingId(), exception);
        }
    }

    private void resolveCompletedMissionWork(MissionResolutionCandidate candidate) {
        try {
            missionRuntime.resolveCompletedWorkOrder(candidate.getWorkOrderId());
        } catch (RuntimeException exception) {
            log.warn("Completed mission work resolution deferred tenant={} workOrder={}", candidate.getTenantId(),
                    candidate.getWorkOrderId(), exception);
        }
    }
}
