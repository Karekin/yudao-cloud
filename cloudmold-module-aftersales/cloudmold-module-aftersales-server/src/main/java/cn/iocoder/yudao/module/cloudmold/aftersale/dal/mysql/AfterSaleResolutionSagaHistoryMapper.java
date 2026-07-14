package cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleResolutionSagaHistoryDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AfterSaleResolutionSagaHistoryMapper extends BaseMapperX<AfterSaleResolutionSagaHistoryDO> {}
