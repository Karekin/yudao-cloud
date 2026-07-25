package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppCheckoutDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppCheckoutMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCheckoutReservationApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCheckoutReservationResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventorySkuAvailabilityView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3AvailabilityQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.AppListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingOfferView;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingView;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderCommandApi;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderCommandResult;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderLineView;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCommandApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCommandResult;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppCheckoutServiceTest {

    private final AppMemberPrincipalResolver principalResolver = mock(AppMemberPrincipalResolver.class);
    private final AppListingQueryApi appListingQueryApi = mock(AppListingQueryApi.class);
    private final ListingQueryApi listingQueryApi = mock(ListingQueryApi.class);
    private final InventoryV3AvailabilityQueryApi inventoryAvailabilityQueryApi = mock(InventoryV3AvailabilityQueryApi.class);
    private final InventoryCheckoutReservationApi reservationApi = mock(InventoryCheckoutReservationApi.class);
    private final OrderCommandApi orderCommandApi = mock(OrderCommandApi.class);
    private final AppOrderQueryApi appOrderQueryApi = mock(AppOrderQueryApi.class);
    private final PaymentCommandApi paymentCommandApi = mock(PaymentCommandApi.class);
    private final AppCheckoutMapper checkoutMapper = mock(AppCheckoutMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final QualityConsumerEvidenceApi qualityConsumerEvidenceApi = mock(QualityConsumerEvidenceApi.class);
    private final Environment environment = mock(Environment.class);
    private final AppFacadeOperationService facadeOperationService = mock(AppFacadeOperationService.class);

    private final AppCheckoutService service = new AppCheckoutService(principalResolver, appListingQueryApi,
            listingQueryApi, inventoryAvailabilityQueryApi, reservationApi, orderCommandApi, appOrderQueryApi,
            paymentCommandApi, checkoutMapper, outboxAppender, qualityConsumerEvidenceApi, environment,
            facadeOperationService);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(11L);
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .memberUserId(1001L).principalId("principal-member-1").principalStatus("ACTIVE")
                .sourceSystem("MEMBER").sourceType("MEMBER_USER").build());
        when(appListingQueryApi.requirePublished("listing-1")).thenReturn(publishedListing());
        when(qualityConsumerEvidenceApi.getLatestBySku("sku-1")).thenReturn(QualityConsumerEvidenceView.builder()
                .canonicalSkuId("sku-1").status("VERIFIED").inspectionTaskId("inspect-1")
                .evidenceToken("sha256:" + "a".repeat(64)).build());
        when(inventoryAvailabilityQueryApi.getBySku(eq("sku-1"), any(Instant.class)))
                .thenReturn(InventorySkuAvailabilityView.builder().canonicalSkuId("sku-1")
                        .allocatableQuantity(new BigDecimal("8.000000")).inventoryVersion(6L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void previewShouldReplaySamePayloadForSamePrincipalAndIdempotencyKey() {
        AtomicReference<AppCheckoutDO> stored = new AtomicReference<>();
        when(checkoutMapper.insertIgnore(any(AppCheckoutDO.class))).thenAnswer(invocation -> {
            AppCheckoutDO value = invocation.getArgument(0);
            if (stored.get() == null) {
                stored.set(copy(value));
                return 1;
            }
            return 0;
        });
        when(checkoutMapper.selectByIdempotency(11L, "principal-member-1", "preview-idem-001"))
                .thenAnswer(invocation -> copy(stored.get()));

        AppCheckoutView first = service.preview("preview-idem-001", "listing-1", "offer-1", "sku-1", 2);
        AppCheckoutView replay = service.preview("preview-idem-001", "listing-1", "offer-1", "sku-1", 2);

        assertThat(first.getDuplicate()).isFalse();
        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getCheckoutToken()).isEqualTo(first.getCheckoutToken());
        verify(outboxAppender, times(1)).append(any());
    }

    @Test
    void previewShouldRejectInventoryShortage() {
        when(inventoryAvailabilityQueryApi.getBySku(eq("sku-1"), any(Instant.class)))
                .thenReturn(InventorySkuAvailabilityView.builder().canonicalSkuId("sku-1")
                        .allocatableQuantity(new BigDecimal("1.000000")).inventoryVersion(6L).build());

        assertThatThrownBy(() -> service.preview("preview-idem-002", "listing-1", "offer-1", "sku-1", 2))
                .hasMessage("insufficient allocatable inventory");
        verify(checkoutMapper, never()).insertIgnore(any());
    }

    @Test
    void previewShouldRejectDifferentPayloadForExistingIdempotencyKey() {
        AppCheckoutDO existing = baseCheckout()
                .setCheckoutToken("checkout-1")
                .setIdempotencyKey("preview-idem-003")
                .setRequestHash("b".repeat(64));
        when(checkoutMapper.insertIgnore(any(AppCheckoutDO.class))).thenReturn(0);
        when(checkoutMapper.selectByIdempotency(11L, "principal-member-1", "preview-idem-003"))
                .thenReturn(existing);

        assertThatThrownBy(() -> service.preview("preview-idem-003", "listing-1", "offer-1", "sku-1", 3))
                .hasMessage("idempotency key conflicts with a different checkout payload");
    }

    @Test
    void previewIdempotencyKeyShouldBeScopedToCurrentPrincipal() {
        Map<String, AppCheckoutDO> stored = new HashMap<>();
        when(checkoutMapper.insertIgnore(any(AppCheckoutDO.class))).thenAnswer(invocation -> {
            AppCheckoutDO value = invocation.getArgument(0);
            String key = value.getBuyerPrincipalId() + "|" + value.getIdempotencyKey();
            if (stored.containsKey(key)) return 0;
            stored.put(key, copy(value));
            return 1;
        });
        when(checkoutMapper.selectByIdempotency(eq(11L), anyString(), eq("shared-idem-001")))
                .thenAnswer(invocation -> copy(stored.get(invocation.getArgument(1) + "|shared-idem-001")));

        AppCheckoutView first = service.preview("shared-idem-001", "listing-1", "offer-1", "sku-1", 1);
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .memberUserId(1002L).principalId("principal-member-2").principalStatus("ACTIVE")
                .sourceSystem("MEMBER").sourceType("MEMBER_USER").build());
        AppCheckoutView second = service.preview("shared-idem-001", "listing-1", "offer-1", "sku-1", 1);

        assertThat(first.getPrincipalId()).isEqualTo("principal-member-1");
        assertThat(second.getPrincipalId()).isEqualTo("principal-member-2");
        assertThat(second.getCheckoutToken()).isNotEqualTo(first.getCheckoutToken());
        verify(checkoutMapper, times(2)).insertIgnore(any());
    }

    @Test
    void createOrderShouldReturnExistingOwnedOrderWithoutReissuingCommands() {
        AppCheckoutDO checkout = baseCheckout()
                .setCheckoutToken("checkout-ordered")
                .setStatus("ORDERED")
                .setOrderId("order-1");
        AppOrderView order = AppOrderView.builder().orderId("order-1").buyerPrincipalId("principal-member-1").build();
        when(checkoutMapper.selectForUpdate(11L, "checkout-ordered")).thenReturn(checkout);
        when(appOrderQueryApi.requireOwned("principal-member-1", "order-1")).thenReturn(order);

        AppOrderView result = service.createOrder("order-idem-001", "checkout-ordered");

        assertThat(result.getOrderId()).isEqualTo("order-1");
        verifyNoInteractions(orderCommandApi, reservationApi);
    }

    @Test
    void createOrderShouldRejectCheckoutOwnedByAnotherPrincipal() {
        when(checkoutMapper.selectForUpdate(11L, "checkout-foreign"))
                .thenReturn(baseCheckout().setCheckoutToken("checkout-foreign").setBuyerPrincipalId("principal-member-2"));

        assertThatThrownBy(() -> service.createOrder("order-idem-002", "checkout-foreign"))
                .hasMessage("checkout does not exist");
        verifyNoInteractions(orderCommandApi, reservationApi);
    }

    @Test
    void createOrderShouldReserveAndConfirmInventoryExactlyOnce() {
        AppCheckoutDO checkout = baseCheckout().setCheckoutToken("checkout-previewed");
        when(checkoutMapper.selectForUpdate(11L, "checkout-previewed")).thenReturn(checkout);
        when(orderCommandApi.execute(argThat(command -> command != null
                && command.getOperation() != null
                && command.getOperation().name().equals("PLACE_FROM_LISTING"))))
                .thenReturn(OrderCommandResult.builder().orderId("order-9").orderNo("CMO0009")
                        .aggregateVersion(1L)
                        .items(List.of(OrderLineView.builder().orderItemId("item-1").build()))
                        .build());
        when(reservationApi.reserve(any())).thenReturn(InventoryCheckoutReservationResult.builder()
                .reservationId("reserve-1").allocationId("alloc-1").aggregateVersion(2L).duplicate(false).build());
        when(orderCommandApi.execute(argThat(command -> command != null
                && command.getOperation() != null
                && command.getOperation().name().equals("CONFIRM_INVENTORY"))))
                .thenReturn(OrderCommandResult.builder().orderId("order-9").aggregateVersion(2L).build());
        when(checkoutMapper.markOrdered(eq(11L), eq("checkout-previewed"), eq("principal-member-1"), eq(1L),
                eq("order-9"), any(LocalDateTime.class))).thenReturn(1);
        when(appOrderQueryApi.requireOwned("principal-member-1", "order-9"))
                .thenReturn(AppOrderView.builder().orderId("order-9").buyerPrincipalId("principal-member-1").build());

        AppOrderView result = service.createOrder("order-idem-003", "checkout-previewed");

        assertThat(result.getOrderId()).isEqualTo("order-9");
        verify(orderCommandApi, times(2)).execute(any());
        verify(reservationApi).reserve(any());
    }

    @Test
    void captureShouldUseOwnedServerAmountAndReplayWithoutDoubleCapture() {
        ReflectionTestUtils.setField(service, "internalTestPaymentEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        when(appOrderQueryApi.requireOwned("principal-member-1", "order-pay-1")).thenReturn(AppOrderView.builder()
                .orderId("order-pay-1").buyerPrincipalId("principal-member-1")
                .status("INVENTORY_RESERVED").aggregateVersion(3L)
                .payableAmountMinor(39800L).currencyCode("CNY").build());
        PaymentCommandResult paid = PaymentCommandResult.builder()
                .paymentId("payment-1").orderId("order-pay-1").capturedAmountMinor(39800L)
                .currencyCode("CNY").currentStatus("CAPTURED").duplicate(false).build();
        when(paymentCommandApi.execute(any())).thenReturn(paid);
        when(orderCommandApi.execute(argThat(command ->
                command.getOperation().name().equals("CONFIRM_PAYMENT"))))
                .thenReturn(OrderCommandResult.builder().orderId("order-pay-1").aggregateVersion(4L).build());
        AtomicReference<PaymentCommandResult> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            AppFacadeOperationService.Operation<PaymentCommandResult> operation = invocation.getArgument(5);
            if (stored.get() == null) {
                stored.set(operation.run(Instant.parse("2026-07-25T00:00:00Z")));
                return new AppFacadeOperationService.Replay<>(stored.get(), false);
            }
            return new AppFacadeOperationService.Replay<>(stored.get(), true);
        }).when(facadeOperationService).execute(eq("CAPTURE_INTERNAL_TEST"), eq("payment-idem-001"),
                eq("principal-member-1"), any(), eq(PaymentCommandResult.class), any());

        PaymentCommandResult first = service.captureInternalTest("payment-idem-001", "order-pay-1", 3L);
        PaymentCommandResult replay = service.captureInternalTest("payment-idem-001", "order-pay-1", 3L);

        assertThat(first.getCapturedAmountMinor()).isEqualTo(39800L);
        assertThat(replay.getDuplicate()).isTrue();
        verify(paymentCommandApi, times(1)).execute(argThat(command ->
                command.getAmountMinor().equals(39800L)
                        && "CNY".equals(command.getCurrencyCode())
                        && "INTERNAL_TEST".equals(command.getProviderCode())));
        verify(orderCommandApi, times(1)).execute(argThat(command ->
                command.getOperation().name().equals("CONFIRM_PAYMENT")
                        && command.getExpectedVersion().equals(3L)));
    }

    @Test
    void captureShouldFailClosedOutsideExplicitNonProductionProfile() {
        ReflectionTestUtils.setField(service, "internalTestPaymentEnabled", true);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "test"});

        assertThatThrownBy(() -> service.captureInternalTest("payment-idem-002", "order-pay-2", 1L))
                .hasMessageContaining("must never be enabled in production");
        verifyNoInteractions(paymentCommandApi);
    }

    private static PublishedListingView publishedListing() {
        return PublishedListingView.builder()
                .listingId("listing-1")
                .listingNo("LIST-1")
                .title("Wool Coat")
                .canonicalSpuId("spu-1")
                .offers(List.of(ListingOfferView.builder()
                        .listingOfferId("offer-1")
                        .canonicalSkuId("sku-1")
                        .priceMinor(19900L)
                        .currencyCode("CNY")
                        .enabled(true)
                        .build()))
                .build();
    }

    private static AppCheckoutDO baseCheckout() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new AppCheckoutDO()
                .setCheckoutToken("checkout-1")
                .setTenantId(11L)
                .setBuyerPrincipalId("principal-member-1")
                .setIdempotencyKey("preview-idem-001")
                .setRequestHash("a".repeat(64))
                .setListingId("listing-1")
                .setListingOfferId("offer-1")
                .setCanonicalSpuId("spu-1")
                .setCanonicalSkuId("sku-1")
                .setQuantity(new BigDecimal("2"))
                .setUnitPriceMinor(19900L)
                .setProductAmountMinor(39800L)
                .setShippingAmountMinor(0L)
                .setDiscountAmountMinor(0L)
                .setPayableAmountMinor(39800L)
                .setCurrencyCode("CNY")
                .setStatus("PREVIEWED")
                .setVersion(1L)
                .setExpiresAt(now.plusMinutes(10))
                .setCreatedAt(now)
                .setUpdatedAt(now);
    }

    private static AppCheckoutDO copy(AppCheckoutDO source) {
        if (source == null) {
            return null;
        }
        return new AppCheckoutDO()
                .setCheckoutToken(source.getCheckoutToken())
                .setTenantId(source.getTenantId())
                .setBuyerPrincipalId(source.getBuyerPrincipalId())
                .setIdempotencyKey(source.getIdempotencyKey())
                .setRequestHash(source.getRequestHash())
                .setListingId(source.getListingId())
                .setListingOfferId(source.getListingOfferId())
                .setCanonicalSpuId(source.getCanonicalSpuId())
                .setCanonicalSkuId(source.getCanonicalSkuId())
                .setQuantity(source.getQuantity())
                .setUnitPriceMinor(source.getUnitPriceMinor())
                .setProductAmountMinor(source.getProductAmountMinor())
                .setShippingAmountMinor(source.getShippingAmountMinor())
                .setDiscountAmountMinor(source.getDiscountAmountMinor())
                .setPayableAmountMinor(source.getPayableAmountMinor())
                .setCurrencyCode(source.getCurrencyCode())
                .setStatus(source.getStatus())
                .setOrderId(source.getOrderId())
                .setVersion(source.getVersion())
                .setExpiresAt(source.getExpiresAt())
                .setCreatedAt(source.getCreatedAt())
                .setUpdatedAt(source.getUpdatedAt());
    }
}
