package cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_catalog_sku")
public class CatalogSkuDO {
    @TableId(type = IdType.INPUT)
    private String skuId;
    private Long tenantId;
    private String spuId;
    private String skuCode;
    private String colorId;
    private String sizeId;
    private String variantKey;
    private String variantKeyHash;
    private String baseUomCode;
    private Integer status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
