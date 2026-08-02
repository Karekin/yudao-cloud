package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AsnLineMapper extends BaseMapperX<AsnLineDO> {
    @Select("SELECT * FROM cloudmold_warehouse_procurement_asn_line WHERE tenant_id=#{tenantId} AND asn_id=#{asnId} ORDER BY line_no")
    List<AsnLineDO> selectByAsn(@Param("tenantId") Long tenantId, @Param("asnId") String asnId);

    @Select("SELECT * FROM cloudmold_warehouse_procurement_asn_line WHERE tenant_id=#{tenantId} AND asn_line_id=#{asnLineId} FOR UPDATE")
    AsnLineDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("asnLineId") String asnLineId);

    @Update("""
            UPDATE cloudmold_warehouse_procurement_asn_line
            SET received_quantity=#{receivedQuantity},pending_quality_quantity=#{pendingQualityQuantity},
                fulfillment_version=fulfillment_version+1,status=#{status},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND asn_line_id=#{asnLineId} AND fulfillment_version=#{fulfillmentVersion}
            """)
    int updateFulfillmentCas(@Param("tenantId") Long tenantId, @Param("asnLineId") String asnLineId,
                             @Param("fulfillmentVersion") Long fulfillmentVersion,
                             @Param("receivedQuantity") BigDecimal receivedQuantity,
                             @Param("pendingQualityQuantity") BigDecimal pendingQualityQuantity,
                             @Param("status") String status, @Param("now") LocalDateTime now);
}
