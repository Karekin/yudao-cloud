package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessEventContinuationReconcilerTest {

    private final AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
    private final SupplyPlanningQueryApi supplyPlanningQueries = mock(SupplyPlanningQueryApi.class);
    private final TemporalBusinessEventContinuationAdapter continuation =
            mock(TemporalBusinessEventContinuationAdapter.class);
    private final BusinessEventContinuationReconciler reconciler =
            new BusinessEventContinuationReconciler(mapper, supplyPlanningQueries, continuation);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void signalsWaitingRunWhenWmsTransferIsFinished() {
        TemporalRunBindingRecord binding = new TemporalRunBindingRecord()
                .setTenantId(162L)
                .setTemporalRunId("run-transfer")
                .setBusinessReferenceId("recommendation-transfer");
        when(mapper.selectWaitingEventBindings(100)).thenReturn(List.of(binding));
        when(supplyPlanningQueries.requireReplenishmentBusinessStage("recommendation-transfer"))
                .thenReturn(ReplenishmentBusinessStageView.builder()
                        .recommendationId("recommendation-transfer")
                        .targetType("TRANSFER_REQUEST")
                        .projectionDocumentStatus("FINISHED")
                        .build());

        reconciler.reconcile();

        verify(continuation).onBusinessEvent(
                162L, "recommendation-transfer",
                "BUSINESS_STAGE_REFRESH", "recommendation-transfer");
    }

    @Test
    void leavesPreparedWmsTransferWaiting() {
        TemporalRunBindingRecord binding = new TemporalRunBindingRecord()
                .setTenantId(162L)
                .setTemporalRunId("run-transfer")
                .setBusinessReferenceId("recommendation-transfer");
        when(mapper.selectWaitingEventBindings(100)).thenReturn(List.of(binding));
        when(supplyPlanningQueries.requireReplenishmentBusinessStage("recommendation-transfer"))
                .thenReturn(ReplenishmentBusinessStageView.builder()
                        .recommendationId("recommendation-transfer")
                        .targetType("TRANSFER_REQUEST")
                        .projectionDocumentStatus("PREPARE")
                        .build());

        reconciler.reconcile();

        verify(continuation, never()).onBusinessEvent(
                162L, "recommendation-transfer",
                "BUSINESS_STAGE_REFRESH", "recommendation-transfer");
    }
}
