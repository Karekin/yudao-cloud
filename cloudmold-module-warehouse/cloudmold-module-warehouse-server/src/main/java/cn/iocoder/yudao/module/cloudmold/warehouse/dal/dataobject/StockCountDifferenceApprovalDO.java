package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_count_difference_approval")
public class StockCountDifferenceApprovalDO {
    private String approvalId;
    private Long tenantId;
    private String stockCountId;
    private String approvalType;
    private String approvedByPrincipalId;
    private BigDecimal totalBookOnHandQuantity;
    private BigDecimal totalCountedOnHandQuantity;
    private BigDecimal totalDifferenceQuantity;
    private String remark;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
