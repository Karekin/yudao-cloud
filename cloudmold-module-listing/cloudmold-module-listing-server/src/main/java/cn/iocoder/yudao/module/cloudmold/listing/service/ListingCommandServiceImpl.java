package cn.iocoder.yudao.module.cloudmold.listing.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ListingCommandServiceImpl implements ListingCommandApi, ListingQueryApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final String CURRENCY_CNY = "CNY";
    private static final Set<String> FIRST_SLICE_CHANNELS = Set.of(
            "INTERNAL_COMPANY", "YSHOPPING_INTERNAL", "YSHOPPING");

    private final ListingOperationMapper operationMapper;
    private final ListingHeaderMapper headerMapper;
    private final ListingOfferMapper offerMapper;
    private final ListingStatusHistoryMapper historyMapper;
    private final ListingReviewDecisionMapper reviewMapper;
    private final CatalogSkuProjectionApi catalogSkuProjectionApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ListingCommandResult execute(ListingCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String requestHash = fingerprint(tenantId, command);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve listing operation");
        ListingOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "listing operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different listing payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing listing operation is not complete");
            ListingCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), ListingCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        ListingCommandResult result = command.getOperation() == ListingOperation.CREATE_DRAFT
                ? createDraft(tenantId, operationId, command, now)
                : transition(tenantId, operationId, command, now);
        require(operationMapper.markSucceeded(operationId, tenantId, result.getListingId(),
                JsonUtils.toJsonString(result), now) == 1, "listing operation completion conflict");
        return result;
    }

    @Override
    public PublishedListingOfferView requirePublishedOffer(PublishedOfferValidationCommand command) {
        require(command != null, "published offer validation command is required");
        requireText(command.getListingId(), "listingId", 36);
        requireText(command.getListingOfferId(), "listingOfferId", 36);
        requireText(command.getCanonicalSkuId(), "canonicalSkuId", 36);
        require(command.getExpectedPriceMinor() != null && command.getExpectedPriceMinor() >= 0,
                "expectedPriceMinor must be nonnegative");
        require(CURRENCY_CNY.equals(command.getCurrencyCode()), "first slice supports CNY only");
        PublishedListingOfferView offer = headerMapper.selectPublishedOffer(
                TenantContextHolder.getRequiredTenantId(), command.getListingId(), command.getListingOfferId(),
                command.getCanonicalSkuId());
        require(offer != null, "listing offer is not currently published");
        require(Objects.equals(offer.getPriceMinor(), command.getExpectedPriceMinor()),
                "order price does not match published listing offer");
        require(Objects.equals(offer.getCurrencyCode(), command.getCurrencyCode()),
                "order currency does not match published listing offer");
        return offer;
    }

    private ListingCommandResult createDraft(Long tenantId, Long operationId, ListingCommand command,
                                             LocalDateTime now) {
        validateCreate(command);
        List<ListingOfferDO> offers = buildOffers(tenantId, null, 1, command.getCanonicalSpuId(),
                command.getOffers(), now);
        String listingId = UUID.randomUUID().toString();
        String listingNo = "CML" + listingId.replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        offers.forEach(offer -> offer.setListingId(listingId));
        ListingHeaderDO header = new ListingHeaderDO().setListingId(listingId).setTenantId(tenantId)
                .setListingNo(listingNo).setRunId(command.getRunId()).setMerchantId(command.getMerchantId())
                .setChannelCode(command.getChannelCode()).setShopId(command.getShopId())
                .setCanonicalSpuId(command.getCanonicalSpuId()).setRevision(1).setTitle(command.getTitle())
                .setPrimaryImageUrl(command.getPrimaryImageUrl()).setCategoryRef(command.getCategoryRef())
                .setBrandRef(command.getBrandRef()).setSourceSystem(command.getSourceSystem())
                .setPublisherRef(command.getPublisherRef()).setCurrencyCode(CURRENCY_CNY)
                .setPublishStartAt(toUtc(command.getPublishStartAt())).setPublishEndAt(toUtc(command.getPublishEndAt()))
                .setStatus("DRAFT").setCompletionPassed(false).setBusinessApproved(false).setRiskApproved(false)
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        headerMapper.insert(header);
        offers.forEach(offerMapper::insert);
        appendHistory(tenantId, operationId, header, null, command, now);
        appendStatusEvent(tenantId, header, offers, null, command);
        return result(operationId, header, offers, null, false);
    }

    private ListingCommandResult transition(Long tenantId, Long operationId, ListingCommand command,
                                            LocalDateTime now) {
        requireText(command.getListingId(), "listingId", 36);
        require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                "expectedVersion must be positive");
        ListingHeaderDO header = headerMapper.selectForUpdate(tenantId, command.getListingId());
        require(header != null, "canonical listing does not exist");
        require(Objects.equals(header.getVersion(), command.getExpectedVersion()), "canonical listing version conflict");
        Transition next = resolveTransition(command, header);
        if ("REJECTED".equals(next.reviewDecision())) requireText(command.getReason(), "reason", 256);
        List<ListingOfferDO> offers;
        if (command.getOperation() == ListingOperation.REVISE) {
            List<ListingOfferCommand> revisedOffers = command.getOffers();
            if (revisedOffers == null || revisedOffers.isEmpty()) {
                revisedOffers = offerMapper.selectByListingRevision(tenantId, header.getListingId(), header.getRevision())
                        .stream().map(ListingCommandServiceImpl::toCommand).toList();
            }
            offers = buildOffers(tenantId, header.getListingId(), next.revision(), header.getCanonicalSpuId(),
                    revisedOffers, now);
        } else {
            require(command.getOffers() == null || command.getOffers().isEmpty(),
                    command.getOperation() + " does not accept offers");
            offers = offerMapper.selectByListingRevision(tenantId, header.getListingId(), header.getRevision());
        }
        require(!offers.isEmpty(), "canonical listing revision has no offers");
        String previous = header.getStatus();
        require(headerMapper.transition(tenantId, header.getListingId(), header.getVersion(), previous,
                next.status(), next.revision(), next.completionPassed(), next.businessApproved(),
                next.riskApproved(), command.getPublisherRef(), now) == 1, "canonical listing transition conflict");
        if (command.getOperation() == ListingOperation.REVISE) {
            offers.forEach(offerMapper::insert);
        }
        header.setStatus(next.status()).setRevision(next.revision()).setCompletionPassed(next.completionPassed())
                .setBusinessApproved(next.businessApproved()).setRiskApproved(next.riskApproved())
                .setVersion(header.getVersion() + 1).setUpdatedAt(now);
        if (command.getPublisherRef() != null) header.setPublisherRef(command.getPublisherRef());
        appendHistory(tenantId, operationId, header, previous, command, now);
        if (next.reviewStage() != null) {
            appendReviewDecision(tenantId, operationId, header, command, next.reviewStage(), next.reviewDecision(), now);
        }
        appendStatusEvent(tenantId, header, offers, previous, command);
        return result(operationId, header, offers, previous, false);
    }

    private Transition resolveTransition(ListingCommand command, ListingHeaderDO header) {
        String status = header.getStatus();
        int revision = header.getRevision();
        boolean completion = Boolean.TRUE.equals(header.getCompletionPassed());
        boolean business = Boolean.TRUE.equals(header.getBusinessApproved());
        boolean risk = Boolean.TRUE.equals(header.getRiskApproved());
        return switch (command.getOperation()) {
            case SUBMIT -> {
                require("DRAFT".equals(status), "SUBMIT requires DRAFT");
                yield new Transition("SUBMITTED", revision, false, false, false, null, null);
            }
            case PASS_COMPLETION -> {
                require("SUBMITTED".equals(status), "PASS_COMPLETION requires SUBMITTED");
                yield new Transition("COMPLETION_PASSED", revision, true, false, false, "COMPLETION", "PASSED");
            }
            case APPROVE_BUSINESS -> {
                require("COMPLETION_PASSED".equals(status), "APPROVE_BUSINESS requires COMPLETION_PASSED");
                yield new Transition("BUSINESS_APPROVED", revision, completion, true, false, "BUSINESS", "PASSED");
            }
            case APPROVE_RISK -> {
                require("BUSINESS_APPROVED".equals(status), "APPROVE_RISK requires BUSINESS_APPROVED");
                yield new Transition("RISK_APPROVED", revision, completion, business, true, "RISK", "PASSED");
            }
            case REJECT_COMPLETION -> reject(status, "SUBMITTED", revision, "COMPLETION");
            case REJECT_BUSINESS -> reject(status, "COMPLETION_PASSED", revision, "BUSINESS");
            case REJECT_RISK -> reject(status, "BUSINESS_APPROVED", revision, "RISK");
            case REVISE -> {
                require("REJECTED".equals(status), "REVISE requires REJECTED");
                yield new Transition("DRAFT", Math.addExact(revision, 1), false, false, false, null, null);
            }
            case PUBLISH -> {
                require("RISK_APPROVED".equals(status) || "UNPUBLISHED".equals(status) || "SUSPENDED".equals(status),
                        "PUBLISH requires RISK_APPROVED, UNPUBLISHED, or SUSPENDED");
                require(completion && business && risk, "PUBLISH requires all three approvals");
                requireText(command.getPublisherRef() != null ? command.getPublisherRef() : header.getPublisherRef(),
                        "publisherRef", 128);
                yield new Transition("PUBLISHED", revision, true, true, true, null, null);
            }
            case UNPUBLISH -> {
                require("PUBLISHED".equals(status), "UNPUBLISH requires PUBLISHED");
                yield new Transition("UNPUBLISHED", revision, completion, business, risk, null, null);
            }
            case SUSPEND -> {
                require("PUBLISHED".equals(status), "SUSPEND requires PUBLISHED");
                requireText(command.getReason(), "reason", 256);
                yield new Transition("SUSPENDED", revision, completion, business, risk, null, null);
            }
            case ARCHIVE -> {
                require(Set.of("DRAFT", "REJECTED", "UNPUBLISHED", "SUSPENDED").contains(status),
                        "ARCHIVE requires DRAFT, REJECTED, UNPUBLISHED, or SUSPENDED");
                yield new Transition("ARCHIVED", revision, completion, business, risk, null, null);
            }
            case CREATE_DRAFT -> throw new IllegalArgumentException("CREATE_DRAFT is not a transition");
        };
    }

    private static Transition reject(String actualStatus, String expectedStatus, int revision, String stage) {
        require(expectedStatus.equals(actualStatus), "REJECT_" + stage + " requires " + expectedStatus);
        return new Transition("REJECTED", revision, false, false, false, stage, "REJECTED");
    }

    private List<ListingOfferDO> buildOffers(Long tenantId, String listingId, int revision, String spuId,
                                             List<ListingOfferCommand> commands, LocalDateTime now) {
        require(commands != null && !commands.isEmpty() && commands.size() <= 500,
                "listing requires 1 to 500 offers");
        Set<String> skuIds = new HashSet<>();
        Set<String> externalIds = new HashSet<>();
        List<ListingOfferDO> offers = new ArrayList<>();
        for (ListingOfferCommand command : commands) {
            require(command != null, "listing offer is required");
            requireText(command.getCanonicalSkuId(), "canonicalSkuId", 36);
            require(skuIds.add(command.getCanonicalSkuId()), "duplicate canonical SKU in one listing revision");
            require(command.getPriceMinor() != null && command.getPriceMinor() >= 0,
                    "priceMinor must be nonnegative");
            require(CURRENCY_CNY.equals(command.getCurrencyCode()), "first slice supports CNY only");
            require(command.getEnabled() == null || command.getEnabled(), "first slice only accepts enabled offers");
            if (command.getExternalOfferId() != null) {
                requireText(command.getExternalOfferId(), "externalOfferId", 128);
                require(externalIds.add(command.getExternalOfferId()), "duplicate external offer id");
            }
            CatalogSkuProjectionView sku = catalogSkuProjectionApi.getActiveSku(command.getCanonicalSkuId());
            require(Objects.equals(spuId, sku.getCanonicalSpuId()),
                    "every listing offer must belong to the listing canonical SPU");
            offers.add(new ListingOfferDO().setListingOfferId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setListingId(listingId).setRevision(revision).setCanonicalSkuId(command.getCanonicalSkuId())
                    .setPriceMinor(command.getPriceMinor()).setCurrencyCode(CURRENCY_CNY).setEnabled(true)
                    .setExternalOfferId(command.getExternalOfferId()).setCreatedAt(now).setUpdatedAt(now));
        }
        return offers;
    }

    private void appendHistory(Long tenantId, Long operationId, ListingHeaderDO header, String previous,
                               ListingCommand command, LocalDateTime now) {
        historyMapper.insert(new ListingStatusHistoryDO().setTenantId(tenantId).setListingId(header.getListingId())
                .setRevision(header.getRevision()).setAggregateVersion(header.getVersion()).setPreviousStatus(previous)
                .setCurrentStatus(header.getStatus()).setOperationId(operationId).setReason(command.getReason())
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC)).setCreatedAt(now));
    }

    private void appendReviewDecision(Long tenantId, Long operationId, ListingHeaderDO header,
                                      ListingCommand command, String stage, String decision, LocalDateTime now) {
        if ("REJECTED".equals(decision)) requireText(command.getReason(), "reason", 256);
        reviewMapper.insert(new ListingReviewDecisionDO().setTenantId(tenantId).setListingId(header.getListingId())
                .setRevision(header.getRevision()).setStage(stage).setAttemptNo(header.getRevision())
                .setDecision(decision).setReason(command.getReason()).setOperationId(operationId)
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC)).setCreatedAt(now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId()); payload.put("listing_id", header.getListingId());
        payload.put("listing_no", header.getListingNo()); payload.put("revision", header.getRevision());
        payload.put("stage", stage); payload.put("decision", decision); payload.put("reason", command.getReason());
        appendEvent(tenantId, header, command, "listing.review.decided", (short) 2, payload);
    }

    private void appendStatusEvent(Long tenantId, ListingHeaderDO header, List<ListingOfferDO> offers,
                                   String previous, ListingCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId()); payload.put("listing_id", header.getListingId());
        payload.put("listing_no", header.getListingNo()); payload.put("merchant_id", header.getMerchantId());
        payload.put("channel_code", header.getChannelCode()); payload.put("shop_id", header.getShopId());
        payload.put("canonical_spu_id", header.getCanonicalSpuId()); payload.put("revision", header.getRevision());
        payload.put("previous_status", previous); payload.put("current_status", header.getStatus());
        payload.put("completion_passed", header.getCompletionPassed());
        payload.put("business_approved", header.getBusinessApproved());
        payload.put("risk_approved", header.getRiskApproved()); payload.put("reason", command.getReason());
        payload.put("offers", offers.stream().map(ListingCommandServiceImpl::eventOffer).toList());
        appendEvent(tenantId, header, command, "listing.status.changed", (short) 1, payload);
    }

    private void appendEvent(Long tenantId, ListingHeaderDO header, ListingCommand command,
                             String eventType, short eventSequence, Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(eventType).schemaVersion(1)
                .sourceSystem("cloudmold-listing").tenantId(tenantId).aggregateType("listing")
                .aggregateId(header.getListingId()).aggregateVersion(header.getVersion()).eventSequence(eventSequence)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(command.getIdempotencyKey())
                .payload(payload).headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse").build());
    }

    private static Map<String, Object> eventOffer(ListingOfferDO offer) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("listing_offer_id", offer.getListingOfferId()); value.put("canonical_sku_id", offer.getCanonicalSkuId());
        value.put("revision", offer.getRevision()); value.put("price_minor", offer.getPriceMinor());
        value.put("currency_code", offer.getCurrencyCode()); value.put("enabled", offer.getEnabled());
        value.put("external_offer_id", offer.getExternalOfferId());
        return value;
    }

    private static ListingCommandResult result(Long operationId, ListingHeaderDO header, List<ListingOfferDO> offers,
                                               String previous, boolean duplicate) {
        return ListingCommandResult.builder().operationId(operationId).listingId(header.getListingId())
                .listingNo(header.getListingNo()).previousStatus(previous).currentStatus(header.getStatus())
                .revision(header.getRevision()).aggregateVersion(header.getVersion())
                .completionPassed(header.getCompletionPassed()).businessApproved(header.getBusinessApproved())
                .riskApproved(header.getRiskApproved()).offers(offers.stream().map(ListingCommandServiceImpl::view).toList())
                .duplicate(duplicate).build();
    }

    private static ListingOfferView view(ListingOfferDO offer) {
        return ListingOfferView.builder().listingOfferId(offer.getListingOfferId())
                .canonicalSkuId(offer.getCanonicalSkuId()).revision(offer.getRevision())
                .priceMinor(offer.getPriceMinor()).currencyCode(offer.getCurrencyCode()).enabled(offer.getEnabled())
                .externalOfferId(offer.getExternalOfferId()).build();
    }

    private static ListingOfferCommand toCommand(ListingOfferDO offer) {
        return ListingOfferCommand.builder().canonicalSkuId(offer.getCanonicalSkuId())
                .priceMinor(offer.getPriceMinor()).currencyCode(offer.getCurrencyCode()).enabled(offer.getEnabled())
                .externalOfferId(offer.getExternalOfferId()).build();
    }

    private static void validateCommon(ListingCommand command) {
        require(command != null && command.getOperation() != null, "listing operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
    }

    private static void validateCreate(ListingCommand command) {
        require(command.getListingId() == null, "CREATE_DRAFT does not accept listingId");
        require(command.getExpectedVersion() == null, "CREATE_DRAFT does not accept expectedVersion");
        requireText(command.getMerchantId(), "merchantId", 128); requireText(command.getShopId(), "shopId", 128);
        requireText(command.getChannelCode(), "channelCode", 32);
        require(FIRST_SLICE_CHANNELS.contains(command.getChannelCode()), "channel is not enabled in the first slice");
        requireText(command.getCanonicalSpuId(), "canonicalSpuId", 36); requireText(command.getTitle(), "title", 255);
        requireText(command.getCategoryRef(), "categoryRef", 128); requireText(command.getBrandRef(), "brandRef", 128);
        requireText(command.getSourceSystem(), "sourceSystem", 64); requireText(command.getPublisherRef(), "publisherRef", 128);
        if (command.getPrimaryImageUrl() != null) requireText(command.getPrimaryImageUrl(), "primaryImageUrl", 1024);
        if (command.getPublishEndAt() != null && command.getPublishStartAt() != null) {
            require(command.getPublishEndAt().isAfter(command.getPublishStartAt()),
                    "publishEndAt must be after publishStartAt");
        }
    }

    private static String fingerprint(Long tenantId, ListingCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId); value.put("operation", command.getOperation());
        value.put("run_id", command.getRunId()); value.put("listing_id", command.getListingId());
        value.put("expected_version", command.getExpectedVersion()); value.put("merchant_id", command.getMerchantId());
        value.put("channel_code", command.getChannelCode()); value.put("shop_id", command.getShopId());
        value.put("canonical_spu_id", command.getCanonicalSpuId()); value.put("title", command.getTitle());
        value.put("primary_image_url", command.getPrimaryImageUrl()); value.put("category_ref", command.getCategoryRef());
        value.put("brand_ref", command.getBrandRef()); value.put("source_system", command.getSourceSystem());
        value.put("publisher_ref", command.getPublisherRef()); value.put("publish_start_at", command.getPublishStartAt());
        value.put("publish_end_at", command.getPublishEndAt()); value.put("offers", command.getOffers());
        value.put("reason", command.getReason()); value.put("occurred_at", command.getOccurredAt());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static LocalDateTime toUtc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try { UUID.fromString(value); } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Transition(String status, Integer revision, Boolean completionPassed, Boolean businessApproved,
                              Boolean riskApproved, String reviewStage, String reviewDecision) {}
}
