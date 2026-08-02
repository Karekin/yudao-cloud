package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_supplier_return_dispatch_batch")
public class SupplierReturnDispatchBatchDO {
    private String batchId;
    private Long tenantId;
    private String returnId;
    private String batchNo;
    private String status;
    private String dispatchedByPrincipalId;
    private String remark;
    private Long version;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
