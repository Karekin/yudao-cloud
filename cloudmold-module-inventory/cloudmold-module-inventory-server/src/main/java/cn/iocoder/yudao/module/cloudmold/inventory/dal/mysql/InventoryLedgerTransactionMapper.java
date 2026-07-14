package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryLedgerTransactionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryLedgerTransactionMapper extends BaseMapperX<InventoryLedgerTransactionDO> {
}
