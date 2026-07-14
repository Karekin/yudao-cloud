package cn.iocoder.yudao.module.cloudmold.order.service.cancellation;

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
@ConditionalOnProperty(prefix = "cloudmold.order-cancellation-saga", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class OrderCancellationSagaJob {
    private static final String LEASE_OWNER = "cloudmold-cancel-saga-" +
            ManagementFactory.getRuntimeMXBean().getName().replaceAll("[^A-Za-z0-9._-]", "_");
    private final OrderCancellationSagaWorker worker;

    @Scheduled(fixedDelayString = "${cloudmold.order-cancellation-saga.fixed-delay-ms:1000}")
    public void run() {
        OrderCancellationSagaRunResult result = worker.runBatch(LEASE_OWNER, 100,
                LocalDateTime.now(ZoneOffset.UTC));
        if (result.claimed() > 0) {
            log.info("[order-cancellation-saga][candidates={},claimed={},completed={},retry={},manual={}]",
                    result.candidates(), result.claimed(), result.completed(),
                    result.retryScheduled(), result.manualReview());
        }
    }
}
