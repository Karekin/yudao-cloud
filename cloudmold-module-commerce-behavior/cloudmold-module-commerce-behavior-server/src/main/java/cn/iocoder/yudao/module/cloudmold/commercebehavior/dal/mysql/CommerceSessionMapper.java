package cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject.CommerceSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CommerceSessionMapper extends BaseMapperX<CommerceSessionDO> {

    @Select("""
            SELECT session_id,tenant_id,principal_id,channel_code,entrypoint_code,status,version,identity_link_version,
                   source_system,source_type,source_id,started_at,last_activity_at,created_at,updated_at
            FROM cloudmold_commerce_session
            WHERE tenant_id=#{tenantId} AND session_id=#{sessionId}
            FOR UPDATE
            """)
    CommerceSessionDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId);

    @Update("""
            UPDATE cloudmold_commerce_session
            SET principal_id=#{principalId},status=#{status},version=version+1,identity_link_version=#{identityLinkVersion},
                last_activity_at=#{lastActivityAt},updated_at=#{updatedAt}
            WHERE tenant_id=#{tenantId} AND session_id=#{sessionId} AND version=#{version}
            """)
    int advanceIdentity(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId,
                        @Param("version") Long version, @Param("principalId") String principalId,
                        @Param("status") String status, @Param("identityLinkVersion") Long identityLinkVersion,
                        @Param("lastActivityAt") LocalDateTime lastActivityAt,
                        @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE cloudmold_commerce_session
            SET status=#{status},version=version+1,last_activity_at=#{lastActivityAt},updated_at=#{updatedAt}
            WHERE tenant_id=#{tenantId} AND session_id=#{sessionId} AND version=#{version}
            """)
    int advanceStatus(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId,
                      @Param("version") Long version, @Param("status") String status,
                      @Param("lastActivityAt") LocalDateTime lastActivityAt,
                      @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE cloudmold_commerce_session
            SET version=version+1,last_activity_at=#{lastActivityAt},updated_at=#{updatedAt}
            WHERE tenant_id=#{tenantId} AND session_id=#{sessionId} AND version=#{version}
            """)
    int advanceActivity(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId,
              @Param("version") Long version,
              @Param("lastActivityAt") LocalDateTime lastActivityAt,
              @Param("updatedAt") LocalDateTime updatedAt);
}
