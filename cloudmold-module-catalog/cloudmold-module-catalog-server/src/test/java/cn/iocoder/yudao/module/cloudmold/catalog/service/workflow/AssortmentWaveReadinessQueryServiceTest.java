package cn.iocoder.yudao.module.cloudmold.catalog.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessRequest;
import cn.iocoder.yudao.module.cloudmold.catalog.api.workflow.AssortmentWaveReadinessResult;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssortmentWaveReadinessQueryServiceTest {

    private final CatalogQueryMapper mapper = mock(CatalogQueryMapper.class);
    private final AssortmentWaveReadinessQueryService service = new AssortmentWaveReadinessQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(162L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldNormalizeInputsAndReturnCatalogAndBusinessBlockers() {
        CatalogWaveAggregateRow row = new CatalogWaveAggregateRow();
        row.setStyleCount(2);
        row.setActiveStyleCount(1);
        row.setSpuCount(3);
        row.setActiveSpuCount(1);
        row.setSkuCount(6);
        row.setActiveSkuCount(0);
        row.setLastCatalogUpdatedAt("2026-07-28T02:30:00Z");
        when(mapper.selectWaveAggregate(162L, 2026, "SUMMER", "WAVE-01")).thenReturn(row);

        AssortmentWaveReadinessResult result = service.inspectWave(AssortmentWaveReadinessRequest.builder()
                .planningYear(2026)
                .seasonCode(" summer ")
                .waveCode(" wave-01 ")
                .build());

        verify(mapper).selectWaveAggregate(162L, 2026, "SUMMER", "WAVE-01");
        assertThat(result.getStatus()).isEqualTo(AssortmentWaveReadinessResult.Status.WAITING);
        assertThat(result.getTerminal()).isFalse();
        assertThat(result.getCatalogSnapshot().getSkuCount()).isEqualTo(6);
        assertThat(result.getCatalogSnapshot().getActiveSkuCount()).isZero();
        assertThat(result.getBlockers()).extracting(AssortmentWaveReadinessResult.Blocker::getCode)
                .containsExactly(
                        "CATALOG_SKU_NOT_ACTIVE",
                        "TREND_SIGNAL_MISSING",
                        "PRICE_BAND_MISSING",
                        "TARGET_STYLE_COUNT_MISSING",
                        "GROSS_MARGIN_TARGET_MISSING",
                        "SUPPLY_PLAN_LINK_MISSING");
        assertThat(result.getSummary()).contains("不能称为完整波段企划案");
    }

    @Test
    void shouldFlagEmptyCatalogScopeAsNeedsCatalog() {
        CatalogWaveAggregateRow row = new CatalogWaveAggregateRow();
        row.setStyleCount(0);
        row.setSpuCount(0);
        row.setSkuCount(0);
        when(mapper.selectWaveAggregate(162L, 2026, "AUTUMN", "WAVE-02")).thenReturn(row);

        AssortmentWaveReadinessResult result = service.inspectWave(AssortmentWaveReadinessRequest.builder()
                .planningYear(2026)
                .seasonCode("AUTUMN")
                .waveCode("WAVE-02")
                .build());

        assertThat(result.getStatus()).isEqualTo(AssortmentWaveReadinessResult.Status.NEEDS_CATALOG);
        assertThat(result.getBlockers()).extracting(AssortmentWaveReadinessResult.Blocker::getCode)
                .contains("CATALOG_SCOPE_EMPTY");
        assertThat(result.getNextActions().get(0)).contains("Catalog");
    }

    @Test
    void shouldRejectInvalidSeason() {
        assertThatThrownBy(() -> service.inspectWave(AssortmentWaveReadinessRequest.builder()
                .planningYear(2026)
                .seasonCode("RAINY")
                .waveCode("WAVE-03")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seasonCode is invalid");
    }
}
