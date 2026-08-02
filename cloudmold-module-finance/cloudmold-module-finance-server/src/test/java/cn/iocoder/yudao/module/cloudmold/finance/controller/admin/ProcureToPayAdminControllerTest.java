package cn.iocoder.yudao.module.cloudmold.finance.controller.admin;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.*;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureToPayAdminVOs.AdminCommandResult;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcureToPayAdminControllerTest {

    @Test
    void exposesOnlyCanonicalCloudMoldP2pRootAndPermissions() throws Exception {
        RequestMapping root = ProcureToPayAdminController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/cloudmold/finance/procure-to-pay");

        assertPermission("invoices", "cloudmold:finance:procure-to-pay:query");
        assertPermission("invoiceCommand", "cloudmold:finance:procure-to-pay:command");
        assertPermission("createPolicy", "cloudmold:finance:procure-to-pay:govern");
        assertThat(root.value()[0]).doesNotContain("/erp", "/pay", "yudao");
    }

    @Test
    void serializesJavascriptUnsafeBigintsAsStrings() throws Exception {
        ProcureToPayResult result = ProcureToPayResult.builder()
                .operationId(9_007_199_254_740_993L).aggregateType("finance_supplier_invoice")
                .aggregateId("invoice-1").aggregateVersion(9_007_199_254_740_995L).status("POSTED").build();
        Method adapter = ProcureToPayAdminController.class.getDeclaredMethod("admin", ProcureToPayResult.class);
        adapter.setAccessible(true);
        AdminCommandResult admin = (AdminCommandResult) adapter.invoke(null, result);

        assertThat(JsonUtils.toJsonString(admin))
                .contains("\"operationId\":\"9007199254740993\"")
                .contains("\"aggregateVersion\":9007199254740995");
    }

    @Test
    void mockMvcAcceptsFrozenFlatRunMatchOperationAndSerializesContractTypes() throws Exception {
        ProcureToPayAdminController controller = new ProcureToPayAdminController();
        SupplierInvoiceCommandApi invoiceApi = mock(SupplierInvoiceCommandApi.class);
        FinanceActorPrincipalPort actorPort = mock(FinanceActorPrincipalPort.class);
        ReflectionTestUtils.setField(controller, "invoiceApi", invoiceApi);
        ReflectionTestUtils.setField(controller, "actorPort", actorPort);
        when(actorPort.resolveSystemAdmin(nullable(Long.class))).thenReturn("finance-admin");
        when(invoiceApi.runThreeWayMatch(any(), eq("finance-admin"))).thenReturn(ProcureToPayResult.builder()
                .operationId(9_007_199_254_740_993L).duplicate(false).aggregateType("finance_invoice_match_run")
                .aggregateId("match-run-1").aggregateVersion(2L).supplierInvoiceId("invoice-1")
                .matchRunId("match-run-1").status("MATCHED").build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/cloudmold/finance/procure-to-pay/supplier-invoices/command")
                        .contentType("application/json")
                        .content("""
                                {"envelope":{"idempotencyKey":"match-1","runId":"run-1",
                                "correlationId":"22222222-2222-4222-8222-222222222222",
                                "occurredAt":"2026-08-02T00:00:00Z"},
                                "operation":"RUN_THREE_WAY_MATCH","supplierInvoiceId":"invoice-1",
                                "expectedVersion":2,"matchPolicyId":"policy-1","matchPolicyVersion":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationId").value("9007199254740993"))
                .andExpect(jsonPath("$.data.aggregateVersion").value(2));

        verify(invoiceApi).runThreeWayMatch(argThat(command -> "invoice-1".equals(command.getSupplierInvoiceId())
                && "policy-1".equals(command.getMatchPolicyId()) && command.getMatchPolicyVersion() == 3L),
                eq("finance-admin"));
    }

    private static void assertPermission(String methodName, String permission) {
        Method method = java.util.Arrays.stream(ProcureToPayAdminController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(method.getAnnotation(PreAuthorize.class).value()).contains(permission);
    }
}
