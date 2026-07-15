package cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject.AiApplicationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface AiApplicationMapper extends BaseMapperX<AiApplicationDO> {
    @Select("SELECT * FROM cloudmold_ai_ops_application WHERE tenant_id=#{tenantId} AND application_id=#{id}")
    AiApplicationDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_ai_ops_application WHERE tenant_id=#{tenantId} AND application_id=#{id} FOR UPDATE")
    AiApplicationDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_ai_ops_application WHERE tenant_id=#{tenantId} AND application_code=#{code}")
    AiApplicationDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Update("UPDATE cloudmold_ai_ops_application SET status=#{status},version=version+1,updated_at=#{now} WHERE tenant_id=#{tenantId} AND application_id=#{id} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("now") LocalDateTime now);
}
