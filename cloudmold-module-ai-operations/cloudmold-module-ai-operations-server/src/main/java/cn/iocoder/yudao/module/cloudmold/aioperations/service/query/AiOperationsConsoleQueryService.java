package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiArtifactPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiObservationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiWorkflowPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql.AiOperationsConsoleQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiOperationsConsoleQueryService {

    private final AiOperationsConsoleQueryMapper queryMapper;

    public PageResult<AiWorkflowPageItem> getWorkflowPage(AiWorkflowPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String applicationId = normalize(request.getApplicationId());
        String applicationCode = normalize(request.getApplicationCode());
        String applicationStatus = normalizeUpper(request.getApplicationStatus());
        String workflowId = normalize(request.getWorkflowId());
        String workflowCode = normalize(request.getWorkflowCode());
        long total = queryMapper.countWorkflowPage(tenantId, applicationId, applicationCode, applicationStatus,
                workflowId, workflowCode);
        if (total == 0) {
            return PageResult.empty();
        }
        return new PageResult<>(queryMapper.selectWorkflowPage(tenantId, applicationId, applicationCode,
                applicationStatus, workflowId, workflowCode, offset(request.getPageNo(), request.getPageSize()),
                request.getPageSize()), total);
    }

    public AiWorkflowRunDetailView getRunDetail(String runId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String normalizedRunId = normalize(runId);
        require(normalizedRunId != null, "runId is required");
        AiWorkflowRunDetailRow row = requireNonNull(queryMapper.selectRunDetail(tenantId, normalizedRunId),
                "workflow run not found");

        AiWorkflowRunDetailView view = new AiWorkflowRunDetailView();
        view.setRun(runView(row));
        view.setApplication(applicationView(row));
        view.setWorkflow(workflowView(row));
        view.setInvocationAttempts(queryMapper.selectRunInvocations(tenantId, normalizedRunId));
        view.setFeedbackArtifacts(queryMapper.selectRunFeedback(tenantId, normalizedRunId));
        view.setStatusHistory(queryMapper.selectRunStatusHistory(tenantId, normalizedRunId));
        return view;
    }

    public PageResult<AiArtifactPageItem> getArtifactPage(AiArtifactPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String runId = normalize(request.getRunId());
        String applicationId = normalize(request.getApplicationId());
        String workflowId = normalize(request.getWorkflowId());
        String feedbackType = normalizeUpper(request.getFeedbackType());
        String outcomeCode = normalizeUpper(request.getOutcomeCode());
        String evaluatorType = normalizeUpper(request.getEvaluatorType());
        long total = queryMapper.countArtifactPage(tenantId, runId, applicationId, workflowId, feedbackType,
                outcomeCode, evaluatorType, request.getOccurredAtFrom(), request.getOccurredAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        return new PageResult<>(queryMapper.selectArtifactPage(tenantId, runId, applicationId, workflowId,
                feedbackType, outcomeCode, evaluatorType, request.getOccurredAtFrom(), request.getOccurredAtTo(),
                offset(request.getPageNo(), request.getPageSize()), request.getPageSize()), total);
    }

    public PageResult<AiObservationPageItem> getObservationPage(AiObservationPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String runId = normalize(request.getRunId());
        String applicationId = normalize(request.getApplicationId());
        String workflowId = normalize(request.getWorkflowId());
        String stepRef = normalize(request.getStepRef());
        String providerCode = normalizeUpper(request.getProviderCode());
        String modelCode = normalize(request.getModelCode());
        String outcome = normalizeUpper(request.getOutcome());
        long total = queryMapper.countObservationPage(tenantId, runId, applicationId, workflowId, stepRef,
                providerCode, modelCode, outcome, request.getOccurredAtFrom(), request.getOccurredAtTo());
        if (total == 0) {
            return PageResult.empty();
        }
        return new PageResult<>(queryMapper.selectObservationPage(tenantId, runId, applicationId, workflowId,
                stepRef, providerCode, modelCode, outcome, request.getOccurredAtFrom(), request.getOccurredAtTo(),
                offset(request.getPageNo(), request.getPageSize()), request.getPageSize()), total);
    }

    private static AiWorkflowRunDetailView.RunView runView(AiWorkflowRunDetailRow row) {
        AiWorkflowRunDetailView.RunView view = new AiWorkflowRunDetailView.RunView();
        view.setRunId(row.getRunId());
        view.setRunKey(row.getRunKey());
        view.setApplicationId(row.getApplicationId());
        view.setWorkflowId(row.getWorkflowId());
        view.setWorkflowVersionId(row.getWorkflowVersionId());
        view.setWorkflowVersion(row.getWorkflowVersion());
        view.setTriggerType(row.getTriggerType());
        view.setBusinessRef(row.getBusinessRef());
        view.setStatus(row.getStatus());
        view.setErrorCode(row.getErrorCode());
        view.setExpectedInvocationCount(row.getExpectedInvocationCount());
        view.setAggregateVersion(row.getAggregateVersion());
        view.setStartedAt(row.getStartedAt());
        view.setFinishedAt(row.getFinishedAt());
        view.setCreatedAt(row.getCreatedAt());
        view.setUpdatedAt(row.getUpdatedAt());
        return view;
    }

    private static AiWorkflowRunDetailView.ApplicationView applicationView(AiWorkflowRunDetailRow row) {
        AiWorkflowRunDetailView.ApplicationView view = new AiWorkflowRunDetailView.ApplicationView();
        view.setApplicationId(row.getApplicationId());
        view.setApplicationCode(row.getApplicationCode());
        view.setApplicationName(row.getApplicationName());
        view.setApplicationStatus(row.getApplicationStatus());
        return view;
    }

    private static AiWorkflowRunDetailView.WorkflowView workflowView(AiWorkflowRunDetailRow row) {
        AiWorkflowRunDetailView.WorkflowView view = new AiWorkflowRunDetailView.WorkflowView();
        view.setWorkflowId(row.getWorkflowId());
        view.setWorkflowCode(row.getWorkflowCode());
        view.setWorkflowVersionId(row.getWorkflowVersionId());
        view.setWorkflowVersion(row.getWorkflowVersion());
        view.setCurrentWorkflowVersion(row.getCurrentWorkflowVersion());
        view.setDefinitionRef(row.getDefinitionRef());
        view.setDefinitionSha256(row.getDefinitionSha256());
        view.setPublishedAt(row.getPublishedAt());
        return view;
    }

    private static long offset(int pageNo, int pageSize) {
        return (long) (pageNo - 1) * pageSize;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
