package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class SourceMappingView {
    private String mappingId;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String targetType;
    private String targetId;
    private Instant validFrom;
    private Instant validTo;
    private String verificationRef;
    private String migrationRunId;
    private String status;
    private Long version;
}
