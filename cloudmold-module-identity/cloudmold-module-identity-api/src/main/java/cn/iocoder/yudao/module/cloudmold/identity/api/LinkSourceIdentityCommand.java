package cn.iocoder.yudao.module.cloudmold.identity.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkSourceIdentityCommand {
    private String idempotencyKey;
    private String runId;
    private String principalId;
    private Long expectedVersion;
    private String principalType;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
