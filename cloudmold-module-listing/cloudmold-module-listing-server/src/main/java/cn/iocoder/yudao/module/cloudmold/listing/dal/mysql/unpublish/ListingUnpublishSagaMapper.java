package cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.unpublish;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish.ListingUnpublishSagaDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ListingUnpublishSagaMapper extends BaseMapperX<ListingUnpublishSagaDO> {
    @Select("SELECT * FROM cloudmold_listing_unpublish_saga WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}")
    ListingUnpublishSagaDO selectTenantSaga(@Param("tenantId") Long tenantId, @Param("sagaId") String sagaId);

    @Select("""
            SELECT * FROM cloudmold_listing_unpublish_saga
            WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}
            FOR UPDATE
            """)
    ListingUnpublishSagaDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("sagaId") String sagaId);

    @Select("""
            SELECT * FROM cloudmold_listing_unpublish_saga
            WHERE tenant_id=#{tenantId} AND source_event_id=#{sourceEventId}
            """)
    ListingUnpublishSagaDO selectBySourceEvent(@Param("tenantId") Long tenantId,
                                               @Param("sourceEventId") String sourceEventId);

    @Select("""
            SELECT * FROM cloudmold_listing_unpublish_saga
            WHERE status IN ('REQUESTED','RETRY_SCHEDULED')
              AND (next_retry_at IS NULL OR next_retry_at <= #{now})
              AND (lease_until IS NULL OR lease_until <= #{now})
            ORDER BY COALESCE(next_retry_at,created_at),created_at,saga_id
            LIMIT #{batchSize}
            """)
    List<ListingUnpublishSagaDO> selectDue(@Param("now") LocalDateTime now,
                                           @Param("batchSize") int batchSize);

    @Update("""
            UPDATE cloudmold_listing_unpublish_saga
            SET lease_owner=#{leaseOwner},lease_until=#{leaseUntil},attempt_count=attempt_count+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}
              AND status IN ('REQUESTED','RETRY_SCHEDULED')
              AND (next_retry_at IS NULL OR next_retry_at <= #{now})
              AND (lease_until IS NULL OR lease_until <= #{now})
            """)
    int claim(@Param("tenantId") Long tenantId, @Param("sagaId") String sagaId,
              @Param("leaseOwner") String leaseOwner, @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("now") LocalDateTime now);
}
