package cn.iocoder.yudao.module.cloudmold.risk.api;

import lombok.*;

import java.time.Instant;

@Value
@Builder
public class IntelligenceEventTaxonomyReference {
    String taxonomyId;
    String taxonomyVersionId;
    Long definitionVersion;
    String eventCode;
    String intelligenceLevel;
    String levelsSha256;
    Instant effectiveFrom;
}
