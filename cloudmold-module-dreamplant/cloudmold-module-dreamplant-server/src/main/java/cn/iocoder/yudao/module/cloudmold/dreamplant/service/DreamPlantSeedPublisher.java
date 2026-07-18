package cn.iocoder.yudao.module.cloudmold.dreamplant.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.dreamplant.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.dreamplant.seed", name = "enabled", havingValue = "true")
public class DreamPlantSeedPublisher implements ApplicationRunner {
    private final DreamPlantCommandApi commandApi;
    private final DreamPlantQueryApi queryApi;

    @Value("${cloudmold.dreamplant.seed.tenant-id:1}")
    private long tenantId;

    @Value("${cloudmold.dreamplant.seed.map-key:dreamplant}")
    private String mapKey;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        TenantContextHolder.setTenantId(tenantId);
        try {
            try {
                queryApi.getPublishedWorldMap(mapKey);
                return;
            } catch (IllegalArgumentException missing) {
                if (!"DreamPlant world map does not exist".equals(missing.getMessage())) {
                    throw missing;
                }
            }
            String payload = new ClassPathResource("dreamplant/world-map-seed.json")
                    .getContentAsString(StandardCharsets.UTF_8);
            String hash = DigestUtil.sha256Hex(payload);
            commandApi.execute(DreamPlantCommand.builder().operation(DreamPlantOperation.PUBLISH_WORLD_MAP)
                    .idempotencyKey("dreamplant-seed-" + hash.substring(0, 24))
                    .runTraceId("dreamplant-seed-run-" + hash.substring(0, 20)).mapKey(mapKey)
                    .expectedVersion(0L).schemaVersion("dreamplant.bootstrap.v1").payloadJson(payload)
                    .payloadSha256(hash).sourceRef("sha256:" + hash).publiclyReadable(true).build());
        } finally {
            TenantContextHolder.clear();
        }
    }
}
