package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class TemporalApprovedTimeoutRecoveryService {

    private final AiOperationsTemporalMapper mapper;
    private final AiOperationsTemporalScheduleService schedules;

    public boolean recover(TemporalRunBindingRecord binding) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (mapper.claimApprovedTimeoutRecovery(
                binding.getTenantId(), binding.getTemporalRunId(), now) != 1) {
            return false;
        }
        try {
            schedules.triggerRecovery(binding.getScheduleId());
            if (mapper.markApprovedTimeoutRecoveryDispatched(
                    binding.getTenantId(), binding.getTemporalRunId(), LocalDateTime.now(ZoneOffset.UTC)) != 1) {
                throw new IllegalStateException("Approved timeout recovery dispatch lost its binding claim");
            }
            log.info("Dispatched fresh run for approved expired workflow tenant={} approval={} schedule={}",
                    binding.getTenantId(), binding.getApprovalId(), binding.getScheduleId());
            return true;
        } catch (RuntimeException exception) {
            mapper.releaseApprovedTimeoutRecovery(binding.getTenantId(), binding.getTemporalRunId(),
                    "RECOVERY_DISPATCH_FAILED", LocalDateTime.now(ZoneOffset.UTC));
            log.warn("Approved timeout recovery remains retryable tenant={} approval={} schedule={}",
                    binding.getTenantId(), binding.getApprovalId(), binding.getScheduleId(), exception);
            return false;
        }
    }
}
