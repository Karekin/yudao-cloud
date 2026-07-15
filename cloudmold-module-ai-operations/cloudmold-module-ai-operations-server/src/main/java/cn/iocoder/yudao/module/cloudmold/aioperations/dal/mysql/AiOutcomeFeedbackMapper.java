package cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject.AiOutcomeFeedbackDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiOutcomeFeedbackMapper extends BaseMapperX<AiOutcomeFeedbackDO> {
    @Select("SELECT * FROM cloudmold_ai_ops_outcome_feedback WHERE tenant_id=#{tenantId} AND feedback_id=#{id}")
    AiOutcomeFeedbackDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_ai_ops_outcome_feedback WHERE tenant_id=#{tenantId} AND feedback_key=#{key}")
    AiOutcomeFeedbackDO selectByFeedbackKey(@Param("tenantId") Long tenantId, @Param("key") String key);
}
