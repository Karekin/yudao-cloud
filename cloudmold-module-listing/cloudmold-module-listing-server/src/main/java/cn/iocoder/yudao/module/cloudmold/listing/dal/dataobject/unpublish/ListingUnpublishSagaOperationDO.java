package cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_listing_unpublish_saga_operation")
public class ListingUnpublishSagaOperationDO {
    @TableId(type = IdType.AUTO)
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String commandType;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String sagaId;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
