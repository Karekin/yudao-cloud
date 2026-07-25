package cn.iocoder.yudao.module.cloudmold.catalog.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.*;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogMasterDataMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogLifecycleMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.CatalogOperationMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogCommandServiceImplTest {

    private final CatalogOperationMapper operationMapper = mock(CatalogOperationMapper.class);
    private final CatalogMasterDataMapper masterDataMapper = mock(CatalogMasterDataMapper.class);
    private final CatalogLifecycleMapper lifecycleMapper = mock(CatalogLifecycleMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CatalogCommandServiceImpl service =
            new CatalogCommandServiceImpl(operationMapper, masterDataMapper, lifecycleMapper, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldDefineNormalizedApparelSkuAndAppendSnapshotEvent() {
        prepareNewOperation();
        prepareMasterData(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), anyString(), any())).thenReturn(1);

        DefineCatalogSkuResult result = service.defineSku(command());

        assertThat(result.getCanonicalStyleId()).isEqualTo("style-1");
        assertThat(result.getCanonicalSpuId()).isEqualTo("spu-1");
        assertThat(result.getCanonicalSkuId()).isEqualTo("sku-1");
        assertThat(result.getStyleStatus()).isEqualTo("DRAFT");
        assertThat(result.getStyleVersion()).isEqualTo(1L);
        assertThat(result.getSpuStatus()).isEqualTo("DRAFT");
        assertThat(result.getSpuVersion()).isEqualTo(1L);
        assertThat(result.getColorStatus()).isEqualTo("DRAFT");
        assertThat(result.getColorVersion()).isEqualTo(1L);
        assertThat(result.getSizeGroupStatus()).isEqualTo("DRAFT");
        assertThat(result.getSizeGroupVersion()).isEqualTo(1L);
        assertThat(result.getSizeStatus()).isEqualTo("DRAFT");
        assertThat(result.getSizeVersion()).isEqualTo(1L);
        assertThat(result.getSkuStatus()).isEqualTo("DRAFT");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getCreated()).isTrue();
        verify(masterDataMapper).insertStyle(anyString(), eq(1L), eq("YS2026-DRESS-001"),
                eq("夏季连衣裙"), eq("INTERNAL:CATEGORY:DRESS"), eq("INTERNAL:BRAND:YSHOPPING"),
                eq(2026), eq("SUMMER"), eq("WAVE-01"), any());
        verify(masterDataMapper).insertSku(anyString(), eq(1L), eq("spu-1"), eq("YS2026-DRESS-001-BLK-M"),
                eq("color-1"), eq("size-1"), eq("COLOR=BLACK|SIZE_GROUP=WOMEN_TOP|SIZE=M"),
                eq("7ed79d6300694c04fac2c994678dff18a44cc0bd4ed7791a0a82cb1f914c8bbb"), eq("PCS"), any());
        verify(outboxAppender).append(argThat(event -> event.getEventType().equals("catalog.sku.defined")
                && event.getSourceSystem().equals("cloudmold-catalog")
                && event.getDestination().equals("catalog-events")
                && event.getAggregateId().equals("sku-1")
                && event.getPayload().get("style_code").equals("YS2026-DRESS-001")
                && event.getPayload().get("primary_barcode").equals("6901234567892")));
    }

    @Test
    void shouldReturnImmutableResultForSameIdempotencyKey() {
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(2));
                    return 0;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        DefineCatalogSkuResult first = DefineCatalogSkuResult.builder().operationId(101L)
                .canonicalStyleId("style-1").canonicalSpuId("spu-1").canonicalSkuId("sku-1")
                .aggregateVersion(1L).created(true).duplicate(false).build();
        when(operationMapper.selectForUpdate(101L, 1L)).thenAnswer(ignored -> new CatalogOperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken("original")
                .setRequestHash(requestHash.get()).setStatus(10).setResultJson(JsonUtils.toJsonString(first)));

        DefineCatalogSkuResult replay = service.defineSku(command());

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getCanonicalSkuId()).isEqualTo("sku-1");
        verifyNoInteractions(masterDataMapper, outboxAppender);
    }

    @Test
    void shouldNotEmitSecondDefinitionForSameBusinessKeysWithNewCommand() {
        prepareNewOperation();
        prepareMasterData(0);
        when(operationMapper.markSucceeded(eq(101L), eq(1L), anyString(), any())).thenReturn(1);

        DefineCatalogSkuResult result = service.defineSku(command());

        assertThat(result.getCreated()).isFalse();
        assertThat(result.getDuplicate()).isFalse();
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void shouldRejectConflictingStyleDefinitionBeforeSkuWrite() {
        prepareNewOperation();
        when(masterDataMapper.selectStyle(1L, "YS2026-DRESS-001")).thenReturn(style().setStyleName("另一款式"));

        assertThatThrownBy(() -> service.defineSku(command()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("styleCode already belongs to a different Style definition");

        verify(masterDataMapper, never()).insertSku(anyString(), anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any());
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void shouldRejectUnqualifiedBrandReferenceBeforeAnyWrite() {
        DefineCatalogSkuCommand invalid = command();
        invalid.setBrandRef("1");

        assertThatThrownBy(() -> service.defineSku(invalid)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("brandRef must be source-qualified");
        verifyNoInteractions(operationMapper, masterDataMapper, outboxAppender);
    }

    @Test
    void shouldActivateStyleWithVersionCheckAndAppendStatusEvent() {
        prepareNewOperation();
        when(lifecycleMapper.selectStyleForUpdate(1L, "11111111-1111-4111-8111-111111111111"))
                .thenReturn(style().setStyleId("11111111-1111-4111-8111-111111111111"));
        when(lifecycleMapper.updateStyle(eq(1L), anyString(), eq(0), eq(10), eq(1L), any())).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-status", "b".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), anyString(), any())).thenReturn(1);

        CatalogLifecycleResult result = service.changeStatus(lifecycle(CatalogEntityType.STYLE,
                "11111111-1111-4111-8111-111111111111", CatalogLifecycleAction.ACTIVATE, 1L));

        assertThat(result.getPreviousStatus()).isEqualTo("DRAFT");
        assertThat(result.getCurrentStatus()).isEqualTo("ACTIVE");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(outboxAppender).append(argThat(event -> event.getEventType().equals("catalog.entity.status_changed")
                && event.getAggregateType().equals("catalog_entity")
                && event.getAggregateVersion() == 2L
                && event.getPayload().get("current_status").equals("ACTIVE")));
    }

    @Test
    void shouldUpdateStyleMetadataWithVersionCheckAndAppendEvent() {
        prepareNewOperation();
        when(lifecycleMapper.selectStyleForUpdate(1L, "11111111-1111-4111-8111-111111111111"))
                .thenReturn(style().setStyleId("11111111-1111-4111-8111-111111111111"));
        when(masterDataMapper.selectStyle(1L, "YS2026-DRESS-001-REV1"))
                .thenReturn(style().setStyleId("11111111-1111-4111-8111-111111111111"));
        when(masterDataMapper.updateStyleMetadata(eq(1L), eq("11111111-1111-4111-8111-111111111111"),
                eq("YS2026-DRESS-001-REV1"), eq("夏季连衣裙升级版"), eq("INTERNAL:CATEGORY:DRESS"),
                eq("INTERNAL:BRAND:YSHOPPING"), eq(2026), eq("AUTUMN"), eq("WAVE-02"), eq(1L), any())).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-style-update", "c".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), anyString(), any())).thenReturn(1);

        CatalogMetadataUpdateResult result = service.updateMetadata(styleUpdate());

        assertThat(result.getBusinessCode()).isEqualTo("YS2026-DRESS-001-REV1");
        assertThat(result.getCurrentStatus()).isEqualTo("DRAFT");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(outboxAppender).append(argThat(event -> event.getEventType().equals("catalog.entity.metadata_updated")
                && event.getAggregateId().equals("11111111-1111-4111-8111-111111111111")
                && event.getPayload().get("style_code").equals("YS2026-DRESS-001-REV1")
                && event.getPayload().get("season_code").equals("AUTUMN")));
    }

    @Test
    void shouldUpdateSpuMetadataWithVersionCheck() {
        prepareNewOperation();
        when(lifecycleMapper.selectSpuForUpdate(1L, "22222222-2222-4222-8222-222222222222"))
                .thenReturn(spu().setSpuId("22222222-2222-4222-8222-222222222222"));
        when(masterDataMapper.selectSpu(1L, "YS2026-DRESS-001-REV1"))
                .thenReturn(spu().setSpuId("22222222-2222-4222-8222-222222222222"));
        when(masterDataMapper.updateSpuMetadata(eq(1L), eq("22222222-2222-4222-8222-222222222222"),
                eq("YS2026-DRESS-001-REV1"), eq("夏季连衣裙 2026 升级版"),
                eq("INTERNAL:CATEGORY:DRESS"), eq(1L), any())).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-spu-update", "d".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), anyString(), any())).thenReturn(1);

        CatalogMetadataUpdateResult result = service.updateMetadata(spuUpdate());

        assertThat(result.getBusinessCode()).isEqualTo("YS2026-DRESS-001-REV1");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(masterDataMapper).updateSpuMetadata(eq(1L), eq("22222222-2222-4222-8222-222222222222"),
                eq("YS2026-DRESS-001-REV1"), eq("夏季连衣裙 2026 升级版"),
                eq("INTERNAL:CATEGORY:DRESS"), eq(1L), any());
    }

    @Test
    void shouldUpdateSkuBusinessCodeWithoutTouchingPricingOrInventory() {
        prepareNewOperation();
        when(lifecycleMapper.selectSkuForUpdate(1L, "33333333-3333-4333-8333-333333333333"))
                .thenReturn(sku().setSkuId("33333333-3333-4333-8333-333333333333"));
        when(masterDataMapper.selectSku(1L, "YS2026-DRESS-001-BLK-M-REV1"))
                .thenReturn(sku().setSkuId("33333333-3333-4333-8333-333333333333"));
        when(masterDataMapper.updateSkuMetadata(eq(1L), eq("33333333-3333-4333-8333-333333333333"),
                eq("YS2026-DRESS-001-BLK-M-REV1"), eq(1L), any())).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-sku-update", "e".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), anyString(), any())).thenReturn(1);

        CatalogMetadataUpdateResult result = service.updateMetadata(skuUpdate());

        assertThat(result.getBusinessCode()).isEqualTo("YS2026-DRESS-001-BLK-M-REV1");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(masterDataMapper).updateSkuMetadata(eq(1L), eq("33333333-3333-4333-8333-333333333333"),
                eq("YS2026-DRESS-001-BLK-M-REV1"), eq(1L), any());
        verify(masterDataMapper, never()).updateStyleMetadata(anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyInt(), anyString(), anyString(), anyLong(), any());
        verify(masterDataMapper, never()).updateSpuMetadata(anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyLong(), any());
    }

    @Test
    void shouldRotatePrimaryBarcodeAndBumpSkuAggregateVersion() {
        prepareNewOperation();
        CatalogSkuDO currentSku = sku().setSkuId("33333333-3333-4333-8333-333333333333");
        CatalogBarcodeDO previous = barcode().setSkuId(currentSku.getSkuId());
        CatalogBarcodeDO rotated = barcode().setBarcodeId("barcode-2").setBarcode("6901234567893")
                .setSkuId(currentSku.getSkuId()).setVersion(1L);
        when(lifecycleMapper.selectSkuForUpdate(1L, currentSku.getSkuId())).thenReturn(currentSku);
        when(masterDataMapper.selectActivePrimaryBarcode(1L, currentSku.getSkuId())).thenReturn(previous);
        when(masterDataMapper.selectBarcode(1L, "6901234567893")).thenReturn(null, rotated);
        when(masterDataMapper.retirePrimaryBarcode(eq(1L), eq("barcode-1"), eq(1L), any())).thenReturn(1);
        when(masterDataMapper.insertPrimaryBarcode(anyString(), eq(1L), eq(currentSku.getSkuId()),
                eq("6901234567893"), eq("EAN13"), any())).thenReturn(1);
        when(masterDataMapper.bumpSkuVersion(eq(1L), eq(currentSku.getSkuId()), eq(1L), any())).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-barcode-rotate", "f".repeat(64), false));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), anyString(), any())).thenReturn(1);

        CatalogBarcodeRotateResult result = service.rotateBarcode(barcodeRotate());

        assertThat(result.getPreviousBarcode()).isEqualTo("6901234567892");
        assertThat(result.getCurrentBarcode()).isEqualTo("6901234567893");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(masterDataMapper).retirePrimaryBarcode(eq(1L), eq("barcode-1"), eq(1L), any());
        verify(masterDataMapper).bumpSkuVersion(eq(1L), eq(currentSku.getSkuId()), eq(1L), any());
        verify(outboxAppender).append(argThat(event -> event.getEventType().equals("catalog.barcode.rotated")
                && event.getAggregateId().equals(currentSku.getSkuId())
                && event.getPayload().get("current_barcode").equals("6901234567893")));
    }

    @Test
    void shouldRejectSkuActivationBeforeSpuApproval() {
        prepareNewOperation();
        CatalogSkuDO sku = sku().setSkuId("33333333-3333-4333-8333-333333333333");
        when(lifecycleMapper.selectSkuForUpdate(1L, sku.getSkuId())).thenReturn(sku);
        when(lifecycleMapper.selectSpu(1L, "spu-1")).thenReturn(spu().setStatus(0));
        when(lifecycleMapper.selectColor(1L, "color-1")).thenReturn(color().setStatus(10));
        when(lifecycleMapper.selectSize(1L, "size-1")).thenReturn(size().setStatus(10));
        when(lifecycleMapper.selectSizeGroup(1L, "size-group-1")).thenReturn(sizeGroup().setStatus(10));

        assertThatThrownBy(() -> service.changeStatus(lifecycle(CatalogEntityType.SKU, sku.getSkuId(),
                CatalogLifecycleAction.ACTIVATE, 1L))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SPU must be APPROVED or ACTIVE before SKU activation");

        verify(lifecycleMapper, never()).updateSku(anyLong(), anyString(), anyInt(), anyInt(), anyLong(), any());
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void shouldRejectStaleExpectedVersionBeforeTransition() {
        prepareNewOperation();
        when(lifecycleMapper.selectColorForUpdate(1L, "44444444-4444-4444-8444-444444444444"))
                .thenReturn(color().setColorId("44444444-4444-4444-8444-444444444444").setVersion(2L));

        assertThatThrownBy(() -> service.changeStatus(lifecycle(CatalogEntityType.COLOR,
                "44444444-4444-4444-8444-444444444444", CatalogLifecycleAction.ACTIVATE, 1L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Catalog expectedVersion conflict");
        verify(lifecycleMapper, never()).updateColor(anyLong(), anyString(), anyInt(), anyInt(), anyLong(), any());
    }

    @Test
    void shouldRejectStyleDeactivationWhileActiveSpuStillReferenceIt() {
        prepareNewOperation();
        when(lifecycleMapper.selectStyleForUpdate(1L, "11111111-1111-4111-8111-111111111111"))
                .thenReturn(style().setStyleId("11111111-1111-4111-8111-111111111111").setStatus(10));
        when(lifecycleMapper.countActiveSpus(1L, "11111111-1111-4111-8111-111111111111")).thenReturn(1);

        assertThatThrownBy(() -> service.changeStatus(lifecycle(CatalogEntityType.STYLE,
                "11111111-1111-4111-8111-111111111111", CatalogLifecycleAction.DEACTIVATE, 1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Style cannot be deactivated while ACTIVE SPU still reference it");
        verify(lifecycleMapper, never()).updateStyle(anyLong(), anyString(), anyInt(), anyInt(), anyLong(), any());
    }

    private void prepareNewOperation() {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(3));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(101L, 1L)).thenAnswer(ignored -> new CatalogOperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
    }

    private void prepareMasterData(int skuInsertResult) {
        when(masterDataMapper.selectStyle(1L, "YS2026-DRESS-001")).thenReturn(style());
        when(masterDataMapper.selectSpu(1L, "YS2026-DRESS-001")).thenReturn(spu());
        when(masterDataMapper.selectColor(1L, "BLACK")).thenReturn(color());
        when(masterDataMapper.selectSizeGroup(1L, "WOMEN_TOP")).thenReturn(sizeGroup());
        when(masterDataMapper.selectSize(1L, "size-group-1", "M")).thenReturn(size());
        when(masterDataMapper.insertSku(anyString(), eq(1L), eq("spu-1"), eq("YS2026-DRESS-001-BLK-M"),
                eq("color-1"), eq("size-1"), anyString(), anyString(), eq("PCS"), any()))
                .thenReturn(skuInsertResult);
        when(masterDataMapper.selectSku(1L, "YS2026-DRESS-001-BLK-M")).thenReturn(sku());
        when(masterDataMapper.selectBarcode(1L, "6901234567892")).thenReturn(barcode());
    }

    private static CatalogStyleDO style() {
        return new CatalogStyleDO().setStyleId("style-1").setTenantId(1L).setStyleCode("YS2026-DRESS-001")
                .setStyleName("夏季连衣裙").setPlanningCategoryRef("INTERNAL:CATEGORY:DRESS")
                .setBrandRef("INTERNAL:BRAND:YSHOPPING").setPlanningYear(2026).setSeasonCode("SUMMER")
                .setWaveCode("WAVE-01").setStatus(0).setVersion(1L);
    }

    private static CatalogSpuDO spu() {
        return new CatalogSpuDO().setSpuId("spu-1").setTenantId(1L).setStyleId("style-1")
                .setSpuCode("YS2026-DRESS-001").setProductName("夏季连衣裙")
                .setSalesCategoryRef("INTERNAL:CATEGORY:DRESS").setStatus(0).setVersion(1L);
    }

    private static CatalogColorDO color() {
        return new CatalogColorDO().setColorId("color-1").setTenantId(1L).setColorCode("BLACK")
                .setDisplayName("黑色").setStatus(0).setVersion(1L);
    }

    private static CatalogSizeGroupDO sizeGroup() {
        return new CatalogSizeGroupDO().setSizeGroupId("size-group-1").setTenantId(1L)
                .setSizeGroupCode("WOMEN_TOP").setSizeGroupName("女装上衣尺码").setStatus(0).setVersion(1L);
    }

    private static CatalogSizeDO size() {
        return new CatalogSizeDO().setSizeId("size-1").setTenantId(1L).setSizeGroupId("size-group-1")
                .setSizeCode("M").setSizeName("M").setSortOrder(20).setStatus(0).setVersion(1L);
    }

    private static CatalogSkuDO sku() {
        String variant = "COLOR=BLACK|SIZE_GROUP=WOMEN_TOP|SIZE=M";
        return new CatalogSkuDO().setSkuId("sku-1").setTenantId(1L).setSpuId("spu-1")
                .setSkuCode("YS2026-DRESS-001-BLK-M").setColorId("color-1").setSizeId("size-1")
                .setVariantKey(variant).setVariantKeyHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(variant))
                .setBaseUomCode("PCS").setStatus(0).setVersion(1L);
    }

    private static CatalogBarcodeDO barcode() {
        return new CatalogBarcodeDO().setBarcodeId("barcode-1").setTenantId(1L).setSkuId("sku-1")
                .setBarcode("6901234567892").setBarcodeType("EAN13").setIsPrimary(true)
                .setStatus(10).setVersion(1L);
    }

    private static DefineCatalogSkuCommand command() {
        return DefineCatalogSkuCommand.builder().idempotencyKey("catalog-test-0001")
                .styleCode("ys2026-dress-001").styleName("夏季连衣裙")
                .planningCategoryRef("INTERNAL:CATEGORY:DRESS").brandRef("INTERNAL:BRAND:YSHOPPING")
                .planningYear(2026).seasonCode("summer").waveCode("wave-01")
                .spuCode("ys2026-dress-001").productName("夏季连衣裙")
                .salesCategoryRef("INTERNAL:CATEGORY:DRESS")
                .skuCode("ys2026-dress-001-blk-m").barcode("6901234567892").barcodeType("EAN13")
                .colorCode("black").colorName("黑色").sizeGroupCode("women_top")
                .sizeGroupName("女装上衣尺码").sizeCode("m").sizeName("M").sizeSort(20)
                .baseUomCode("pcs").status("draft")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-12T10:00:00Z")).build();
    }

    private static CatalogLifecycleCommand lifecycle(CatalogEntityType type, String id,
                                                     CatalogLifecycleAction action, long version) {
        return CatalogLifecycleCommand.builder().entityType(type).entityId(id).action(action)
                .expectedVersion(version).idempotencyKey("catalog-lifecycle-test-" + type + "-" + action)
                .reason("automated lifecycle test")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-12T11:00:00Z")).build();
    }

    private static CatalogMetadataUpdateCommand styleUpdate() {
        return CatalogMetadataUpdateCommand.builder().entityType(CatalogEntityType.STYLE)
                .entityId("11111111-1111-4111-8111-111111111111").expectedVersion(1L)
                .idempotencyKey("catalog-style-update-1").reason("rename style after planning review")
                .styleCode("ys2026-dress-001-rev1").styleName("夏季连衣裙升级版")
                .planningCategoryRef("INTERNAL:CATEGORY:DRESS").brandRef("INTERNAL:BRAND:YSHOPPING")
                .planningYear(2026).seasonCode("autumn").waveCode("wave-02")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-12T12:00:00Z")).build();
    }

    private static CatalogMetadataUpdateCommand spuUpdate() {
        return CatalogMetadataUpdateCommand.builder().entityType(CatalogEntityType.SPU)
                .entityId("22222222-2222-4222-8222-222222222222").expectedVersion(1L)
                .idempotencyKey("catalog-spu-update-1").reason("refresh product naming after content review")
                .spuCode("ys2026-dress-001-rev1").productName("夏季连衣裙 2026 升级版")
                .salesCategoryRef("INTERNAL:CATEGORY:DRESS")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-12T12:05:00Z")).build();
    }

    private static CatalogMetadataUpdateCommand skuUpdate() {
        return CatalogMetadataUpdateCommand.builder().entityType(CatalogEntityType.SKU)
                .entityId("33333333-3333-4333-8333-333333333333").expectedVersion(1L)
                .idempotencyKey("catalog-sku-update-1").reason("align sku code with approved copy deck")
                .skuCode("ys2026-dress-001-blk-m-rev1")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-12T12:10:00Z")).build();
    }

    private static CatalogBarcodeRotateCommand barcodeRotate() {
        return CatalogBarcodeRotateCommand.builder().skuId("33333333-3333-4333-8333-333333333333")
                .expectedVersion(1L).idempotencyKey("catalog-barcode-rotate-1")
                .reason("replace sampled internal barcode with approved retail barcode")
                .barcode("6901234567893").barcodeType("EAN13")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-12T12:15:00Z")).build();
    }
}
