package cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_catalog_size")
public class CatalogSizeDO {
    @TableId(type = IdType.INPUT)
    private String sizeId;
    private Long tenantId;
    private String sizeGroupId;
    private String sizeCode;
    private String sizeName;
    private Integer sortOrder;
    private Integer status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
