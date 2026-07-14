package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryLedgerEntryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryLedgerEntryMapper extends BaseMapperX<InventoryLedgerEntryDO> {
}
