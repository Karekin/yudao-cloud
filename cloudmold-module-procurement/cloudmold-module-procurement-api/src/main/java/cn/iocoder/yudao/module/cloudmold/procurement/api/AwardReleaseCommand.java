package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwardReleaseCommand {
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private String awardId;
    private Long expectedAwardVersion;
}
