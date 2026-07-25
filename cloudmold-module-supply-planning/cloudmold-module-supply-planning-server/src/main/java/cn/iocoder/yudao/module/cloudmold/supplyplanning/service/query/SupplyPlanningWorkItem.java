package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SupplyPlanningWorkItem {
    private String aggregateId;
    private String itemType;
    private String code;
    private String relatedRef;
    private String status;
    private Long aggregateVersion;
    private LocalDate businessDate;
    private LocalDateTime updatedAt;
}
