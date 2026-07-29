package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunQueryServiceFacade;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动并周期性保证每个托管定义都有 Temporal 每日调度。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
@Slf4j
public class AiOperationsTemporalSeedRunner implements ApplicationRunner {

    private final AiOperationsTemporalScheduleService scheduleService;
    private final AiOperationsTemporalSeedProperties seedProperties;
    private final AiOperationsManagedRunQueryServiceFacade workflows;
    private final ManagedWorkflowAgentGovernanceSeeder governanceSeeder;

    @Override
    public void run(ApplicationArguments args) {
        reconcile();
    }

    @Scheduled(fixedDelayString =
            "${cloudmold.ai-operations.temporal.seed.reconcile-interval-ms:600000}")
    public void reconcile() {
        if (!seedProperties.isEnabled()) {
            return;
        }
        if (seedProperties.getTenantIds() == null || seedProperties.getTenantIds().isEmpty()) {
            log.info("AI Operations Temporal managed daily seed skipped: no tenantIds configured");
            return;
        }

        for (Long tenantId : seedProperties.getTenantIds()) {
            if (tenantId == null) {
                continue;
            }
            TenantContextHolder.setTenantId(tenantId);
            setSeedLoginUser(tenantId);
            try {
                reconcileTenant(tenantId);
            } catch (RuntimeException exception) {
                log.warn("AI Operations Temporal managed daily reconcile failed for tenant {}: {}",
                        tenantId, exception.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
                TenantContextHolder.clear();
            }
        }
    }

    private void reconcileTenant(Long tenantId) {
        List<ManagedSkillTaskWorkflowView> registered = workflows.listWorkflowsAs(
                seedProperties.getOperatorUserId(), seedProperties.getOperatorUserType());
        ManagedWorkflowAgentGovernanceSeeder.ReconcileResult governance =
                governanceSeeder.reconcile(tenantId, registered);
        int created = 0;
        int existing = 0;
        int failed = 0;
        for (ManagedSkillTaskWorkflowView workflow : registered) {
            try {
                if (scheduleService.reconcileManagedDaily(workflow, seedProperties)) {
                    created++;
                } else {
                    existing++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn("AI Operations Temporal managed daily reconcile failed for tenant {}, skill {}@{}: {}",
                        tenantId, workflow.getSkillId(), workflow.getSkillVersion(), exception.getMessage());
            }
        }
        Set<String> desiredSkillIds = registered.stream()
                .map(ManagedSkillTaskWorkflowView::getSkillId)
                .collect(Collectors.toUnmodifiableSet());
        int pausedObsolete = scheduleService.pauseObsoleteManagedDaily(desiredSkillIds);
        log.info("AI Operations Temporal managed daily schedules reconciled for tenant {}: "
                        + "registered={}, created={}, existing={}, failed={}, pausedObsolete={}, "
                        + "governedRoles={}, createdRoles={}, createdPolicies={}, createdGrants={}",
                tenantId, registered.size(), created, existing, failed, pausedObsolete,
                governance.roleCount(), governance.createdRoles(),
                governance.createdPolicies(), governance.createdGrants());
    }

    private void setSeedLoginUser(Long tenantId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(seedProperties.getOperatorUserId());
        loginUser.setUserType(seedProperties.getOperatorUserType());
        loginUser.setTenantId(tenantId);
        loginUser.setVisitTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        loginUser, null, Collections.emptyList()));
    }
}
