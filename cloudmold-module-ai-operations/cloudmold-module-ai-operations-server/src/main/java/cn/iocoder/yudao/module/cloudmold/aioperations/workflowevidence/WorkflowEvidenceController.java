package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceDigestQueryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceFeedbackRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceIngestRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceProblemRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceTimelineQueryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view.WorkflowEvidenceDigestView;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view.WorkflowEvidenceTimelineItemView;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view.WorkflowEvidenceWriteView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Workflow Evidence Hub")
@RestController
@RequestMapping("/cloudmold/ai-operations/workflow-evidence")
@Validated
@RequiredArgsConstructor
public class WorkflowEvidenceController {

    private final WorkflowEvidenceService service;

    @PostMapping("/ingest")
    @Operation(summary = "追加 Workflow observation 与可选外部证据快照")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:manage')")
    public CommonResult<WorkflowEvidenceWriteView> ingest(@Valid @RequestBody WorkflowEvidenceIngestRequest request) {
        return success(service.ingest(request));
    }

    @PostMapping("/problem")
    @Operation(summary = "追加 Workflow problem")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:manage')")
    public CommonResult<WorkflowEvidenceWriteView> recordProblem(
            @Valid @RequestBody WorkflowEvidenceProblemRequest request) {
        return success(service.recordProblem(request));
    }

    @PostMapping("/feedback")
    @Operation(summary = "追加 Workflow user feedback")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:manage')")
    public CommonResult<WorkflowEvidenceWriteView> recordFeedback(
            @Valid @RequestBody WorkflowEvidenceFeedbackRequest request) {
        return success(service.recordFeedback(request));
    }

    @GetMapping("/query")
    @Operation(summary = "按固定 workflow 与时间窗分页查询 Evidence Hub 时间线")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<PageResult<WorkflowEvidenceTimelineItemView>> query(
            @Valid WorkflowEvidenceTimelineQueryRequest request) {
        return success(service.query(request));
    }

    @GetMapping("/digests/daily")
    @Operation(summary = "按天聚合 digest-ready 证据摘要")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<WorkflowEvidenceDigestView> dailyDigest(@Valid WorkflowEvidenceDigestQueryRequest request) {
        return success(service.dailyDigest(request));
    }

    @GetMapping("/digests/weekly")
    @Operation(summary = "按周聚合 digest-ready 证据摘要")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<WorkflowEvidenceDigestView> weeklyDigest(@Valid WorkflowEvidenceDigestQueryRequest request) {
        return success(service.weeklyDigest(request));
    }
}
