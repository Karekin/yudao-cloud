package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppCartDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppCartItemDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppCartItemMapper;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppCartMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3AvailabilityQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.AppListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingOfferView;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingView;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppCartServiceTest {

    private final AppMemberPrincipalResolver principalResolver = mock(AppMemberPrincipalResolver.class);
    private final AppCartMapper cartMapper = mock(AppCartMapper.class);
    private final AppCartItemMapper cartItemMapper = mock(AppCartItemMapper.class);
    private final CatalogSkuProjectionApi catalogSkuProjectionApi = mock(CatalogSkuProjectionApi.class);
    private final AppListingQueryApi appListingQueryApi = mock(AppListingQueryApi.class);
    private final ListingQueryApi listingQueryApi = mock(ListingQueryApi.class);
    private final InventoryV3AvailabilityQueryApi inventoryAvailabilityQueryApi = mock(InventoryV3AvailabilityQueryApi.class);
    private final QualityConsumerEvidenceApi qualityConsumerEvidenceApi = mock(QualityConsumerEvidenceApi.class);
    private final AppFacadeOperationService facadeOperationService = mock(AppFacadeOperationService.class);
    private final AppCheckoutService checkoutService = mock(AppCheckoutService.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);

    private final AppCartService service = new AppCartService(principalResolver, cartMapper, cartItemMapper,
            catalogSkuProjectionApi, appListingQueryApi, listingQueryApi, inventoryAvailabilityQueryApi,
            qualityConsumerEvidenceApi, facadeOperationService, checkoutService, outboxAppender);

    private final Map<String, AppCartDO> carts = new LinkedHashMap<>();
    private final Map<String, AppCartItemDO> items = new LinkedHashMap<>();
    private final Map<String, AppCartView> operationResults = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(11L);
        when(principalResolver.requireCurrent()).thenReturn(principal("principal-member-1", 1001L));
        CatalogSkuProjectionView projection = new CatalogSkuProjectionView();
        projection.setCanonicalSkuId("sku-1");
        projection.setSkuCode("SKU-1");
        when(catalogSkuProjectionApi.getActiveSku("sku-1")).thenReturn(projection);
        when(appListingQueryApi.requirePublished("listing-1")).thenReturn(publishedListing("offer-1", 19900L));
        when(listingQueryApi.requirePublishedOffer(any())).thenReturn(null);
        when(qualityConsumerEvidenceApi.getLatestBySku("sku-1")).thenReturn(QualityConsumerEvidenceView.builder()
                .canonicalSkuId("sku-1").status("VERIFIED").build());
        when(inventoryAvailabilityQueryApi.getBySku(eq("sku-1"), any(Instant.class)))
                .thenReturn(InventorySkuAvailabilityView.builder().canonicalSkuId("sku-1")
                        .allocatableQuantity(new BigDecimal("8.000000")).inventoryVersion(6L).build());
        when(checkoutService.preview(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(AppCheckoutView.builder().checkoutToken("checkout-1").build());
        mockStorage();
        mockFacadeReplay();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void addShouldMergeAndReplayByOperationLedger() {
        AppCartView first = service.addOrMerge("cart-add-001", 0L, "listing-1", "offer-1", "sku-1", 2, true);
        AppCartView replay = service.addOrMerge("cart-add-001", 0L, "listing-1", "offer-1", "sku-1", 2, true);
        AppCartView merged = service.addOrMerge("cart-add-002", 1L, "listing-1", "offer-1", "sku-1", 3, true);

        assertThat(first.getDuplicate()).isFalse();
        assertThat(replay.getDuplicate()).isTrue();
        assertThat(first.getAggregateVersion()).isEqualTo(1L);
        assertThat(merged.getAggregateVersion()).isEqualTo(2L);
        assertThat(merged.getLines()).hasSize(1);
        assertThat(merged.getLines().get(0).getQuantity()).isEqualByComparingTo("5");
        verify(outboxAppender, times(2)).append(any());
    }

    @Test
    void addShouldPropagatePayloadConflict() {
        when(facadeOperationService.execute(eq("CART_ADD"), eq("cart-add-conflict"),
                eq("principal-member-1"), any(), eq(AppCartView.class), any()))
                .thenThrow(new IllegalArgumentException("idempotency key conflicts with a different App operation payload"));

        assertThatThrownBy(() -> service.addOrMerge(
                "cart-add-conflict", 0L, "listing-1", "offer-1", "sku-1", 1, true))
                .hasMessageContaining("idempotency key conflicts");
    }

    @Test
    void addShouldRejectVersionConflict() {
        service.addOrMerge("cart-add-010", 0L, "listing-1", "offer-1", "sku-1", 1, true);

        assertThatThrownBy(() -> service.addOrMerge(
                "cart-add-011", 0L, "listing-1", "offer-1", "sku-1", 1, true))
                .hasMessage("cart version conflict");
    }

    @Test
    void getShouldRemainTenantPrincipalScoped() {
        service.addOrMerge("cart-add-020", 0L, "listing-1", "offer-1", "sku-1", 1, true);
        when(principalResolver.requireCurrent()).thenReturn(principal("principal-member-2", 1002L));

        AppCartView secondPrincipal = service.get();

        assertThat(secondPrincipal.getAggregateVersion()).isZero();
        assertThat(secondPrincipal.getLines()).isEmpty();
    }

    @Test
    void addShouldRejectInvalidOffer() {
        when(appListingQueryApi.requirePublished("listing-1")).thenReturn(publishedListing("offer-x", 19900L));

        assertThatThrownBy(() -> service.addOrMerge(
                "cart-add-invalid-offer", 0L, "listing-1", "offer-1", "sku-1", 1, true))
                .hasMessage("listing offer is no longer sellable");
    }

    @Test
    void getShouldExposeRecallAndInventoryInvalidReasons() {
        service.addOrMerge("cart-add-030", 0L, "listing-1", "offer-1", "sku-1", 2, true);
        when(qualityConsumerEvidenceApi.getLatestBySku("sku-1")).thenReturn(QualityConsumerEvidenceView.builder()
                .canonicalSkuId("sku-1").status("RECALLED").build());

        AppCartView recalled = service.get();

        assertThat(recalled.getLines()).singleElement()
                .extracting(AppCartView.Line::getInvalidReasonCode)
                .isEqualTo("QUALITY_NOT_VERIFIED");

        when(qualityConsumerEvidenceApi.getLatestBySku("sku-1")).thenReturn(QualityConsumerEvidenceView.builder()
                .canonicalSkuId("sku-1").status("VERIFIED").build());
        when(inventoryAvailabilityQueryApi.getBySku(eq("sku-1"), any(Instant.class)))
                .thenReturn(InventorySkuAvailabilityView.builder().canonicalSkuId("sku-1")
                        .allocatableQuantity(BigDecimal.ZERO).inventoryVersion(7L).build());

        AppCartView outOfStock = service.get();

        assertThat(outOfStock.getLines()).singleElement()
                .extracting(AppCartView.Line::getInvalidReasonCode)
                .isEqualTo("INSUFFICIENT_ALLOCATABLE_INVENTORY");
    }

    @Test
    void previewSelectedShouldFailWithoutAddressOrWithMultiSelect() {
        service.addOrMerge("cart-add-040", 0L, "listing-1", "offer-1", "sku-1", 1, true);

        assertThatThrownBy(() -> service.previewSelected("cart-preview-001", 1L, ""))
                .hasMessage("addressRef is required");

        PublishedListingView secondListing = PublishedListingView.builder()
                .listingId("listing-2")
                .listingNo("LIST-2")
                .title("Puffer Coat")
                .primaryImageUrl("https://img.example.com/2.jpg")
                .canonicalSpuId("spu-2")
                .offers(List.of(ListingOfferView.builder().listingOfferId("offer-2")
                        .canonicalSkuId("sku-1").priceMinor(29900L).currencyCode("CNY").enabled(true).build()))
                .build();
        when(appListingQueryApi.requirePublished("listing-2")).thenReturn(secondListing);
        service.addOrMerge("cart-add-041", 1L, "listing-2", "offer-2", "sku-1", 1, true);

        assertThatThrownBy(() -> service.previewSelected(
                "cart-preview-002", 2L, "11111111-1111-4111-8111-111111111111"))
                .hasMessage("cart checkout does not support multi-select");
    }

    @Test
    void previewSelectedShouldUseExactlyOneSelectedLine() {
        service.addOrMerge("cart-add-050", 0L, "listing-1", "offer-1", "sku-1", 2, true);

        AppCheckoutView result = service.previewSelected(
                "cart-preview-003", 1L, "11111111-1111-4111-8111-111111111111");

        assertThat(result.getCheckoutToken()).isEqualTo("checkout-1");
        verify(checkoutService).preview("cart-preview-003", "listing-1", "offer-1", "sku-1",
                2, "11111111-1111-4111-8111-111111111111");
    }

    private void mockFacadeReplay() {
        doAnswer(invocation -> {
            String operationType = invocation.getArgument(0);
            String idempotencyKey = invocation.getArgument(1);
            String principalId = invocation.getArgument(2);
            @SuppressWarnings("unchecked")
            AppFacadeOperationService.Operation<AppCartView> operation =
                    invocation.getArgument(5, AppFacadeOperationService.Operation.class);
            String key = operationType + "|" + principalId + "|" + idempotencyKey;
            AppCartView existing = operationResults.get(key);
            if (existing != null) {
                return new AppFacadeOperationService.Replay<>(copyCartView(existing), true);
            }
            AppCartView created = operation.run(Instant.parse("2026-07-25T00:00:00Z"));
            operationResults.put(key, copyCartView(created));
            return new AppFacadeOperationService.Replay<>(created, false);
        }).when(facadeOperationService).execute(anyString(), anyString(), anyString(),
                any(), eq(AppCartView.class), any());
    }

    private void mockStorage() {
        when(cartMapper.insertIgnore(anyString(), anyLong(), anyString(), any(LocalDateTime.class))).thenAnswer(invocation -> {
            String cartId = invocation.getArgument(0, String.class);
            Long tenantId = invocation.getArgument(1, Long.class);
            String principalId = invocation.getArgument(2, String.class);
            LocalDateTime now = invocation.getArgument(3, LocalDateTime.class);
            String key = tenantId + "|" + principalId;
            if (carts.containsKey(key)) {
                return 0;
            }
            carts.put(key, new AppCartDO().setCartId(cartId).setTenantId(tenantId)
                    .setBuyerPrincipalId(principalId).setVersion(0L).setStatus("ACTIVE")
                    .setCreatedAt(now).setUpdatedAt(now));
            return 1;
        });
        when(cartMapper.selectOwned(anyLong(), anyString())).thenAnswer(invocation ->
                copyCart(carts.get(invocation.getArgument(0) + "|" + invocation.getArgument(1))));
        when(cartMapper.selectOwnedForUpdate(anyLong(), anyString())).thenAnswer(invocation ->
                copyCart(carts.get(invocation.getArgument(0) + "|" + invocation.getArgument(1))));
        when(cartMapper.updateVersion(anyLong(), anyString(), anyLong(), anyLong(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    Long tenantId = invocation.getArgument(0);
                    String cartId = invocation.getArgument(1);
                    Long expected = invocation.getArgument(2);
                    Long next = invocation.getArgument(3);
                    LocalDateTime now = invocation.getArgument(4);
                    AppCartDO stored = findCart(tenantId, cartId);
                    if (stored == null || !Objects.equals(stored.getVersion(), expected)) {
                        return 0;
                    }
                    stored.setVersion(next).setUpdatedAt(now);
                    return 1;
                });
        when(cartItemMapper.selectByCart(anyLong(), anyString())).thenAnswer(invocation ->
                items.values().stream()
                        .filter(item -> Objects.equals(item.getTenantId(), invocation.getArgument(0))
                                && Objects.equals(item.getCartId(), invocation.getArgument(1)))
                        .sorted(Comparator.comparing(AppCartItemDO::getUpdatedAt).reversed()
                                .thenComparing(AppCartItemDO::getLineId))
                        .map(AppCartServiceTest::copyCartItem)
                        .toList());
        when(cartItemMapper.selectOwnedLine(anyLong(), anyString(), anyString())).thenAnswer(invocation ->
                copyCartItem(items.get(invocation.getArgument(0) + "|" + invocation.getArgument(1) + "|" + invocation.getArgument(2))));
        when(cartItemMapper.selectByIdentity(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> items.values().stream()
                        .filter(item -> Objects.equals(item.getTenantId(), invocation.getArgument(0))
                                && Objects.equals(item.getCartId(), invocation.getArgument(1))
                                && Objects.equals(item.getListingId(), invocation.getArgument(2))
                                && Objects.equals(item.getListingOfferId(), invocation.getArgument(3))
                                && Objects.equals(item.getCanonicalSkuId(), invocation.getArgument(4)))
                        .findFirst().map(AppCartServiceTest::copyCartItem).orElse(null));
        when(cartItemMapper.countByCart(anyLong(), anyString())).thenAnswer(invocation ->
                items.values().stream()
                        .filter(item -> Objects.equals(item.getTenantId(), invocation.getArgument(0))
                                && Objects.equals(item.getCartId(), invocation.getArgument(1)))
                        .count());
        when(cartItemMapper.insert(any(AppCartItemDO.class))).thenAnswer(invocation -> {
            AppCartItemDO item = copyCartItem(invocation.getArgument(0));
            items.put(item.getTenantId() + "|" + item.getCartId() + "|" + item.getLineId(), item);
            return 1;
        });
        when(cartItemMapper.updateQuantityAndSelection(anyLong(), anyString(), anyString(),
                any(BigDecimal.class), anyBoolean(), any(LocalDateTime.class))).thenAnswer(invocation -> {
            AppCartItemDO stored = items.get(invocation.getArgument(0) + "|" + invocation.getArgument(1) + "|" + invocation.getArgument(2));
            if (stored == null) {
                return 0;
            }
            stored.setQuantity(invocation.getArgument(3)).setSelected(invocation.getArgument(4))
                    .setUpdatedAt(invocation.getArgument(5));
            return 1;
        });
        when(cartItemMapper.updateSelection(anyLong(), anyString(), anyString(),
                anyBoolean(), any(LocalDateTime.class))).thenAnswer(invocation -> {
            AppCartItemDO stored = items.get(invocation.getArgument(0) + "|" + invocation.getArgument(1) + "|" + invocation.getArgument(2));
            if (stored == null) {
                return 0;
            }
            stored.setSelected(invocation.getArgument(3)).setUpdatedAt(invocation.getArgument(4));
            return 1;
        });
        when(cartItemMapper.deleteLine(anyLong(), anyString(), anyString())).thenAnswer(invocation ->
                items.remove(invocation.getArgument(0) + "|" + invocation.getArgument(1) + "|" + invocation.getArgument(2)) != null ? 1 : 0);
        when(cartItemMapper.deleteByCart(anyLong(), anyString())).thenAnswer(invocation -> {
            List<String> keys = items.keySet().stream()
                    .filter(key -> key.startsWith(invocation.getArgument(0) + "|" + invocation.getArgument(1) + "|"))
                    .toList();
            keys.forEach(items::remove);
            return keys.size();
        });
    }

    private static AppMemberPrincipalView principal(String principalId, Long memberUserId) {
        return AppMemberPrincipalView.builder()
                .principalId(principalId)
                .memberUserId(memberUserId)
                .principalStatus("ACTIVE")
                .sourceSystem("MEMBER")
                .sourceType("MEMBER_USER")
                .build();
    }

    private static PublishedListingView publishedListing(String offerId, Long priceMinor) {
        return PublishedListingView.builder()
                .listingId("listing-1")
                .listingNo("LIST-1")
                .title("Wool Coat")
                .primaryImageUrl("https://img.example.com/1.jpg")
                .canonicalSpuId("spu-1")
                .offers(List.of(ListingOfferView.builder()
                        .listingOfferId(offerId)
                        .canonicalSkuId("sku-1")
                        .priceMinor(priceMinor)
                        .currencyCode("CNY")
                        .enabled(true)
                        .build()))
                .build();
    }

    private AppCartDO findCart(Long tenantId, String cartId) {
        return carts.values().stream()
                .filter(cart -> Objects.equals(cart.getTenantId(), tenantId)
                        && Objects.equals(cart.getCartId(), cartId))
                .findFirst().orElse(null);
    }

    private static AppCartDO copyCart(AppCartDO source) {
        if (source == null) {
            return null;
        }
        return new AppCartDO()
                .setCartId(source.getCartId())
                .setTenantId(source.getTenantId())
                .setBuyerPrincipalId(source.getBuyerPrincipalId())
                .setVersion(source.getVersion())
                .setStatus(source.getStatus())
                .setCreatedAt(source.getCreatedAt())
                .setUpdatedAt(source.getUpdatedAt());
    }

    private static AppCartItemDO copyCartItem(AppCartItemDO source) {
        if (source == null) {
            return null;
        }
        return new AppCartItemDO()
                .setLineId(source.getLineId())
                .setTenantId(source.getTenantId())
                .setCartId(source.getCartId())
                .setBuyerPrincipalId(source.getBuyerPrincipalId())
                .setListingId(source.getListingId())
                .setListingOfferId(source.getListingOfferId())
                .setCanonicalSkuId(source.getCanonicalSkuId())
                .setQuantity(source.getQuantity())
                .setSelected(source.getSelected())
                .setCreatedAt(source.getCreatedAt())
                .setUpdatedAt(source.getUpdatedAt());
    }

    private static AppCartView copyCartView(AppCartView source) {
        return AppCartView.builder()
                .cartId(source.getCartId())
                .principalId(source.getPrincipalId())
                .aggregateVersion(source.getAggregateVersion())
                .lineCount(source.getLineCount())
                .selectedLineCount(source.getSelectedLineCount())
                .duplicate(source.getDuplicate())
                .lines(source.getLines().stream().map(line -> AppCartView.Line.builder()
                        .lineId(line.getLineId())
                        .listingId(line.getListingId())
                        .listingOfferId(line.getListingOfferId())
                        .canonicalSkuId(line.getCanonicalSkuId())
                        .title(line.getTitle())
                        .primaryImageUrl(line.getPrimaryImageUrl())
                        .quantity(line.getQuantity())
                        .selected(line.getSelected())
                        .valid(line.getValid())
                        .invalidReasonCode(line.getInvalidReasonCode())
                        .currentUnitPriceMinor(line.getCurrentUnitPriceMinor())
                        .currentProductAmountMinor(line.getCurrentProductAmountMinor())
                        .currencyCode(line.getCurrencyCode())
                        .availableQuantity(line.getAvailableQuantity())
                        .qualityStatus(line.getQualityStatus())
                        .build()).toList())
                .build();
    }
}
