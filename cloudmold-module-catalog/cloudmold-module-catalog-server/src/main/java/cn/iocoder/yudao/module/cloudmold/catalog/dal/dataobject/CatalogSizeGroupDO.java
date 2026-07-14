package cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_catalog_size_group")
public class CatalogSizeGroupDO {
    @TableId(type = IdType.INPUT)
    private String sizeGroupId;
    private Long tenantId;
    private String sizeGroupCode;
    private String sizeGroupName;
    private Integer status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
