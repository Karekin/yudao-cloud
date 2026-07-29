package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_growth_experiment_variant")
public class GrowthExperimentVariantDO {
    @TableId(type = IdType.INPUT)
    private String variantId;
    private Long tenantId;
    private String experimentId;
    private String variantCode;
    private String variantKind;
    private Integer allocationBasisPoints;
    private LocalDateTime createdAt;
}
