package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommand;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommandResult;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractOperation;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractSourceView;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractView;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.SalesContract;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.SalesContractItem;
import cn.iocoder.yudao.module.cloudmold.crm.dal.mysql.contract.SalesContractMapper;
import cn.iocoder.yudao.module.cloudmold.crm.service.CrmQueryService;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantReferenceView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesContractServiceTest {

    private static final long TENANT_ID = 162L;
    private static final String ACTOR = "principal-admin";
    private static final long ACTOR_ADMIN_USER_ID = 7L;

    private final SalesContractMapper mapper = mock(SalesContractMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final SalesContractActorPrincipalPort actorPrincipalPort = mock(SalesContractActorPrincipalPort.class);
    private final CatalogSkuValidationApi catalogSkuValidationApi = mock(CatalogSkuValidationApi.class);
    private final MerchantReferenceValidationApi merchantReferenceValidationApi =
            mock(MerchantReferenceValidationApi.class);
    private final BpmProcessInstanceApi bpmProcessInstanceApi = mock(BpmProcessInstanceApi.class);
    private final CrmQueryService crmQueryService = mock(CrmQueryService.class);
    private final SalesContractBpmModelRegistrar bpmModelRegistrar = mock(SalesContractBpmModelRegistrar.class);
    private final SalesContractService service = new SalesContractService(
            mapper, outboxAppender, actorPrincipalPort, catalogSkuValidationApi, merchantReferenceValidationApi,
            bpmProcessInstanceApi, crmQueryService, bpmModelRegistrar);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(mapper.insertOrResolveOperation(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(701L);
        when(mapper.selectOperationForUpdate(TENANT_ID, 701L)).thenAnswer(invocation -> {
            Operation operation = new Operation();
            operation.setOperationId(701L);
            operation.setTenantId(TENANT_ID);
            operation.setRequestHash(requestHash.get());
            operation.setAttemptToken(attemptToken.get());
            operation.setStatus(0);
            return operation;
        });
        when(mapper.markOperationSucceeded(eq(TENANT_ID), eq(701L), eq("sales_contract"), anyString(), anyString(), any()))
                .thenReturn(1);
        when(merchantReferenceValidationApi.requireActiveReference(any())).thenReturn(new MerchantReferenceView()
                .setMerchantId("merchant-1")
                .setShopId("shop-1")
                .setLegalEntityId("legal-1"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createDraftPersistsContractAndEmitsOutbox() {
        when(mapper.insertSalesContract(any())).thenReturn(1);
        when(mapper.insertItems(anyList())).thenReturn(2);
        when(mapper.insertStatusHistory(any())).thenReturn(1);

        SalesContractCommandResult result = service.execute(createDraftCommand(), ACTOR, ACTOR_ADMIN_USER_ID);

        assertThat(result.getSalesContractId()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getVersion()).isEqualTo(1L);
        verify(actorPrincipalPort).requireActive(ACTOR);
        verify(catalogSkuValidationApi).requireActiveSku("sku-1");
        verify(catalogSkuValidationApi).requireActiveSku("sku-2");
        ArgumentCaptor<AppendDomainEventCommand> eventCaptor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("crm.sales_contract.status_changed");
        assertThat(eventCaptor.getValue().getPayload()).containsEntry("currency_code", "CNY");
    }

    @Test
    void automationCommandResolvesVerifiedRpcIdentities() {
        when(actorPrincipalPort.resolveSystemAdmin(226L)).thenReturn(ACTOR);
        when(mapper.insertSalesContract(any())).thenReturn(1);
        when(mapper.insertItems(anyList())).thenReturn(2);
        when(mapper.insertStatusHistory(any())).thenReturn(1);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(226L);

            SalesContractCommandResult result = service.execute(createDraftCommand());

            assertThat(result.getStatus()).isEqualTo("DRAFT");
            verify(actorPrincipalPort).resolveSystemAdmin(226L);
            verify(actorPrincipalPort).requireActive(ACTOR);
        }
    }

    @Test
    void submitApprovalCallsBpmAndTransitionsStatus() {
        SalesContract existing = new SalesContract()
                .setSalesContractId("contract-1")
                .setTenantId(TENANT_ID)
                .setContractCode("SC-001")
                .setContractName("Sales Contract")
                .setCustomerId("11")
                .setSellerMerchantId("merchant-1")
                .setSellerShopId("shop-1")
                .setSellerLegalEntityId("legal-1")
                .setStatus("DRAFT")
                .setCurrencyCode("CNY")
                .setTotalAmountMinor(3500L)
                .setEffectiveDate(LocalDate.of(2026, 8, 1))
                .setExpiresOn(LocalDate.of(2026, 12, 31))
                .setVersion(2L);
        when(mapper.selectSalesContractForUpdate(TENANT_ID, "contract-1")).thenReturn(existing);
        when(mapper.selectItems(TENANT_ID, "contract-1")).thenReturn(List.of(
                item("item-1", "contract-1", 1, "sku-1", "SKU 1", "PCS", "2", 1000L, 2000L),
                item("item-2", "contract-1", 2, "sku-2", "SKU 2", "PCS", "1", 1500L, 1500L)));
        when(mapper.submitApproval(eq(TENANT_ID), eq("contract-1"), eq(2L), eq(3L),
                eq("PENDING_APPROVAL"), eq("process-17"), eq(ACTOR), any())).thenReturn(1);
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        when(bpmModelRegistrar.registerAndResolveReviewers(ACTOR_ADMIN_USER_ID)).thenReturn(List.of(229L));
        when(bpmProcessInstanceApi.createProcessInstance(eq(ACTOR_ADMIN_USER_ID), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("process-17"));

        SalesContractCommandResult result = service.execute(submitCommand(), ACTOR, ACTOR_ADMIN_USER_ID);

        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.getApprovalProcessInstanceId()).isEqualTo("process-17");
        verify(bpmProcessInstanceApi).createProcessInstance(eq(ACTOR_ADMIN_USER_ID), argThat(request ->
                "cloudmold_sales_contract_approval".equals(request.getProcessDefinitionKey())
                        && "contract-1".equals(request.getBusinessKey())
                        && request.getVariables().get("customer_id").equals("11")
                        && request.getStartUserSelectAssignees().get("sales_contract_review")
                        .equals(List.of(229L))));
    }

    @Test
    void requireReceivableSourceAcceptsOnlyActiveStatus() {
        when(mapper.selectSalesContract(TENANT_ID, "contract-1")).thenReturn(new SalesContract()
                .setSalesContractId("contract-1")
                .setCustomerId("11")
                .setStatus("ACTIVE")
                .setCurrencyCode("CNY")
                .setTotalAmountMinor(3500L)
                .setVersion(3L)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now()));
        when(mapper.selectItems(TENANT_ID, "contract-1")).thenReturn(List.of());

        SalesContractSourceView source = service.requireReceivableSource("11", "contract-1");

        assertThat(source.getStatus()).isEqualTo("ACTIVE");
        assertThat(source.getTotalAmountMinor()).isEqualTo(3500L);
    }

    @Test
    void duplicateIdempotencyReplaysCachedResult() {
        when(mapper.selectOperationForUpdate(TENANT_ID, 701L)).thenAnswer(invocation -> {
            Operation operation = new Operation();
            operation.setOperationId(701L);
            operation.setTenantId(TENANT_ID);
            operation.setRequestHash(requestHash.get());
            operation.setAttemptToken("other-attempt");
            operation.setStatus(SalesContractService.OPERATION_SUCCEEDED);
            operation.setResultJson("""
                    {"operationId":701,"duplicate":false,"salesContractId":"contract-1","status":"DRAFT",
                    "version":1,"totalAmountMinor":3500,"currencyCode":"CNY"}
                    """);
            return operation;
        });

        SalesContractCommandResult replay = service.execute(createDraftCommand(), ACTOR, ACTOR_ADMIN_USER_ID);

        assertThat(replay.isDuplicate()).isTrue();
        verify(mapper, never()).insertSalesContract(any());
    }

    @Test
    void rejectsFinanceValidationForPendingApprovalContract() {
        when(mapper.selectSalesContract(TENANT_ID, "contract-1")).thenReturn(new SalesContract()
                .setSalesContractId("contract-1")
                .setCustomerId("11")
                .setStatus("PENDING_APPROVAL")
                .setCurrencyCode("CNY")
                .setTotalAmountMinor(3500L)
                .setVersion(3L)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now()));
        when(mapper.selectItems(TENANT_ID, "contract-1")).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireReceivableSource("11", "contract-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be active");
    }

    private static SalesContractCommand createDraftCommand() {
        return SalesContractCommand.builder()
                .operation(SalesContractOperation.CREATE_DRAFT)
                .idempotencyKey("create-contract-1")
                .runId("run-1")
                .correlationId("corr-1")
                .causationId("cause-1")
                .occurredAt(Instant.parse("2026-08-08T12:00:00Z"))
                .reasonCode("INITIAL")
                .contractCode("SC-001")
                .contractName("Sales Contract")
                .customerId("11")
                .sellerMerchantId("merchant-1")
                .sellerShopId("shop-1")
                .currencyCode("CNY")
                .effectiveDate(LocalDate.of(2026, 8, 1))
                .expiresOn(LocalDate.of(2026, 12, 31))
                .items(List.of(
                        SalesContractCommand.Item.builder()
                                .salesContractItemId("item-1")
                                .canonicalSkuId("sku-1")
                                .itemName("SKU 1")
                                .uomCode("PCS")
                                .quantity("2")
                                .unitPriceMinor(1000L)
                                .lineAmountMinor(2000L)
                                .build(),
                        SalesContractCommand.Item.builder()
                                .salesContractItemId("item-2")
                                .canonicalSkuId("sku-2")
                                .itemName("SKU 2")
                                .uomCode("PCS")
                                .quantity("1")
                                .unitPriceMinor(1500L)
                                .lineAmountMinor(1500L)
                                .build()))
                .build();
    }

    private static SalesContractCommand submitCommand() {
        return SalesContractCommand.builder()
                .operation(SalesContractOperation.SUBMIT_APPROVAL)
                .idempotencyKey("submit-contract-1")
                .runId("run-2")
                .correlationId("corr-2")
                .causationId("cause-2")
                .occurredAt(Instant.parse("2026-08-08T13:00:00Z"))
                .reasonCode("SUBMIT")
                .salesContractId("contract-1")
                .expectedVersion(2L)
                .build();
    }

    private static SalesContractItem item(String itemId, String salesContractId, int lineNo, String skuId,
                                          String itemName, String uomCode, String quantity,
                                          long unitPriceMinor, long lineAmountMinor) {
        return new SalesContractItem()
                .setSalesContractItemId(itemId)
                .setTenantId(TENANT_ID)
                .setSalesContractId(salesContractId)
                .setLineNo(lineNo)
                .setCanonicalSkuId(skuId)
                .setItemName(itemName)
                .setUomCode(uomCode)
                .setQuantity(new BigDecimal(quantity))
                .setUnitPriceMinor(unitPriceMinor)
                .setLineAmountMinor(lineAmountMinor)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }
}
