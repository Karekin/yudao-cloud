package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppCheckoutDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppCheckoutMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityConsumerEvidenceApi;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppCheckoutService {

    private static final Duration CHECKOUT_TTL = Duration.ofMinutes(15);
    private static final String CURRENCY_CNY = "CNY";

    private final AppMemberPrincipalResolver principalResolver;
    private final AppListingQueryApi appListingQueryApi;
    private final ListingQueryApi listingQueryApi;
    private final InventoryV3AvailabilityQueryApi inventoryAvailabilityQueryApi;
    private final InventoryCheckoutReservationApi reservationApi;
    private final OrderCommandApi orderCommandApi;
    private final AppOrderQueryApi appOrderQueryApi;
    private final PaymentCommandApi paymentCommandApi;
    private final AppCheckoutMapper checkoutMapper;
    private final OutboxAppender outboxAppender;
    private final QualityConsumerEvidenceApi qualityConsumerEvidenceApi;
    private final Environment environment;
    private final AppFacadeOperationService facadeOperationService;

    @org.springframework.beans.factory.annotation.Value("${cloudmold.app-commerce.internal-test-payment-enabled:false}")
    private boolean internalTestPaymentEnabled;

    @Transactional(rollbackFor = Exception.class)
    public AppCheckoutView preview(String idempotencyKey, String listingId, String listingOfferId,
                                   String canonicalSkuId, int quantity) {
        requireKey(idempotencyKey);
        require(quantity > 0 && quantity <= 99, "quantity must be between 1 and 99");
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        PublishedListingView listing = appListingQueryApi.requirePublished(listingId);
        ListingOfferView selected = listing.getOffers().stream()
                .filter(offer -> Objects.equals(offer.getListingOfferId(), listingOfferId)
                        && Objects.equals(offer.getCanonicalSkuId(), canonicalSkuId)
                        && Boolean.TRUE.equals(offer.getEnabled()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("published listing offer does not exist"));
        listingQueryApi.requirePublishedOffer(PublishedOfferValidationCommand.builder()
                .listingId(listingId).listingOfferId(listingOfferId).canonicalSkuId(canonicalSkuId)
                .expectedPriceMinor(selected.getPriceMinor()).currencyCode(selected.getCurrencyCode()).build());
        var quality = qualityConsumerEvidenceApi.getLatestBySku(canonicalSkuId);
        require(quality != null && "VERIFIED".equals(quality.getStatus()),
                "canonical SKU lacks approved consumer quality evidence");
        InventorySkuAvailabilityView inventory = inventoryAvailabilityQueryApi.getBySku(canonicalSkuId, Instant.now());
        BigDecimal requested = BigDecimal.valueOf(quantity);
        require(inventory.getAllocatableQuantity() != null
                        && inventory.getAllocatableQuantity().compareTo(requested) >= 0,
                "insufficient allocatable inventory");
        require(CURRENCY_CNY.equals(selected.getCurrencyCode()), "consumer checkout supports CNY only");
        long productAmount = Math.multiplyExact(selected.getPriceMinor(), quantity);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("tenantId", tenantId);
        fingerprint.put("principalId", principal.getPrincipalId());
        fingerprint.put("listingId", listingId);
        fingerprint.put("listingOfferId", listingOfferId);
        fingerprint.put("canonicalSkuId", canonicalSkuId);
        fingerprint.put("quantity", quantity);
        fingerprint.put("unitPriceMinor", selected.getPriceMinor());
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(fingerprint));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        AppCheckoutDO checkout = new AppCheckoutDO().setCheckoutToken(UUID.randomUUID().toString())
                .setTenantId(tenantId).setBuyerPrincipalId(principal.getPrincipalId())
                .setIdempotencyKey(idempotencyKey).setRequestHash(requestHash)
                .setListingId(listingId).setListingOfferId(listingOfferId)
                .setCanonicalSpuId(listing.getCanonicalSpuId()).setCanonicalSkuId(canonicalSkuId)
                .setQuantity(requested).setUnitPriceMinor(selected.getPriceMinor())
                .setProductAmountMinor(productAmount).setShippingAmountMinor(0L).setDiscountAmountMinor(0L)
                .setPayableAmountMinor(productAmount).setCurrencyCode(CURRENCY_CNY)
                .setStatus("PREVIEWED").setVersion(1L).setExpiresAt(now.plus(CHECKOUT_TTL))
                .setCreatedAt(now).setUpdatedAt(now);
        boolean inserted = checkoutMapper.insertIgnore(checkout) == 1;
        if (!inserted) {
            AppCheckoutDO existing = checkoutMapper.selectByIdempotency(
                    tenantId, principal.getPrincipalId(), idempotencyKey);
            require(existing != null && Objects.equals(existing.getRequestHash(), requestHash),
                    "idempotency key conflicts with a different checkout payload");
            require(Objects.equals(existing.getBuyerPrincipalId(), principal.getPrincipalId()),
                    "checkout idempotency key is owned by another principal");
            return toView(existing, inventory.getAllocatableQuantity(), true);
        }
        appendPreviewEvent(checkout);
        return toView(checkout, inventory.getAllocatableQuantity(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppOrderView createOrder(String idempotencyKey, String checkoutToken) {
        requireKey(idempotencyKey);
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        AppCheckoutDO checkout = checkoutMapper.selectForUpdate(tenantId, checkoutToken);
        require(checkout != null && Objects.equals(checkout.getBuyerPrincipalId(), principal.getPrincipalId()),
                "checkout does not exist");
        if ("ORDERED".equals(checkout.getStatus())) {
            return appOrderQueryApi.requireOwned(principal.getPrincipalId(), checkout.getOrderId());
        }
        require("PREVIEWED".equals(checkout.getStatus()), "checkout cannot create an order");
        require(checkout.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)), "checkout has expired");
        Instant now = Instant.now();
        String correlationId = checkout.getCheckoutToken();
        OrderCommandResult placed = orderCommandApi.execute(OrderCommand.builder()
                .operation(OrderOperation.PLACE_FROM_LISTING).idempotencyKey(idempotencyKey + ":place")
                .runId(checkout.getCheckoutToken()).buyerId(principal.getPrincipalId())
                .items(List.of(OrderLineCommand.builder().lineKey("line-1")
                        .canonicalSkuId(checkout.getCanonicalSkuId()).quantity(checkout.getQuantity())
                        .unitPriceMinor(checkout.getUnitPriceMinor()).listingId(checkout.getListingId())
                        .listingOfferId(checkout.getListingOfferId()).build()))
                .shippingAmountMinor(checkout.getShippingAmountMinor())
                .discountAmountMinor(checkout.getDiscountAmountMinor()).currencyCode(checkout.getCurrencyCode())
                .correlationId(correlationId).causationId(checkout.getCheckoutToken()).occurredAt(now).build());
        require(placed.getItems() != null && placed.getItems().size() == 1,
                "consumer first slice requires exactly one order item");
        OrderLineView line = placed.getItems().get(0);
        InventoryCheckoutReservationResult reserved = reservationApi.reserve(
                InventoryCheckoutReservationCommand.builder()
                        .idempotencyKey(idempotencyKey + ":reserve")
                        .canonicalSkuId(checkout.getCanonicalSkuId()).quantity(checkout.getQuantity())
                        .orderId(placed.getOrderId()).orderItemId(line.getOrderItemId()).orderNo(placed.getOrderNo())
                        .correlationId(correlationId).causationId(correlationId).occurredAt(now).build());
        OrderCommandResult confirmed = orderCommandApi.execute(OrderCommand.builder()
                .operation(OrderOperation.CONFIRM_INVENTORY).idempotencyKey(idempotencyKey + ":confirm-inventory")
                .runId(checkout.getCheckoutToken()).orderId(placed.getOrderId())
                .expectedVersion(placed.getAggregateVersion())
                .reservationReferences(List.of(OrderLineReference.builder()
                        .orderItemId(line.getOrderItemId()).reservationId(reserved.getReservationId()).build()))
                .correlationId(correlationId).causationId(correlationId).occurredAt(now).build());
        require(checkoutMapper.markOrdered(tenantId, checkoutToken, principal.getPrincipalId(),
                checkout.getVersion(), confirmed.getOrderId(), LocalDateTime.now(ZoneOffset.UTC)) == 1,
                "checkout order transition conflict");
        return appOrderQueryApi.requireOwned(principal.getPrincipalId(), confirmed.getOrderId());
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentCommandResult captureInternalTest(String idempotencyKey, String orderId, Long expectedOrderVersion) {
        require(internalTestPaymentEnabled && isNonProductionProfile(),
                "INTERNAL_TEST payment is disabled; it must never be enabled in production");
        requireKey(idempotencyKey);
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("expectedOrderVersion", expectedOrderVersion);
        AppFacadeOperationService.Replay<PaymentCommandResult> replay = facadeOperationService.execute(
                "CAPTURE_INTERNAL_TEST", idempotencyKey, principal.getPrincipalId(), payload,
                PaymentCommandResult.class,
                occurredAt -> captureInternalTestOnce(idempotencyKey, orderId, expectedOrderVersion,
                        principal.getPrincipalId(), occurredAt));
        replay.value().setDuplicate(replay.duplicate() || Boolean.TRUE.equals(replay.value().getDuplicate()));
        return replay.value();
    }

    private PaymentCommandResult captureInternalTestOnce(String idempotencyKey, String orderId,
                                                         Long expectedOrderVersion, String principalId,
                                                         Instant occurredAt) {
        AppOrderView order = appOrderQueryApi.requireOwned(principalId, orderId);
        require("INVENTORY_RESERVED".equals(order.getStatus()), "canonical order is not payable");
        require(Objects.equals(order.getAggregateVersion(), expectedOrderVersion), "canonical order version conflict");
        String correlationId = UUID.nameUUIDFromBytes(("payment:" + orderId).getBytes()).toString();
        PaymentCommandResult payment = paymentCommandApi.execute(PaymentCommand.builder()
                .operation(PaymentOperation.CAPTURE).idempotencyKey(idempotencyKey + ":capture")
                .runId(orderId).orderId(orderId).amountMinor(order.getPayableAmountMinor())
                .currencyCode(order.getCurrencyCode()).providerCode("INTERNAL_TEST")
                .providerTransactionId("APP-" + DigestUtil.sha256Hex(idempotencyKey).substring(0, 24))
                .correlationId(correlationId).causationId(correlationId).occurredAt(occurredAt).build());
        orderCommandApi.execute(OrderCommand.builder().operation(OrderOperation.CONFIRM_PAYMENT)
                .idempotencyKey(idempotencyKey + ":confirm-order").runId(orderId).orderId(orderId)
                .expectedVersion(expectedOrderVersion).paymentId(payment.getPaymentId())
                .correlationId(correlationId).causationId(correlationId).occurredAt(occurredAt).build());
        return payment;
    }

    private boolean isNonProductionProfile() {
        Set<String> profiles = Set.of(environment.getActiveProfiles());
        return profiles.stream().noneMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile))
                && profiles.stream().anyMatch(profile -> "local".equalsIgnoreCase(profile)
                        || "demo".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile));
    }

    private void appendPreviewEvent(AppCheckoutDO checkout) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buyer_principal_id", checkout.getBuyerPrincipalId());
        payload.put("listing_id", checkout.getListingId());
        payload.put("listing_offer_id", checkout.getListingOfferId());
        payload.put("canonical_spu_id", checkout.getCanonicalSpuId());
        payload.put("canonical_sku_id", checkout.getCanonicalSkuId());
        payload.put("quantity", checkout.getQuantity());
        payload.put("unit_price_minor", checkout.getUnitPriceMinor());
        payload.put("payable_amount_minor", checkout.getPayableAmountMinor());
        payload.put("currency_code", checkout.getCurrencyCode());
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("app.checkout.previewed")
                .schemaVersion(1).sourceSystem("cloudmold-app-commerce").tenantId(checkout.getTenantId())
                .aggregateType("app_checkout").aggregateId(checkout.getCheckoutToken())
                .aggregateVersion(checkout.getVersion()).eventSequence((short) 1)
                .occurredAt(checkout.getCreatedAt().toInstant(ZoneOffset.UTC))
                .correlationId(checkout.getCheckoutToken()).causationId(checkout.getCheckoutToken())
                .idempotencyKey(checkout.getIdempotencyKey() + ":event")
                .payload(payload).headers(Map.of("status", checkout.getStatus()))
                .destination("lakehouse").build());
    }

    private static AppCheckoutView toView(AppCheckoutDO value, BigDecimal available, boolean duplicate) {
        return AppCheckoutView.builder().checkoutToken(value.getCheckoutToken())
                .principalId(value.getBuyerPrincipalId()).listingId(value.getListingId())
                .listingOfferId(value.getListingOfferId()).canonicalSpuId(value.getCanonicalSpuId())
                .canonicalSkuId(value.getCanonicalSkuId()).quantity(value.getQuantity())
                .unitPriceMinor(value.getUnitPriceMinor()).productAmountMinor(value.getProductAmountMinor())
                .shippingAmountMinor(value.getShippingAmountMinor()).discountAmountMinor(value.getDiscountAmountMinor())
                .payableAmountMinor(value.getPayableAmountMinor()).currencyCode(value.getCurrencyCode())
                .availableQuantity(available).expiresAt(value.getExpiresAt()).duplicate(duplicate).build();
    }

    private static void requireKey(String value) {
        require(value != null && value.length() >= 8 && value.length() <= 128,
                "idempotencyKey must contain 8 to 128 characters");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    @lombok.Value
    @Builder
    public static class AppOrderCreateCommand {
        String idempotencyKey;
        String checkoutToken;
    }
}
