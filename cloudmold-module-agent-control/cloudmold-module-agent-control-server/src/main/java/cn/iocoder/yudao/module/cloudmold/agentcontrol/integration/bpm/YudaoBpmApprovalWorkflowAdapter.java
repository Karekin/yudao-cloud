package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.agent-control.approval-workflow",
        name = "enabled", havingValue = "true")
public class YudaoBpmApprovalWorkflowAdapter implements AgentApprovalWorkflowAdapter {

    private final BpmProcessInstanceApi bpmProcessInstances;

    @Override
    public String start(ApprovalWorkflowStartCandidate candidate) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("approval_id", candidate.getApprovalId());
        variables.put("work_order_id", candidate.getWorkOrderId());
        variables.put("action_code", candidate.getActionCode());
        variables.put("role_code", candidate.getRoleCode());
        variables.put("risk_level", candidate.getRiskLevel());
        variables.put("scope_hash", candidate.getScopeHash());
        BpmProcessInstanceCreateReqDTO request = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(candidate.getProcessDefinitionKey())
                .setBusinessKey(candidate.getBusinessKey())
                .setVariables(variables);
        String processInstanceId = bpmProcessInstances.createProcessInstance(
                candidate.getRequesterUserId(), request).getCheckedData();
        if (StrUtil.isBlank(processInstanceId)) {
            throw new IllegalStateException("BPM returned an empty process instance id");
        }
        return processInstanceId;
    }

}
