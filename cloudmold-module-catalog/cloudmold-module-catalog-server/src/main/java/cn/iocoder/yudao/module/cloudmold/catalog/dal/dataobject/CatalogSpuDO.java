package cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_catalog_spu")
public class CatalogSpuDO {
    @TableId(type = IdType.INPUT)
    private String spuId;
    private Long tenantId;
    private String styleId;
    private String spuCode;
    private String productName;
    private String salesCategoryRef;
    private Integer status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
