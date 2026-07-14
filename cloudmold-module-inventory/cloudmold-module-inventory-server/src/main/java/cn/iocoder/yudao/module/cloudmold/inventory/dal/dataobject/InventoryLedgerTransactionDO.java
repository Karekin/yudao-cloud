package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cloudmold_inventory_ledger_transaction")
@Data
public class InventoryLedgerTransactionDO {

    @TableId(type = IdType.AUTO)
    private Long ledgerTransactionId;
    private Long tenantId;
    private Long operationId;
    private String commandType;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private String businessNo;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;

}
