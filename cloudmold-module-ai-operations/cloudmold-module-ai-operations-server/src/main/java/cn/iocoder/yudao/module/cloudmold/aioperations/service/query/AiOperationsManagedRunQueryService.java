package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class AiOperationsManagedRunQueryService {

    static final String CONSOLE_SKILL_ID = "ai-operations.console";

    private final SkillTaskQueryApi skillTaskQueryApi;

    public List<ManagedSkillTaskWorkflowView> listManagedWorkflows() {
        return queryWithRpcContext("managed-workflows", skillTaskQueryApi::listManagedWorkflows);
    }

    public List<ManagedSkillTaskWorkflowView> listManagedWorkflowsAs(Long operatorUserId,
                                                                     Integer operatorUserType) {
        return queryWithRpcContext("managed-workflows", operatorUserId, operatorUserType,
                skillTaskQueryApi::listManagedWorkflows);
    }

    public PageResult<ManagedSkillTaskRunView> getManagedRunPage(ManagedSkillTaskRunPageRequest request) {
        Objects.requireNonNull(request, "request");
        return queryWithRpcContext("managed-runs:page", () -> skillTaskQueryApi.pageManagedRuns(request));
    }

    public ManagedSkillTaskDetailView getManagedRun(String taskId) {
        return queryWithRpcContext("managed-runs:" + normalize(taskId), () -> skillTaskQueryApi.getManagedRun(taskId));
    }

    private <T> T queryWithRpcContext(String runIdSuffix, Supplier<T> action) {
        LoginUser loginUser = Objects.requireNonNull(SecurityFrameworkUtils.getLoginUser(),
                "Login user is required for AI Operations managed query");
        return queryWithRpcContext(runIdSuffix, loginUser.getId(), loginUser.getUserType(), action);
    }

    private <T> T queryWithRpcContext(String runIdSuffix, Long operatorUserId,
                                      Integer operatorUserType, Supplier<T> action) {
        Objects.requireNonNull(operatorUserId, "operatorUserId");
        Objects.requireNonNull(operatorUserType, "operatorUserType");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        CloudMoldRpcCallContext context = new CloudMoldRpcCallContext(tenantId, operatorUserId,
                operatorUserType, CONSOLE_SKILL_ID, "console:" + normalize(runIdSuffix));
        SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
        try (CloudMoldRpcCallContext.Scope ignored = CloudMoldRpcCallContext.open(context)) {
            // The signed CloudMold context is the only identity contract crossing this RPC boundary.
            // Prevent Dubbo Spring Security from serializing the full web LoginUser principal.
            SecurityContextHolder.clearContext();
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previousSecurityContext);
        }
    }

    private static String normalize(String value) {
        return value == null ? "unknown" : value.trim().replace(' ', '-');
    }
}
