package cn.iocoder.yudao.module.cloudmold.quality.service.query;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class QualityWorkItem {
    private String aggregateId;
    private String itemType;
    private String code;
    private String relatedRef;
    private String status;
    private Long aggregateVersion;
    private LocalDate dueDate;
    private LocalDateTime updatedAt;
}
