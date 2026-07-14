package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.OrderCancellationSagaItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderCancellationSagaItemMapper extends BaseMapperX<OrderCancellationSagaItemDO> {
    @Select("""
            SELECT * FROM cloudmold_order_cancellation_saga_item
            WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}
            ORDER BY order_item_id
            """)
    List<OrderCancellationSagaItemDO> selectBySaga(@Param("tenantId") Long tenantId,
                                                    @Param("sagaId") String sagaId);

    @Select("""
            SELECT * FROM cloudmold_order_cancellation_saga_item
            WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}
              AND status NOT IN ('NOT_REQUIRED','RELEASED')
            ORDER BY order_item_id LIMIT 1
            """)
    OrderCancellationSagaItemDO selectNextRelease(@Param("tenantId") Long tenantId,
                                                   @Param("sagaId") String sagaId);

    @Select("""
            SELECT * FROM cloudmold_order_cancellation_saga_item
            WHERE tenant_id=#{tenantId} AND saga_item_id=#{sagaItemId} FOR UPDATE
            """)
    OrderCancellationSagaItemDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                 @Param("sagaItemId") String sagaItemId);
}
