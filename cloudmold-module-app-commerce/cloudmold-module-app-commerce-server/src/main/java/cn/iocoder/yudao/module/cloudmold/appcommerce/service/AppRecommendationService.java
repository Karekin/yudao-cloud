package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppRecommendationDecisionDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppRecommendationItemDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql.AppRecommendationMapper;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppRecommendationService {

    static final String POLICY_VERSION = "LOCAL_DETERMINISTIC_V1";
    private static final String SOURCE_SYSTEM = "cloudmold-app-commerce";
    private static final String DATA_CLASSIFICATION = "RESTRICTED_BEHAVIOR";

    private final AppProductReadService productReadService;
    private final AppRecommendationMapper recommendationMapper;
    private final CommerceBehaviorCommandApi commerceBehaviorCommandApi;
    private final OutboxAppender outboxAppender;

    @Value("${cloudmold.app-commerce.recommendation-ttl-seconds:900}")
    private int recommendationTtlSeconds;

    @Value("${cloudmold.app-commerce.channel-code:YSHOPPING}")
    private String appChannel;

    @Transactional(rollbackFor = Exception.class)
    public AppRecommendationDecisionView recommend(String sessionId, String sceneCode, int pageSize) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "tenant_id", tenantId,
                "session_id", sessionId,
                "scene_code", sceneCode,
                "page_size", pageSize,
                "policy_version", POLICY_VERSION)));
        AppRecommendationDecisionDO existing = recommendationMapper.selectActiveDecision(
                tenantId, sessionId, sceneCode, requestHash, now);
        if (existing != null) {
            return toView(existing, recommendationMapper.selectItems(tenantId, existing.getDecisionId()));
        }

        List<RecommendationCandidate> candidates = buildCandidates(pageSize);
        String decisionId = UUID.nameUUIDFromBytes(("recommendation:" + requestHash).getBytes(StandardCharsets.UTF_8))
                .toString();
        String token = "recommend:" + requestHash.substring(0, 24);
        LocalDateTime expiresAt = now.plusSeconds(Math.max(60, recommendationTtlSeconds));
        AppRecommendationDecisionDO decision = new AppRecommendationDecisionDO();
        decision.setDecisionId(decisionId);
        decision.setTenantId(tenantId);
        decision.setSessionId(sessionId);
        decision.setSceneCode(sceneCode);
        decision.setRequestHash(requestHash);
        decision.setDecisionToken(token);
        decision.setResultSetToken(token);
        decision.setPolicyVersion(POLICY_VERSION);
        decision.setStatus("ACTIVE");
        decision.setTtlSeconds(Math.max(60, recommendationTtlSeconds));
        decision.setItemCount(candidates.size());
        decision.setGeneratedAt(now);
        decision.setExpiresAt(expiresAt);
        decision.setCreatedAt(now);
        decision.setUpdatedAt(now);
        require(recommendationMapper.insertDecision(decision) == 1, "failed to persist recommendation decision");

        List<AppRecommendationItemDO> items = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            RecommendationCandidate candidate = candidates.get(i);
            AppRecommendationItemDO item = new AppRecommendationItemDO();
            item.setTenantId(tenantId);
            item.setDecisionId(decisionId);
            item.setRankNo(i + 1);
            item.setReasonCode(candidate.reasonCode());
            item.setListingId(candidate.product().getListingId());
            item.setListingNo(candidate.product().getListingNo());
            item.setListingTitle(candidate.product().getTitle());
            item.setPrimaryImageUrl(candidate.product().getPrimaryImageUrl());
            item.setListingOfferId(candidate.sku().getListingOfferId());
            item.setMerchantId(candidate.product().getMerchantId());
            item.setShopId(candidate.product().getShopId());
            item.setChannelCode(appChannel);
            item.setCanonicalSpuId(candidate.product().getCanonicalSpuId());
            item.setCanonicalSkuId(candidate.sku().getCanonicalSkuId());
            item.setPriceMinor(candidate.sku().getPriceMinor());
            item.setCurrencyCode(candidate.sku().getCurrencyCode());
            item.setInventoryVersion(candidate.sku().getInventoryVersion());
            item.setAvailableQuantity(candidate.sku().getAvailableQuantity());
            item.setQualityStatus(candidate.sku().getQualityStatus());
            item.setCreatedAt(now);
            require(recommendationMapper.insertItem(item) == 1, "failed to persist recommendation item");
            items.add(item);
        }

        appendDecisionEvent(decision, items, "app.recommendation.generated", (short) 1, sessionId, sceneCode);
        appendDecisionEvent(decision, items, "app.recommendation.served", (short) 2, sessionId, sceneCode);
        return toView(decision, items);
    }

    public CommerceBehaviorCommandApi.CommerceBehaviorCommandResult recordExposure(String idempotencyKey, String sessionId,
                                                                                   String decisionToken, String listingId,
                                                                                   int rank) {
        return recordRecommendationBehavior("RECOMMENDATION_EXPOSED", "RECOMMENDATION_EXPOSURE",
                idempotencyKey, sessionId, decisionToken, listingId, rank);
    }

    public CommerceBehaviorCommandApi.CommerceBehaviorCommandResult recordClick(String idempotencyKey, String sessionId,
                                                                                String decisionToken, String listingId,
                                                                                int rank) {
        return recordRecommendationBehavior("RECOMMENDATION_CLICKED", "RECOMMENDATION_CLICK",
                idempotencyKey, sessionId, decisionToken, listingId, rank);
    }

    private CommerceBehaviorCommandApi.CommerceBehaviorCommandResult recordRecommendationBehavior(
            String behaviorType, String sourceType, String idempotencyKey, String sessionId,
            String decisionToken, String listingId, int rank) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        AppRecommendationItemDO activeItem = recommendationMapper.selectActiveItem(
                tenantId, sessionId, decisionToken, listingId, rank, LocalDateTime.now(ZoneOffset.UTC));
        require(activeItem != null, "recommendation token, listing, rank, or TTL is invalid");
        String behaviorId = UUID.nameUUIDFromBytes(
                (behaviorType + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString();
        return commerceBehaviorCommandApi.recordBehavior(
                CommerceBehaviorCommandApi.RecordCommerceBehaviorCommand.builder()
                        .idempotencyKey(idempotencyKey)
                        .runId(sessionId)
                        .behaviorId(behaviorId)
                        .sessionId(sessionId)
                        .behaviorType(behaviorType)
                        .listingId(listingId)
                        .resultSetToken(decisionToken)
                        .resultPosition(rank)
                        .sourceSystem("YSHOPPING_UNIAPP")
                        .sourceType(sourceType)
                        .sourceId("recommendation-event:" + behaviorId)
                        .correlationId(sessionId)
                        .occurredAt(Instant.now())
                        .build());
    }

    private List<RecommendationCandidate> buildCandidates(int pageSize) {
        AppProductPageView page = productReadService.page(null, 1, Math.min(Math.max(pageSize * 4, pageSize), 50));
        return page.getList().stream()
                .map(this::toCandidate)
                .filter(candidate -> candidate != null)
                .sorted(this::compareCandidates)
                .limit(pageSize)
                .toList();
    }

    private RecommendationCandidate toCandidate(AppProductView product) {
        if (product == null || product.getSkus() == null) {
            return null;
        }
        AppProductView.Sku sku = product.getSkus().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled())
                        && Boolean.TRUE.equals(item.getInStock())
                        && item.getAvailableQuantity() != null
                        && item.getAvailableQuantity().signum() > 0)
                .findFirst()
                .orElse(null);
        if (sku == null) {
            return null;
        }
        String reasonCode = sku.getAvailableQuantity().compareTo(BigDecimal.valueOf(5)) >= 0
                ? "VERIFIED_IN_STOCK" : "VERIFIED_LOW_STOCK";
        return new RecommendationCandidate(product, sku, reasonCode);
    }

    private void appendDecisionEvent(AppRecommendationDecisionDO decision, List<AppRecommendationItemDO> items,
                                     String eventType, short sequence, String sessionId, String sceneCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", sessionId);
        payload.put("scene_code", sceneCode);
        payload.put("decision_id", decision.getDecisionId());
        payload.put("decision_token", decision.getDecisionToken());
        payload.put("policy_version", decision.getPolicyVersion());
        payload.put("ttl_seconds", decision.getTtlSeconds());
        payload.put("item_count", decision.getItemCount());
        payload.put("expires_at", decision.getExpiresAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("items", items.stream().map(item -> Map.of(
                "rank", item.getRankNo(),
                "reason_code", item.getReasonCode(),
                "listing_id", item.getListingId(),
                "listing_offer_id", item.getListingOfferId(),
                "canonical_spu_id", item.getCanonicalSpuId(),
                "canonical_sku_id", item.getCanonicalSkuId(),
                "price_minor", item.getPriceMinor(),
                "currency_code", item.getCurrencyCode(),
                "quality_status", item.getQualityStatus())).toList());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType(eventType)
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(decision.getTenantId())
                .aggregateType("app_recommendation_decision")
                .aggregateId(decision.getDecisionId())
                .aggregateVersion(1L)
                .eventSequence(sequence)
                .occurredAt(decision.getGeneratedAt().toInstant(ZoneOffset.UTC))
                .correlationId(sessionId)
                .causationId(decision.getDecisionId())
                .idempotencyKey(decision.getDecisionId() + ":" + eventType)
                .payload(payload)
                .headers(Map.of("pii_safe", false, "data_classification", DATA_CLASSIFICATION))
                .destination("lakehouse")
                .build());
    }

    private AppRecommendationDecisionView toView(AppRecommendationDecisionDO decision, List<AppRecommendationItemDO> items) {
        return AppRecommendationDecisionView.builder()
                .sceneCode(decision.getSceneCode())
                .policyVersion(decision.getPolicyVersion())
                .decisionToken(decision.getDecisionToken())
                .resultSetToken(decision.getResultSetToken())
                .ttlSeconds(decision.getTtlSeconds())
                .expiresAt(decision.getExpiresAt().toInstant(ZoneOffset.UTC))
                .items(items.stream().map(item -> AppRecommendationDecisionView.Item.builder()
                        .rank(item.getRankNo())
                        .reasonCode(item.getReasonCode())
                        .listingId(item.getListingId())
                        .listingNo(item.getListingNo())
                        .title(item.getListingTitle())
                        .primaryImageUrl(item.getPrimaryImageUrl())
                        .merchantId(item.getMerchantId())
                        .shopId(item.getShopId())
                        .canonicalSpuId(item.getCanonicalSpuId())
                        .canonicalSkuId(item.getCanonicalSkuId())
                        .listingOfferId(item.getListingOfferId())
                        .priceMinor(item.getPriceMinor())
                        .currencyCode(item.getCurrencyCode())
                        .inventoryVersion(item.getInventoryVersion())
                        .availableQuantity(item.getAvailableQuantity())
                        .qualityStatus(item.getQualityStatus())
                        .build()).toList())
                .build();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private int compareCandidates(RecommendationCandidate left, RecommendationCandidate right) {
        int available = compareBigDecimalDesc(left.sku().getAvailableQuantity(), right.sku().getAvailableQuantity());
        if (available != 0) {
            return available;
        }
        int price = Comparator.nullsLast(Long::compareTo).compare(left.sku().getPriceMinor(), right.sku().getPriceMinor());
        if (price != 0) {
            return price;
        }
        int listingVersion = Comparator.nullsLast(Long::compareTo)
                .compare(right.product().getListingVersion(), left.product().getListingVersion());
        if (listingVersion != 0) {
            return listingVersion;
        }
        return left.product().getListingId().compareTo(right.product().getListingId());
    }

    private static int compareBigDecimalDesc(BigDecimal left, BigDecimal right) {
        return Comparator.nullsLast(BigDecimal::compareTo).compare(right, left);
    }

    private record RecommendationCandidate(AppProductView product, AppProductView.Sku sku, String reasonCode) {
    }
}
