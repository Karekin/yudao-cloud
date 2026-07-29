package cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.ReturnDispositionAssessmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReturnDispositionAssessmentMapper extends BaseMapperX<ReturnDispositionAssessmentDO> {

    @Select("""
            SELECT * FROM cloudmold_return_disposition_assessment
            WHERE tenant_id=#{tenantId} AND after_sale_id=#{afterSaleId}
            """)
    ReturnDispositionAssessmentDO selectByAfterSale(@Param("tenantId") Long tenantId,
                                                    @Param("afterSaleId") String afterSaleId);
}
