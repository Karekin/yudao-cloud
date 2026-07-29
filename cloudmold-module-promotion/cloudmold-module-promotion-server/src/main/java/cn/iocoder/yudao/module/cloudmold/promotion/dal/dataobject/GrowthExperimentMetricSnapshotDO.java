package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_growth_experiment_metric_snapshot")
public class GrowthExperimentMetricSnapshotDO {
    @TableId(type = IdType.INPUT)
    private String snapshotId;
    private Long tenantId;
    private String snapshotKey;
    private String experimentId;
    private String variantCode;
    private String metricCode;
    private LocalDateTime measuredFrom;
    private LocalDateTime measuredTo;
    private Integer sampleCount;
    private Long metricValueMicros;
    private LocalDateTime dataFreshUntil;
    private String evidenceRef;
    private LocalDateTime createdAt;
}
