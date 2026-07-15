package cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.CommunityContentDO;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.CommunityInteractionDO;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.CommunityModerationCaseDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface CommunityMapper extends BaseMapperX<CommunityContentDO> {

    @Select("""
            SELECT content_id,tenant_id,author_principal_id,content_type,body_ref,status,version,
                   source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_community_content
            WHERE tenant_id=#{tenantId} AND content_id=#{contentId}
            """)
    CommunityContentDO selectContent(@Param("tenantId") Long tenantId, @Param("contentId") String contentId);

    @Select("""
            SELECT content_id,tenant_id,author_principal_id,content_type,body_ref,status,version,
                   source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_community_content
            WHERE tenant_id=#{tenantId} AND content_id=#{contentId}
            FOR UPDATE
            """)
    CommunityContentDO selectContentForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("contentId") String contentId);

    @Insert("""
            INSERT INTO cloudmold_engagement_community_interaction
              (interaction_id,tenant_id,actor_principal_id,interaction_type,target_type,target_id,payload_ref,
               occurred_at,created_at)
            VALUES (#{interactionId},#{tenantId},#{actorPrincipalId},#{interactionType},#{targetType},
                    #{targetId},#{payloadRef},#{occurredAt},#{createdAt})
            """)
    int insertInteraction(CommunityInteractionDO row);

    @Insert("""
            INSERT INTO cloudmold_engagement_moderation_case
              (moderation_case_id,tenant_id,content_id,reporter_principal_id,report_reason_code,evidence_ref,
               status,version,created_at,updated_at)
            VALUES (#{moderationCaseId},#{tenantId},#{contentId},#{reporterPrincipalId},#{reportReasonCode},
                    #{evidenceRef},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertModerationCase(CommunityModerationCaseDO row);

    @Select("""
            SELECT moderation_case_id,tenant_id,content_id,reporter_principal_id,moderator_principal_id,
                   report_reason_code,evidence_ref,status,decision,decision_reason_code,version,created_at,updated_at
            FROM cloudmold_engagement_moderation_case
            WHERE tenant_id=#{tenantId} AND moderation_case_id=#{moderationCaseId}
            """)
    CommunityModerationCaseDO selectModerationCase(@Param("tenantId") Long tenantId,
                                                   @Param("moderationCaseId") String moderationCaseId);

    @Select("""
            SELECT moderation_case_id,tenant_id,content_id,reporter_principal_id,moderator_principal_id,
                   report_reason_code,evidence_ref,status,decision,decision_reason_code,version,created_at,updated_at
            FROM cloudmold_engagement_moderation_case
            WHERE tenant_id=#{tenantId} AND moderation_case_id=#{moderationCaseId}
            FOR UPDATE
            """)
    CommunityModerationCaseDO selectModerationCaseForUpdate(@Param("tenantId") Long tenantId,
                                                            @Param("moderationCaseId") String moderationCaseId);

    @Update("""
            UPDATE cloudmold_engagement_moderation_case
            SET moderator_principal_id=#{moderatorPrincipalId},status=#{status},decision=#{decision},
                decision_reason_code=#{decisionReasonCode},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND moderation_case_id=#{moderationCaseId}
              AND version=#{expectedVersion} AND status='OPEN'
            """)
    int decideModerationCase(@Param("tenantId") Long tenantId,
                             @Param("moderationCaseId") String moderationCaseId,
                             @Param("expectedVersion") Long expectedVersion,
                             @Param("moderatorPrincipalId") String moderatorPrincipalId,
                             @Param("status") String status,
                             @Param("decision") String decision,
                             @Param("decisionReasonCode") String decisionReasonCode,
                             @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_engagement_community_content
            SET status=#{status},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND content_id=#{contentId} AND version=#{expectedVersion}
            """)
    int updateContentStatus(@Param("tenantId") Long tenantId, @Param("contentId") String contentId,
                            @Param("expectedVersion") Long expectedVersion, @Param("status") String status,
                            @Param("now") LocalDateTime now);
}
