package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppProductReviewDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppProductReviewListingSnapshotDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppProductReviewMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentView;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderLineView;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppProductReviewService {

    private static final String SOURCE_SYSTEM = "cloudmold-app-commerce";
    private static final String AUTHOR_LABEL = "匿名买家";
    private static final int SUMMARY_MAX_CHARS = 120;

    private final AppMemberPrincipalResolver principalResolver;
    private final AppOrderQueryApi orderQueryApi;
    private final AppFulfillmentQueryApi fulfillmentQueryApi;
    private final AppProductReviewMapper reviewMapper;
    private final AppFacadeOperationService facadeOperationService;
    private final AppAddressVaultCrypto addressVaultCrypto;
    private final OutboxAppender outboxAppender;
    private final Environment environment;

    public AppProductReviewEligibilityView getEligibility(String orderId, String orderItemId) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        EligibilitySnapshot snapshot = loadEligibility(principal.getPrincipalId(), orderId, orderItemId);
        return snapshot.view();
    }

    @Transactional(rollbackFor = Exception.class)
    public AppProductReviewView create(String idempotencyKey, String orderId, String orderItemId,
                                       int productScore, int serviceScore, int logisticsScore, String body) {
        requireKey(idempotencyKey);
        requireScore(productScore, "productScore");
        requireScore(serviceScore, "serviceScore");
        requireScore(logisticsScore, "logisticsScore");
        String normalizedBody = normalizeBody(body);
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderItemId", orderItemId);
        payload.put("productScore", productScore);
        payload.put("serviceScore", serviceScore);
        payload.put("logisticsScore", logisticsScore);
        payload.put("bodyDigestSha256", DigestUtil.sha256Hex(normalizedBody));
        AppFacadeOperationService.Replay<AppProductReviewView> replay = facadeOperationService.execute(
                "CREATE_PRODUCT_REVIEW", idempotencyKey, principal.getPrincipalId(), payload,
                AppProductReviewView.class,
                occurredAt -> createOnce(principal.getPrincipalId(), orderId, orderItemId, productScore,
                        serviceScore, logisticsScore, normalizedBody, occurredAt));
        replay.value().setDuplicate(Boolean.TRUE.equals(replay.value().getDuplicate()) || replay.duplicate());
        return replay.value();
    }

    public AppPublicProductReviewPageView listApprovedByListing(String listingId, int pageNo, int pageSize) {
        requirePage(pageNo, pageSize);
        requireNotBlank(listingId, "listingId is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long total = reviewMapper.countApprovedByListing(tenantId, listingId.trim());
        if (total == 0) {
            return AppPublicProductReviewPageView.builder().total(0L).pageNo(pageNo).pageSize(pageSize)
                    .list(List.of()).build();
        }
        long offset = (long) (pageNo - 1) * pageSize;
        return AppPublicProductReviewPageView.builder()
                .total(total)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .list(reviewMapper.selectApprovedByListing(tenantId, listingId.trim(), offset, pageSize).stream()
                        .map(this::toPublicItem)
                        .toList())
                .build();
    }

    public AppPublicProductReviewPageView listApprovedByProduct(String canonicalSpuId, int pageNo, int pageSize) {
        requirePage(pageNo, pageSize);
        requireNotBlank(canonicalSpuId, "canonicalSpuId is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long total = reviewMapper.countApprovedByProduct(tenantId, canonicalSpuId.trim());
        if (total == 0) {
            return AppPublicProductReviewPageView.builder().total(0L).pageNo(pageNo).pageSize(pageSize)
                    .list(List.of()).build();
        }
        long offset = (long) (pageNo - 1) * pageSize;
        return AppPublicProductReviewPageView.builder()
                .total(total)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .list(reviewMapper.selectApprovedByProduct(tenantId, canonicalSpuId.trim(), offset, pageSize).stream()
                        .map(this::toPublicItem)
                        .toList())
                .build();
    }

    private AppProductReviewView createOnce(String principalId, String orderId, String orderItemId,
                                            int productScore, int serviceScore, int logisticsScore,
                                            String normalizedBody, Instant occurredAt) {
        EligibilitySnapshot eligibility = loadEligibility(principalId, orderId, orderItemId);
        require(Boolean.TRUE.equals(eligibility.view().getEligible()),
                eligibility.view().getReasonCode() == null ? "product review is not eligible"
                        : eligibility.view().getReasonCode());
        require(eligibility.existing() == null, "product review already exists");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String reviewId = UUID.randomUUID().toString();
        int overallScore = deriveOverall(productScore, serviceScore, logisticsScore);
        String contentDigest = DigestUtil.sha256Hex(normalizedBody);
        String publicSummary = summarize(normalizedBody);
        AppAddressVaultCrypto.Encrypted encrypted = addressVaultCrypto.encrypt(
                aad(tenantId, principalId, reviewId),
                normalizedBody.getBytes(StandardCharsets.UTF_8));
        LocalDateTime now = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        Moderation moderation = resolveModeration(now);
        AppProductReviewDO row = new AppProductReviewDO();
        row.setReviewId(reviewId);
        row.setTenantId(tenantId);
        row.setBuyerPrincipalId(principalId);
        row.setOrderId(eligibility.order().getOrderId());
        row.setOrderItemId(eligibility.line().getOrderItemId());
        row.setPaymentId(eligibility.order().getPaymentId());
        row.setFulfillmentId(eligibility.order().getFulfillmentId());
        row.setListingId(eligibility.snapshot().getListingId());
        row.setListingOfferId(eligibility.snapshot().getListingOfferId());
        row.setMerchantId(eligibility.snapshot().getMerchantId());
        row.setShopId(eligibility.snapshot().getShopId());
        row.setCanonicalSpuId(eligibility.snapshot().getCanonicalSpuId());
        row.setCanonicalSkuId(eligibility.snapshot().getCanonicalSkuId());
        row.setProductScore(productScore);
        row.setServiceScore(serviceScore);
        row.setLogisticsScore(logisticsScore);
        row.setOverallScore(overallScore);
        row.setContentKeyId(encrypted.keyId());
        row.setContentIv(encrypted.initializationVector());
        row.setContentCiphertext(encrypted.ciphertext());
        row.setContentDigestSha256(contentDigest);
        row.setPublicSummary(publicSummary);
        row.setModerationStatus(moderation.status());
        row.setModerationPolicy(moderation.policy());
        row.setApprovedAt(moderation.approvedAt());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        require(reviewMapper.insert(row) == 1, "failed to persist product review");
        appendCreated(row, occurredAt);
        return toPrivateView(row, normalizedBody, false);
    }

    private EligibilitySnapshot loadEligibility(String principalId, String orderId, String orderItemId) {
        requireNotBlank(principalId, "principalId is required");
        requireNotBlank(orderId, "orderId is required");
        requireNotBlank(orderItemId, "orderItemId is required");
        AppOrderView order = orderQueryApi.requireOwned(principalId, orderId.trim());
        OrderLineView line = order.getItems() == null ? null : order.getItems().stream()
                .filter(item -> Objects.equals(item.getOrderItemId(), orderItemId.trim()))
                .findFirst().orElse(null);
        require(line != null, "canonical order item does not exist");
        AppProductReviewDO existing = reviewMapper.selectByOrderItem(TenantContextHolder.getRequiredTenantId(), line.getOrderItemId());
        if (existing != null) {
            return new EligibilitySnapshot(order, line, snapshotFromReview(existing), existing,
                    AppProductReviewEligibilityView.builder()
                            .eligible(false)
                            .reasonCode("ALREADY_REVIEWED")
                            .existingReviewId(existing.getReviewId())
                            .existingModerationStatus(existing.getModerationStatus())
                            .orderId(order.getOrderId())
                            .orderItemId(line.getOrderItemId())
                            .listingId(existing.getListingId())
                            .listingOfferId(existing.getListingOfferId())
                            .merchantId(existing.getMerchantId())
                            .shopId(existing.getShopId())
                            .canonicalSpuId(existing.getCanonicalSpuId())
                            .canonicalSkuId(existing.getCanonicalSkuId())
                            .build());
        }
        AppProductReviewListingSnapshotDO snapshot = requireListingSnapshot(line);
        String reason = evaluateEligibilityReason(order, line);
        return new EligibilitySnapshot(order, line, snapshot, null,
                AppProductReviewEligibilityView.builder()
                        .eligible(reason == null)
                        .reasonCode(reason)
                        .orderId(order.getOrderId())
                        .orderItemId(line.getOrderItemId())
                        .listingId(snapshot.getListingId())
                        .listingOfferId(snapshot.getListingOfferId())
                        .merchantId(snapshot.getMerchantId())
                        .shopId(snapshot.getShopId())
                        .canonicalSpuId(snapshot.getCanonicalSpuId())
                        .canonicalSkuId(snapshot.getCanonicalSkuId())
                        .build());
    }

    private String evaluateEligibilityReason(AppOrderView order, OrderLineView line) {
        if (!isPaymentSettled(order)) {
            return "PAYMENT_NOT_CAPTURED";
        }
        if (!isDelivered(order)) {
            return "FULFILLMENT_NOT_DELIVERED";
        }
        if (line.getListingRevision() == null || line.getListingVersion() == null
                || line.getListingId() == null || line.getListingOfferId() == null || line.getCanonicalSkuId() == null) {
            return "ORDER_ITEM_REVIEW_FACTS_INCOMPLETE";
        }
        return null;
    }

    private boolean isPaymentSettled(AppOrderView order) {
        if (order == null) {
            return false;
        }
        if (Set.of("COMPLETED", "RETURNED").contains(order.getStatus())) {
            return true;
        }
        return Set.of("CAPTURED", "REFUNDED").contains(order.getPaymentStatus());
    }

    private boolean isDelivered(AppOrderView order) {
        if (order == null) {
            return false;
        }
        if (Set.of("COMPLETED", "RETURNED").contains(order.getStatus())) {
            return true;
        }
        if ("DELIVERED".equals(order.getFulfillmentStatus())) {
            return true;
        }
        if (order.getFulfillmentId() == null) {
            return false;
        }
        AppFulfillmentView fulfillment = fulfillmentQueryApi.getByOrder(order.getOrderId());
        return fulfillment != null && "DELIVERED".equals(fulfillment.getStatus());
    }

    private AppProductReviewListingSnapshotDO requireListingSnapshot(OrderLineView line) {
        require(line.getListingRevision() != null && line.getListingRevision() > 0,
                "canonical order item listing revision is required");
        AppProductReviewListingSnapshotDO snapshot = reviewMapper.selectListingSnapshot(
                TenantContextHolder.getRequiredTenantId(),
                line.getListingId(),
                line.getListingRevision(),
                line.getListingOfferId(),
                line.getCanonicalSkuId());
        require(snapshot != null, "canonical order item listing snapshot is unavailable");
        return snapshot;
    }

    private void appendCreated(AppProductReviewDO row, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("review_id", row.getReviewId());
        payload.put("order_id", row.getOrderId());
        payload.put("order_item_id", row.getOrderItemId());
        payload.put("listing_id", row.getListingId());
        payload.put("listing_offer_id", row.getListingOfferId());
        payload.put("merchant_id", row.getMerchantId());
        payload.put("shop_id", row.getShopId());
        payload.put("canonical_spu_id", row.getCanonicalSpuId());
        payload.put("canonical_sku_id", row.getCanonicalSkuId());
        payload.put("product_score", row.getProductScore());
        payload.put("service_score", row.getServiceScore());
        payload.put("logistics_score", row.getLogisticsScore());
        payload.put("overall_score", row.getOverallScore());
        payload.put("content_digest_sha256", row.getContentDigestSha256());
        payload.put("public_summary", row.getPublicSummary());
        payload.put("moderation_status", row.getModerationStatus());
        payload.put("moderation_policy", row.getModerationPolicy());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("app.product_review.created")
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(row.getTenantId())
                .aggregateType("app_product_review")
                .aggregateId(row.getReviewId())
                .aggregateVersion(1L)
                .eventSequence((short) 1)
                .occurredAt(occurredAt)
                .correlationId(row.getOrderId())
                .causationId(row.getReviewId())
                .idempotencyKey("app-product-review:" + row.getReviewId())
                .payload(payload)
                .headers(Map.of("pii_safe", true, "contains_raw_content", false))
                .destination("lakehouse")
                .build());
    }

    private AppProductReviewView toPrivateView(AppProductReviewDO row, String body, boolean duplicate) {
        return AppProductReviewView.builder()
                .reviewId(row.getReviewId())
                .orderId(row.getOrderId())
                .orderItemId(row.getOrderItemId())
                .listingId(row.getListingId())
                .listingOfferId(row.getListingOfferId())
                .merchantId(row.getMerchantId())
                .shopId(row.getShopId())
                .canonicalSpuId(row.getCanonicalSpuId())
                .canonicalSkuId(row.getCanonicalSkuId())
                .productScore(row.getProductScore())
                .serviceScore(row.getServiceScore())
                .logisticsScore(row.getLogisticsScore())
                .overallScore(row.getOverallScore())
                .body(body)
                .publicSummary(row.getPublicSummary())
                .moderationStatus(row.getModerationStatus())
                .moderationPolicy(row.getModerationPolicy())
                .duplicate(duplicate)
                .createdAt(row.getCreatedAt().toInstant(ZoneOffset.UTC))
                .approvedAt(row.getApprovedAt() == null ? null : row.getApprovedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    private AppPublicProductReviewPageView.Item toPublicItem(AppProductReviewDO row) {
        require("APPROVED".equals(row.getModerationStatus()), "public review row must be approved");
        return AppPublicProductReviewPageView.Item.builder()
                .reviewId(row.getReviewId())
                .authorLabel(AUTHOR_LABEL)
                .listingId(row.getListingId())
                .listingOfferId(row.getListingOfferId())
                .canonicalSpuId(row.getCanonicalSpuId())
                .canonicalSkuId(row.getCanonicalSkuId())
                .productScore(row.getProductScore())
                .serviceScore(row.getServiceScore())
                .logisticsScore(row.getLogisticsScore())
                .overallScore(row.getOverallScore())
                .body(decryptBody(row))
                .publicSummary(row.getPublicSummary())
                .approvedAt(row.getApprovedAt() == null ? null : row.getApprovedAt().toInstant(ZoneOffset.UTC))
                .createdAt(row.getCreatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    private String decryptBody(AppProductReviewDO row) {
        byte[] plaintext = addressVaultCrypto.decrypt(
                aad(row.getTenantId(), row.getBuyerPrincipalId(), row.getReviewId()),
                row.getContentKeyId(), row.getContentIv(), row.getContentCiphertext());
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private Moderation resolveModeration(LocalDateTime now) {
        return isLocalTestLike()
                ? new Moderation("APPROVED", "LOCAL_TEST_AUTO_APPROVE", now)
                : new Moderation("PENDING_MODERATION", "PRODUCTION_FAIL_CLOSED", null);
    }

    private boolean isLocalTestLike() {
        Set<String> profiles = Set.of(environment.getActiveProfiles());
        boolean production = profiles.stream().anyMatch(profile ->
                "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
        if (production) {
            return false;
        }
        return profiles.isEmpty() || profiles.stream().anyMatch(profile ->
                "local".equalsIgnoreCase(profile) || "demo".equalsIgnoreCase(profile)
                        || "test".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile));
    }

    private static int deriveOverall(int productScore, int serviceScore, int logisticsScore) {
        return Math.round((productScore + serviceScore + logisticsScore) / 3.0f);
    }

    private static String summarize(String body) {
        return body.length() <= SUMMARY_MAX_CHARS ? body : body.substring(0, SUMMARY_MAX_CHARS);
    }

    private static String normalizeBody(String body) {
        requireNotBlank(body, "body is required");
        String normalized = body.trim().replaceAll("\\s+", " ");
        require(normalized.length() <= 2000, "body must be at most 2000 characters");
        return normalized;
    }

    private static void requirePage(int pageNo, int pageSize) {
        require(pageNo > 0, "pageNo must be positive");
        require(pageSize > 0 && pageSize <= 50, "pageSize must be between 1 and 50");
    }

    private static void requireScore(int score, String field) {
        require(score >= 1 && score <= 5, field + " must be between 1 and 5");
    }

    private static void requireKey(String value) {
        require(value != null && value.length() >= 8 && value.length() <= 128,
                "idempotencyKey must contain 8 to 128 characters");
    }

    private static String aad(Long tenantId, String principalId, String reviewId) {
        return tenantId + "|" + principalId + "|" + reviewId + "|PRODUCT_REVIEW";
    }

    private static AppProductReviewListingSnapshotDO snapshotFromReview(AppProductReviewDO row) {
        AppProductReviewListingSnapshotDO snapshot = new AppProductReviewListingSnapshotDO();
        snapshot.setListingId(row.getListingId());
        snapshot.setListingOfferId(row.getListingOfferId());
        snapshot.setMerchantId(row.getMerchantId());
        snapshot.setShopId(row.getShopId());
        snapshot.setCanonicalSpuId(row.getCanonicalSpuId());
        snapshot.setCanonicalSkuId(row.getCanonicalSkuId());
        return snapshot;
    }

    private static void requireNotBlank(String value, String message) {
        require(value != null && !value.isBlank(), message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record Moderation(String status, String policy, LocalDateTime approvedAt) {}

    private record EligibilitySnapshot(AppOrderView order, OrderLineView line,
                                       AppProductReviewListingSnapshotDO snapshot, AppProductReviewDO existing,
                                       AppProductReviewEligibilityView view) {}
}
