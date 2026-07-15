package cn.iocoder.yudao.module.cloudmold.tokenplatform.api;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenPlatformCommandResult {
    private Long operationId;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private boolean duplicate;
}
