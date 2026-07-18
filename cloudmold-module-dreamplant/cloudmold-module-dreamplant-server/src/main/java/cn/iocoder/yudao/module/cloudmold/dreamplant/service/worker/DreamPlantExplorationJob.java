package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.dreamplant.exploration-worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DreamPlantExplorationJob {

    private final DreamPlantExplorationWorker worker;
    private final String leaseOwner = "dreamplant-worker-" + UUID.randomUUID();

    @Value("${cloudmold.dreamplant.exploration-worker.batch-size:10}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${cloudmold.dreamplant.exploration-worker.fixed-delay:5000}")
    public void run() {
        worker.runBatch(leaseOwner, batchSize, LocalDateTime.now(ZoneOffset.UTC));
    }

}
