package cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.unpublish;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish.ListingUnpublishSagaItemDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ListingUnpublishSagaItemMapper extends BaseMapperX<ListingUnpublishSagaItemDO> {
    @Select("""
            SELECT * FROM cloudmold_listing_unpublish_saga_item
            WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}
            ORDER BY listing_id
            """)
    List<ListingUnpublishSagaItemDO> selectBySaga(@Param("tenantId") Long tenantId,
                                                   @Param("sagaId") String sagaId);

    @Select("""
            SELECT * FROM cloudmold_listing_unpublish_saga_item
            WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}
              AND status IN ('PENDING','RETRY_SCHEDULED')
            ORDER BY listing_id LIMIT 1
            """)
    ListingUnpublishSagaItemDO selectNext(@Param("tenantId") Long tenantId,
                                          @Param("sagaId") String sagaId);

    @Select("""
            SELECT * FROM cloudmold_listing_unpublish_saga_item
            WHERE tenant_id=#{tenantId} AND saga_item_id=#{sagaItemId}
            FOR UPDATE
            """)
    ListingUnpublishSagaItemDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                @Param("sagaItemId") String sagaItemId);
}
