package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.GrowthExperimentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface GrowthExperimentMapper extends BaseMapperX<GrowthExperimentDO> {
    @Select("SELECT * FROM cloudmold_promotion_growth_experiment WHERE tenant_id=#{tenantId} AND experiment_id=#{id}")
    GrowthExperimentDO selectById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_promotion_growth_experiment WHERE tenant_id=#{tenantId} AND experiment_id=#{id} FOR UPDATE")
    GrowthExperimentDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_promotion_growth_experiment WHERE tenant_id=#{tenantId} AND experiment_code=#{code}")
    GrowthExperimentDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Update("""
        UPDATE cloudmold_promotion_growth_experiment
        SET status=#{status}, started_at=#{occurredAt}, version=version+1, updated_at=#{occurredAt}
        WHERE tenant_id=#{tenantId} AND experiment_id=#{experimentId} AND version=#{version} AND status='DRAFT'
        """)
    int startCas(@Param("tenantId") Long tenantId, @Param("experimentId") String experimentId,
                 @Param("version") Long version, @Param("status") String status,
                 @Param("occurredAt") LocalDateTime occurredAt);

    @Update("""
        UPDATE cloudmold_promotion_growth_experiment
        SET status='CONCLUDED', concluded_at=#{occurredAt}, decision=#{decision},
            confidence_basis_points=#{confidenceBasisPoints}, guardrail_status=#{guardrailStatus},
            conclusion_evidence_ref=#{evidenceRef}, conclusion_reason=#{reason},
            version=version+1, updated_at=#{occurredAt}
        WHERE tenant_id=#{tenantId} AND experiment_id=#{experimentId} AND version=#{version} AND status='RUNNING'
        """)
    int concludeCas(@Param("tenantId") Long tenantId, @Param("experimentId") String experimentId,
                    @Param("version") Long version, @Param("decision") String decision,
                    @Param("confidenceBasisPoints") Integer confidenceBasisPoints,
                    @Param("guardrailStatus") String guardrailStatus,
                    @Param("evidenceRef") String evidenceRef, @Param("reason") String reason,
                    @Param("occurredAt") LocalDateTime occurredAt);

    @Update("""
        UPDATE cloudmold_promotion_growth_experiment
        SET status='CANCELLED', concluded_at=#{occurredAt}, conclusion_reason=#{reason},
            version=version+1, updated_at=#{occurredAt}
        WHERE tenant_id=#{tenantId} AND experiment_id=#{experimentId} AND version=#{version}
          AND status IN ('DRAFT','RUNNING')
        """)
    int cancelCas(@Param("tenantId") Long tenantId, @Param("experimentId") String experimentId,
                  @Param("version") Long version, @Param("reason") String reason,
                  @Param("occurredAt") LocalDateTime occurredAt);
}
