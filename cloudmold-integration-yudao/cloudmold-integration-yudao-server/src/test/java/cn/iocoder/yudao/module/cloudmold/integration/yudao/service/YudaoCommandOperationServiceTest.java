package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoCommandOperationMapper;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoCommandOperationRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YudaoCommandOperationServiceTest {

    private final YudaoCommandOperationMapper mapper = mock(YudaoCommandOperationMapper.class);
    private final YudaoCommandOperationService service = new YudaoCommandOperationService(mapper);

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCommitFirstResultToDurableLedger() {
        AtomicReference<String> requestHash = new AtomicReference<>();
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(mapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(7L);
        when(mapper.selectForUpdate(7L, 1L)).thenAnswer(ignored -> row(requestHash.get(), attemptToken.get(), 0, null));
        when(mapper.markSucceeded(anyLong(), anyLong(), anyString(), any())).thenReturn(1);

        assertThat(service.executeLong("CREATE_ERP_CUSTOMER", "customer-run-001",
                new TestCommand("A"), () -> 41L)).isEqualTo(41L);

        verify(mapper).markSucceeded(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void shouldReplayStoredResultWithoutCallingUpstreamAgain() {
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(mapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(8L);
        when(mapper.selectForUpdate(8L, 1L)).thenAnswer(ignored -> row(requestHash.get(), "previous-attempt", 10, "41"));
        @SuppressWarnings("unchecked") Supplier<Long> action = mock(Supplier.class);

        assertThat(service.executeLong("CREATE_ERP_CUSTOMER", "customer-run-001",
                new TestCommand("A"), action)).isEqualTo(41L);

        verify(action, never()).get();
        verify(mapper, never()).markSucceeded(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void shouldRejectSameKeyWithDifferentPayload() {
        when(mapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
        when(mapper.selectLastInsertId()).thenReturn(9L);
        when(mapper.selectForUpdate(9L, 1L)).thenReturn(row("different-hash", "previous-attempt", 10, "41"));

        assertThatThrownBy(() -> service.executeLong("CREATE_ERP_CUSTOMER", "customer-run-001",
                new TestCommand("B"), () -> 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different yudao command payload");
    }

    private static YudaoCommandOperationRow row(String hash, String attemptToken, int status, String resultJson) {
        YudaoCommandOperationRow row = new YudaoCommandOperationRow();
        row.setOperationId(1L);
        row.setTenantId(1L);
        row.setOperationType("CREATE_ERP_CUSTOMER");
        row.setIdempotencyKey("customer-run-001");
        row.setRequestHash(hash);
        row.setAttemptToken(attemptToken);
        row.setStatus(status);
        row.setResultJson(resultJson);
        return row;
    }

    private record TestCommand(String value) {
    }
}
