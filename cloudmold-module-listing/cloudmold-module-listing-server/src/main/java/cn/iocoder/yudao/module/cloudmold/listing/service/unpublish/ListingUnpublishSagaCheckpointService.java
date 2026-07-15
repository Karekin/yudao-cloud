package cn.iocoder.yudao.module.cloudmold.listing.service.unpublish;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.listing.api.ListingCommandResult;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.unpublish.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ListingUnpublishSagaCheckpointService {

    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private final ListingUnpublishSagaMapper sagaMapper;
    private final ListingUnpublishSagaItemMapper itemMapper;
    private final ListingUnpublishSagaHistoryMapper historyMapper;
    private final OutboxAppender outboxAppender;

    public void appendInitial(ListingUnpublishSagaDO saga, LocalDateTime now) {
        appendHistoryAndEvent(saga, null, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRunning(Long tenantId, String sagaId, String leaseOwner, LocalDateTime now) {
        ListingUnpublishSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        if ("UNPUBLISHING".equals(saga.getStatus())) return;
        String previous = saga.getStatus();
        saga.setStatus("UNPUBLISHING").setActiveStep("UNPUBLISH_LISTINGS").setVersion(saga.getVersion() + 1)
                .setNextRetryAt(null).setLastErrorCode(null).setLastErrorMessage(null).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "Listing unpublish Saga running checkpoint conflict");
        appendHistoryAndEvent(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markItemRunning(Long tenantId, String sagaId, String sagaItemId,
                                String leaseOwner, LocalDateTime now) {
        requireLeased(tenantId, sagaId, leaseOwner);
        ListingUnpublishSagaItemDO item = requireItem(tenantId, sagaId, sagaItemId);
        if (Set.of("UNPUBLISHED", "SKIPPED").contains(item.getStatus())) return;
        item.setStatus("RUNNING").setAttemptCount(item.getAttemptCount() + 1)
                .setLastErrorCode(null).setLastErrorMessage(null).setUpdatedAt(now);
        require(itemMapper.updateById(item) == 1, "Listing unpublish Saga item running checkpoint conflict");
    }

    @Transactional(rollbackFor = Exception.class)
    public void markItemUnpublished(Long tenantId, String sagaId, String sagaItemId, String leaseOwner,
                                    ListingCommandResult result, LocalDateTime now) {
        ListingUnpublishSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        ListingUnpublishSagaItemDO item = requireItem(tenantId, sagaId, sagaItemId);
        if ("UNPUBLISHED".equals(item.getStatus())) return;
        require(Objects.equals(item.getListingId(), result.getListingId())
                        && "UNPUBLISHED".equals(result.getCurrentStatus()),
                "Listing unpublish result does not match Saga snapshot");
        item.setStatus("UNPUBLISHED").setListingOperationId(result.getOperationId())
                .setFinalListingStatus(result.getCurrentStatus()).setFinalListingVersion(result.getAggregateVersion())
                .setLastErrorCode(null).setLastErrorMessage(null).setCompletedAt(now).setUpdatedAt(now);
        require(itemMapper.updateById(item) == 1, "Listing unpublish Saga item completion conflict");
        advanceCount(saga, false, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markItemSkipped(Long tenantId, String sagaId, String sagaItemId, String leaseOwner,
                                String finalStatus, Long finalVersion, LocalDateTime now) {
        ListingUnpublishSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        ListingUnpublishSagaItemDO item = requireItem(tenantId, sagaId, sagaItemId);
        if ("SKIPPED".equals(item.getStatus())) return;
        require(!"PUBLISHED".equals(finalStatus), "PUBLISHED Listing cannot be skipped");
        item.setStatus("SKIPPED").setFinalListingStatus(finalStatus).setFinalListingVersion(finalVersion)
                .setLastErrorCode(null).setLastErrorMessage(null).setCompletedAt(now).setUpdatedAt(now);
        require(itemMapper.updateById(item) == 1, "Listing unpublish Saga item skip conflict");
        advanceCount(saga, true, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailure(Long tenantId, String sagaId, String sagaItemId, String leaseOwner,
                            Throwable failure, LocalDateTime now) {
        ListingUnpublishSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        String code = failure.getClass().getSimpleName();
        String message = summarize(failure);
        boolean exhausted = saga.getAttemptCount() >= saga.getMaxAttempts();
        String previous = saga.getStatus();
        LocalDateTime nextRetry = exhausted ? null : now.plus(backoff(saga.getAttemptCount()));
        saga.setStatus(exhausted ? "MANUAL_REVIEW" : "RETRY_SCHEDULED")
                .setNextRetryAt(nextRetry).setLeaseOwner(null).setLeaseUntil(null)
                .setLastErrorCode(code).setLastErrorMessage(message)
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "Listing unpublish Saga failure checkpoint conflict");
        if (sagaItemId != null) {
            ListingUnpublishSagaItemDO item = itemMapper.selectForUpdate(tenantId, sagaItemId);
            if (item != null && !Set.of("UNPUBLISHED", "SKIPPED").contains(item.getStatus())) {
                item.setStatus(exhausted ? "MANUAL_REVIEW" : "RETRY_SCHEDULED")
                        .setLastErrorCode(code).setLastErrorMessage(message).setUpdatedAt(now);
                require(itemMapper.updateById(item) == 1, "Listing unpublish Saga item failure conflict");
            }
        }
        appendHistoryAndEvent(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markCompleted(Long tenantId, String sagaId, String leaseOwner, LocalDateTime now) {
        ListingUnpublishSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        require(saga.getUnpublishedListingCount() + saga.getSkippedListingCount()
                        == saga.getExpectedListingCount(),
                "Listing unpublish Saga cannot complete before every frozen Listing is handled");
        String previous = saga.getStatus();
        saga.setStatus("COMPLETED").setActiveStep("NONE").setNextRetryAt(null)
                .setLeaseOwner(null).setLeaseUntil(null).setLastErrorCode(null).setLastErrorMessage(null)
                .setCompletedAt(now).setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "Listing unpublish Saga terminal checkpoint conflict");
        appendHistoryAndEvent(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public ListingUnpublishSagaDO retryManually(Long tenantId, String sagaId, Long expectedVersion,
                                                 LocalDateTime now) {
        ListingUnpublishSagaDO saga = sagaMapper.selectForUpdate(tenantId, sagaId);
        require(saga != null, "Listing unpublish Saga does not exist");
        require("MANUAL_REVIEW".equals(saga.getStatus()), "RETRY requires MANUAL_REVIEW");
        require(Objects.equals(saga.getVersion(), expectedVersion), "Listing unpublish Saga version conflict");
        String previous = saga.getStatus();
        saga.setStatus("RETRY_SCHEDULED").setAttemptCount(0).setNextRetryAt(now)
                .setLeaseOwner(null).setLeaseUntil(null).setLastErrorCode(null).setLastErrorMessage(null)
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "Listing unpublish Saga manual retry conflict");
        for (ListingUnpublishSagaItemDO item : itemMapper.selectBySaga(tenantId, sagaId)) {
            if ("MANUAL_REVIEW".equals(item.getStatus())) {
                item.setStatus("RETRY_SCHEDULED").setLastErrorCode(null).setLastErrorMessage(null)
                        .setUpdatedAt(now);
                require(itemMapper.updateById(item) == 1, "Listing unpublish Saga item manual retry conflict");
            }
        }
        appendHistoryAndEvent(saga, previous, now);
        return saga;
    }

    private void advanceCount(ListingUnpublishSagaDO saga, boolean skipped, LocalDateTime now) {
        String previous = saga.getStatus();
        if (skipped) saga.setSkippedListingCount(saga.getSkippedListingCount() + 1);
        else saga.setUnpublishedListingCount(saga.getUnpublishedListingCount() + 1);
        saga.setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "Listing unpublish Saga count checkpoint conflict");
        appendHistoryAndEvent(saga, previous, now);
    }

    private ListingUnpublishSagaDO requireLeased(Long tenantId, String sagaId, String leaseOwner) {
        ListingUnpublishSagaDO saga = sagaMapper.selectForUpdate(tenantId, sagaId);
        require(saga != null, "Listing unpublish Saga does not exist");
        require(Objects.equals(saga.getLeaseOwner(), leaseOwner), "Listing unpublish Saga lease was lost");
        return saga;
    }

    private ListingUnpublishSagaItemDO requireItem(Long tenantId, String sagaId, String sagaItemId) {
        ListingUnpublishSagaItemDO item = itemMapper.selectForUpdate(tenantId, sagaItemId);
        require(item != null && Objects.equals(item.getSagaId(), sagaId),
                "Listing unpublish Saga item disappeared");
        return item;
    }

    private void appendHistoryAndEvent(ListingUnpublishSagaDO saga, String previous, LocalDateTime now) {
        historyMapper.insert(new ListingUnpublishSagaHistoryDO().setTenantId(saga.getTenantId())
                .setSagaId(saga.getSagaId()).setAggregateVersion(saga.getVersion())
                .setPreviousStatus(previous).setCurrentStatus(saga.getStatus()).setActiveStep(saga.getActiveStep())
                .setAttemptCount(saga.getAttemptCount()).setExpectedListingCount(saga.getExpectedListingCount())
                .setUnpublishedListingCount(saga.getUnpublishedListingCount())
                .setSkippedListingCount(saga.getSkippedListingCount()).setErrorCode(saga.getLastErrorCode())
                .setErrorMessage(saga.getLastErrorMessage()).setNextRetryAt(saga.getNextRetryAt())
                .setOccurredAt(now).setCreatedAt(now));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", saga.getRunId());
        payload.put("saga_id", saga.getSagaId());
        payload.put("source_event_id", saga.getSourceEventId());
        payload.put("source_entity_type", saga.getSourceEntityType());
        payload.put("source_aggregate_version", saga.getSourceAggregateVersion());
        payload.put("merchant_id", saga.getMerchantId());
        payload.put("shop_id", saga.getShopId());
        payload.put("previous_status", previous);
        payload.put("current_status", saga.getStatus());
        payload.put("active_step", saga.getActiveStep());
        payload.put("attempt", saga.getAttemptCount());
        payload.put("reason", saga.getReason());
        payload.put("expected_listing_count", saga.getExpectedListingCount());
        payload.put("unpublished_listing_count", saga.getUnpublishedListingCount());
        payload.put("skipped_listing_count", saga.getSkippedListingCount());
        payload.put("error_code", truncate(saga.getLastErrorCode(), 64));
        payload.put("error_message", truncate(saga.getLastErrorMessage(), 256));
        payload.put("next_retry_at", instant(saga.getNextRetryAt()));
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("listing.sales_eligibility_enforcement.status_changed").schemaVersion(1)
                .sourceSystem("cloudmold-listing").tenantId(saga.getTenantId())
                .aggregateType("listing_sales_eligibility_enforcement").aggregateId(saga.getSagaId())
                .aggregateVersion(saga.getVersion()).eventSequence((short) 1)
                .occurredAt(now.toInstant(ZoneOffset.UTC)).correlationId(saga.getCorrelationId())
                .causationId(saga.getSourceEventId())
                .idempotencyKey("listing-unpublish-saga:" + saga.getSagaId() + ":event:" + saga.getVersion())
                .payload(payload).headers(Map.of("source_entity_type", saga.getSourceEntityType()))
                .destination("lakehouse").build());
    }

    static Duration backoff(int attempt) {
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), 1L << Math.min(Math.max(attempt - 1, 0), 30));
        return Duration.ofSeconds(seconds);
    }

    private static String instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static String summarize(Throwable failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        value = value.replaceAll("(?i)(password|token|secret|authorization)\\s*[=:]\\s*[^,;\\s]+", "$1=[REDACTED]");
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
