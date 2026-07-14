package cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_catalog_barcode")
public class CatalogBarcodeDO {
    @TableId(type = IdType.INPUT)
    private String barcodeId;
    private Long tenantId;
    private String skuId;
    private String barcode;
    private String barcodeType;
    private Boolean isPrimary;
    private Integer status;
    private Long version;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
