package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplenishmentExecutionView {
    private String recommendationId;
    private String planId;
    private String recommendationStatus;
    private String targetType;
    private String targetAggregateType;
    private String targetAggregateId;
    private String targetAggregateNo;
    private String targetAggregateStatus;
    private BigDecimal requestedQuantity;
    private String uomCode;
    private LocalDate needByDate;
    private String convertedByPrincipalId;
    private LocalDateTime convertedAt;
}
