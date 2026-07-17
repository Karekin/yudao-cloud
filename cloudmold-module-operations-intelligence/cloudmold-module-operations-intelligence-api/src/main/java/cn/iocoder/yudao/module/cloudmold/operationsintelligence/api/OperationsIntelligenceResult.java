package cn.iocoder.yudao.module.cloudmold.operationsintelligence.api;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsIntelligenceResult {
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
}
