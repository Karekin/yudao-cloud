package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmSystemModelRegisterReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.definition.BpmSystemModelApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class YudaoBpmApprovalWorkflowAdapterTest {

    private final BpmProcessInstanceApi bpm = mock(BpmProcessInstanceApi.class);
    private final BpmSystemModelApi models = mock(BpmSystemModelApi.class);
    private final YudaoBpmApprovalWorkflowAdapter adapter = new YudaoBpmApprovalWorkflowAdapter(bpm, models);

    @BeforeEach
    void setUp() {
        when(models.register(any())).thenReturn(CommonResult.success("model-17"));
    }

    @Test
    void startsVersionedProcessWithOnlyFrozenAllowlistedVariables() {
        when(bpm.createProcessInstance(eq(100L), any())).thenReturn(CommonResult.success("process-17"));
        ApprovalWorkflowStartCandidate candidate = candidate();

        assertThat(adapter.start(candidate)).isEqualTo("process-17");

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> request =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(bpm).createProcessInstance(eq(100L), request.capture());
        assertThat(request.getValue().getProcessDefinitionKey()).isEqualTo("cloudmold-agent-approval-v1");
        assertThat(request.getValue().getBusinessKey()).isEqualTo("cloudmold-agent-approval:17:approval-1");
        assertThat(request.getValue().getVariables()).contains(
                org.assertj.core.api.Assertions.entry("approval_id", "approval-1"),
                org.assertj.core.api.Assertions.entry("work_order_id", "work-1"),
                org.assertj.core.api.Assertions.entry("action_code", "purchase.commit"),
                org.assertj.core.api.Assertions.entry("role_code", "buyer"),
                org.assertj.core.api.Assertions.entry("risk_level", "R3"),
                org.assertj.core.api.Assertions.entry("scope_hash", "a".repeat(64)),
                org.assertj.core.api.Assertions.entry("approval_stage",
                        "OPERATING_PRINCIPAL_THEN_DOMAIN_COUNTERSIGN"),
                org.assertj.core.api.Assertions.entry("approver_source", "TENANT_APPROVAL_AUTHORITY_GRANT"),
                org.assertj.core.api.Assertions.entry("responsibility_roles",
                        java.util.List.of("buyer", "finance")),
                org.assertj.core.api.Assertions.entry("responsibility_authority_sha256", "b".repeat(64)),
                org.assertj.core.api.Assertions.entry("countersign_strategy",
                        "SERIAL_STAGE_THEN_PARALLEL_UNANIMOUS"),
                org.assertj.core.api.Assertions.entry("business_action",
                        "提交采购单（purchase.commit）"),
                org.assertj.core.api.Assertions.entry("impact_objects",
                        "supplierId=supplier-1；sku=SKU-A"),
                org.assertj.core.api.Assertions.entry("impact_metrics",
                        "quantity=120；amountMinor=398000；currency=CNY"),
                org.assertj.core.api.Assertions.entry("recommendation", "建议采购"),
                org.assertj.core.api.Assertions.entry("evidence_summary", "销量预测与安全库存"),
                org.assertj.core.api.Assertions.entry("non_execution_consequence", "预计三天后缺货"),
                org.assertj.core.api.Assertions.entry("execution_steps", "创建采购单, 等待供应商确认"));
        assertThat(request.getValue().getStartUserSelectAssignees())
                .containsOnlyKeys(YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY,
                        YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY);
        assertThat(request.getValue().getStartUserSelectAssignees()
                .get(YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY)).containsExactly(200L);
        assertThat(request.getValue().getStartUserSelectAssignees()
                .get(YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY))
                .containsExactly(210L, 220L);

        ArgumentCaptor<BpmSystemModelRegisterReqDTO> modelRequest =
                ArgumentCaptor.forClass(BpmSystemModelRegisterReqDTO.class);
        verify(models).register(modelRequest.capture());
        assertThat(modelRequest.getValue().getFormCustomCreatePath())
                .isEqualTo("/cloudmold/agent-control/approval-form");
        assertThat(modelRequest.getValue().getFormCustomViewPath())
                .isEqualTo("/cloudmold/agent-control/approval-form.vue");
    }

    @Test
    void rejectsEmptyProcessInstanceId() {
        when(bpm.createProcessInstance(eq(100L), any())).thenReturn(CommonResult.success(""));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.start(candidate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BPM returned an empty process instance id");
    }

    @Test
    void rejectsMissingOrSelfApproverBeforeCallingBpm() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> adapter.start(candidate().setApproverUserId(null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approver is missing");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> adapter.start(candidate().setApproverUserId(100L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ from requester");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> adapter.start(candidate().setApproverUserId(300L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ from work-order executor");
        verifyNoInteractions(bpm, models);
    }

    @Test
    void rejectsMissingOrConflictingR3ResponsibilityAuthority() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> adapter.start(candidate()
                .setResponsibilityRoleCodes(java.util.List.of())
                .setResponsibilityApproverUserIds(java.util.List.of())
                .setResponsibilityAuthoritySha256(null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authority is missing");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> adapter.start(candidate()
                .setResponsibilityApproverUserIds(java.util.List.of(200L, 220L)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("separation of duties");
        verifyNoInteractions(bpm, models);
    }

    @Test
    void bundledApprovalWorkflowContainsRenderableDiagramInterchange() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "approval-workflow/cloudmold-agent-approval-v1.bpmn20.xml")) {
            assertThat(input).isNotNull();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var document = factory.newDocumentBuilder().parse(input);

            String bpmnDiNamespace = "http://www.omg.org/spec/BPMN/20100524/DI";
            assertThat(document.getElementsByTagNameNS(bpmnDiNamespace, "BPMNDiagram").getLength()).isEqualTo(1);
            assertThat(document.getElementsByTagNameNS(bpmnDiNamespace, "BPMNShape").getLength()).isEqualTo(8);
            assertThat(document.getElementsByTagNameNS(bpmnDiNamespace, "BPMNEdge").getLength()).isEqualTo(10);
            assertThat(document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL",
                    "terminateEventDefinition").getLength()).isEqualTo(1);
        }
    }

    @Test
    void addsTheDedicatedAiReviewerAsAnOrSignPeer() {
        AgentApprovalWorkflowProperties properties = new AgentApprovalWorkflowProperties();
        properties.setAiReviewerOrSignEnabled(true);
        properties.setAiReviewerUserIds(Set.of(229L));
        properties.setAiReviewAllowedRiskLevels(Set.of("R2", "R3"));
        YudaoBpmApprovalWorkflowAdapter orSignAdapter =
                new YudaoBpmApprovalWorkflowAdapter(bpm, models, properties);
        when(bpm.createProcessInstance(eq(100L), any())).thenReturn(CommonResult.success("process-18"));

        orSignAdapter.start(candidate());

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> request =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(bpm).createProcessInstance(eq(100L), request.capture());
        assertThat(request.getValue().getVariables()).containsEntry("ai_or_sign_enabled", true);
        assertThat(request.getValue().getStartUserSelectAssignees()
                .get(YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY)).containsExactly(229L);
    }

    private static ApprovalWorkflowStartCandidate candidate() {
        return new ApprovalWorkflowStartCandidate().setTenantId(17L).setApprovalId("approval-1")
                .setWorkOrderId("work-1").setWorkOrderTitle("提交采购单")
                .setBusinessContextJson("""
                        {"supplierId":"supplier-1","sku":"SKU-A","quantity":120,
                         "amountMinor":398000,"currency":"CNY","recommendation":"建议采购",
                         "evidenceSummary":"销量预测与安全库存","nonExecutionConsequence":"预计三天后缺货",
                         "executionSteps":["创建采购单","等待供应商确认"]}
                        """)
                .setActionCode("purchase.commit").setRoleCode("buyer")
                .setRiskLevel("R3").setRequesterUserId(100L).setExecutorUserId(300L)
                .setApproverUserId(200L)
                .setResponsibilityRoleCodes(java.util.List.of("buyer", "finance"))
                .setResponsibilityApproverUserIds(java.util.List.of(210L, 220L))
                .setResponsibilityAuthoritySha256("b".repeat(64)).setScopeHash("a".repeat(64))
                .setProcessDefinitionKey("cloudmold-agent-approval-v1")
                .setBusinessKey("cloudmold-agent-approval:17:approval-1")
                .setStatus("START_REQUESTED").setVersion(1L);
    }

}
