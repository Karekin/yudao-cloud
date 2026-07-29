package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PutawayMapper extends BaseMapperX<PutawayDO> {
    @Select("SELECT * FROM cloudmold_putaway WHERE tenant_id=#{tenantId} AND putaway_id=#{putawayId} FOR UPDATE")
    PutawayDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("putawayId") String putawayId);

    @Select("SELECT * FROM cloudmold_putaway WHERE tenant_id=#{tenantId} AND receipt_id=#{receiptId}")
    PutawayDO selectByReceipt(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId);

    @Update("UPDATE cloudmold_putaway SET status=#{status},version=version+1,updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND putaway_id=#{putawayId} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("putawayId") String putawayId,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("now") LocalDateTime now);
}
