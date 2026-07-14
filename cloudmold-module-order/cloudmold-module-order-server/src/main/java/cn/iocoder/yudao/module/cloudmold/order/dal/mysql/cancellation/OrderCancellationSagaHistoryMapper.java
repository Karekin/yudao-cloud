package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.OrderCancellationSagaHistoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderCancellationSagaHistoryMapper extends BaseMapperX<OrderCancellationSagaHistoryDO> {
}
