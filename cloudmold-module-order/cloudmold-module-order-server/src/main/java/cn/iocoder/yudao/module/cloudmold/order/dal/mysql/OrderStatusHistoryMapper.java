package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderStatusHistoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderStatusHistoryMapper extends BaseMapperX<OrderStatusHistoryDO> {
}
