package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AsnMapper extends BaseMapperX<AsnDO> {
    @Select("SELECT * FROM cloudmold_warehouse_procurement_asn WHERE tenant_id=#{tenantId} AND asn_id=#{asnId} FOR UPDATE")
    AsnDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("asnId") String asnId);

    @Select("SELECT * FROM cloudmold_warehouse_procurement_asn WHERE tenant_id=#{tenantId} AND procurement_order_id=#{procurementOrderId}")
    java.util.List<AsnDO> selectByProcurementOrderId(@Param("tenantId") Long tenantId,
                                                     @Param("procurementOrderId") String procurementOrderId);

    @Select("SELECT * FROM cloudmold_warehouse_procurement_asn WHERE tenant_id=#{tenantId} AND asn_no=#{asnNo}")
    AsnDO selectByAsnNo(@Param("tenantId") Long tenantId, @Param("asnNo") String asnNo);

    @Update("UPDATE cloudmold_warehouse_procurement_asn SET status=#{status},version=version+1,updated_at=#{now} "
            + "WHERE tenant_id=#{tenantId} AND asn_id=#{asnId} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("asnId") String asnId,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("now") LocalDateTime now);
}
