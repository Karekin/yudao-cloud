package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Approval;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;

import java.time.LocalDateTime;

@FunctionalInterface
public interface AgentApprovalWorkflowRegistrar {

    AgentApprovalWorkflowRegistrar DISABLED = (tenantId, approval, workOrder, now) -> {
    };

    void register(Long tenantId, Approval approval, WorkOrder workOrder, LocalDateTime now);

}
