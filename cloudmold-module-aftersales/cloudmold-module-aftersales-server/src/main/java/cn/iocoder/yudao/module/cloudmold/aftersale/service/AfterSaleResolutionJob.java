package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.*;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.after-sale-resolution-saga", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AfterSaleResolutionJob {
    private static final String LEASE_OWNER = "cloudmold-after-sale-saga-" +
            ManagementFactory.getRuntimeMXBean().getName().replaceAll("[^A-Za-z0-9._-]", "_");
    private final AfterSaleResolutionWorker worker;

    @Scheduled(fixedDelayString = "${cloudmold.after-sale-resolution-saga.fixed-delay-ms:1000}")
    public void run() {
        AfterSaleResolutionRunResult result = worker.runBatch(LEASE_OWNER, 100,
                LocalDateTime.now(ZoneOffset.UTC));
        if (result.claimed() > 0) {
            log.info("[after-sale-resolution][candidates={},claimed={},completed={},retry={},manual={}]",
                    result.candidates(), result.claimed(), result.completed(), result.retryScheduled(),
                    result.manualReview());
        }
    }
}
