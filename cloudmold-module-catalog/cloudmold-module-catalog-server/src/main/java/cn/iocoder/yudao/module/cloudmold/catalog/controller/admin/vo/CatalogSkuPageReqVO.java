package cn.iocoder.yudao.module.cloudmold.catalog.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - CloudMold 规范 SKU 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CatalogSkuPageReqVO extends PageParam {

    @Schema(description = "SKU 编码，支持模糊匹配", example = "YS-DA4E609E")
    private String skuCode;

    @Schema(description = "SPU 编码，支持模糊匹配", example = "YS-DA4E609E")
    private String spuCode;

    @Schema(description = "生命周期状态：0 DRAFT、10 ACTIVE、20 INACTIVE、90 ARCHIVED", example = "10")
    private Integer status;
}
