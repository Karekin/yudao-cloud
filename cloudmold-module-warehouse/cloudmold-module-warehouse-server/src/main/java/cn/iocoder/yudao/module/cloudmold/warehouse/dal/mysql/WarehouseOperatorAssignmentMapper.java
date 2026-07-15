package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseOperatorAssignmentDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface WarehouseOperatorAssignmentMapper extends BaseMapperX<WarehouseOperatorAssignmentDO> {
    @Select("SELECT * FROM cloudmold_warehouse_operator_assignment WHERE tenant_id=#{tenantId} AND assignment_id=#{id} FOR UPDATE")
    WarehouseOperatorAssignmentDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Update("UPDATE cloudmold_warehouse_operator_assignment SET status='ENDED',valid_to=#{at},version=version+1,updated_at=#{at} WHERE tenant_id=#{tenantId} AND assignment_id=#{id} AND version=#{version} AND status='ACTIVE'")
    int endCas(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("version") Long version,
               @Param("at") LocalDateTime at);
}
