package cn.iocoder.yudao.module.cloudmold.crm.controller.admin.contract;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommandPort;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommandResult;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractOperation;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractQueryApi;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractView;
import cn.iocoder.yudao.module.cloudmold.crm.service.contract.SalesContractActorPrincipalPort;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesContractAdminControllerTest {

    @Test
    void exposesOnlyCanonicalCloudMoldRootAndPermissions() {
        RequestMapping root = SalesContractAdminController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/cloudmold/crm/sales-contracts");

        assertPermission("execute", "cloudmold:crm:sales-contract:command");
        assertPermission("get", "cloudmold:crm:sales-contract:query");
        assertThat(root.value()[0]).doesNotContain("/contract", "yudao-module-crm", "/crm/contract");
    }

    @Test
    void mockMvcRoutesCommandAndQueryThroughCanonicalApis() throws Exception {
        SalesContractAdminController controller = new SalesContractAdminController();
        SalesContractCommandPort commandApi = mock(SalesContractCommandPort.class);
        SalesContractQueryApi queryApi = mock(SalesContractQueryApi.class);
        SalesContractActorPrincipalPort actorPort = mock(SalesContractActorPrincipalPort.class);
        ReflectionTestUtils.setField(controller, "commandApi", commandApi);
        ReflectionTestUtils.setField(controller, "queryApi", queryApi);
        ReflectionTestUtils.setField(controller, "actorPrincipalPort", actorPort);
        when(actorPort.resolveSystemAdmin(nullable(Long.class))).thenReturn("crm-admin");
        when(commandApi.execute(any(), eq("crm-admin"), nullable(Long.class))).thenReturn(
                SalesContractCommandResult.builder()
                        .operationId(701L)
                        .salesContractId("contract-1")
                        .status("DRAFT")
                        .version(1L)
                        .currencyCode("CNY")
                        .totalAmountMinor(3500L)
                        .build());
        when(queryApi.get("contract-1")).thenReturn(SalesContractView.builder()
                .salesContractId("contract-1")
                .contractCode("SC-001")
                .contractName("Sales Contract")
                .customerId("11")
                .sellerMerchantId("merchant-1")
                .sellerShopId("shop-1")
                .sellerLegalEntityId("legal-1")
                .status("ACTIVE")
                .currencyCode("CNY")
                .totalAmountMinor(3500L)
                .effectiveDate(LocalDate.of(2026, 8, 1))
                .expiresOn(LocalDate.of(2026, 12, 31))
                .version(3L)
                .createdAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 8, 8, 0, 0))
                .items(List.of(SalesContractView.Item.builder()
                        .salesContractItemId("item-1")
                        .canonicalSkuId("sku-1")
                        .itemName("SKU 1")
                        .uomCode("PCS")
                        .quantity("2")
                        .unitPriceMinor(1000L)
                        .lineAmountMinor(2000L)
                        .lineNo(1)
                        .build()))
                .build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommand request =
                cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommand.builder()
                        .operation(SalesContractOperation.CREATE_DRAFT)
                        .idempotencyKey("create-contract-1")
                        .runId("run-1")
                        .correlationId("corr-1")
                        .causationId("cause-1")
                        .occurredAt(java.time.Instant.parse("2026-08-08T12:00:00Z"))
                        .reasonCode("INITIAL")
                        .contractCode("SC-001")
                        .contractName("Sales Contract")
                        .customerId("11")
                        .sellerMerchantId("merchant-1")
                        .sellerShopId("shop-1")
                        .currencyCode("CNY")
                        .effectiveDate(LocalDate.of(2026, 8, 1))
                        .items(List.of(cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommand.Item.builder()
                                .salesContractItemId("item-1")
                                .canonicalSkuId("sku-1")
                                .itemName("SKU 1")
                                .uomCode("PCS")
                                .quantity("2")
                                .unitPriceMinor(1000L)
                                .lineAmountMinor(2000L)
                                .build()))
                        .build();

        mvc.perform(post("/cloudmold/crm/sales-contracts/command")
                        .contentType("application/json")
                        .content(JsonUtils.toJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salesContractId").value("contract-1"))
                .andExpect(jsonPath("$.data.totalAmountMinor").value(3500));

        mvc.perform(get("/cloudmold/crm/sales-contracts/contract-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salesContractId").value("contract-1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].canonicalSkuId").value("sku-1"));

        verify(commandApi).execute(any(), eq("crm-admin"), nullable(Long.class));
        verify(queryApi).get("contract-1");
    }

    private static void assertPermission(String methodName, String permission) {
        Method method = java.util.Arrays.stream(SalesContractAdminController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        assertThat(method.getAnnotation(PreAuthorize.class).value()).contains(permission);
    }
}
