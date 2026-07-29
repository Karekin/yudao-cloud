package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 入库命令幂等信封，结构与 WarehouseOperationDO 同形 */
@Data
@TableName("cloudmold_inbound_operation")
public class InboundOperationDO {
    @TableId(type = IdType.AUTO)
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String sourceEventId;
    private String operationType;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String aggregateId;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
