package cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.CommunityContentDO;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.CommunityInteractionDO;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.CommunityModerationCaseDO;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.CommunityReactionStateDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommunityMapper extends BaseMapperX<CommunityContentDO> {

    @Select("""
            SELECT content_id,tenant_id,author_principal_id,content_type,body_ref,body_key_id,body_iv,
                   body_ciphertext,body_digest_sha256,canonical_spu_id,canonical_sku_id,listing_id,
                   listing_offer_id,status,version,
                   source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_community_content
            WHERE tenant_id=#{tenantId} AND content_id=#{contentId}
            """)
    CommunityContentDO selectContent(@Param("tenantId") Long tenantId, @Param("contentId") String contentId);

    @Select("""
            SELECT content_id,tenant_id,author_principal_id,content_type,body_ref,body_key_id,body_iv,
                   body_ciphertext,body_digest_sha256,canonical_spu_id,canonical_sku_id,listing_id,
                   listing_offer_id,status,version,
                   source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_community_content
            WHERE tenant_id=#{tenantId} AND content_id=#{contentId}
            FOR UPDATE
            """)
    CommunityContentDO selectContentForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("contentId") String contentId);

    @Select("""
            SELECT content_id,tenant_id,author_principal_id,content_type,body_ref,body_key_id,body_iv,
                   body_ciphertext,body_digest_sha256,canonical_spu_id,canonical_sku_id,listing_id,
                   listing_offer_id,status,version,source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_community_content
            WHERE tenant_id=#{tenantId} AND status='PUBLISHED' AND content_type='POST'
            ORDER BY created_at DESC,content_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<CommunityContentDO> selectPublishedPage(@Param("tenantId") Long tenantId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_engagement_community_content
            WHERE tenant_id=#{tenantId} AND status='PUBLISHED' AND content_type='POST'
            """)
    long countPublished(@Param("tenantId") Long tenantId);

    @Insert("""
            INSERT INTO cloudmold_engagement_community_interaction
              (interaction_id,tenant_id,actor_principal_id,interaction_type,target_type,target_id,payload_ref,
               payload_key_id,payload_iv,payload_ciphertext,payload_digest_sha256,occurred_at,created_at)
            VALUES (#{interactionId},#{tenantId},#{actorPrincipalId},#{interactionType},#{targetType},
                    #{targetId},#{payloadRef},#{payloadKeyId},#{payloadIv},#{payloadCiphertext},
                    #{payloadDigestSha256},#{occurredAt},#{createdAt})
            """)
    int insertInteraction(CommunityInteractionDO row);

    @Select("""
            SELECT interaction_id,tenant_id,actor_principal_id,interaction_type,target_type,target_id,payload_ref,
                   payload_key_id,payload_iv,payload_ciphertext,payload_digest_sha256,occurred_at,created_at
            FROM cloudmold_engagement_community_interaction
            WHERE tenant_id=#{tenantId} AND interaction_type='COMMENT' AND target_type='CONTENT'
              AND target_id=#{contentId}
            ORDER BY occurred_at ASC,interaction_id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<CommunityInteractionDO> selectComments(@Param("tenantId") Long tenantId,
                                                @Param("contentId") String contentId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_engagement_community_interaction
            WHERE tenant_id=#{tenantId} AND interaction_type='COMMENT' AND target_type='CONTENT'
              AND target_id=#{contentId}
            """)
    long countComments(@Param("tenantId") Long tenantId, @Param("contentId") String contentId);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_engagement_community_reaction_state
            WHERE tenant_id=#{tenantId} AND reaction_type='LIKE' AND target_type='CONTENT'
              AND target_id=#{contentId} AND status='ACTIVE'
            """)
    long countActiveLikes(@Param("tenantId") Long tenantId, @Param("contentId") String contentId);

    @Select("""
            SELECT reaction_id,tenant_id,actor_principal_id,reaction_type,target_type,target_id,status,version,
                   created_at,updated_at
            FROM cloudmold_engagement_community_reaction_state
            WHERE tenant_id=#{tenantId} AND actor_principal_id=#{actorPrincipalId}
              AND reaction_type=#{reactionType} AND target_type=#{targetType} AND target_id=#{targetId}
            """)
    CommunityReactionStateDO selectReaction(@Param("tenantId") Long tenantId,
                                            @Param("actorPrincipalId") String actorPrincipalId,
                                            @Param("reactionType") String reactionType,
                                            @Param("targetType") String targetType,
                                            @Param("targetId") String targetId);

    @Select("""
            SELECT reaction_id,tenant_id,actor_principal_id,reaction_type,target_type,target_id,status,version,
                   created_at,updated_at
            FROM cloudmold_engagement_community_reaction_state
            WHERE tenant_id=#{tenantId} AND actor_principal_id=#{actorPrincipalId}
              AND reaction_type=#{reactionType} AND target_type=#{targetType} AND target_id=#{targetId}
            FOR UPDATE
            """)
    CommunityReactionStateDO selectReactionForUpdate(@Param("tenantId") Long tenantId,
                                                     @Param("actorPrincipalId") String actorPrincipalId,
                                                     @Param("reactionType") String reactionType,
                                                     @Param("targetType") String targetType,
                                                     @Param("targetId") String targetId);

    @Insert("""
            INSERT INTO cloudmold_engagement_community_reaction_state
              (reaction_id,tenant_id,actor_principal_id,reaction_type,target_type,target_id,status,version,
               created_at,updated_at)
            VALUES (#{reactionId},#{tenantId},#{actorPrincipalId},#{reactionType},#{targetType},#{targetId},
                    #{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertReaction(CommunityReactionStateDO row);

    @Update("""
            UPDATE cloudmold_engagement_community_reaction_state
            SET status=#{status},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND reaction_id=#{reactionId} AND version=#{expectedVersion}
            """)
    int updateReaction(@Param("tenantId") Long tenantId, @Param("reactionId") String reactionId,
                       @Param("expectedVersion") Long expectedVersion, @Param("status") String status,
                       @Param("now") LocalDateTime now);

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
