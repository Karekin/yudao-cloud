package cn.iocoder.yudao.module.cloudmold.tokenplatform.api;

import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenPlatformAggregateView {
    private String aggregateType;
    private String aggregateId;
    private String businessCode;
    private String status;
    private Long version;
    private Map<String, Object> attributes;
}
