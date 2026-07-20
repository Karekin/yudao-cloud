package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.MissionEventCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.LegacyPurchaseInBridgeState;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.LegacyPurchaseInFact;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyErpPurchaseInEventBridgeTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final MissionRuntimeService runtime = mock(MissionRuntimeService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T06:00:00Z"), ZoneOffset.UTC);
    private final LegacyErpPurchaseInEventBridge bridge = new LegacyErpPurchaseInEventBridge(mapper, runtime, clock);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void appendsExplicitLegacyEventAndWakesMatchingMission() {
        TenantContextHolder.setTenantId(17L);
        LegacyPurchaseInFact fact = fact();
        when(mapper.selectLegacyPurchaseInFact(17L, 9001L)).thenReturn(fact);
        when(mapper.upsertLegacyPurchaseInBridge(any())).thenReturn(1);
        when(mapper.insertAgentOutbox(anyString(), eq(17L), eq("legacy_erp_purchase_in"), eq("9001"),
                eq(LegacyErpPurchaseInEventBridge.EVENT_TYPE), anyString(), any())).thenReturn(1);
        when(runtime.matchEvent(any())).thenReturn(AgentControlResult.builder().status("MATCHED").build());

        var result = bridge.capture(9001L, "mission-correlation-1");

        assertThat(result.getStatus()).isEqualTo("MATCHED");
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertAgentOutbox(anyString(), eq(17L), eq("legacy_erp_purchase_in"), eq("9001"),
                eq(LegacyErpPurchaseInEventBridge.EVENT_TYPE), payload.capture(), any());
        assertThat(payload.getValue()).contains("LEGACY_ERP_ADAPTER", "LOCAL_TEST", "purchaseInId");
        ArgumentCaptor<MissionEventCommand> event = ArgumentCaptor.forClass(MissionEventCommand.class);
        verify(runtime).matchEvent(event.capture());
        assertThat(event.getValue().getSourceSystem()).isEqualTo("legacy-erp");
        assertThat(event.getValue().getAggregateType()).isEqualTo("purchase_in");
        assertThat(event.getValue().getAggregateId()).isEqualTo("9001");
    }

    @Test
    void repeatedSourceStateDoesNotAppendOrWakeAgain() {
        TenantContextHolder.setTenantId(17L);
        LegacyPurchaseInFact fact = fact();
        when(mapper.selectLegacyPurchaseInFact(17L, 9001L)).thenReturn(fact);
        when(mapper.selectLegacyPurchaseInBridgeForUpdate(17L, 9001L)).thenReturn(
                new LegacyPurchaseInBridgeState().setTenantId(17L).setPurchaseInId(9001L)
                        .setObservedStatus(20).setSourceUpdatedAt(fact.getSourceUpdatedAt()));

        var result = bridge.capture(9001L, "mission-correlation-1");

        assertThat(result.isDuplicate()).isTrue();
        verify(mapper, never()).insertAgentOutbox(anyString(), anyLong(), anyString(), anyString(), anyString(),
                anyString(), any());
        verifyNoInteractions(runtime);
    }

    private static LegacyPurchaseInFact fact() {
        return new LegacyPurchaseInFact().setPurchaseInId(9001L).setTenantId(17L).setPurchaseInNo("CGIR-9001")
                .setStatus(20).setSupplierId(81L).setOrderId(71L).setTotalCount(new BigDecimal("20"))
                .setSourceUpdatedAt(LocalDateTime.of(2026, 7, 19, 13, 59));
    }
}
