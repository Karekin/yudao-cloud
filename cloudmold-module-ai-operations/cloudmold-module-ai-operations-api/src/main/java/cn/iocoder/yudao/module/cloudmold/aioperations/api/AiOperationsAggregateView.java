package cn.iocoder.yudao.module.cloudmold.aioperations.api;

import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOperationsAggregateView {
    private String aggregateType;
    private String aggregateId;
    private String businessCode;
    private String status;
    private Long version;
    private Map<String, Object> attributes;
}
