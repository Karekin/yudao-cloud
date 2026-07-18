package cn.iocoder.yudao.module.cloudmold.dreamplant.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DreamPlantWorldMapSnapshot implements java.io.Serializable {
    private String mapKey;
    private Long version;
    private String schemaVersion;
    private String payloadJson;
    private String payloadSha256;
    private String sourceRef;
    private Boolean publiclyReadable;
    private Instant publishedAt;
}
