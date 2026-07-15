package cn.iocoder.yudao.module.cloudmold.aioperations.api;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOperationsCommandResult {
    private Long operationId;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private boolean duplicate;
}
