package cn.iocoder.yudao.module.cloudmold.commercebehavior.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.*;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingQueryApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingOfferView;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedOfferValidationCommand;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAttributionView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CommerceBehaviorCommandServiceImpl implements CommerceBehaviorCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-commerce-behavior";
    private static final String DATA_CLASSIFICATION = "RESTRICTED_BEHAVIOR";
    private static final Pattern TYPE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Pattern CONTROLLED_TOKEN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9:_-]{7,127}$");
    private static final long FUTURE_SKEW_SECONDS = 300L;
    private static final long ATTRIBUTION_WINDOW_HOURS = 24L;
    private static final Set<String> SESSION_STATUSES = Set.of("ACTIVE", "CHECKOUT_IN_PROGRESS", "ABANDONED", "EXPIRED");
    private static final Set<String> BEHAVIOR_TYPES = Set.of(
            "PDP_VIEWED", "SEARCH_REQUESTED", "SEARCH_RESULT_EXPOSED",
            "SEARCH_RESULT_CLICKED", "CART_ADDED", "CART_REMOVED", "CHECKOUT_STARTED", "CHECKOUT_ABANDONED");

    private final CommerceBehaviorOperationMapper operationMapper;
    private final CommerceSessionMapper sessionMapper;
    private final CommerceSessionIdentityLinkMapper identityLinkMapper;
    private final CommerceBehaviorEventMapper behaviorEventMapper;
    private final CommerceSessionPaymentAttributionMapper paymentAttributionMapper;
    private final PrincipalValidationApi principalValidationApi;
    private final ListingQueryApi listingQueryApi;
    private final OrderQueryApi orderQueryApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommerceBehaviorCommandResult startSession(StartCommerceSessionCommand command) {
        validateStart(command);
        return execute("START_SESSION", command.getIdempotencyKey(), fingerprint(command), context -> {
            requireNotMateriallyFuture(command.getOccurredAt(), context.now);
            CommerceSessionDO existing = sessionMapper.selectForUpdate(context.tenantId, command.getSessionId());
            require(existing == null, "commerce session already exists");
            LocalDateTime occurredAt = utc(command.getOccurredAt());
            CommerceSessionDO session = new CommerceSessionDO()
                    .setSessionId(command.getSessionId())
                    .setTenantId(context.tenantId)
                    .setPrincipalId(null)
                    .setChannelCode(command.getChannelCode())
                    .setEntrypointCode(command.getEntrypointCode())
                    .setStatus("ACTIVE")
                    .setVersion(1L)
                    .setIdentityLinkVersion(0L)
                    .setSourceSystem(command.getSourceSystem())
                    .setSourceType(command.getSourceType())
                    .setSourceId(command.getSourceId())
                    .setStartedAt(occurredAt)
                    .setLastActivityAt(occurredAt)
                    .setCreatedAt(context.now)
                    .setUpdatedAt(context.now);
            require(sessionMapper.insert(session) == 1, "failed to create commerce session");
            append(context.tenantId, "commerce.session.status_changed", command.getSessionId(), 1L, (short) 1,
                    command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                    command.getIdempotencyKey(), payload(
                            "run_id", command.getRunId(),
                            "session_id", session.getSessionId(),
                            "principal_id", null,
                            "previous_status", null,
                            "current_status", session.getStatus(),
                            "channel_code", session.getChannelCode(),
                            "entrypoint_code", session.getEntrypointCode(),
                            "started_at", command.getOccurredAt().toString(),
                            "last_activity_at", command.getOccurredAt().toString(),
                            "source_system", session.getSourceSystem(),
                            "source_type", session.getSourceType(),
                            "source_id", session.getSourceId()),
                    "START_SESSION");
            return result(context.operationId, session.getSessionId(), session.getStatus(), session.getVersion());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommerceBehaviorCommandResult linkSessionIdentity(LinkCommerceSessionIdentityCommand command) {
        validateLink(command);
        return execute("LINK_SESSION_IDENTITY", command.getIdempotencyKey(), fingerprint(command), context -> {
            principalValidationApi.requireActivePrincipal(command.getPrincipalId());
            CommerceSessionDO session = requireSession(context.tenantId, command.getSessionId());
            requireNotMateriallyFuture(command.getOccurredAt(), context.now);
            requireNotBeforeSessionActivity(command.getOccurredAt(), session);
            require(Objects.equals(session.getVersion(), command.getExpectedSessionVersion()), "session version conflict");
            require(session.getPrincipalId() == null || Objects.equals(session.getPrincipalId(), command.getPrincipalId()),
                    "session principal rebinding is not supported");
            require(!Objects.equals(session.getPrincipalId(), command.getPrincipalId()),
                    "session is already linked to principal");
            Long nextLinkVersion = session.getIdentityLinkVersion() + 1;
            LocalDateTime occurredAt = utc(command.getOccurredAt());
            CommerceSessionIdentityLinkDO link = new CommerceSessionIdentityLinkDO()
                    .setLinkId(UUID.randomUUID().toString())
                    .setTenantId(context.tenantId)
                    .setSessionId(session.getSessionId())
                    .setPrincipalId(command.getPrincipalId())
                    .setLinkVersion(nextLinkVersion)
                    .setSessionVersion(session.getVersion() + 1)
                    .setSourceSystem(command.getSourceSystem())
                    .setSourceType(command.getSourceType())
                    .setSourceId(command.getSourceId())
                    .setLinkedAt(occurredAt)
                    .setCreatedAt(context.now);
            require(identityLinkMapper.insert(link) == 1, "failed to persist session identity link");
            require(sessionMapper.advanceIdentity(context.tenantId, session.getSessionId(), session.getVersion(),
                    command.getPrincipalId(), session.getStatus(), nextLinkVersion, occurredAt, context.now) == 1,
                    "session identity transition conflict");
            append(context.tenantId, "commerce.session.identity_linked", session.getSessionId(), session.getVersion() + 1,
                    (short) 1, command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                    command.getIdempotencyKey(), payload(
                            "run_id", command.getRunId(),
                            "session_id", session.getSessionId(),
                            "previous_principal_id", session.getPrincipalId(),
                            "current_principal_id", command.getPrincipalId(),
                            "link_version", nextLinkVersion,
                            "session_status", session.getStatus(),
                            "linked_at", command.getOccurredAt().toString(),
                            "source_system", command.getSourceSystem(),
                            "source_type", command.getSourceType(),
                            "source_id", command.getSourceId()),
                    "LINK_SESSION_IDENTITY");
            return result(context.operationId, session.getSessionId(), session.getStatus(), session.getVersion() + 1);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommerceBehaviorCommandResult recordBehavior(RecordCommerceBehaviorCommand command) {
        validateBehavior(command);
        return execute("RECORD_BEHAVIOR", command.getIdempotencyKey(), fingerprint(command), context -> {
            CommerceSessionDO session = requireSession(context.tenantId, command.getSessionId());
            requireNotMateriallyFuture(command.getOccurredAt(), context.now);
            requireNotBeforeSessionActivity(command.getOccurredAt(), session);
            require(!Set.of("ABANDONED", "EXPIRED").contains(session.getStatus()),
                    "terminal session cannot accept more behavior");
            requireBehaviorTransition(session.getStatus(), command.getBehaviorType());
            if (command.getPrincipalId() != null) {
                principalValidationApi.requireActivePrincipal(command.getPrincipalId());
                require(Objects.equals(session.getPrincipalId(), command.getPrincipalId()),
                        "principal must be linked to session before recording behavior");
            }
            PublishedListingOfferView listingOffer = resolveListingOffer(command);
            if (command.getExpectedSessionVersion() != null) {
                require(Objects.equals(session.getVersion(), command.getExpectedSessionVersion()), "session version conflict");
            }
            String nextStatus = nextStatusForBehavior(command.getBehaviorType(), session.getStatus());
            LocalDateTime occurredAt = utc(command.getOccurredAt());
            Long eventSessionVersion = session.getVersion();
            short sequence = 1;
            if (!Objects.equals(nextStatus, session.getStatus())) {
                require(sessionMapper.advanceStatus(context.tenantId, session.getSessionId(), session.getVersion(),
                        nextStatus, occurredAt, context.now) == 1, "session status transition conflict");
                append(context.tenantId, "commerce.session.status_changed", session.getSessionId(), session.getVersion() + 1,
                        (short) 1, command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                        command.getIdempotencyKey(), payload(
                                "run_id", command.getRunId(),
                                "session_id", session.getSessionId(),
                                "principal_id", session.getPrincipalId(),
                                "previous_status", session.getStatus(),
                                "current_status", nextStatus,
                                "channel_code", session.getChannelCode(),
                                "entrypoint_code", session.getEntrypointCode(),
                                "last_activity_at", command.getOccurredAt().toString(),
                                "source_system", session.getSourceSystem(),
                                "source_type", session.getSourceType(),
                                "source_id", session.getSourceId()),
                        "RECORD_BEHAVIOR");
                eventSessionVersion = session.getVersion() + 1;
                sequence = 2;
            } else {
                require(sessionMapper.advanceActivity(context.tenantId, session.getSessionId(), session.getVersion(),
                        occurredAt, context.now) == 1, "session activity transition conflict");
                eventSessionVersion = session.getVersion() + 1;
            }
            CommerceBehaviorEventDO behavior = new CommerceBehaviorEventDO()
                    .setBehaviorId(command.getBehaviorId())
                    .setTenantId(context.tenantId)
                    .setSessionId(session.getSessionId())
                    .setSessionVersion(eventSessionVersion)
                    .setBehaviorType(command.getBehaviorType())
                    .setPrincipalId(session.getPrincipalId())
                    .setCanonicalSpuId(command.getCanonicalSpuId())
                    .setSkuId(command.getSkuId())
                    .setListingId(listingOffer == null ? null : listingOffer.getListingId())
                    .setListingOfferId(listingOffer == null ? null : listingOffer.getListingOfferId())
                    .setMerchantId(listingOffer == null ? null : listingOffer.getMerchantId())
                    .setShopId(listingOffer == null ? null : listingOffer.getShopId())
                    .setChannelCode(listingOffer == null ? null : listingOffer.getChannelCode())
                    .setSearchToken(command.getSearchToken())
                    .setResultSetToken(command.getResultSetToken())
                    .setResultPosition(command.getResultPosition())
                    .setQuantity(command.getQuantity())
                    .setCheckoutToken(command.getCheckoutToken())
                    .setSourceSystem(command.getSourceSystem())
                    .setSourceType(command.getSourceType())
                    .setSourceId(command.getSourceId())
                    .setOccurredAt(occurredAt)
                    .setCreatedAt(context.now);
            require(behaviorEventMapper.insert(behavior) == 1, "failed to persist commerce behavior");
            append(context.tenantId, "commerce.behavior.recorded", session.getSessionId(), eventSessionVersion, sequence,
                    command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                    command.getIdempotencyKey(), payload(
                            "run_id", command.getRunId(),
                            "behavior_id", command.getBehaviorId(),
                            "session_id", session.getSessionId(),
                            "session_version", eventSessionVersion,
                            "behavior_type", command.getBehaviorType(),
                            "principal_id", session.getPrincipalId(),
                            "canonical_spu_id", command.getCanonicalSpuId(),
                            "sku_id", command.getSkuId(),
                            "listing_id", listingOffer == null ? null : listingOffer.getListingId(),
                            "listing_offer_id", listingOffer == null ? null : listingOffer.getListingOfferId(),
                            "merchant_id", listingOffer == null ? null : listingOffer.getMerchantId(),
                            "shop_id", listingOffer == null ? null : listingOffer.getShopId(),
                            "channel_code", listingOffer == null ? null : listingOffer.getChannelCode(),
                            "search_token", command.getSearchToken(),
                            "result_set_token", command.getResultSetToken(),
                            "result_position", command.getResultPosition(),
                            "quantity", command.getQuantity(),
                            "checkout_token", command.getCheckoutToken(),
                            "occurred_at", command.getOccurredAt().toString(),
                            "source_system", command.getSourceSystem(),
                            "source_type", command.getSourceType(),
                            "source_id", command.getSourceId()),
                    "RECORD_BEHAVIOR");
            return result(context.operationId, session.getSessionId(), nextStatus, eventSessionVersion);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommerceBehaviorCommandResult attributePaidOrder(AttributePaidOrderCommand command) {
        validateAttribution(command);
        return execute("ATTRIBUTE_PAID_ORDER", command.getIdempotencyKey(), fingerprint(command), context -> {
            CommerceSessionDO session = requireSession(context.tenantId, command.getSessionId());
            requireNotMateriallyFuture(command.getOccurredAt(), context.now);
            require(!Set.of("ABANDONED", "EXPIRED").contains(session.getStatus()),
                    "terminal session cannot attribute payment");
            require(session.getPrincipalId() != null, "session must be linked to a principal before payment attribution");
            if (command.getExpectedSessionVersion() != null) {
                require(Objects.equals(session.getVersion(), command.getExpectedSessionVersion()),
                        "session version conflict");
            }
            CommerceBehaviorEventDO checkoutStarted = behaviorEventMapper.selectLatestCheckoutStarted(
                    context.tenantId, session.getSessionId(), command.getCheckoutToken());
            require(checkoutStarted != null, "checkoutToken has no CHECKOUT_STARTED event in session");
            require(behaviorEventMapper.countCheckoutAbandoned(context.tenantId, session.getSessionId(),
                    command.getCheckoutToken()) == 0, "checkoutToken is already abandoned");
            Instant checkoutStartedAt = checkoutStarted.getOccurredAt().toInstant(ZoneOffset.UTC);
            require(!command.getOccurredAt().isBefore(checkoutStartedAt),
                    "occurredAt is earlier than checkout started");
            require(!command.getOccurredAt().isAfter(checkoutStartedAt.plus(ATTRIBUTION_WINDOW_HOURS, ChronoUnit.HOURS)),
                    "payment attribution exceeds 24h checkout window");
            requireNotBeforeSessionActivity(command.getOccurredAt(), session);
            OrderAttributionView order = orderQueryApi.requireAttributedOrder(command.getOrderId());
            require(Objects.equals(order.getBuyerId(), session.getPrincipalId()),
                    "paid order buyer does not match session principal");
            require(Objects.equals(order.getPaymentId(), command.getPaymentId()),
                    "paymentId does not match canonical order");
            require(checkoutStarted.getMerchantId() != null && checkoutStarted.getShopId() != null,
                    "paid store attribution requires a governed Listing/Offer checkout context");
            require(Objects.equals(checkoutStarted.getMerchantId(), order.getMerchantId()),
                    "paid order merchant does not match checkout Listing/Offer");
            require(Objects.equals(checkoutStarted.getShopId(), order.getShopId()),
                    "paid order shop does not match checkout Listing/Offer");
            require(Objects.equals(checkoutStarted.getChannelCode(), order.getChannelCode()),
                    "paid order channel does not match checkout Listing/Offer");
            require(sessionMapper.advanceActivity(context.tenantId, session.getSessionId(), session.getVersion(),
                    utc(command.getOccurredAt()), context.now) == 1, "session activity transition conflict");
            Long attributedSessionVersion = session.getVersion() + 1;
            CommerceSessionPaymentAttributionDO attribution = new CommerceSessionPaymentAttributionDO()
                    .setAttributionId(command.getAttributionId())
                    .setTenantId(context.tenantId)
                    .setSessionId(session.getSessionId())
                    .setSessionVersion(attributedSessionVersion)
                    .setPrincipalId(session.getPrincipalId())
                    .setCheckoutToken(command.getCheckoutToken())
                    .setOrderId(order.getOrderId())
                    .setOrderVersion(order.getAggregateVersion())
                    .setPaymentId(order.getPaymentId())
                    .setMerchantId(order.getMerchantId())
                    .setShopId(order.getShopId())
                    .setChannelCode(order.getChannelCode())
                    .setSourceSystem(command.getSourceSystem())
                    .setSourceType(command.getSourceType())
                    .setSourceId(command.getSourceId())
                    .setOccurredAt(utc(command.getOccurredAt()))
                    .setCreatedAt(context.now);
            require(paymentAttributionMapper.insert(attribution) == 1, "failed to persist payment attribution");
            append(context.tenantId, "commerce.session.payment_attributed", session.getSessionId(), attributedSessionVersion,
                    (short) 1, command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                    command.getIdempotencyKey(), payload(
                            "run_id", command.getRunId(),
                            "attribution_id", command.getAttributionId(),
                            "session_id", session.getSessionId(),
                            "session_version", attributedSessionVersion,
                            "principal_id", session.getPrincipalId(),
                            "checkout_token", command.getCheckoutToken(),
                            "order_id", order.getOrderId(),
                            "order_version", order.getAggregateVersion(),
                            "payment_id", order.getPaymentId(),
                            "merchant_id", order.getMerchantId(),
                            "shop_id", order.getShopId(),
                            "channel_code", order.getChannelCode(),
                            "order_status", order.getStatus(),
                            "attributed_at", command.getOccurredAt().toString(),
                            "source_system", command.getSourceSystem(),
                            "source_type", command.getSourceType(),
                            "source_id", command.getSourceId()),
                    "ATTRIBUTE_PAID_ORDER");
            return result(context.operationId, session.getSessionId(), session.getStatus(), attributedSessionVersion);
        });
    }

    private CommerceSessionDO requireSession(Long tenantId, String sessionId) {
        CommerceSessionDO session = sessionMapper.selectForUpdate(tenantId, sessionId);
        require(session != null, "commerce session does not exist");
        return session;
    }

    private <T> CommerceBehaviorCommandResult execute(String commandType, String idempotencyKey, String requestHash,
                                                      OperationHandler<T> handler) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, idempotencyKey, commandType, requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve commerce behavior operation");
        CommerceBehaviorOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "commerce behavior operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different commerce behavior payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing commerce behavior operation is not complete");
            CommerceBehaviorCommandResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), CommerceBehaviorCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }
        OperationContext context = new OperationContext(tenantId, operationId, now);
        CommerceBehaviorCommandResult result = handler.handle(context);
        require(operationMapper.markSucceeded(operationId, tenantId, result.getAggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "commerce behavior operation completion conflict");
        return result;
    }

    private void append(Long tenantId, String eventType, String aggregateId, Long aggregateVersion, short sequence,
                        Instant occurredAt, String correlationId, String causationId, String idempotencyKey,
                        Map<String, Object> payload, String operation) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType(eventType)
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType("commerce_session")
                .aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion)
                .eventSequence(sequence)
                .occurredAt(occurredAt)
                .correlationId(correlationId)
                .causationId(causationId)
                .idempotencyKey(idempotencyKey + ":" + eventType + ":" + sequence)
                .payload(payload)
                .headers(Map.of("pii_safe", false, "data_classification", DATA_CLASSIFICATION, "operation", operation))
                .destination("lakehouse")
                .build());
    }

    private static String nextStatusForBehavior(String behaviorType, String currentStatus) {
        return switch (behaviorType) {
            case "CHECKOUT_STARTED" -> "CHECKOUT_IN_PROGRESS";
            case "CHECKOUT_ABANDONED" -> "ABANDONED";
            default -> currentStatus;
        };
    }

    private static CommerceBehaviorCommandResult result(Long operationId, String sessionId, String status, Long version) {
        return CommerceBehaviorCommandResult.builder()
                .operationId(operationId)
                .aggregateId(sessionId)
                .aggregateType("commerce_session")
                .status(status)
                .aggregateVersion(version)
                .duplicate(false)
                .build();
    }

    private static String fingerprint(Object command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", TenantContextHolder.getRequiredTenantId());
        value.put("command", command);
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Map<String, Object> payload(Object... items) {
        require(items.length % 2 == 0, "payload must contain key-value pairs");
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < items.length; i += 2) {
            payload.put(String.valueOf(items[i]), items[i + 1]);
        }
        return payload;
    }

    private static void validateStart(StartCommerceSessionCommand command) {
        require(command != null, "startSession command is required");
        validateCommon(command.getIdempotencyKey(), command.getRunId(), command.getCorrelationId(),
                command.getCausationId(), command.getOccurredAt(), command.getSourceSystem(),
                command.getSourceType(), command.getSourceId());
        requireUuid(command.getSessionId(), "sessionId");
        requireTypeCode(command.getChannelCode(), "channelCode");
        requireTypeCode(command.getEntrypointCode(), "entrypointCode");
    }

    private static void validateLink(LinkCommerceSessionIdentityCommand command) {
        require(command != null, "linkSessionIdentity command is required");
        validateCommon(command.getIdempotencyKey(), command.getRunId(), command.getCorrelationId(),
                command.getCausationId(), command.getOccurredAt(), command.getSourceSystem(),
                command.getSourceType(), command.getSourceId());
        requireUuid(command.getSessionId(), "sessionId");
        requireUuid(command.getPrincipalId(), "principalId");
        require(command.getExpectedSessionVersion() != null && command.getExpectedSessionVersion() > 0,
                "expectedSessionVersion must be positive");
    }

    private static void validateBehavior(RecordCommerceBehaviorCommand command) {
        require(command != null, "recordBehavior command is required");
        validateCommon(command.getIdempotencyKey(), command.getRunId(), command.getCorrelationId(),
                command.getCausationId(), command.getOccurredAt(), command.getSourceSystem(),
                command.getSourceType(), command.getSourceId());
        requireUuid(command.getBehaviorId(), "behaviorId");
        requireUuid(command.getSessionId(), "sessionId");
        require(BEHAVIOR_TYPES.contains(command.getBehaviorType()), "behaviorType is not supported");
        if (command.getPrincipalId() != null) requireUuid(command.getPrincipalId(), "principalId");
        if (command.getExpectedSessionVersion() != null) {
            require(command.getExpectedSessionVersion() > 0, "expectedSessionVersion must be positive");
        }
        switch (command.getBehaviorType()) {
            case "PDP_VIEWED" -> requireUuid(command.getCanonicalSpuId(), "canonicalSpuId");
            case "SEARCH_REQUESTED" -> requireToken(command.getSearchToken(), "searchToken");
            case "SEARCH_RESULT_EXPOSED" -> {
                requireToken(command.getSearchToken(), "searchToken");
                requireToken(command.getResultSetToken(), "resultSetToken");
            }
            case "SEARCH_RESULT_CLICKED" -> {
                requireToken(command.getSearchToken(), "searchToken");
                requireToken(command.getResultSetToken(), "resultSetToken");
                require(command.getResultPosition() != null && command.getResultPosition() >= 0,
                        "resultPosition must be non-negative");
                requireUuid(command.getCanonicalSpuId(), "canonicalSpuId");
            }
            case "CART_ADDED", "CART_REMOVED" -> {
                requireUuid(command.getCanonicalSpuId(), "canonicalSpuId");
                require(command.getQuantity() != null && command.getQuantity() > 0, "quantity must be positive");
            }
            case "CHECKOUT_STARTED", "CHECKOUT_ABANDONED" -> requireToken(command.getCheckoutToken(), "checkoutToken");
            default -> {
            }
        }
        if (command.getSkuId() != null) requireUuid(command.getSkuId(), "skuId");
        boolean hasListingContext = command.getListingId() != null
                || command.getListingOfferId() != null
                || command.getExpectedPriceMinor() != null
                || command.getCurrencyCode() != null;
        if (hasListingContext) {
            requireUuid(command.getListingId(), "listingId");
            requireUuid(command.getListingOfferId(), "listingOfferId");
            requireUuid(command.getSkuId(), "skuId");
            require(command.getExpectedPriceMinor() != null && command.getExpectedPriceMinor() >= 0,
                    "expectedPriceMinor must be nonnegative");
            requireText(command.getCurrencyCode(), "currencyCode", 8);
        }
    }

    private PublishedListingOfferView resolveListingOffer(RecordCommerceBehaviorCommand command) {
        boolean hasListingContext = command.getListingId() != null
                || command.getListingOfferId() != null
                || command.getExpectedPriceMinor() != null
                || command.getCurrencyCode() != null;
        if (!hasListingContext) {
            return null;
        }
        return listingQueryApi.requirePublishedOffer(PublishedOfferValidationCommand.builder()
                .listingId(command.getListingId())
                .listingOfferId(command.getListingOfferId())
                .canonicalSkuId(command.getSkuId())
                .expectedPriceMinor(command.getExpectedPriceMinor())
                .currencyCode(command.getCurrencyCode())
                .build());
    }

    private static void validateAttribution(AttributePaidOrderCommand command) {
        require(command != null, "attributePaidOrder command is required");
        validateCommon(command.getIdempotencyKey(), command.getRunId(), command.getCorrelationId(),
                command.getCausationId(), command.getOccurredAt(), command.getSourceSystem(),
                command.getSourceType(), command.getSourceId());
        requireUuid(command.getAttributionId(), "attributionId");
        requireUuid(command.getSessionId(), "sessionId");
        requireUuid(command.getOrderId(), "orderId");
        requireUuid(command.getPaymentId(), "paymentId");
        requireToken(command.getCheckoutToken(), "checkoutToken");
        if (command.getExpectedSessionVersion() != null) {
            require(command.getExpectedSessionVersion() > 0, "expectedSessionVersion must be positive");
        }
    }

    private static void validateCommon(String idempotencyKey, String runId, String correlationId, String causationId,
                                       Instant occurredAt, String sourceSystem, String sourceType, String sourceId) {
        requireText(idempotencyKey, "idempotencyKey", 128);
        require(idempotencyKey.length() >= 8, "idempotencyKey is too short");
        requireUuid(runId, "runId");
        requireUuid(correlationId, "correlationId");
        if (causationId != null) requireUuid(causationId, "causationId");
        require(occurredAt != null, "occurredAt is required");
        requireTypeCode(sourceSystem, "sourceSystem");
        requireTypeCode(sourceType, "sourceType");
        requireToken(sourceId, "sourceId");
    }

    private static void requireToken(String value, String field) {
        requireText(value, field, 128);
        require(CONTROLLED_TOKEN.matcher(value).matches(), field + " must be a controlled token or digest");
    }

    private static void requireTypeCode(String value, String field) {
        requireText(value, field, 32);
        require(TYPE_CODE.matcher(value).matches(), field + " must be an uppercase type code");
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is required");
    }

    private static void requireNotMateriallyFuture(Instant occurredAt, LocalDateTime now) {
        Instant maxAllowed = now.toInstant(ZoneOffset.UTC).plus(FUTURE_SKEW_SECONDS, ChronoUnit.SECONDS);
        require(!occurredAt.isAfter(maxAllowed), "occurredAt is materially in the future");
    }

    private static void requireNotBeforeSessionActivity(Instant occurredAt, CommerceSessionDO session) {
        Instant startedAt = session.getStartedAt().toInstant(ZoneOffset.UTC);
        Instant lastActivityAt = session.getLastActivityAt().toInstant(ZoneOffset.UTC);
        require(!occurredAt.isBefore(startedAt), "occurredAt is earlier than session startedAt");
        require(!occurredAt.isBefore(lastActivityAt), "occurredAt is earlier than session lastActivityAt");
    }

    private static void requireBehaviorTransition(String currentStatus, String behaviorType) {
        require(SESSION_STATUSES.contains(currentStatus), "session status is not supported");
        switch (behaviorType) {
            case "CHECKOUT_STARTED" ->
                    require("ACTIVE".equals(currentStatus), "CHECKOUT_STARTED requires ACTIVE session");
            case "CHECKOUT_ABANDONED" ->
                    require("CHECKOUT_IN_PROGRESS".equals(currentStatus),
                            "CHECKOUT_ABANDONED requires CHECKOUT_IN_PROGRESS session");
            default -> {
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @FunctionalInterface
    private interface OperationHandler<T> {
        CommerceBehaviorCommandResult handle(OperationContext context);
    }

    private record OperationContext(Long tenantId, Long operationId, LocalDateTime now) {
    }
}
