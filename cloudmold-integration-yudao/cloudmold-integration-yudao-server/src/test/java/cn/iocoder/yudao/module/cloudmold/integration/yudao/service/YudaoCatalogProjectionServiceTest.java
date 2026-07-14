package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.*;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.dataobject.LegacyCatalogProjectionDO;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.mysql.LegacyCatalogProjectionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class YudaoCatalogProjectionServiceTest {

    private final CatalogSkuProjectionApi catalogApi = mock(CatalogSkuProjectionApi.class);
    private final LegacyCatalogProjectionMapper mapper = mock(LegacyCatalogProjectionMapper.class);
    private final YudaoCatalogProjectionService service = new YudaoCatalogProjectionService(catalogApi, mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(catalogApi.getActiveSku("sku-1")).thenReturn(activeSku());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPlanThreeOwnershipSafeProjections() {
        when(mapper.selectByBusinessKeyForUpdate(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(null);
        when(mapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), any())).thenReturn(1);
        when(mapper.selectLastInsertId()).thenReturn(11L, 12L, 13L);

        LegacyCatalogProjectionResult result = service.plan(command(EnumSet.allOf(LegacyCatalogTargetSystem.class)));

        assertThat(result.getProjections()).hasSize(3).allSatisfy(item -> {
            assertThat(item.getState()).isEqualTo("PENDING");
            assertThat(item.getChanged()).isTrue();
            assertThat(item.getAggregateVersion()).isEqualTo(2L);
        });
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(mapper, times(3)).insertOrResolve(eq(1L), anyString(), anyString(), eq("sku-1"), eq(2L),
                payload.capture(), anyString(), any());
        assertThat(payload.getAllValues()).allSatisfy(value -> {
            assertThat(value).contains("canonical_sku_id", "projection_policy");
            assertThat(value).doesNotContain("\"stock\"", "\"price\"", "\"listing_status\"");
        });
    }

    @Test
    void shouldReturnUnchangedWhenVersionAndHashMatch() {
        CatalogSkuProjectionView sku = activeSku();
        String payload = JsonUtils.toJsonString(YudaoCatalogProjectionService.payload(
                LegacyCatalogTargetSystem.MALL, "SKU", sku));
        String hash = DigestUtil.sha256Hex(payload);
        when(mapper.selectByBusinessKeyForUpdate(eq(1L), eq("MALL"), eq("SKU"), eq("sku-1")))
                .thenAnswer(ignored -> new LegacyCatalogProjectionDO().setProjectionId(21L).setTenantId(1L)
                        .setAggregateVersion(2L).setPayloadHash(hash));

        LegacyCatalogProjectionResult result = service.plan(command(EnumSet.of(LegacyCatalogTargetSystem.MALL)));

        assertThat(result.getProjections()).singleElement().satisfies(item -> assertThat(item.getChanged()).isFalse());
        verify(mapper, never()).refresh(anyLong(), anyLong(), anyLong(), anyString(), anyString(), any());
        verify(mapper, never()).insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), any());
    }

    private static PlanLegacyCatalogProjectionCommand command(EnumSet<LegacyCatalogTargetSystem> targets) {
        PlanLegacyCatalogProjectionCommand command = new PlanLegacyCatalogProjectionCommand();
        command.setCanonicalSkuId("sku-1");
        command.setTargets(targets);
        return command;
    }

    private static CatalogSkuProjectionView activeSku() {
        CatalogSkuProjectionView value = new CatalogSkuProjectionView();
        value.setCanonicalStyleId("style-1"); value.setStyleCode("STYLE-1"); value.setStyleName("连衣裙");
        value.setCanonicalSpuId("spu-1"); value.setSpuCode("SPU-1"); value.setProductName("连衣裙");
        value.setCanonicalSkuId("sku-1"); value.setSkuCode("SKU-1");
        value.setColorCode("BLACK"); value.setColorName("黑色"); value.setSizeGroupCode("WOMEN_TOP");
        value.setSizeCode("M"); value.setSizeName("M"); value.setPrimaryBarcode("CM-SKU-1");
        value.setBaseUomCode("PCS"); value.setCatalogStatus("ACTIVE"); value.setAggregateVersion(2L);
        return value;
    }
}
