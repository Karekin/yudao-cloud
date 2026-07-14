package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.returning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning.ReturnInspectionDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReturnInspectionMapper extends BaseMapperX<ReturnInspectionDO> {
    @Select("SELECT * FROM cloudmold_return_inspection WHERE tenant_id=#{tenantId} AND return_fulfillment_id=#{id}")
    ReturnInspectionDO selectByFulfillment(@Param("tenantId") Long tenantId, @Param("id") String id);
}
