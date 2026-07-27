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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationsManagedRunQueryServiceTest {

    private final SkillTaskQueryApi skillTaskQueryApi = mock(SkillTaskQueryApi.class);
    private final AiOperationsManagedRunQueryService service = new AiOperationsManagedRunQueryService(skillTaskQueryApi);
    private MockedStatic<SecurityFrameworkUtils> securityFrameworkUtils;
    private SecurityContext webSecurityContext;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
        securityFrameworkUtils = mockStatic(SecurityFrameworkUtils.class);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(101L);
        loginUser.setUserType(2);
        securityFrameworkUtils.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser);
        webSecurityContext = SecurityContextHolder.createEmptyContext();
        webSecurityContext.setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
        SecurityContextHolder.setContext(webSecurityContext);
    }

    @AfterEach
    void tearDown() {
        CloudMoldRpcCallContext.clear();
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        securityFrameworkUtils.close();
    }

    @Test
    void shouldDelegateManagedWorkflowListToSkillTaskQueryApi() {
        List<ManagedSkillTaskWorkflowView> workflows = List.of(ManagedSkillTaskWorkflowView.builder()
                .skillId("skill-a")
                .durableAuthority("SKILL_TASK")
                .orchestrationSurface("DEER_FLOW")
                .build());
        when(skillTaskQueryApi.listManagedWorkflows()).thenAnswer(invocation -> {
            assertThat(CloudMoldRpcCallContext.requireCurrent()).isEqualTo(
                    new CloudMoldRpcCallContext(17L, 101L, 2, "ai-operations.console",
                            "console:managed-workflows"));
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            return workflows;
        });

        List<ManagedSkillTaskWorkflowView> result = service.listManagedWorkflows();

        assertThat(result).isEqualTo(workflows);
        assertThat(SecurityContextHolder.getContext()).isSameAs(webSecurityContext);
        verify(skillTaskQueryApi).listManagedWorkflows();
    }

    @Test
    void shouldDelegateManagedRunPageToSkillTaskQueryApi() {
        ManagedSkillTaskRunPageRequest request = new ManagedSkillTaskRunPageRequest();
        request.setPageNo(1);
        request.setPageSize(10);
        PageResult<ManagedSkillTaskRunView> page = new PageResult<>(List.of(
                ManagedSkillTaskRunView.builder().taskId("task-1").build()), 1L);
        when(skillTaskQueryApi.pageManagedRuns(request)).thenAnswer(invocation -> {
            assertThat(CloudMoldRpcCallContext.requireCurrent()).isEqualTo(
                    new CloudMoldRpcCallContext(17L, 101L, 2, "ai-operations.console",
                            "console:managed-runs:page"));
            return page;
        });

        PageResult<ManagedSkillTaskRunView> result = service.getManagedRunPage(request);

        assertThat(result).isEqualTo(page);
        verify(skillTaskQueryApi).pageManagedRuns(request);
    }

    @Test
    void shouldDelegateManagedRunDetailToSkillTaskQueryApi() {
        ManagedSkillTaskDetailView detail = ManagedSkillTaskDetailView.builder().build();
        when(skillTaskQueryApi.getManagedRun("task-1")).thenAnswer(invocation -> {
            assertThat(CloudMoldRpcCallContext.requireCurrent()).isEqualTo(
                    new CloudMoldRpcCallContext(17L, 101L, 2, "ai-operations.console",
                            "console:managed-runs:task-1"));
            return detail;
        });

        ManagedSkillTaskDetailView result = service.getManagedRun("task-1");

        assertThat(result).isSameAs(detail);
        verify(skillTaskQueryApi).getManagedRun("task-1");
    }
}
