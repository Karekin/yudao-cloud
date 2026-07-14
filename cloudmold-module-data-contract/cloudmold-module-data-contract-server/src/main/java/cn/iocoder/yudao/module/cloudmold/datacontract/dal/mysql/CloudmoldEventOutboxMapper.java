package cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject.CloudmoldEventOutboxDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true") // Outbox relay is cross-tenant; every business lookup supplies tenant explicitly.
public interface CloudmoldEventOutboxMapper extends BaseMapperX<CloudmoldEventOutboxDO> {

    default CloudmoldEventOutboxDO selectByIdempotencyKey(Long tenantId, String eventType, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<CloudmoldEventOutboxDO>()
                .eq(CloudmoldEventOutboxDO::getTenantId, tenantId)
                .eq(CloudmoldEventOutboxDO::getEventType, eventType)
                .eq(CloudmoldEventOutboxDO::getIdempotencyKey, idempotencyKey));
    }

    @Select("""
            SELECT event_id
            FROM cloudmold_event_outbox
            WHERE available_at <= #{now}
              AND (status = 0 OR (status = 10 AND lease_until <= #{now}))
            ORDER BY available_at, recorded_at, event_id
            LIMIT #{batchSize}
            """)
    List<String> selectClaimableEventIds(@Param("now") LocalDateTime now,
                                         @Param("batchSize") int batchSize);

    @Update("""
            UPDATE cloudmold_event_outbox
            SET status = 10, lease_owner = #{leaseOwner}, lease_until = #{leaseUntil}
            WHERE event_id = #{eventId}
              AND available_at <= #{now}
              AND (status = 0 OR (status = 10 AND lease_until <= #{now}))
            """)
    int claim(@Param("eventId") String eventId,
              @Param("leaseOwner") String leaseOwner,
              @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_event_outbox
            SET status = 20, published_at = #{publishedAt},
                lease_owner = NULL, lease_until = NULL,
                last_error_code = NULL, last_error_summary = NULL
            WHERE event_id = #{eventId} AND status = 10 AND lease_owner = #{leaseOwner}
            """)
    int markPublished(@Param("eventId") String eventId,
                      @Param("leaseOwner") String leaseOwner,
                      @Param("publishedAt") LocalDateTime publishedAt);

    @Update("""
            UPDATE cloudmold_event_outbox
            SET status = #{nextStatus}, attempt_count = attempt_count + 1,
                available_at = #{availableAt}, lease_owner = NULL, lease_until = NULL,
                last_error_code = #{errorCode}, last_error_summary = #{errorSummary}
            WHERE event_id = #{eventId} AND status = 10 AND lease_owner = #{leaseOwner}
            """)
    int markPublishFailed(@Param("eventId") String eventId,
                          @Param("leaseOwner") String leaseOwner,
                          @Param("nextStatus") int nextStatus,
                          @Param("availableAt") LocalDateTime availableAt,
                          @Param("errorCode") String errorCode,
                          @Param("errorSummary") String errorSummary);

    @Update("""
            UPDATE cloudmold_event_outbox
            SET status = 0, available_at = #{availableAt},
                lease_owner = NULL, lease_until = NULL,
                last_error_code = 'UNSUPPORTED_DESTINATION', last_error_summary = #{errorSummary}
            WHERE event_id = #{eventId} AND status = 10 AND lease_owner = #{leaseOwner}
            """)
    int releaseUnsupported(@Param("eventId") String eventId,
                           @Param("leaseOwner") String leaseOwner,
                           @Param("availableAt") LocalDateTime availableAt,
                           @Param("errorSummary") String errorSummary);

}
