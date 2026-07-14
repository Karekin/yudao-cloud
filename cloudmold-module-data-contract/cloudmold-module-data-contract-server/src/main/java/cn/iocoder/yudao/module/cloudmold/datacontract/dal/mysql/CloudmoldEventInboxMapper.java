package cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject.CloudmoldEventInboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CloudmoldEventInboxMapper extends BaseMapperX<CloudmoldEventInboxDO> {

    @Select("""
            SELECT consumer_id, event_id, tenant_id, event_type, schema_version,
                   payload_hash, processed_at, result_hash
            FROM cloudmold_event_inbox
            WHERE consumer_id = #{consumerId} AND event_id = #{eventId}
            """)
    CloudmoldEventInboxDO selectOne(@Param("consumerId") String consumerId,
                                    @Param("eventId") String eventId);

    @Insert("""
            INSERT INTO cloudmold_event_inbox
              (consumer_id, event_id, tenant_id, event_type, schema_version, payload_hash, processed_at)
            VALUES
              (#{consumerId}, #{eventId}, #{tenantId}, #{eventType}, #{schemaVersion}, #{payloadHash}, #{processedAt})
            """)
    int insertClaim(CloudmoldEventInboxDO row);

    @Update("""
            UPDATE cloudmold_event_inbox SET result_hash = #{resultHash}, processed_at = #{processedAt}
            WHERE consumer_id = #{consumerId} AND event_id = #{eventId}
            """)
    int complete(@Param("consumerId") String consumerId,
                 @Param("eventId") String eventId,
                 @Param("resultHash") String resultHash,
                 @Param("processedAt") java.time.LocalDateTime processedAt);

}
