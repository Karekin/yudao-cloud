package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.GrowthExperimentMetricSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GrowthExperimentMetricSnapshotMapper extends BaseMapperX<GrowthExperimentMetricSnapshotDO> {
    @Select("""
        SELECT * FROM cloudmold_promotion_growth_experiment_metric_snapshot
        WHERE tenant_id=#{tenantId} AND snapshot_key=#{snapshotKey}
        """)
    GrowthExperimentMetricSnapshotDO selectByKey(@Param("tenantId") Long tenantId,
                                                 @Param("snapshotKey") String snapshotKey);

    @Select("""
        SELECT s.* FROM cloudmold_promotion_growth_experiment_metric_snapshot s
        INNER JOIN (
          SELECT variant_code, MAX(measured_to) measured_to
          FROM cloudmold_promotion_growth_experiment_metric_snapshot
          WHERE tenant_id=#{tenantId} AND experiment_id=#{experimentId} AND metric_code=#{metricCode}
          GROUP BY variant_code
        ) latest ON latest.variant_code=s.variant_code AND latest.measured_to=s.measured_to
        WHERE s.tenant_id=#{tenantId} AND s.experiment_id=#{experimentId} AND s.metric_code=#{metricCode}
        ORDER BY s.variant_code
        """)
    List<GrowthExperimentMetricSnapshotDO> selectLatestByVariant(
            @Param("tenantId") Long tenantId, @Param("experimentId") String experimentId,
            @Param("metricCode") String metricCode);
}
