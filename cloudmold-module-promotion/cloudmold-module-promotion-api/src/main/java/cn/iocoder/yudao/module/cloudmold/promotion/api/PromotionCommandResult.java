package cn.iocoder.yudao.module.cloudmold.promotion.api;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionCommandResult implements Serializable {
    private Long operationId;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private boolean duplicate;
}
