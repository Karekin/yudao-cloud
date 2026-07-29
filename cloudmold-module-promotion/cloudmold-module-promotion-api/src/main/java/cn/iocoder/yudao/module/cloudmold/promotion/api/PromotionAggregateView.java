package cn.iocoder.yudao.module.cloudmold.promotion.api;

import lombok.*;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionAggregateView implements Serializable {
    private String aggregateType;
    private String aggregateId;
    private String businessCode;
    private String status;
    private Long version;
    private Map<String, Object> attributes;
}
