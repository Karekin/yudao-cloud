package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_scrap_history")
public class InventoryScrapHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long tenantId;
    private String scrapId;
    private String status;
    private Long statusVersion;
    private String actorPrincipalId;
    private String note;
    private Long operationId;
    private LocalDateTime changedAt;
}
