package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.definition.BpmSystemModelApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmSystemModelRegisterReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.agent-control.approval-workflow",
        name = "enabled", havingValue = "true")
public class YudaoBpmApprovalWorkflowAdapter implements AgentApprovalWorkflowAdapter {

    static final String APPROVAL_TASK_KEY = "approval_review";
    static final String RESPONSIBILITY_TASK_KEY = "domain_responsibility_countersign";
    private static final String MODEL_CATEGORY = "cloudmold-agent-control";
    private static final String MODEL_RESOURCE =
            "approval-workflow/cloudmold-agent-approval-v1.bpmn20.xml";

    private final BpmProcessInstanceApi bpmProcessInstances;
    private final BpmSystemModelApi bpmSystemModels;

    @Override
    public String start(ApprovalWorkflowStartCandidate candidate) {
        if (candidate.getApproverUserId() == null || candidate.getApproverUserId() <= 0) {
            throw new IllegalStateException("BPM approval approver is missing");
        }
        if (candidate.getApproverUserId().equals(candidate.getRequesterUserId())) {
            throw new IllegalStateException("BPM approval approver must differ from requester");
        }
        if (candidate.getApproverUserId().equals(candidate.getExecutorUserId())) {
            throw new IllegalStateException("BPM approval approver must differ from work-order executor");
        }
        List<Long> responsibilityApprovers =
                candidate.getResponsibilityApproverUserIds() == null
                        ? List.of() : candidate.getResponsibilityApproverUserIds();
        List<String> responsibilityRoles =
                candidate.getResponsibilityRoleCodes() == null
                        ? List.of() : candidate.getResponsibilityRoleCodes();
        if ("R3".equals(candidate.getRiskLevel())) {
            if (responsibilityRoles.isEmpty() || responsibilityApprovers.isEmpty()
                    || StrUtil.isBlank(candidate.getResponsibilityAuthoritySha256())) {
                throw new IllegalStateException("R3 responsibility countersign authority is missing");
            }
            if (new HashSet<>(responsibilityApprovers).size() != responsibilityApprovers.size()
                    || responsibilityApprovers.contains(candidate.getRequesterUserId())
                    || responsibilityApprovers.contains(candidate.getExecutorUserId())
                    || responsibilityApprovers.contains(candidate.getApproverUserId())) {
                throw new IllegalStateException("R3 responsibility countersign violates separation of duties");
            }
        } else if (!responsibilityRoles.isEmpty() || !responsibilityApprovers.isEmpty()
                || candidate.getResponsibilityAuthoritySha256() != null) {
            throw new IllegalStateException("non-R3 approval must not carry responsibility countersign authority");
        }
        ensureWorkflowModel(candidate);
        AgentApprovalBusinessContextPresenter.Presentation presentation =
                AgentApprovalBusinessContextPresenter.present(candidate);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("approval_id", candidate.getApprovalId());
        variables.put("work_order_id", candidate.getWorkOrderId());
        variables.put("action_code", candidate.getActionCode());
        variables.put("role_code", candidate.getRoleCode());
        variables.put("risk_level", candidate.getRiskLevel());
        variables.put("scope_hash", candidate.getScopeHash());
        variables.put("approval_stage", "R3".equals(candidate.getRiskLevel())
                ? "OPERATING_PRINCIPAL_THEN_DOMAIN_COUNTERSIGN"
                : "OPERATING_PRINCIPAL_FIRST_REVIEW");
        variables.put("approver_source", "TENANT_APPROVAL_AUTHORITY_GRANT");
        variables.put("responsibility_roles", responsibilityRoles);
        variables.put("responsibility_authority_sha256",
                candidate.getResponsibilityAuthoritySha256() == null
                        ? "" : candidate.getResponsibilityAuthoritySha256());
        variables.put("countersign_strategy", "R3".equals(candidate.getRiskLevel())
                ? "SERIAL_STAGE_THEN_PARALLEL_UNANIMOUS"
                : "NOT_REQUIRED");
        variables.put("business_action", presentation.businessAction());
        variables.put("impact_objects", presentation.impactObjects());
        variables.put("impact_metrics", presentation.impactMetrics());
        variables.put("recommendation", presentation.recommendation());
        variables.put("evidence_summary", presentation.evidence());
        variables.put("non_execution_consequence", presentation.nonExecutionConsequence());
        variables.put("execution_steps", presentation.executionSteps());
        Map<String, List<Long>> assignees = new LinkedHashMap<>();
        assignees.put(APPROVAL_TASK_KEY, List.of(candidate.getApproverUserId()));
        if ("R3".equals(candidate.getRiskLevel())) {
            assignees.put(RESPONSIBILITY_TASK_KEY, responsibilityApprovers);
        }
        BpmProcessInstanceCreateReqDTO request = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(candidate.getProcessDefinitionKey())
                .setBusinessKey(candidate.getBusinessKey())
                .setVariables(variables)
                .setStartUserSelectAssignees(assignees);
        String processInstanceId = bpmProcessInstances.createProcessInstance(
                candidate.getRequesterUserId(), request).getCheckedData();
        if (StrUtil.isBlank(processInstanceId)) {
            throw new IllegalStateException("BPM returned an empty process instance id");
        }
        return processInstanceId;
    }

    private void ensureWorkflowModel(ApprovalWorkflowStartCandidate candidate) {
        BpmSystemModelRegisterReqDTO request = new BpmSystemModelRegisterReqDTO()
                .setKey(candidate.getProcessDefinitionKey())
                .setName("Agent 高风险动作审批")
                .setDescription("CloudMold Agent 在执行 R2/R3 写操作前发起，由人工审批后自动回调并恢复运行。")
                .setCategoryCode(MODEL_CATEGORY)
                .setCategoryName("AI 运营审批")
                .setBpmnXml(readModel())
                .setFormCustomCreatePath("/cloudmold/agent-control/approval-form")
                .setFormCustomViewPath("/cloudmold/agent-control/approval-form.vue")
                .setManagerUserId(candidate.getRequesterUserId());
        bpmSystemModels.register(request).checkError();
    }

    private String readModel() {
        try {
            return new ClassPathResource(MODEL_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Agent approval BPMN resource is unavailable", exception);
        }
    }

}
