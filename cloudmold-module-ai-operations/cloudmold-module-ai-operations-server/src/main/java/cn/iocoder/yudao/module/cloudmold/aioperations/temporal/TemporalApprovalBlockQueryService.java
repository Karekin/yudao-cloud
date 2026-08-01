package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TemporalApprovalBlockQueryService {

    private final AiOperationsTemporalMapper mapper;

    public TemporalApprovalBlockView getByApprovalId(String approvalId) {
        TemporalRunBindingRecord binding = mapper.selectRunBindingByApproval(
                TenantContextHolder.getRequiredTenantId(), approvalId);
        if (binding == null) {
            return null;
        }
        return TemporalApprovalBlockView.builder()
                .approvalId(binding.getApprovalId())
                .workOrderId(binding.getWorkOrderId())
                .scheduleId(binding.getScheduleId())
                .temporalWorkflowId(binding.getTemporalWorkflowId())
                .temporalRunId(binding.getTemporalRunId())
                .skillTaskId(binding.getSkillTaskId())
                .managedRunId(binding.getManagedRunId())
                .status(binding.getStatus())
                .updatedAt(binding.getUpdatedAt())
                .build();
    }
}
