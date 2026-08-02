package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PutawayLineMapper extends BaseMapperX<PutawayLineDO> {
    @Select("SELECT * FROM cloudmold_warehouse_procurement_putaway_line WHERE tenant_id=#{tenantId} AND putaway_id=#{putawayId} ORDER BY created_at,putaway_line_id")
    List<PutawayLineDO> selectByPutaway(@Param("tenantId") Long tenantId, @Param("putawayId") String putawayId);
}
