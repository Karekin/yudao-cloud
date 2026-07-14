package cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_after_sale_history")
public class AfterSaleHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long tenantId;
    private String afterSaleId;
    private Long aggregateVersion;
    private String previousStatus;
    private String currentStatus;
    private Long operationId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
