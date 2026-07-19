package cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.datacontract.service.query.EventOutboxPageItem;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件外发表被 Relay 跨租户使用，全局租户拦截器对本表已关闭，
 * 故显式声明 @InterceptorIgnore 并在 SQL 中带 tenant_id 过滤。
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface EventOutboxPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_event_outbox o
            WHERE o.tenant_id = #{tenantId}
            <if test="eventId != null">AND o.event_id = #{eventId}</if>
            <if test="eventType != null">AND o.event_type = #{eventType}</if>
            <if test="status != null">AND o.status = #{status}</if>
            <if test="aggregateType != null">AND o.aggregate_type = #{aggregateType}</if>
            <if test="aggregateId != null">AND o.aggregate_id = #{aggregateId}</if>
            <if test="recordedAtFrom != null">AND o.recorded_at &gt;= #{recordedAtFrom}</if>
            <if test="recordedAtTo != null">AND o.recorded_at &lt;= #{recordedAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("eventId") String eventId,
                   @Param("eventType") String eventType,
                   @Param("status") Integer status,
                   @Param("aggregateType") String aggregateType,
                   @Param("aggregateId") String aggregateId,
                   @Param("recordedAtFrom") LocalDateTime recordedAtFrom,
                   @Param("recordedAtTo") LocalDateTime recordedAtTo);

    @Select("""
            <script>
            SELECT o.event_id,
                   o.event_type,
                   o.schema_version,
                   o.aggregate_type,
                   o.aggregate_id,
                   o.aggregate_version,
                   o.status,
                   o.destination,
                   o.attempt_count,
                   o.occurred_at,
                   o.recorded_at,
                   o.published_at
            FROM cloudmold_event_outbox o
            WHERE o.tenant_id = #{tenantId}
            <if test="eventId != null">AND o.event_id = #{eventId}</if>
            <if test="eventType != null">AND o.event_type = #{eventType}</if>
            <if test="status != null">AND o.status = #{status}</if>
            <if test="aggregateType != null">AND o.aggregate_type = #{aggregateType}</if>
            <if test="aggregateId != null">AND o.aggregate_id = #{aggregateId}</if>
            <if test="recordedAtFrom != null">AND o.recorded_at &gt;= #{recordedAtFrom}</if>
            <if test="recordedAtTo != null">AND o.recorded_at &lt;= #{recordedAtTo}</if>
            ORDER BY o.recorded_at DESC, o.event_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<EventOutboxPageItem> selectPage(@Param("tenantId") Long tenantId,
                                         @Param("eventId") String eventId,
                                         @Param("eventType") String eventType,
                                         @Param("status") Integer status,
                                         @Param("aggregateType") String aggregateType,
                                         @Param("aggregateId") String aggregateId,
                                         @Param("recordedAtFrom") LocalDateTime recordedAtFrom,
                                         @Param("recordedAtTo") LocalDateTime recordedAtTo,
                                         @Param("offset") long offset,
                                         @Param("limit") int limit);
}
