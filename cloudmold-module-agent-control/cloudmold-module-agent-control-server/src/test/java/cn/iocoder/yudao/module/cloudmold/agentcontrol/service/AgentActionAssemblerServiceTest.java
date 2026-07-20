package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.AgentControlProperties;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ActorRoleGrant;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentActionAssemblerServiceTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final SkillTaskCommandApi skillTasks = mock(SkillTaskCommandApi.class);
    private final AgentExecutionBindingApi bindings = mock(AgentExecutionBindingApi.class);
    private final AgentControlProperties properties = new AgentControlProperties();
    private final AgentActionAssemblerService service =
            new AgentActionAssemblerService(mapper, skillTasks, bindings, properties);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void assemblesAllTechnicalFieldsOnServerAndBindsSubmittedTask() {
        TenantContextHolder.setTenantId(17L);
        properties.setActionSubmissionEnabled(true);
        String input = "{\"canonicalSpuId\":\"spu-1\",\"lowStockThreshold\":5}";
        WorkOrder workOrder = executableWorkOrder(input);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(workOrder);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("inventory-control"),
                any(LocalDateTime.class))).thenReturn(new ActorRoleGrant());
        when(skillTasks.submit(any())).thenAnswer(ignored -> {
            assertThat(CloudMoldRpcCallContext.requireCurrent()).isEqualTo(
                    new CloudMoldRpcCallContext(17L, 101L, 2,
                            AgentActionAssemblerService.STOCKOUT_SKILL, "run-1"));
            return SkillTaskView.builder().taskId("task-1").build();
        });
        when(bindings.bind(any(), eq(101L))).thenReturn(AgentControlResult.builder()
                .aggregateId("binding-1").status("BOUND").build());

        var result = service.assembleAndSubmit(AgentActionAssemblyCommand.builder()
                .workOrderId("wo-1").workOrderExpectedVersion(2L).build(), 101L);

        ArgumentCaptor<SkillTaskSubmitCommand> submit = ArgumentCaptor.forClass(SkillTaskSubmitCommand.class);
        verify(skillTasks).submit(submit.capture());
        assertThat(submit.getValue().getSkillId()).isEqualTo(AgentActionAssemblerService.STOCKOUT_SKILL);
        assertThat(submit.getValue().getSkillVersion()).isEqualTo("1.0.0");
        assertThat(submit.getValue().getRunId()).isEqualTo("run-1");
        assertThat(submit.getValue().getClientRequestKey()).isEqualTo("agent-control:wo-1:execution:1");
        assertThat(submit.getValue().getInputJson()).isEqualTo(input);
        assertThat(submit.getValue().getApprovalRef()).isNull();
        assertThat(result.getExecutionBindingId()).isEqualTo("binding-1");
        assertThatThrownBy(CloudMoldRpcCallContext::requireCurrent)
                .hasMessage("CloudMold RPC context is required");
    }

    @Test
    void failsClosedWhenDisabledOrFrozenContextContainsTechnicalFields() {
        TenantContextHolder.setTenantId(17L);
        var command = AgentActionAssemblyCommand.builder().workOrderId("wo-1").workOrderExpectedVersion(2L).build();
        assertThatThrownBy(() -> service.assembleAndSubmit(command, 101L))
                .hasMessage("Agent action submission is disabled");

        properties.setActionSubmissionEnabled(true);
        WorkOrder unsafe = executableWorkOrder("{\"canonicalSpuId\":\"spu-1\"}");
        unsafe.setBusinessContextJson("{\"canonicalSpuId\":\"spu-1\",\"operatorId\":999}")
                .setExecutionInputSha256(AgentControlJson.sha256(
                        "{\"canonicalSpuId\":\"spu-1\",\"operatorId\":999}"));
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(unsafe);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(101L), eq("inventory-control"), any()))
                .thenReturn(new ActorRoleGrant());
        assertThatThrownBy(() -> service.assembleAndSubmit(command, 101L))
                .hasMessage("businessContextJson must not contain technical execution parameters");
        verifyNoInteractions(skillTasks, bindings);
    }

    private static WorkOrder executableWorkOrder(String input) {
        String canonical = AgentControlJson.canonicalBusinessObject(input, "input");
        return new WorkOrder().setWorkOrderId("wo-1").setTenantId(17L).setRoleCode("inventory-control")
                .setActionCode(AgentActionAssemblerService.STOCKOUT_ACTION).setStatus("IN_PROGRESS")
                .setAssigneeUserId(101L).setExecutionRequired(true).setRiskLevel("R1")
                .setSkillId(AgentActionAssemblerService.STOCKOUT_SKILL).setSkillVersion("1.0.0")
                .setSkillDefinitionClosureSha256("a".repeat(64)).setBusinessContextJson(canonical)
                .setExecutionInputSha256(AgentControlJson.sha256(canonical)).setActiveRunId("run-1").setVersion(2L);
    }

}
