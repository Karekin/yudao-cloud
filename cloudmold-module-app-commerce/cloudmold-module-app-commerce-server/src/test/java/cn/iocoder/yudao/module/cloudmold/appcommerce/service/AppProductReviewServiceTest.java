package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppProductReviewDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppProductReviewListingSnapshotDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppProductReviewMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentView;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.AppOrderView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderLineView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppProductReviewServiceTest {

    private final AppMemberPrincipalResolver principalResolver = mock(AppMemberPrincipalResolver.class);
    private final AppOrderQueryApi orderQueryApi = mock(AppOrderQueryApi.class);
    private final AppFulfillmentQueryApi fulfillmentQueryApi = mock(AppFulfillmentQueryApi.class);
    private final AppProductReviewMapper reviewMapper = mock(AppProductReviewMapper.class);
    private final AppFacadeOperationService facadeOperationService = mock(AppFacadeOperationService.class);
    private final AppAddressVaultCrypto addressVaultCrypto = mock(AppAddressVaultCrypto.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final Environment environment = mock(Environment.class);

    private final AppProductReviewService service = new AppProductReviewService(principalResolver, orderQueryApi,
            fulfillmentQueryApi, reviewMapper, facadeOperationService, addressVaultCrypto, outboxAppender, environment);

    private final Map<String, AppProductReviewDO> reviewsById = new LinkedHashMap<>();
    private final Map<String, AppProductReviewDO> reviewsByOrderItem = new LinkedHashMap<>();
    private final Map<String, AppProductReviewView> operationResults = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(11L);
        when(principalResolver.requireCurrent()).thenReturn(AppMemberPrincipalView.builder()
                .memberUserId(1001L)
                .principalId("principal-member-1")
                .principalStatus("ACTIVE")
                .sourceSystem("MEMBER")
                .sourceType("MEMBER_USER")
                .build());
        when(orderQueryApi.requireOwned("principal-member-1", "order-1")).thenReturn(order("order-1", "DELIVERED", "CAPTURED"));
        when(fulfillmentQueryApi.getByOrder("order-1")).thenReturn(AppFulfillmentView.builder()
                .fulfillmentId("ful-1").orderId("order-1").status("DELIVERED").build());
        when(reviewMapper.selectListingSnapshot(11L, "listing-1", 2, "offer-1", "sku-1"))
                .thenReturn(snapshot());
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        when(addressVaultCrypto.encrypt(anyString(), any())).thenAnswer(invocation ->
                new AppAddressVaultCrypto.Encrypted("review-key-1",
                        new byte[12], ("cipher:" + new String(invocation.getArgument(1, byte[].class), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));
        when(addressVaultCrypto.decrypt(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            byte[] ciphertext = invocation.getArgument(3, byte[].class);
            String value = new String(ciphertext, StandardCharsets.UTF_8);
            return value.startsWith("cipher:") ? value.substring("cipher:".length()).getBytes(StandardCharsets.UTF_8) : ciphertext;
        });
        mockReviewStorage();
        mockFacadeReplay();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void eligibilityShouldReturnTrueForDeliveredCapturedOwnedItem() {
        AppProductReviewEligibilityView result = service.getEligibility("order-1", "item-1");

        assertThat(result.getEligible()).isTrue();
        assertThat(result.getListingId()).isEqualTo("listing-1");
        assertThat(result.getCanonicalSpuId()).isEqualTo("spu-1");
    }

    @Test
    void eligibilityShouldRejectWhenPaymentNotCaptured() {
        when(orderQueryApi.requireOwned("principal-member-1", "order-2")).thenReturn(order("order-2", "SHIPPED", "CREATED"));

        AppProductReviewEligibilityView result = service.getEligibility("order-2", "item-1");

        assertThat(result.getEligible()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("PAYMENT_NOT_CAPTURED");
    }

    @Test
    void createShouldEncryptApproveLocallyAndReplay() {
        AppProductReviewView first = service.create("review-idem-001", "order-1", "item-1",
                5, 4, 3, "  Great coat, arrived fast. ");
        AppProductReviewView replay = service.create("review-idem-001", "order-1", "item-1",
                5, 4, 3, "  Great coat, arrived fast. ");

        assertThat(first.getDuplicate()).isFalse();
        assertThat(first.getModerationStatus()).isEqualTo("APPROVED");
        assertThat(first.getOverallScore()).isEqualTo(4);
        assertThat(first.getBody()).isEqualTo("Great coat, arrived fast.");
        assertThat(replay.getDuplicate()).isTrue();
        verify(outboxAppender, times(1)).append(argThat(command ->
                !"Great coat, arrived fast.".equals(String.valueOf(command.getPayload().get("body")))
                        && command.getPayload().containsKey("content_digest_sha256")));
    }

    @Test
    void createShouldFailWhenOrderItemAlreadyReviewed() {
        service.create("review-idem-010", "order-1", "item-1", 5, 5, 5, "Excellent");

        assertThatThrownBy(() -> service.create("review-idem-011", "order-1", "item-1", 4, 4, 4, "Again"))
                .hasMessage("ALREADY_REVIEWED");
    }

    @Test
    void createShouldFailClosedToPendingModerationInProduction() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});

        AppProductReviewView result = service.create("review-idem-020", "order-1", "item-1",
                5, 4, 4, "Pending moderation");

        assertThat(result.getModerationStatus()).isEqualTo("PENDING_MODERATION");
        assertThat(service.listApprovedByListing("listing-1", 1, 20).getList()).isEmpty();
    }

    @Test
    void publicQueriesShouldReturnApprovedReviewsWithoutPrincipal() {
        AppProductReviewView created = service.create("review-idem-030", "order-1", "item-1",
                4, 4, 5, "Anonymous visible review");

        AppPublicProductReviewPageView listing = service.listApprovedByListing("listing-1", 1, 20);
        AppPublicProductReviewPageView product = service.listApprovedByProduct("spu-1", 1, 20);

        assertThat(created.getReviewId()).isEqualTo(listing.getList().get(0).getReviewId());
        assertThat(listing.getList().get(0).getAuthorLabel()).isEqualTo("匿名买家");
        assertThat(listing.getList().get(0).getBody()).isEqualTo("Anonymous visible review");
        assertThat(product.getList()).hasSize(1);
    }

    @Test
    void publicQueriesShouldExcludeHiddenRows() {
        AppProductReviewDO hidden = storedReview("hidden-1", "item-hidden", "HIDDEN", null, "Hidden body");
        reviewsById.put(hidden.getReviewId(), hidden);
        reviewsByOrderItem.put(hidden.getOrderItemId(), hidden);

        AppPublicProductReviewPageView listing = service.listApprovedByListing("listing-1", 1, 20);

        assertThat(listing.getList()).isEmpty();
    }

    private void mockFacadeReplay() {
        doAnswer(invocation -> {
            String operationType = invocation.getArgument(0);
            String idempotencyKey = invocation.getArgument(1);
            String principalId = invocation.getArgument(2);
            @SuppressWarnings("unchecked")
            AppFacadeOperationService.Operation<AppProductReviewView> operation =
                    invocation.getArgument(5, AppFacadeOperationService.Operation.class);
            String key = operationType + "|" + principalId + "|" + idempotencyKey;
            AppProductReviewView existing = operationResults.get(key);
            if (existing != null) {
                return new AppFacadeOperationService.Replay<>(copy(existing), true);
            }
            AppProductReviewView created = operation.run(Instant.parse("2026-07-25T00:00:00Z"));
            operationResults.put(key, copy(created));
            return new AppFacadeOperationService.Replay<>(created, false);
        }).when(facadeOperationService).execute(anyString(), anyString(), anyString(),
                any(), eq(AppProductReviewView.class), any());
    }

    private void mockReviewStorage() {
        when(reviewMapper.selectByOrderItem(anyLong(), anyString())).thenAnswer(invocation ->
                reviewsByOrderItem.get(invocation.getArgument(1)));
        when(reviewMapper.selectById(anyLong(), anyString())).thenAnswer(invocation ->
                reviewsById.get(invocation.getArgument(1)));
        when(reviewMapper.insert(any(AppProductReviewDO.class))).thenAnswer(invocation -> {
            AppProductReviewDO row = invocation.getArgument(0, AppProductReviewDO.class);
            reviewsById.put(row.getReviewId(), row);
            reviewsByOrderItem.put(row.getOrderItemId(), row);
            return 1;
        });
        when(reviewMapper.countApprovedByListing(anyLong(), anyString())).thenAnswer(invocation ->
                reviewsById.values().stream()
                        .filter(row -> Objects.equals(row.getListingId(), invocation.getArgument(1))
                                && "APPROVED".equals(row.getModerationStatus()))
                        .count());
        when(reviewMapper.countApprovedByProduct(anyLong(), anyString())).thenAnswer(invocation ->
                reviewsById.values().stream()
                        .filter(row -> Objects.equals(row.getCanonicalSpuId(), invocation.getArgument(1))
                                && "APPROVED".equals(row.getModerationStatus()))
                        .count());
        when(reviewMapper.selectApprovedByListing(anyLong(), anyString(), anyLong(), anyInt())).thenAnswer(invocation ->
                reviewsById.values().stream()
                        .filter(row -> Objects.equals(row.getListingId(), invocation.getArgument(1))
                                && "APPROVED".equals(row.getModerationStatus()))
                        .sorted(Comparator.comparing(AppProductReviewDO::getApprovedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(AppProductReviewDO::getCreatedAt, Comparator.reverseOrder()))
                        .toList());
        when(reviewMapper.selectApprovedByProduct(anyLong(), anyString(), anyLong(), anyInt())).thenAnswer(invocation ->
                reviewsById.values().stream()
                        .filter(row -> Objects.equals(row.getCanonicalSpuId(), invocation.getArgument(1))
                                && "APPROVED".equals(row.getModerationStatus()))
                        .sorted(Comparator.comparing(AppProductReviewDO::getApprovedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(AppProductReviewDO::getCreatedAt, Comparator.reverseOrder()))
                        .toList());
    }

    private static AppOrderView order(String orderId, String fulfillmentStatus, String paymentStatus) {
        return AppOrderView.builder()
                .orderId(orderId)
                .orderNo("CMO-1")
                .buyerPrincipalId("principal-member-1")
                .status("SHIPPED")
                .paymentId("payment-1")
                .paymentStatus(paymentStatus)
                .fulfillmentId("ful-1")
                .fulfillmentStatus(fulfillmentStatus)
                .items(List.of(OrderLineView.builder()
                        .orderItemId("item-1")
                        .listingId("listing-1")
                        .listingOfferId("offer-1")
                        .listingRevision(2)
                        .listingVersion(6L)
                        .canonicalSkuId("sku-1")
                        .shopId("shop-1")
                        .build()))
                .build();
    }

    private static AppProductReviewListingSnapshotDO snapshot() {
        AppProductReviewListingSnapshotDO snapshot = new AppProductReviewListingSnapshotDO();
        snapshot.setListingId("listing-1");
        snapshot.setListingNo("LIST-1");
        snapshot.setMerchantId("merchant-1");
        snapshot.setShopId("shop-1");
        snapshot.setCanonicalSpuId("spu-1");
        snapshot.setTitle("Wool Coat");
        snapshot.setPrimaryImageUrl("https://img.example.com/1.jpg");
        snapshot.setListingOfferId("offer-1");
        snapshot.setCanonicalSkuId("sku-1");
        return snapshot;
    }

    private static AppProductReviewDO storedReview(String reviewId, String orderItemId, String moderationStatus,
                                                   LocalDateTime approvedAt, String body) {
        AppProductReviewDO row = new AppProductReviewDO();
        row.setReviewId(reviewId);
        row.setTenantId(11L);
        row.setBuyerPrincipalId("principal-member-1");
        row.setOrderId("order-1");
        row.setOrderItemId(orderItemId);
        row.setPaymentId("payment-1");
        row.setFulfillmentId("ful-1");
        row.setListingId("listing-1");
        row.setListingOfferId("offer-1");
        row.setMerchantId("merchant-1");
        row.setShopId("shop-1");
        row.setCanonicalSpuId("spu-1");
        row.setCanonicalSkuId("sku-1");
        row.setProductScore(4);
        row.setServiceScore(4);
        row.setLogisticsScore(4);
        row.setOverallScore(4);
        row.setContentKeyId("review-key-1");
        row.setContentIv(new byte[12]);
        row.setContentCiphertext(("cipher:" + body).getBytes(StandardCharsets.UTF_8));
        row.setContentDigestSha256(DigestUtil.sha256Hex(body));
        row.setPublicSummary(body);
        row.setModerationStatus(moderationStatus);
        row.setModerationPolicy("policy");
        row.setApprovedAt(approvedAt);
        row.setCreatedAt(LocalDateTime.ofInstant(Instant.parse("2026-07-25T00:00:00Z"), ZoneOffset.UTC));
        row.setUpdatedAt(row.getCreatedAt());
        return row;
    }

    private static AppProductReviewView copy(AppProductReviewView source) {
        return AppProductReviewView.builder()
                .reviewId(source.getReviewId())
                .orderId(source.getOrderId())
                .orderItemId(source.getOrderItemId())
                .listingId(source.getListingId())
                .listingOfferId(source.getListingOfferId())
                .merchantId(source.getMerchantId())
                .shopId(source.getShopId())
                .canonicalSpuId(source.getCanonicalSpuId())
                .canonicalSkuId(source.getCanonicalSkuId())
                .productScore(source.getProductScore())
                .serviceScore(source.getServiceScore())
                .logisticsScore(source.getLogisticsScore())
                .overallScore(source.getOverallScore())
                .body(source.getBody())
                .publicSummary(source.getPublicSummary())
                .moderationStatus(source.getModerationStatus())
                .moderationPolicy(source.getModerationPolicy())
                .duplicate(source.getDuplicate())
                .createdAt(source.getCreatedAt())
                .approvedAt(source.getApprovedAt())
                .build();
    }
}
