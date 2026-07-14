package cn.iocoder.yudao.module.cloudmold.datacontract.service.outbox;

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
@ConditionalOnProperty(prefix = "cloudmold.outbox-relay", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class OutboxRelayJob {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final String LEASE_OWNER = "cloudmold-outbox-" +
            ManagementFactory.getRuntimeMXBean().getName().replaceAll("[^A-Za-z0-9._-]", "_");

    private final OutboxRelayService relayService;

    @Scheduled(fixedDelayString = "${cloudmold.outbox-relay.fixed-delay-ms:1000}")
    public void relay() {
        OutboxRelayResult result = relayService.relayBatch(LEASE_OWNER, DEFAULT_BATCH_SIZE,
                LocalDateTime.now(ZoneOffset.UTC));
        if (result.published() > 0 || result.retried() > 0 || result.dead() > 0 || result.unsupported() > 0) {
            log.info("[relay][candidates={}, claimed={}, published={}, retried={}, dead={}, unsupported={}]",
                    result.candidates(), result.claimed(), result.published(), result.retried(),
                    result.dead(), result.unsupported());
        }
    }
}
