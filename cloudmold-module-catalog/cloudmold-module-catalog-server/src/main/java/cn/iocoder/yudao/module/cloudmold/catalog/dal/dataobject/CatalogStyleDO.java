package cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_catalog_style")
public class CatalogStyleDO {
    @TableId(type = IdType.INPUT)
    private String styleId;
    private Long tenantId;
    private String styleCode;
    private String styleName;
    private String planningCategoryRef;
    private String brandRef;
    private Integer planningYear;
    private String seasonCode;
    private String waveCode;
    private Integer status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
