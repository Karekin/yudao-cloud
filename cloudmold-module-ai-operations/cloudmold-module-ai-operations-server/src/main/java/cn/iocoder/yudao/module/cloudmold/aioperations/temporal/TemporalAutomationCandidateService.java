package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.TemporalAutomationCandidateCreateReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunQueryServiceFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class TemporalAutomationCandidateService {

    private final AiOperationsTemporalMapper mapper;
    private final AiOperationsManagedRunQueryServiceFacade workflows;

    public TemporalAutomationCandidateView enqueue(TemporalAutomationCandidateCreateReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        workflows.requireWorkflow(request.getSkillId(), request.getSkillVersion());
        require(JsonUtils.parseTree(request.getInputJson()) != null,
                "Automation candidate inputJson must be valid JSON");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime dueAt = request.getDueAt() == null ? now : request.getDueAt();
        String candidateId = "mac-" + DigestUtil.sha256Hex(
                tenantId + ":" + request.getSkillId() + ":" + request.getSkillVersion()
                        + ":" + request.getClientRequestKey()).substring(0, 32);
        TemporalAutomationCandidateRecord record = new TemporalAutomationCandidateRecord()
                .setTenantId(tenantId)
                .setCandidateId(candidateId)
                .setClientRequestKey(request.getClientRequestKey())
                .setSkillId(request.getSkillId())
                .setSkillVersion(request.getSkillVersion())
                .setBusinessKey(request.getBusinessKey())
                .setInputJson(request.getInputJson())
                .setDueAt(dueAt)
                .setStatus("PENDING")
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        boolean created = mapper.insertAutomationCandidate(record) == 1;
        return TemporalAutomationCandidateView.builder()
                .candidateId(candidateId)
                .clientRequestKey(request.getClientRequestKey())
                .skillId(request.getSkillId())
                .skillVersion(request.getSkillVersion())
                .businessKey(request.getBusinessKey())
                .status(created ? "PENDING" : "DUPLICATE")
                .dueAt(dueAt.toInstant(ZoneOffset.UTC))
                .created(created)
                .build();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
