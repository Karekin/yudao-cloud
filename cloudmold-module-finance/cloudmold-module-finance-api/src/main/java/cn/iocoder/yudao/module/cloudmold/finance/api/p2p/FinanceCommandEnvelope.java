package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCommandEnvelope implements Serializable {
    private String correlationId;
    private String causationId;
    private String runId;
    private String idempotencyKey;
    private Instant occurredAt;
}
