package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StockCountPageItem {
    private String stockCountId;
    private String stockCountCode;
    private String countMode;
    private String scopeType;
    private String scopeLabel;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String status;
    private Long version;
    private Integer lineCount;
    private Integer countedLineCount;
    private Integer differenceLineCount;
    private Long freezeLedgerTransactionId;
    private LocalDateTime freezeCapturedAt;
    private LocalDateTime updatedAt;
}
