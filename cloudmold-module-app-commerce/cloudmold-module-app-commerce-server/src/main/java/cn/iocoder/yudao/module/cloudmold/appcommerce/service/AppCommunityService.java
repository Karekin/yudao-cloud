package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementCommandApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppCommunityService {

    private static final String SOURCE_SYSTEM = "YSHOPPING_UNIAPP";
    private static final String SOURCE_TYPE = "MOBILE_COMMUNITY_POST";

    private final AppMemberPrincipalResolver principalResolver;
    private final AppProductReadService productReadService;
    private final AppAddressVaultCrypto crypto;
    private final EngagementCommandApi commandApi;
    private final EngagementQueryApi queryApi;

    @Value("${cloudmold.app-commerce.community-moderation-mode:DISABLED}")
    private String moderationMode;

    public AppCommunityView.Page feed(int pageNo, int pageSize) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        EngagementQueryApi.CommunityContentPageView page =
                queryApi.pagePublishedCommunityContent(pageNo, pageSize, principal.getPrincipalId());
        return AppCommunityView.Page.builder().list(page.getList().stream().map(this::mapContent).toList())
                .total(page.getTotal()).pageNo(page.getPageNo()).pageSize(page.getPageSize()).build();
    }

    public AppCommunityView detail(String contentId) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        return mapContent(queryApi.getPublishedCommunityContent(contentId, principal.getPrincipalId()));
    }

    public AppCommunityView.CommentPage comments(String contentId, int pageNo, int pageSize) {
        principalResolver.requireCurrent();
        EngagementQueryApi.CommunityCommentPageView page =
                queryApi.pageCommunityComments(contentId, pageNo, pageSize);
        return AppCommunityView.CommentPage.builder().list(page.getList().stream().map(this::mapComment).toList())
                .total(page.getTotal()).pageNo(page.getPageNo()).pageSize(page.getPageSize()).build();
    }

    public AppCommunityView createPost(String idempotencyKey, String body, String listingId,
                                       String listingOfferId, String canonicalSpuId, String canonicalSkuId) {
        require("LOCAL_TEST".equalsIgnoreCase(moderationMode),
                "App community publishing requires the LOCAL_TEST deterministic moderation mode");
        requireText(idempotencyKey, "idempotencyKey", 96);
        requireText(body, "body", 2000);
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        AppCommunityView.ProductLink link = productLink(listingId, listingOfferId, canonicalSpuId, canonicalSkuId);
        require(link.isNavigable(), "community post product link is not currently sellable");
        String contentId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.now();
        byte[] plaintext = body.getBytes(StandardCharsets.UTF_8);
        String aad = contentAad(principal.getPrincipalId(), contentId);
        AppAddressVaultCrypto.Encrypted encrypted = crypto.encrypt(aad, plaintext);
        commandApi.createCommunityContent(EngagementCommandApi.CreateCommunityContentCommand.builder()
                .idempotencyKey(idempotencyKey + ":create").runId(contentId).contentId(contentId)
                .authorPrincipalId(principal.getPrincipalId()).contentType("POST")
                .bodyRef("vault://community/content/" + contentId).bodyKeyId(encrypted.keyId())
                .bodyIv(encrypted.initializationVector()).bodyCiphertext(encrypted.ciphertext())
                .bodyDigestSha256(DigestUtil.sha256Hex(plaintext))
                .canonicalSpuId(canonicalSpuId).canonicalSkuId(canonicalSkuId)
                .listingId(listingId).listingOfferId(listingOfferId).desiredStatus("DRAFT")
                .sourceSystem(SOURCE_SYSTEM).sourceType(SOURCE_TYPE).sourceId(contentId)
                .correlationId(correlationId).occurredAt(occurredAt).build());
        commandApi.transitionCommunityContent(transition(idempotencyKey + ":submit", contentId,
                principal.getPrincipalId(), "PENDING_MODERATION", 1L, "APP_SUBMITTED",
                correlationId, occurredAt));
        commandApi.transitionCommunityContent(transition(idempotencyKey + ":approve", contentId,
                principal.getPrincipalId(), "PUBLISHED", 2L, "LOCAL_TEST_POLICY_APPROVED",
                correlationId, occurredAt));
        return mapContent(queryApi.getPublishedCommunityContent(contentId, principal.getPrincipalId()));
    }

    public AppCommunityView addComment(String idempotencyKey, String contentId, String body) {
        requireText(idempotencyKey, "idempotencyKey", 96);
        requireText(body, "body", 1000);
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        String interactionId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        byte[] plaintext = body.getBytes(StandardCharsets.UTF_8);
        AppAddressVaultCrypto.Encrypted encrypted =
                crypto.encrypt(commentAad(principal.getPrincipalId(), interactionId), plaintext);
        commandApi.recordCommunityInteraction(EngagementCommandApi.RecordCommunityInteractionCommand.builder()
                .idempotencyKey(idempotencyKey).runId(contentId).interactionId(interactionId)
                .actorPrincipalId(principal.getPrincipalId()).interactionType("COMMENT")
                .targetType("CONTENT").targetId(contentId)
                .payloadRef("vault://community/comment/" + interactionId)
                .payloadKeyId(encrypted.keyId()).payloadIv(encrypted.initializationVector())
                .payloadCiphertext(encrypted.ciphertext()).payloadDigestSha256(DigestUtil.sha256Hex(plaintext))
                .correlationId(correlationId).occurredAt(Instant.now()).build());
        return mapContent(queryApi.getPublishedCommunityContent(contentId, principal.getPrincipalId()));
    }

    public AppCommunityView setLiked(String idempotencyKey, String contentId, boolean liked) {
        requireText(idempotencyKey, "idempotencyKey", 96);
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        EngagementQueryApi.CommunityContentView current =
                queryApi.getPublishedCommunityContent(contentId, principal.getPrincipalId());
        if (current.isLikedByCurrentUser() == liked) {
            return mapContent(current);
        }
        commandApi.changeCommunityReaction(EngagementCommandApi.ChangeCommunityReactionCommand.builder()
                .idempotencyKey(idempotencyKey).runId(contentId)
                .reactionId(liked ? UUID.randomUUID().toString() : current.getCurrentUserReactionId())
                .actorPrincipalId(principal.getPrincipalId()).reactionType("LIKE")
                .targetType("CONTENT").targetId(contentId).desiredStatus(liked ? "ACTIVE" : "REMOVED")
                .expectedVersion(liked ? null : current.getCurrentUserReactionVersion())
                .correlationId(UUID.randomUUID().toString()).occurredAt(Instant.now()).build());
        return mapContent(queryApi.getPublishedCommunityContent(contentId, principal.getPrincipalId()));
    }

    private AppCommunityView mapContent(EngagementQueryApi.CommunityContentView content) {
        byte[] plaintext = crypto.decrypt(contentAad(content.getAuthorPrincipalId(), content.getContentId()),
                content.getBodyKeyId(), content.getBodyIv(), content.getBodyCiphertext());
        String digest = DigestUtil.sha256Hex(plaintext);
        require(Objects.equals(digest, content.getBodyDigestSha256()), "community content digest mismatch");
        return AppCommunityView.builder().contentId(content.getContentId())
                .authorPrincipalId(content.getAuthorPrincipalId())
                .body(new String(plaintext, StandardCharsets.UTF_8))
                .likeCount(content.getLikeCount()).commentCount(content.getCommentCount())
                .likedByCurrentUser(content.isLikedByCurrentUser()).createdAt(content.getCreatedAt())
                .productLink(productLink(content.getListingId(), content.getListingOfferId(),
                        content.getCanonicalSpuId(), content.getCanonicalSkuId())).build();
    }

    private AppCommunityView.Comment mapComment(EngagementQueryApi.CommunityCommentView comment) {
        byte[] plaintext = crypto.decrypt(commentAad(comment.getActorPrincipalId(), comment.getInteractionId()),
                comment.getPayloadKeyId(), comment.getPayloadIv(), comment.getPayloadCiphertext());
        require(Objects.equals(DigestUtil.sha256Hex(plaintext), comment.getPayloadDigestSha256()),
                "community comment digest mismatch");
        return AppCommunityView.Comment.builder().interactionId(comment.getInteractionId())
                .actorPrincipalId(comment.getActorPrincipalId())
                .body(new String(plaintext, StandardCharsets.UTF_8)).occurredAt(comment.getOccurredAt()).build();
    }

    private AppCommunityView.ProductLink productLink(String listingId, String listingOfferId,
                                                      String canonicalSpuId, String canonicalSkuId) {
        try {
            AppProductView product = productReadService.detail(listingId);
            if (!Objects.equals(product.getCanonicalSpuId(), canonicalSpuId)) {
                return unavailableLink(listingId, listingOfferId, canonicalSpuId, canonicalSkuId,
                        "CANONICAL_PRODUCT_CHANGED");
            }
            AppProductView.Sku sku = product.getSkus().stream()
                    .filter(item -> Objects.equals(item.getListingOfferId(), listingOfferId)
                            && Objects.equals(item.getCanonicalSkuId(), canonicalSkuId))
                    .findFirst().orElse(null);
            if (sku == null || !Boolean.TRUE.equals(sku.getEnabled()) || !Boolean.TRUE.equals(sku.getInStock())
                    || !"VERIFIED".equals(sku.getQualityStatus())) {
                return unavailableLink(listingId, listingOfferId, canonicalSpuId, canonicalSkuId,
                        "PRODUCT_LINK_NOT_SELLABLE");
            }
            return AppCommunityView.ProductLink.builder().navigable(true).listingId(listingId)
                    .listingOfferId(listingOfferId).canonicalSpuId(canonicalSpuId)
                    .canonicalSkuId(canonicalSkuId).title(product.getTitle())
                    .primaryImageUrl(product.getPrimaryImageUrl()).build();
        } catch (RuntimeException unavailable) {
            return unavailableLink(listingId, listingOfferId, canonicalSpuId, canonicalSkuId,
                    "PRODUCT_LINK_UNAVAILABLE");
        }
    }

    private static AppCommunityView.ProductLink unavailableLink(String listingId, String listingOfferId,
                                                                 String canonicalSpuId, String canonicalSkuId,
                                                                 String reason) {
        return AppCommunityView.ProductLink.builder().navigable(false).unavailableReason(reason)
                .listingId(listingId).listingOfferId(listingOfferId).canonicalSpuId(canonicalSpuId)
                .canonicalSkuId(canonicalSkuId).build();
    }

    private static EngagementCommandApi.TransitionCommunityContentCommand transition(
            String key, String contentId, String actor, String status, long version, String reason,
            String correlationId, Instant occurredAt) {
        return EngagementCommandApi.TransitionCommunityContentCommand.builder()
                .idempotencyKey(key).runId(contentId).contentId(contentId).actorPrincipalId(actor)
                .desiredStatus(status).expectedVersion(version).reasonCode(reason)
                .correlationId(correlationId).occurredAt(occurredAt).build();
    }

    private static String contentAad(String principalId, String contentId) {
        return "community-content|" + TenantContextHolder.getRequiredTenantId() + "|" + principalId + "|" + contentId;
    }

    private static String commentAad(String principalId, String interactionId) {
        return "community-comment|" + TenantContextHolder.getRequiredTenantId() + "|" + principalId + "|" + interactionId;
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
