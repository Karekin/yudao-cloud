package cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentWaveDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentWaveHistoryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AssortmentWaveMapper extends BaseMapperX<AssortmentWaveDO> {

    @Select("""
        SELECT * FROM cloudmold_assortment_wave
        WHERE tenant_id=#{tenantId} AND wave_id=#{waveId}
        """)
    AssortmentWaveDO selectOneById(@Param("tenantId") Long tenantId,
                                   @Param("waveId") String waveId);

    @Select("""
        SELECT * FROM cloudmold_assortment_wave
        WHERE tenant_id=#{tenantId} AND wave_id=#{waveId}
        FOR UPDATE
        """)
    AssortmentWaveDO selectForUpdate(@Param("tenantId") Long tenantId,
                                     @Param("waveId") String waveId);

    @Update("""
        UPDATE cloudmold_assortment_wave
        SET status=#{row.status},
            candidate_count=#{row.candidateCount},
            evaluated_candidate_count=#{row.evaluatedCandidateCount},
            selected_style_count=#{row.selectedStyleCount},
            decision_policy_version=#{row.decisionPolicyVersion},
            decision_evidence_sha256=#{row.decisionEvidenceSha256},
            decision_summary=#{row.decisionSummary},
            approved_by_principal_id=#{row.approvedByPrincipalId},
            approval_evidence_sha256=#{row.approvalEvidenceSha256},
            launch_calendar_ref=#{row.launchCalendarRef},
            downstream_handoff_ref=#{row.downstreamHandoffRef},
            publication_evidence_sha256=#{row.publicationEvidenceSha256},
            reason_code=#{row.reasonCode},
            version=#{row.version},
            selected_at=#{row.selectedAt},
            approved_at=#{row.approvedAt},
            published_at=#{row.publishedAt},
            updated_at=#{row.updatedAt}
        WHERE tenant_id=#{tenantId} AND wave_id=#{row.waveId} AND version=#{expectedVersion}
        """)
    int updateWorkflowState(@Param("tenantId") Long tenantId,
                            @Param("row") AssortmentWaveDO row,
                            @Param("expectedVersion") Long expectedVersion);

    @Insert("""
        INSERT INTO cloudmold_assortment_wave_history
          (wave_id,tenant_id,aggregate_version,command_type,from_status,to_status,
           actor_principal_id,reason_code,evidence_sha256,created_at)
        VALUES (#{waveId},#{tenantId},#{aggregateVersion},#{commandType},#{fromStatus},#{toStatus},
                #{actorPrincipalId},#{reasonCode},#{evidenceSha256},#{createdAt})
        """)
    int insertHistory(AssortmentWaveHistoryDO history);
}
