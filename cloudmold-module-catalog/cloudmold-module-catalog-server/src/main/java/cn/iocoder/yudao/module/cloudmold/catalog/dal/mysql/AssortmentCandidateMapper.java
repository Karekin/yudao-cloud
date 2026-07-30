package cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentCandidateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AssortmentCandidateMapper extends BaseMapperX<AssortmentCandidateDO> {

    @Select("""
        SELECT * FROM cloudmold_assortment_candidate
        WHERE tenant_id=#{tenantId} AND candidate_id=#{candidateId}
        FOR UPDATE
        """)
    AssortmentCandidateDO selectForUpdate(@Param("tenantId") Long tenantId,
                                          @Param("candidateId") String candidateId);

    @Select("""
        SELECT * FROM cloudmold_assortment_candidate
        WHERE tenant_id=#{tenantId} AND wave_id=#{waveId}
        ORDER BY created_at ASC, candidate_id ASC
        """)
    List<AssortmentCandidateDO> selectByWave(@Param("tenantId") Long tenantId,
                                             @Param("waveId") String waveId);

    @Update("""
        UPDATE cloudmold_assortment_candidate
        SET trend_score=#{row.trendScore}, demand_score=#{row.demandScore},
            audience_fit_score=#{row.audienceFitScore}, supply_risk_score=#{row.supplyRiskScore},
            predicted_return_rate_bps=#{row.predictedReturnRateBps},
            weighted_score=#{row.weightedScore}, evidence_sha256=#{row.evidenceSha256},
            rationale=#{row.rationale}, status=#{row.status}, version=#{row.version},
            evaluated_at=#{row.evaluatedAt}, updated_at=#{row.updatedAt}
        WHERE tenant_id=#{tenantId} AND candidate_id=#{row.candidateId} AND version=#{expectedVersion}
        """)
    int updateEvaluation(@Param("tenantId") Long tenantId,
                         @Param("row") AssortmentCandidateDO row,
                         @Param("expectedVersion") Long expectedVersion);

    @Update("""
        UPDATE cloudmold_assortment_candidate
        SET status=#{status}, version=version+1,
            selected_at=CASE WHEN #{status}='SELECTED' THEN #{now} ELSE NULL END,
            updated_at=#{now}
        WHERE tenant_id=#{tenantId} AND candidate_id=#{candidateId}
          AND wave_id=#{waveId} AND status='EVALUATED'
        """)
    int updateSelection(@Param("tenantId") Long tenantId,
                        @Param("waveId") String waveId,
                        @Param("candidateId") String candidateId,
                        @Param("status") String status,
                        @Param("now") LocalDateTime now);
}
