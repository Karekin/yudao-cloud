package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskTransferReqVO;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentApprovalDetailView;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.review.AgentApprovalReviewApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.review.AgentApprovalReviewCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.review.AgentApprovalReviewContext;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.review.AgentApprovalReviewResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Completes only the current Agent Control BPM task assigned to an allowlisted AI service user.
 */
@Service
@RequiredArgsConstructor
public class AgentApprovalReviewService implements AgentApprovalReviewApi {

    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT");
    private static final Set<String> REVIEW_TASK_KEYS = Set.of(
            YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY,
            YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY,
            YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY);
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_AUDIT_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");
    private static final String COMMAND_TYPE = "AGENT_APPROVAL_AI_REVIEW";
    private static final int REASON_LIMIT = 500;
    private static final int AUDIT_VALUE_LIMIT = 128;
    private static final int MAX_LEGACY_TASKS_PER_APPROVAL = 32;

    private final AgentControlStoreMapper mapper;
    private final BpmTaskService bpmTasks;
    private final AgentApprovalWorkflowProperties properties;
    private final AgentApprovalWorkflowService workflows;

    @Override
    public AgentApprovalReviewContext getReviewContext(String approvalId) {
        Review review = requireReview(approvalId);
        AgentApprovalDetailView detail = review.detail();
        AgentApprovalBusinessContextPresenter.Presentation presentation =
                AgentApprovalBusinessContextPresenter.present(new ApprovalWorkflowStartCandidate()
                        .setWorkOrderTitle(detail.getTitle())
                        .setBusinessContextJson(detail.getBusinessContextJson())
                        .setActionCode(detail.getActionCode())
                        .setScopeHash(detail.getScopeHash()));
        return AgentApprovalReviewContext.builder()
                .approvalId(detail.getApprovalId())
                .workOrderId(detail.getWorkOrderId())
                .title(detail.getTitle())
                .roleCode(detail.getRoleCode())
                .actionCode(detail.getActionCode())
                .riskLevel(detail.getRiskLevel())
                .scopeHash(detail.getScopeHash())
                .workflowStatus(detail.getWorkflowStatus())
                .businessAction(presentation.businessAction())
                .impactObjects(presentation.impactObjects())
                .impactMetrics(presentation.impactMetrics())
                .recommendation(presentation.recommendation())
                .evidenceSummary(presentation.evidence())
                .nonExecutionConsequence(presentation.nonExecutionConsequence())
                .executionSteps(presentation.executionSteps())
                .taskName(review.task().getName())
                .taskDefinitionKey(review.task().getTaskDefinitionKey())
                .requestedAt(detail.getRequestedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentApprovalReviewResult submitDecision(AgentApprovalReviewCommand command) {
        require(command != null, "review command is required");
        String idempotencyKey = requireAuditRef(command.getIdempotencyKey(), "idempotencyKey");
        String decision = normalizedDecision(command.getDecision());
        String reasonText = requireText(command.getReason(), "reason", REASON_LIMIT);
        String modelId = requireAuditRef(command.getModelId(), "modelId");
        String modelRunId = requireAuditRef(command.getModelRunId(), "modelRunId");
        String evidenceSha256 = StrUtil.nullToEmpty(command.getEvidenceSha256()).trim().toLowerCase(Locale.ROOT);
        require(SHA256.matcher(evidenceSha256).matches(),
                "evidenceSha256 must be a 64-character hexadecimal SHA-256");

        Reviewer reviewer = requireReviewer();
        AgentApprovalDetailView detail = requireApproval(command.getApprovalId(), reviewer.tenantId(), false);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = requestHash(reviewer, detail.getApprovalId(), decision, reasonText,
                modelId, modelRunId, evidenceSha256);
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(reviewer.tenantId(), idempotencyKey, COMMAND_TYPE,
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve AI review operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, reviewer.tenantId());
        require(operation != null, "AI review operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(COMMAND_TYPE.equals(operation.getCommandType())
                            && Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different AI review request");
            require(operation.getStatus() == 10 && operation.getResultJson() != null,
                    "existing AI review operation is incomplete");
            AgentApprovalReviewResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), AgentApprovalReviewResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Review review = requireAssignedReview(reviewer, detail);
        String reason = auditReason(decision, reasonText, modelId, modelRunId, evidenceSha256);
        if (YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY.equals(
                review.task().getTaskDefinitionKey())) {
            submitTaskDecision(review.operatorUserId(), review.task(), decision, reason);
            observeExplicitAiTerminal(reviewer, detail, review.task(), decision, reason);
        } else if (properties.isAiReviewerOrSignEnabled()) {
            submitLegacyOrSignDecision(reviewer, detail, decision, reason);
        } else {
            submitTaskDecision(review.operatorUserId(), review.task(), decision, reason);
        }
        AgentApprovalReviewResult result = AgentApprovalReviewResult.builder()
                .approvalId(review.detail().getApprovalId())
                .decision(decision)
                .status("BPM_DECISION_SUBMITTED")
                .duplicate(false)
                .build();
        require(mapper.markOperationSucceeded(operationId, reviewer.tenantId(), "AGENT_APPROVAL",
                review.detail().getApprovalId(), JsonUtils.toJsonString(result), now) == 1,
                "AI review operation completion conflict");
        return result;
    }

    private void observeExplicitAiTerminal(Reviewer reviewer, AgentApprovalDetailView detail, Task task,
                                           String decision, String reason) {
        int status = "APPROVE".equals(decision)
                ? BpmProcessInstanceStatusEnum.APPROVE.getStatus()
                : BpmProcessInstanceStatusEnum.REJECT.getStatus();
        workflows.observe(new BpmProcessInstanceStatusEvent(this)
                .setId(detail.getProcessInstanceId())
                .setProcessDefinitionKey(properties.getProcessDefinitionKey())
                .setBusinessKey("cloudmold-agent-approval:" + reviewer.tenantId() + ":" + detail.getApprovalId())
                .setStatus(status).setReason(reason)
                .setTerminalOperatorUserId(reviewer.operatorUserId())
                .setTerminalTaskId(task.getId())
                .setTerminalTaskDefinitionKey(task.getTaskDefinitionKey()));
    }

    private Review requireReview(String approvalId) {
        Reviewer reviewer = requireReviewer();
        AgentApprovalDetailView detail = requireApproval(approvalId, reviewer.tenantId(), true);
        return requireAssignedReview(reviewer, detail);
    }

    private Reviewer requireReviewer() {
        require(properties.isEnabled(), "approval workflow is disabled");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long operatorUserId = SecurityFrameworkUtils.getLoginUserId();
        require(operatorUserId != null && operatorUserId > 0, "authenticated AI reviewer is required");
        require(properties.getAiReviewerUserIds().contains(operatorUserId),
                "authenticated actor is not an allowlisted AI reviewer");
        return new Reviewer(tenantId, operatorUserId);
    }

    private AgentApprovalDetailView requireApproval(String approvalId, Long tenantId, boolean requirePending) {
        requireText(approvalId, "approvalId", 128);
        AgentApprovalDetailView detail = mapper.selectApprovalDetail(tenantId, approvalId);
        require(detail != null, "approval not found");
        require(properties.getAiReviewAllowedRiskLevels().contains(detail.getRiskLevel()),
                "approval risk level is not enabled for AI review");
        if (requirePending) {
            require("PENDING".equals(detail.getStatus()), "only a PENDING approval can be reviewed");
        }
        return detail;
    }

    private Review requireAssignedReview(Reviewer reviewer, AgentApprovalDetailView detail) {
        require("PENDING".equals(detail.getStatus()), "only a PENDING approval can be reviewed");
        require(detail.getProcessInstanceId() != null && !detail.getProcessInstanceId().isBlank(),
                "approval BPM process instance is unavailable");

        assertReviewerSeparation(reviewer, detail);
        List<Task> tasks = governedTasks(detail);
        List<Task> assignedTasks = tasks.stream()
                .filter(task -> Objects.equals(task.getAssignee(), String.valueOf(reviewer.operatorUserId())))
                .toList();
        List<Task> explicitAiTasks = assignedTasks.stream()
                .filter(task -> YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY.equals(
                        task.getTaskDefinitionKey()))
                .toList();
        require(explicitAiTasks.size() <= 1, "multiple explicit AI review tasks are running");
        if (explicitAiTasks.size() == 1) {
            return new Review(reviewer.operatorUserId(), detail, explicitAiTasks.get(0));
        }
        if (assignedTasks.size() == 1) {
            return new Review(reviewer.operatorUserId(), detail, assignedTasks.get(0));
        }
        if (properties.isAiReviewerOrSignEnabled() && !tasks.isEmpty()) {
            require(tasks.stream().noneMatch(task -> YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY.equals(
                            task.getTaskDefinitionKey())),
                    "explicit AI review task is not assigned to the authenticated AI reviewer");
            return new Review(reviewer.operatorUserId(), detail, selectLegacyTask(tasks));
        }
        require(false, assignedTasks.isEmpty()
                ? "no governed BPM review task is assigned to the AI reviewer"
                : "multiple governed BPM review tasks are assigned to the AI reviewer");
        throw new IllegalStateException("unreachable");
    }

    private void submitLegacyOrSignDecision(Reviewer reviewer, AgentApprovalDetailView detail,
                                            String decision, String reason) {
        int completed = 0;
        while (true) {
            List<Task> tasks = governedTasks(detail);
            require(tasks.stream().noneMatch(task -> YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY.equals(
                            task.getTaskDefinitionKey())),
                    "legacy takeover is forbidden when an explicit AI OR-sign task exists");
            if (tasks.isEmpty()) {
                require(completed > 0, "no governed BPM review task is available for AI OR-sign");
                return;
            }
            require(++completed <= MAX_LEGACY_TASKS_PER_APPROVAL,
                    "AI OR-sign exceeded the governed legacy task limit");
            Task task = selectLegacyTask(tasks);
            transferToAiReviewer(reviewer, task, reason);
            submitTaskDecision(reviewer.operatorUserId(), task, decision, reason);
            if ("REJECT".equals(decision) || !"R3".equals(detail.getRiskLevel())) {
                return;
            }
        }
    }

    private void transferToAiReviewer(Reviewer reviewer, Task task, String reason) {
        String aiUserId = String.valueOf(reviewer.operatorUserId());
        if (Objects.equals(task.getAssignee(), aiUserId)) {
            return;
        }
        require(task.getAssignee() != null && !task.getAssignee().isBlank(),
                "governed BPM task has no current assignee");
        Long currentAssignee;
        try {
            currentAssignee = Long.valueOf(task.getAssignee());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("governed BPM task assignee is invalid", exception);
        }
        bpmTasks.transferTask(currentAssignee, new BpmTaskTransferReqVO()
                .setId(task.getId())
                .setAssigneeUserId(reviewer.operatorUserId())
                .setReason("AI 或签接管既有审批任务；" + reason));
    }

    private void submitTaskDecision(Long reviewerUserId, Task task, String decision, String reason) {
        if ("APPROVE".equals(decision)) {
            bpmTasks.approveTask(reviewerUserId, new BpmTaskApproveReqVO()
                    .setId(task.getId()).setReason(reason));
        } else {
            bpmTasks.rejectTask(reviewerUserId, new BpmTaskRejectReqVO()
                    .setId(task.getId()).setReason(reason));
        }
    }

    private List<Task> governedTasks(AgentApprovalDetailView detail) {
        return bpmTasks.getRunningTaskListByProcessInstanceId(
                        detail.getProcessInstanceId(), true, null).stream()
                .filter(task -> REVIEW_TASK_KEYS.contains(task.getTaskDefinitionKey()))
                .toList();
    }

    private static Task selectLegacyTask(List<Task> tasks) {
        return tasks.stream()
                .min(Comparator.comparingInt(AgentApprovalReviewService::taskStage)
                        .thenComparing(Task::getId))
                .orElseThrow();
    }

    private static int taskStage(Task task) {
        return YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY.equals(task.getTaskDefinitionKey()) ? 0 : 1;
    }

    private void assertReviewerSeparation(Reviewer reviewer, AgentApprovalDetailView detail) {
        require(!Objects.equals(detail.getRequesterUserId(), reviewer.operatorUserId()),
                "AI reviewer cannot be the approval requester");
        require(!Objects.equals(detail.getApproverUserId(), reviewer.operatorUserId()),
                "AI reviewer must be independent from the human operating approver");
        WorkOrder workOrder = mapper.selectWorkOrder(reviewer.tenantId(), detail.getWorkOrderId());
        require(workOrder != null, "approval work order is missing");
        require(!Objects.equals(workOrder.getAssigneeUserId(), reviewer.operatorUserId()),
                "AI reviewer cannot be the work-order executor");
    }

    private static String normalizedDecision(String value) {
        String decision = StrUtil.nullToEmpty(value).trim().toUpperCase(Locale.ROOT);
        require(DECISIONS.contains(decision), "decision must be APPROVE or REJECT");
        return decision;
    }

    private static String auditReason(String decision, String reason, String modelId,
                                      String modelRunId, String evidenceSha256) {
        return "[AI代理审核 decision=" + decision
                + " model=" + modelId
                + " run=" + modelRunId
                + " evidenceSha256=" + evidenceSha256
                + "] " + reason;
    }

    private static String requestHash(Reviewer reviewer, String approvalId, String decision, String reason,
                                      String modelId, String modelRunId, String evidenceSha256) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandType", COMMAND_TYPE);
        payload.put("tenantId", reviewer.tenantId());
        payload.put("operatorUserId", reviewer.operatorUserId());
        payload.put("approvalId", approvalId);
        payload.put("decision", decision);
        payload.put("reason", reason);
        payload.put("modelId", modelId);
        payload.put("modelRunId", modelRunId);
        payload.put("evidenceSha256", evidenceSha256);
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(payload));
    }

    private static String requireAuditRef(String value, String field) {
        String normalized = requireText(value, field, AUDIT_VALUE_LIMIT);
        require(SAFE_AUDIT_REF.matcher(normalized).matches(), field + " is invalid");
        return normalized;
    }

    private static String requireText(String value, String field, int limit) {
        require(value != null && !value.isBlank(), field + " is required");
        String normalized = value.trim();
        require(normalized.length() <= limit, field + " exceeds " + limit + " characters");
        return normalized;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Review(Long operatorUserId, AgentApprovalDetailView detail, Task task) {
    }

    private record Reviewer(Long tenantId, Long operatorUserId) {
    }
}
