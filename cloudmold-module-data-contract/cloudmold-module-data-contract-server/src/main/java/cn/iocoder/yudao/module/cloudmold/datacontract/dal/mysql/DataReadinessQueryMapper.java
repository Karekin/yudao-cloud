package cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.datacontract.service.query.DataReadinessOutboxOverviewRow;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface DataReadinessQueryMapper {

    @Select("""
            SELECT COALESCE(SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END), 0) AS pendingCount,
                   COALESCE(SUM(CASE WHEN status = 10 THEN 1 ELSE 0 END), 0) AS claimedCount,
                   COALESCE(SUM(CASE WHEN status = 20 THEN 1 ELSE 0 END), 0) AS publishedCount,
                   COALESCE(SUM(CASE WHEN status = 30 THEN 1 ELSE 0 END), 0) AS deadCount,
                   MIN(CASE WHEN status = 0 THEN recorded_at END) AS oldestPendingRecordedAt,
                   MAX(recorded_at) AS latestRecordedAt,
                   MAX(CASE WHEN status = 20 THEN published_at END) AS latestPublishedAt
            FROM cloudmold_event_outbox
            WHERE tenant_id = #{tenantId}
            """)
    DataReadinessOutboxOverviewRow selectOutboxOverview(@Param("tenantId") Long tenantId);
}
