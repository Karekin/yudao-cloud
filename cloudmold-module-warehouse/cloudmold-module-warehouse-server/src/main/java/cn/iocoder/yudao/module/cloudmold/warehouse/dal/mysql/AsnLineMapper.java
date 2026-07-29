package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AsnLineMapper extends BaseMapperX<AsnLineDO> {
    @Select("SELECT * FROM cloudmold_asn_line WHERE tenant_id=#{tenantId} AND asn_id=#{asnId} ORDER BY line_no")
    List<AsnLineDO> selectByAsn(@Param("tenantId") Long tenantId, @Param("asnId") String asnId);
}
