package cn.iocoder.yudao.module.cloudmold.listing.service.unpublish;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.listing-unpublish-saga", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ListingUnpublishSagaJob {
    private static final String LEASE_OWNER = "cloudmold-listing-unpublish-saga-" +
            ManagementFactory.getRuntimeMXBean().getName().replaceAll("[^A-Za-z0-9._-]", "_");
    private final ListingUnpublishSagaWorker worker;

    @Scheduled(fixedDelayString = "${cloudmold.listing-unpublish-saga.fixed-delay-ms:1000}")
    public void run() {
        ListingUnpublishSagaRunResult result = worker.runBatch(LEASE_OWNER, 100,
                LocalDateTime.now(ZoneOffset.UTC));
        if (result.claimed() > 0) {
            log.info("[listing-unpublish-saga][candidates={},claimed={},completed={},retry={},manual={}]",
                    result.candidates(), result.claimed(), result.completed(),
                    result.retryScheduled(), result.manualReview());
        }
    }
}
