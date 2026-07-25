package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class AppCommunityView {
    String contentId;
    String authorPrincipalId;
    String body;
    long likeCount;
    long commentCount;
    boolean likedByCurrentUser;
    Instant createdAt;
    ProductLink productLink;

    @Value
    @Builder
    public static class ProductLink {
        boolean navigable;
        String unavailableReason;
        String listingId;
        String listingOfferId;
        String canonicalSpuId;
        String canonicalSkuId;
        String title;
        String primaryImageUrl;
    }

    @Value
    @Builder
    public static class Page {
        List<AppCommunityView> list;
        long total;
        int pageNo;
        int pageSize;
    }

    @Value
    @Builder
    public static class Comment {
        String interactionId;
        String actorPrincipalId;
        String body;
        Instant occurredAt;
    }

    @Value
    @Builder
    public static class CommentPage {
        List<Comment> list;
        long total;
        int pageNo;
        int pageSize;
    }
}
