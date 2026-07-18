package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import lombok.Builder;
import lombok.Value;

public interface DreamPlantWorldMapPort {

    PublishedWorldMap loadPublishedWorldMap(Long tenantId, String mapKey);

    void publishWorldMap(PublishWorldMapRequest request);

    @Value
    @Builder
    class PublishedWorldMap {
        Long tenantId;
        String mapKey;
        Long version;
        String schemaVersion;
        String payloadJson;
        Boolean publiclyReadable;
        String sourceRef;
    }

    @Value
    @Builder
    class PublishWorldMapRequest {
        Long tenantId;
        String mapKey;
        Long expectedVersion;
        String schemaVersion;
        String payloadJson;
        String payloadSha256;
        String sourceRef;
        Boolean publiclyReadable;
        String idempotencyKey;
        String runTraceId;
    }

}
