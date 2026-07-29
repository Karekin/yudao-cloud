package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_growth_experiment_exposure")
public class GrowthExperimentExposureDO {
    @TableId(type = IdType.INPUT)
    private String exposureId;
    private Long tenantId;
    private String exposureKey;
    private String experimentId;
    private String variantCode;
    private String principalHash;
    private String assignmentVersion;
    private LocalDateTime exposedAt;
    private LocalDateTime createdAt;
}
