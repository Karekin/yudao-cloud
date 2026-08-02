package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_count")
public class StockCountDO {
    private String stockCountId;
    private Long tenantId;
    private String stockCountCode;
    private String countMode;
    private String scopeType;
    private String scopeLabel;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String reasonCode;
    private String remark;
    private String status;
    private Long version;
    private Long freezeLedgerTransactionId;
    private LocalDateTime freezeCapturedAt;
    private Integer lineCount;
    private Integer countedLineCount;
    private Integer differenceLineCount;
    private String createdByPrincipalId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
