package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOrderDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferRequestDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferStatusHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockTransferCommandServiceImplTest {

    private final StockTransferStoreMapper mapper = mock(StockTransferStoreMapper.class);
    private final WarehouseReferenceValidationApi warehouseApi = mock(WarehouseReferenceValidationApi.class);
    private final CatalogSkuValidationApi skuApi = mock(CatalogSkuValidationApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final StockTransferCommandServiceImpl service = new StockTransferCommandServiceImpl(
            mapper, warehouseApi, skuApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult(
                "event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsApprovedTransferRequestAndPrepareOrder() {
        StockTransferCommand command = command("warehouse-source", "warehouse-target");
        prepareNewOperation(command);
        when(mapper.selectRequestBySourceBusiness(1L, "REPLENISHMENT", "recommendation-01")).thenReturn(null);
        when(mapper.insertRequest(any())).thenReturn(1);
        when(mapper.insertOrder(any())).thenReturn(1);
        when(mapper.insertRequestLine(any())).thenReturn(1);
        when(mapper.insertOrderLine(any())).thenReturn(1);
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(11L), eq(1L), anyString(), anyString(), any())).thenReturn(1);

        StockTransferResult result = service.execute(command);

        assertThat(result.getRequestStatus()).isEqualTo("APPROVED");
        assertThat(result.getOrderStatus()).isEqualTo("PREPARE");
        assertThat(result.getCurrentStageCode()).isEqualTo("TRANSFER_OUTBOUND");
        verify(warehouseApi).requireActiveWarehouse("warehouse-source");
        verify(warehouseApi).requireActiveWarehouse("warehouse-target");
        verify(skuApi).requireActiveSku("sku-01");
        verify(skuApi).requireActiveSku("sku-02");

        verify(mapper).insertRequest(argThat((StockTransferRequestDO row) ->
                "APPROVED".equals(row.getStatus())
                        && "warehouse-source".equals(row.getSourceWarehouseId())
                        && "warehouse-target".equals(row.getTargetWarehouseId())));
        verify(mapper).insertOrder(argThat((StockTransferOrderDO row) ->
                "PREPARE".equals(row.getStatus())
                        && "warehouse-source".equals(row.getSourceWarehouseId())
                        && "warehouse-target".equals(row.getTargetWarehouseId())));
        ArgumentCaptor<StockTransferStatusHistoryDO> historyCaptor =
                ArgumentCaptor.forClass(StockTransferStatusHistoryDO.class);
        verify(mapper, times(2)).insertStatusHistory(historyCaptor.capture());
        assertThat(historyCaptor.getAllValues()).extracting(StockTransferStatusHistoryDO::getStageCode)
                .containsExactly("REQUEST_APPROVED", "TRANSFER_OUTBOUND");

        ArgumentCaptor<AppendDomainEventCommand> eventCaptor =
                ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, times(2)).append(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(AppendDomainEventCommand::getEventType)
                .containsExactly("stock_transfer.request.approved", "stock_transfer.order.prepared");
    }

    @Test
    void replaysImmutableStoredResultForDuplicateIdempotencyKey() {
        StockTransferCommand command = command("warehouse-source", "warehouse-target");
        StockTransferResult stored = StockTransferResult.builder()
                .requestId("request-01").requestCode("STRRQ-0001").requestStatus("APPROVED")
                .orderId("order-01").orderCode("STRORD-0001").orderStatus("PREPARE")
                .currentStageCode("TRANSFER_OUTBOUND").currentStageLabel("等待调拨出库")
                .aggregateVersion(1L).duplicate(false).build();
        when(mapper.selectLastInsertId()).thenReturn(22L);
        when(mapper.selectOperationForUpdate(22L, 1L)).thenReturn(new StockTransferOperationDO()
                .setOperationId(22L).setTenantId(1L).setAttemptToken("first-attempt")
                .setRequestHash(StockTransferCommandServiceImpl.fingerprint(1L, command))
                .setStatus(StockTransferCommandServiceImpl.OPERATION_SUCCEEDED)
                .setResultJson(JsonUtils.toJsonString(stored)));

        StockTransferResult replay = service.execute(command);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getRequestId()).isEqualTo("request-01");
        verifyNoInteractions(warehouseApi, skuApi, outboxAppender);
    }

    @Test
    void rejectsSameSourceAndTargetWarehouse() {
        StockTransferCommand command = command("warehouse-same", "warehouse-same");

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceWarehouseId and targetWarehouseId must differ");

        verifyNoInteractions(mapper, warehouseApi, skuApi, outboxAppender);
    }

    private void prepareNewOperation(StockTransferCommand command) {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        AtomicReference<String> requestHash = new AtomicReference<>();
        doAnswer(invocation -> {
            requestHash.set(invocation.getArgument(4));
            attemptToken.set(invocation.getArgument(5));
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(1L), eq(command.getIdempotencyKey()),
                eq(command.getSourceEventId()), eq(command.getOperation().name()), anyString(), anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(11L);
        doAnswer(invocation -> new StockTransferOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken(attemptToken.get())
                .setRequestHash(requestHash.get()).setStatus(0))
                .when(mapper).selectOperationForUpdate(11L, 1L);
    }

    private static StockTransferCommand command(String sourceWarehouseId, String targetWarehouseId) {
        return StockTransferCommand.builder()
                .operation(StockTransferOperation.CREATE_APPROVED_REQUEST)
                .idempotencyKey("transfer-request-key-01")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-08-02T02:00:00Z"))
                .requestCode("STRRQ-0001")
                .orderCode("STRORD-0001")
                .sourceBusinessType("REPLENISHMENT")
                .sourceBusinessRef("recommendation-01")
                .ownerType("MERCHANT")
                .ownerId("merchant-01")
                .sourceWarehouseId(sourceWarehouseId)
                .targetWarehouseId(targetWarehouseId)
                .reasonCode("REPLENISHMENT_APPROVED")
                .remark("replenishment transfer")
                .lines(List.of(
                        StockTransferCommand.LineDefinition.builder()
                                .lineNumber(10).canonicalSkuId("sku-01")
                                .requestedQuantity(new BigDecimal("8.000000")).uomCode("PIECE")
                                .remark("line-01").build(),
                        StockTransferCommand.LineDefinition.builder()
                                .lineNumber(20).canonicalSkuId("sku-02")
                                .requestedQuantity(new BigDecimal("4.250000")).uomCode("PIECE")
                                .remark("line-02").build()))
                .build();
    }
}
