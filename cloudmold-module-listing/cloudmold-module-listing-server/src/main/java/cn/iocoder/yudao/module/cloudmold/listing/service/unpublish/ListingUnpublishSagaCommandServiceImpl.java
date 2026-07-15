package cn.iocoder.yudao.module.cloudmold.listing.service.unpublish;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingHeaderDO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingHeaderMapper;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.unpublish.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ListingUnpublishSagaCommandServiceImpl
        implements ListingUnpublishSagaCommandApi, ListingUnpublishSagaQueryApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final int DEFAULT_MAX_ATTEMPTS = 8;

    private final ListingUnpublishSagaOperationMapper operationMapper;
    private final ListingUnpublishSagaMapper sagaMapper;
    private final ListingUnpublishSagaItemMapper itemMapper;
    private final ListingHeaderMapper listingMapper;
    private final ListingUnpublishSagaCheckpointService checkpointService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ListingUnpublishSagaView execute(ListingUnpublishSagaCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve Listing unpublish Saga operation");
        ListingUnpublishSagaOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "Listing unpublish Saga operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different Listing unpublish Saga payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing Listing unpublish Saga operation is not complete");
            ListingUnpublishSagaView replay = JsonUtils.parseObject(operation.getResultJson(),
                    ListingUnpublishSagaView.class);
            replay.setDuplicate(true);
            return replay;
        }

        ListingUnpublishSagaView result = command.getOperation() == ListingUnpublishSagaOperation.START
                ? start(tenantId, command, now) : retry(tenantId, command, now);
        require(operationMapper.markSucceeded(operationId, tenantId, result.getSagaId(),
                JsonUtils.toJsonString(result), now) == 1, "Listing unpublish Saga operation completion conflict");
        return result;
    }

    @Override
    public ListingUnpublishSagaView get(String sagaId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(sagaId, "sagaId", 36);
        ListingUnpublishSagaDO saga = sagaMapper.selectTenantSaga(tenantId, sagaId);
        require(saga != null, "Listing unpublish Saga does not exist");
        return view(saga, false);
    }

    private ListingUnpublishSagaView start(Long tenantId, ListingUnpublishSagaCommand command,
                                            LocalDateTime now) {
        require(command.getSagaId() == null, "START does not accept sagaId");
        require(command.getExpectedVersion() == null, "START does not accept expectedVersion");
        requireUuid(command.getSourceEventId(), "sourceEventId");
        require(command.getSourceAggregateVersion() != null && command.getSourceAggregateVersion() > 0,
                "sourceAggregateVersion must be positive");
        requireText(command.getMerchantId(), "merchantId", 36);
        requireText(command.getReason(), "reason", 512);
        require(Set.of("MERCHANT", "SHOP").contains(command.getSourceEntityType()),
                "sourceEntityType must be MERCHANT or SHOP");
        if ("SHOP".equals(command.getSourceEntityType())) {
            requireText(command.getShopId(), "shopId", 36);
        } else {
            require(command.getShopId() == null, "MERCHANT source does not accept shopId");
        }
        require(sagaMapper.selectBySourceEvent(tenantId, command.getSourceEventId()) == null,
                "source Merchant event already owns a Listing unpublish Saga");

        List<ListingHeaderDO> listings = listingMapper.selectPublishedForEligibilityEnforcement(
                tenantId, command.getMerchantId(), command.getShopId());
        String sagaId = UUID.randomUUID().toString();
        LocalDateTime occurredAt = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        ListingUnpublishSagaDO saga = new ListingUnpublishSagaDO().setSagaId(sagaId).setTenantId(tenantId)
                .setIdempotencyKey(command.getIdempotencyKey()).setRequestHash(fingerprint(tenantId, command))
                .setRunId(command.getRunId()).setSourceEventId(command.getSourceEventId())
                .setSourceEntityType(command.getSourceEntityType())
                .setSourceAggregateVersion(command.getSourceAggregateVersion())
                .setMerchantId(command.getMerchantId()).setShopId(command.getShopId())
                .setStatus("REQUESTED").setActiveStep("UNPUBLISH_LISTINGS")
                .setExpectedListingCount(listings.size()).setUnpublishedListingCount(0).setSkippedListingCount(0)
                .setAttemptCount(0).setMaxAttempts(DEFAULT_MAX_ATTEMPTS).setVersion(1L)
                .setReason(command.getReason()).setCorrelationId(command.getCorrelationId())
                .setCausationId(command.getSourceEventId()).setOccurredAt(occurredAt)
                .setCreatedAt(now).setUpdatedAt(now);
        sagaMapper.insert(saga);
        for (ListingHeaderDO listing : listings) {
            itemMapper.insert(new ListingUnpublishSagaItemDO().setSagaItemId(UUID.randomUUID().toString())
                    .setTenantId(tenantId).setSagaId(sagaId).setListingId(listing.getListingId())
                    .setListingVersionAtRequest(listing.getVersion())
                    .setUnpublishIdempotencyKey("listing-unpublish:" + sagaId + ":" + listing.getListingId())
                    .setStatus("PENDING").setAttemptCount(0).setCreatedAt(now).setUpdatedAt(now));
        }
        checkpointService.appendInitial(saga, now);
        return view(saga, false);
    }

    private ListingUnpublishSagaView retry(Long tenantId, ListingUnpublishSagaCommand command,
                                            LocalDateTime now) {
        requireText(command.getSagaId(), "sagaId", 36);
        require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                "RETRY requires expectedVersion");
        require(command.getSourceEventId() == null && command.getSourceEntityType() == null
                        && command.getSourceAggregateVersion() == null && command.getMerchantId() == null
                        && command.getShopId() == null,
                "RETRY does not accept source identity fields");
        ListingUnpublishSagaDO saga = checkpointService.retryManually(tenantId, command.getSagaId(),
                command.getExpectedVersion(), now);
        return view(saga, false);
    }

    private ListingUnpublishSagaView view(ListingUnpublishSagaDO saga, boolean duplicate) {
        List<ListingUnpublishSagaItemView> items = itemMapper.selectBySaga(saga.getTenantId(), saga.getSagaId())
                .stream().map(item -> ListingUnpublishSagaItemView.builder()
                        .sagaItemId(item.getSagaItemId()).listingId(item.getListingId())
                        .listingVersionAtRequest(item.getListingVersionAtRequest()).status(item.getStatus())
                        .attemptCount(item.getAttemptCount()).listingOperationId(item.getListingOperationId())
                        .finalListingStatus(item.getFinalListingStatus())
                        .finalListingVersion(item.getFinalListingVersion()).build()).toList();
        return ListingUnpublishSagaView.builder().sagaId(saga.getSagaId()).runId(saga.getRunId())
                .sourceEventId(saga.getSourceEventId()).sourceEntityType(saga.getSourceEntityType())
                .sourceAggregateVersion(saga.getSourceAggregateVersion()).merchantId(saga.getMerchantId())
                .shopId(saga.getShopId()).status(saga.getStatus()).activeStep(saga.getActiveStep())
                .expectedListingCount(saga.getExpectedListingCount())
                .unpublishedListingCount(saga.getUnpublishedListingCount())
                .skippedListingCount(saga.getSkippedListingCount()).attemptCount(saga.getAttemptCount())
                .maxAttempts(saga.getMaxAttempts()).aggregateVersion(saga.getVersion()).reason(saga.getReason())
                .lastErrorCode(saga.getLastErrorCode()).lastErrorMessage(saga.getLastErrorMessage())
                .nextRetryAt(instant(saga.getNextRetryAt())).completedAt(instant(saga.getCompletedAt()))
                .items(items).duplicate(duplicate).build();
    }

    private static void validateCommon(ListingUnpublishSagaCommand command) {
        require(command != null && command.getOperation() != null, "Listing unpublish Saga operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
    }

    private static String fingerprint(Long tenantId, ListingUnpublishSagaCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId); value.put("operation", command.getOperation());
        value.put("run_id", command.getRunId()); value.put("saga_id", command.getSagaId());
        value.put("expected_version", command.getExpectedVersion()); value.put("source_event_id", command.getSourceEventId());
        value.put("source_entity_type", command.getSourceEntityType());
        value.put("source_aggregate_version", command.getSourceAggregateVersion());
        value.put("merchant_id", command.getMerchantId()); value.put("shop_id", command.getShopId());
        value.put("reason", command.getReason()); value.put("correlation_id", command.getCorrelationId());
        value.put("causation_id", command.getCausationId()); value.put("occurred_at", command.getOccurredAt());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
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
}
