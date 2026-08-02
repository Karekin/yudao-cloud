package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_ledger_transaction_v3")
public class InventoryV3LedgerTransactionDO {
    @TableId(type = IdType.AUTO)
    private Long ledgerTransactionId;
    private Long tenantId;
    private Long operationId;
    private Long stockTransferOperationId;
    private Long procurementReceiptOperationId;
    private String movementGroupId;
    private String commandType;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private String businessNo;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
