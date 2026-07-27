package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.TemporalScheduleCreateReqVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 启动时按配置保证自动铺品定时工作流存在。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal.seed", name = "enabled", havingValue = "true")
@Slf4j
public class AiOperationsTemporalSeedRunner implements ApplicationRunner {

    private final AiOperationsTemporalScheduleService scheduleService;
    private final AiOperationsTemporalSeedProperties seedProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (seedProperties.getTenantIds() == null || seedProperties.getTenantIds().isEmpty()) {
            log.info("AI Operations Temporal auto-shelf seed skipped: no tenantIds configured");
            return;
        }

        for (Long tenantId : seedProperties.getTenantIds()) {
            if (tenantId == null) {
                continue;
            }
            TenantContextHolder.setTenantId(tenantId);
            setSeedLoginUser(tenantId);
            try {
                TemporalScheduleCreateReqVO request = buildRequest();
                scheduleService.create(request);
                log.info("AI Operations Temporal auto-shelf schedule initialized for tenant {}", tenantId);
            } catch (IllegalArgumentException exception) {
                if (exception.getMessage() != null && exception.getMessage().contains("already exists")) {
                    log.info("AI Operations Temporal auto-shelf schedule already exists for tenant {}", tenantId);
                } else if (exception.getMessage() != null && exception.getMessage().contains("not registered")) {
                    log.warn("AI Operations Temporal auto-shelf workflow is not registered; skip tenant {}", tenantId);
                    log.warn("Configure exact skillId/skillVersion for auto-shelf seed before enabling temporal auto seed");
                } else {
                    throw exception;
                }
            } finally {
                SecurityContextHolder.clearContext();
                TenantContextHolder.clear();
            }
        }
    }

    private TemporalScheduleCreateReqVO buildRequest() {
        TemporalScheduleCreateReqVO request = new TemporalScheduleCreateReqVO();
        request.setScheduleId(seedProperties.getScheduleId());
        request.setDisplayName(seedProperties.getDisplayName());
        request.setDescription(seedProperties.getDescription());
        request.setSkillId(seedProperties.getSkillId());
        request.setSkillVersion(seedProperties.getSkillVersion());
        request.setInputJson(seedProperties.getInputJson());
        request.setIntervalSeconds(seedProperties.getIntervalSeconds());
        request.setTimeZone(seedProperties.getTimeZone());
        request.setRoleCode(seedProperties.getRoleCode());
        request.setActionCode(seedProperties.getActionCode());
        request.setPaused(seedProperties.isPaused());
        return request;
    }

    private void setSeedLoginUser(Long tenantId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(seedProperties.getOperatorUserId());
        loginUser.setUserType(seedProperties.getOperatorUserType());
        loginUser.setTenantId(tenantId);
        loginUser.setVisitTenantId(tenantId);
        SecurityFrameworkUtils.setLoginUser(loginUser, null);
    }
}
