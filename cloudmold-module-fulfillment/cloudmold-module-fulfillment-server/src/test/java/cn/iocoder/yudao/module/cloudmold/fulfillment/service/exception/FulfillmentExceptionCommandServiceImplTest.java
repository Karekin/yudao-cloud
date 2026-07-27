package cn.iocoder.yudao.module.cloudmold.fulfillment.service.exception;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.FulfillmentOrderDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.exception.FulfillmentExceptionDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.exception.FulfillmentExceptionOperationDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentOrderMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.exception.FulfillmentExceptionMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.exception.FulfillmentExceptionOperationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FulfillmentExceptionCommandServiceImplTest {

    private final FulfillmentExceptionOperationMapper operationMapper =
            mock(FulfillmentExceptionOperationMapper.class);
    private final FulfillmentExceptionMapper exceptionMapper = mock(FulfillmentExceptionMapper.class);
    private final FulfillmentOrderMapper fulfillmentMapper = mock(FulfillmentOrderMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final FulfillmentExceptionCommandServiceImpl service =
            new FulfillmentExceptionCommandServiceImpl(operationMapper, exceptionMapper,
                    fulfillmentMapper, outboxAppender);

    private final AtomicReference<String> currentAttempt = new AtomicReference<>();
    private final AtomicReference<FulfillmentExceptionDO> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    currentAttempt.set(invocation.getArgument(4));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(31L);
        when(operationMapper.selectForUpdate(31L, 1L)).thenAnswer(ignored ->
                new FulfillmentExceptionOperationDO().setOperationId(31L).setTenantId(1L)
                        .setAttemptToken(currentAttempt.get()).setStatus(0));
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(fulfillmentMapper.selectByIdForValidation(1L, "fulfillment-1"))
                .thenReturn(new FulfillmentOrderDO().setFulfillmentId("fulfillment-1")
                        .setTenantId(1L).setOrderId("order-1").setStatus("IN_TRANSIT").setVersion(3L));
        when(exceptionMapper.countActive(1L, "fulfillment-1")).thenReturn(0);
        when(exceptionMapper.insert(any(FulfillmentExceptionDO.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(exceptionMapper.selectForUpdate(eq(1L), anyString())).thenAnswer(ignored -> stored.get());
        when(exceptionMapper.transition(anyLong(), anyString(), anyLong(), anyString(), anyString(),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(java.time.LocalDateTime.class),
                nullable(java.time.LocalDateTime.class), any(java.time.LocalDateTime.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(
                new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPersistFullApprovedExceptionLifecycleWithEvidence() {
        FulfillmentExceptionView opened = service.execute(open(FulfillmentExceptionType.DELAY));
        FulfillmentExceptionView planned = service.execute(transition(FulfillmentExceptionOperation.PLAN, 1L)
                .setAction(FulfillmentExceptionAction.CONTACT_CARRIER)
                .setActionDescription("联系承运商确认最新轨迹与预计送达时间")
                .setEvidenceRef("evidence://delay-plan-1"));
        FulfillmentExceptionView waitingApproval = service.execute(
                transition(FulfillmentExceptionOperation.REQUEST_APPROVAL, 2L)
                        .setApprovalRef("bpm://process/approval-1"));
        FulfillmentExceptionView executing = service.execute(
                transition(FulfillmentExceptionOperation.START_EXECUTION, 3L)
                        .setApprovalRef("bpm://process/approval-1"));
        FulfillmentExceptionView resolved = service.execute(
                transition(FulfillmentExceptionOperation.RESOLVE, 4L)
                        .setEvidenceRef("evidence://carrier-confirmation-1")
                        .setResolutionSummary("承运商确认恢复运输，预计次日送达"));
        FulfillmentExceptionView closed = service.execute(
                transition(FulfillmentExceptionOperation.CLOSE, 5L));

        assertThat(opened.getStatus()).isEqualTo(FulfillmentExceptionStatus.OPEN);
        assertThat(planned.getPlanEvidenceRef()).isEqualTo("evidence://delay-plan-1");
        assertThat(waitingApproval.getStatus()).isEqualTo(FulfillmentExceptionStatus.WAITING_APPROVAL);
        assertThat(executing.getStatus()).isEqualTo(FulfillmentExceptionStatus.EXECUTING);
        assertThat(resolved.getResolutionEvidenceRef()).isEqualTo("evidence://carrier-confirmation-1");
        assertThat(closed.getStatus()).isEqualTo(FulfillmentExceptionStatus.CLOSED);
        assertThat(closed.getAggregateVersion()).isEqualTo(6L);
        verify(outboxAppender, times(6)).append(argThat(event ->
                event.getEventType().equals("fulfillment.exception.status.changed")
                        && event.getAggregateType().equals("fulfillment_exception")
                        && event.getPayload().get("exception_id") != null));
    }

    @Test
    void shouldAcceptAllCanonicalExceptionTypes() {
        for (FulfillmentExceptionType type : FulfillmentExceptionType.values()) {
            FulfillmentExceptionView result = service.execute(open(type)
                    .setIdempotencyKey("exception-open-" + type.name().toLowerCase()));
            assertThat(result.getExceptionType()).isEqualTo(type);
        }
        verify(exceptionMapper, times(4)).insert(any(FulfillmentExceptionDO.class));
    }

    @Test
    void shouldRejectExecutionWithoutMatchingBpmApprovalEvidence() {
        stored.set(exception("WAITING_APPROVAL", 3L)
                .setApprovalRef("bpm://process/approval-1")
                .setActionCode(FulfillmentExceptionAction.REISSUE_SHIPMENT.name()));

        assertThatThrownBy(() -> service.execute(
                transition(FulfillmentExceptionOperation.START_EXECUTION, 3L)
                        .setApprovalRef("bpm://process/other")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("START_EXECUTION requires the approved BPM reference");

        verify(exceptionMapper, never()).transition(anyLong(), anyString(), anyLong(), anyString(), anyString(),
                nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(java.time.LocalDateTime.class),
                nullable(java.time.LocalDateTime.class), any(java.time.LocalDateTime.class));
    }

    @Test
    void shouldReturnImmutableFirstResultOnIdempotentReplay() {
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    return 0;
                });
        FulfillmentExceptionView first = FulfillmentExceptionView.builder()
                .operationId(31L).exceptionId("exception-1").exceptionNo("CMX1")
                .runId("run-1").fulfillmentId("fulfillment-1").orderId("order-1")
                .exceptionType(FulfillmentExceptionType.LOST).status(FulfillmentExceptionStatus.OPEN)
                .aggregateVersion(1L).duplicate(false).build();
        when(operationMapper.selectForUpdate(31L, 1L)).thenAnswer(ignored ->
                new FulfillmentExceptionOperationDO().setOperationId(31L).setTenantId(1L)
                        .setAttemptToken("existing").setRequestHash(requestHash.get())
                        .setStatus(10).setResultJson(JsonUtils.toJsonString(first)));

        FulfillmentExceptionView replay = service.execute(open(FulfillmentExceptionType.LOST));

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getExceptionId()).isEqualTo("exception-1");
        verify(exceptionMapper, never()).insert(any(FulfillmentExceptionDO.class));
        verify(outboxAppender, never()).append(any());
    }

    @Test
    void shouldRejectVersionConflictBeforeTransition() {
        stored.set(exception("OPEN", 2L));

        assertThatThrownBy(() -> service.execute(
                transition(FulfillmentExceptionOperation.PLAN, 1L)
                        .setAction(FulfillmentExceptionAction.TRACK_AND_WAIT)
                        .setActionDescription("等待新轨迹")
                        .setEvidenceRef("evidence://plan")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("canonical fulfillment exception version conflict");
    }

    @Test
    void shouldReadLatestTenantScopedExceptionByOrder() {
        when(exceptionMapper.selectLatestByOrder(1L, "order-1"))
                .thenReturn(exception("RESOLVED", 5L)
                        .setActionCode(FulfillmentExceptionAction.TRACK_AND_WAIT.name())
                        .setPlanEvidenceRef("evidence://plan")
                        .setApprovalRef("bpm://process/approval-1")
                        .setResolutionEvidenceRef("evidence://resolution")
                        .setResolutionSummary("轨迹恢复"));

        FulfillmentExceptionView result = service.getLatestByOrder("order-1");

        assertThat(result.getStatus()).isEqualTo(FulfillmentExceptionStatus.RESOLVED);
        assertThat(result.getResolutionEvidenceRef()).isEqualTo("evidence://resolution");
        verify(exceptionMapper).selectLatestByOrder(1L, "order-1");
    }

    private static FulfillmentExceptionCommand open(FulfillmentExceptionType type) {
        return FulfillmentExceptionCommand.builder()
                .operation(FulfillmentExceptionOperation.OPEN)
                .idempotencyKey("exception-open-lifecycle")
                .runId("run-1")
                .fulfillmentId("fulfillment-1")
                .orderId("order-1")
                .exceptionType(type)
                .reason("物流轨迹超过承诺时效")
                .occurredAt(Instant.parse("2026-07-27T00:00:00Z"))
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .build();
    }

    private static FulfillmentExceptionCommand transition(FulfillmentExceptionOperation operation,
                                                          long expectedVersion) {
        return FulfillmentExceptionCommand.builder()
                .operation(operation)
                .idempotencyKey("exception-" + operation.name().toLowerCase() + "-" + expectedVersion)
                .runId("run-1")
                .exceptionId("exception-1")
                .expectedVersion(expectedVersion)
                .occurredAt(Instant.parse("2026-07-27T00:01:00Z"))
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .build();
    }

    private static FulfillmentExceptionDO exception(String status, long version) {
        return new FulfillmentExceptionDO()
                .setExceptionId("exception-1")
                .setExceptionNo("CMX1")
                .setTenantId(1L)
                .setRunId("run-1")
                .setFulfillmentId("fulfillment-1")
                .setOrderId("order-1")
                .setExceptionType(FulfillmentExceptionType.DELAY.name())
                .setStatus(status)
                .setReason("物流轨迹超过承诺时效")
                .setVersion(version);
    }
}
