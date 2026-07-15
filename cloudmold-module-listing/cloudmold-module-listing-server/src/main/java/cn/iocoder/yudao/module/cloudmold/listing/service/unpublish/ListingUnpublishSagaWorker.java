package cn.iocoder.yudao.module.cloudmold.listing.service.unpublish;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingHeaderDO;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish.*;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.ListingHeaderMapper;
import cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.unpublish.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ListingUnpublishSagaWorker {

    static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    private final ListingUnpublishSagaMapper sagaMapper;
    private final ListingUnpublishSagaItemMapper itemMapper;
    private final ListingHeaderMapper listingMapper;
    private final ListingCommandApi listingCommandApi;
    private final ListingUnpublishSagaCheckpointService checkpointService;

    public ListingUnpublishSagaRunResult runBatch(String leaseOwner, int batchSize, LocalDateTime now) {
        require(leaseOwner != null && !leaseOwner.isBlank(), "leaseOwner is required");
        require(batchSize > 0 && batchSize <= 1000, "batchSize must be between 1 and 1000");
        require(now != null, "now is required");
        List<ListingUnpublishSagaDO> candidates = sagaMapper.selectDue(now, batchSize);
        int claimed = 0;
        int completed = 0;
        int retry = 0;
        int manual = 0;
        for (ListingUnpublishSagaDO candidate : candidates) {
            if (sagaMapper.claim(candidate.getTenantId(), candidate.getSagaId(), leaseOwner,
                    now.plus(LEASE_DURATION), now) != 1) continue;
            claimed++;
            try {
                TenantUtils.execute(candidate.getTenantId(), () -> process(candidate.getTenantId(),
                        candidate.getSagaId(), leaseOwner, now));
                completed++;
            } catch (Exception ignored) {
                ListingUnpublishSagaDO current = sagaMapper.selectTenantSaga(candidate.getTenantId(),
                        candidate.getSagaId());
                if (current != null && "MANUAL_REVIEW".equals(current.getStatus())) manual++;
                else retry++;
            }
        }
        return new ListingUnpublishSagaRunResult(candidates.size(), claimed, completed, retry, manual);
    }

    void process(Long tenantId, String sagaId, String leaseOwner, LocalDateTime now) {
        String activeItemId = null;
        try {
            checkpointService.markRunning(tenantId, sagaId, leaseOwner, checkpointNow(now));
            ListingUnpublishSagaDO saga = requireSaga(tenantId, sagaId);
            while (true) {
                ListingUnpublishSagaItemDO item = itemMapper.selectNext(tenantId, sagaId);
                if (item == null) break;
                activeItemId = item.getSagaItemId();
                checkpointService.markItemRunning(tenantId, sagaId, activeItemId, leaseOwner,
                        checkpointNow(now));
                try {
                    ListingCommandResult result = listingCommandApi.execute(ListingCommand.builder()
                            .operation(ListingOperation.UNPUBLISH)
                            .idempotencyKey(item.getUnpublishIdempotencyKey()).runId(truncate(saga.getRunId(), 64))
                            .listingId(item.getListingId()).expectedVersion(item.getListingVersionAtRequest())
                            .reason(truncate(saga.getReason(), 256)).correlationId(saga.getCorrelationId())
                            .causationId(saga.getSourceEventId())
                            .occurredAt(saga.getOccurredAt().toInstant(ZoneOffset.UTC)).build());
                    checkpointService.markItemUnpublished(tenantId, sagaId, activeItemId, leaseOwner,
                            result, checkpointNow(now));
                } catch (RuntimeException failure) {
                    ListingHeaderDO current = listingMapper.selectTenantListing(tenantId, item.getListingId());
                    if (current != null && !"PUBLISHED".equals(current.getStatus())) {
                        checkpointService.markItemSkipped(tenantId, sagaId, activeItemId, leaseOwner,
                                current.getStatus(), current.getVersion(), checkpointNow(now));
                    } else {
                        throw failure;
                    }
                }
                activeItemId = null;
            }
            saga = requireSaga(tenantId, sagaId);
            require(saga.getUnpublishedListingCount() + saga.getSkippedListingCount()
                            == saga.getExpectedListingCount(),
                    "Listing unpublish Saga frozen set is incomplete");
            checkpointService.markCompleted(tenantId, sagaId, leaseOwner, checkpointNow(now));
        } catch (Throwable failure) {
            try {
                checkpointService.markFailure(tenantId, sagaId, activeItemId, leaseOwner, failure,
                        checkpointNow(now));
            } catch (Throwable checkpointFailure) {
                failure.addSuppressed(checkpointFailure);
            }
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Listing unpublish Saga execution failed", failure);
        }
    }

    private ListingUnpublishSagaDO requireSaga(Long tenantId, String sagaId) {
        ListingUnpublishSagaDO saga = sagaMapper.selectTenantSaga(tenantId, sagaId);
        require(saga != null, "Listing unpublish Saga does not exist");
        return saga;
    }

    private static LocalDateTime checkpointNow(LocalDateTime floor) {
        LocalDateTime wallClock = LocalDateTime.now(ZoneOffset.UTC);
        return wallClock.isAfter(floor) ? wallClock : floor.plusNanos(1);
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
