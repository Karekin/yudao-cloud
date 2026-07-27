package cn.iocoder.yudao.module.cloudmold.procurement.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcurementQueryService implements ProcurementQueryApi {
    private final ProcurementMapper mapper;

    @Override
    public ProcurementOrderView requireCurrent(String orderId) {
        ProcurementOrderView view = mapper.selectCurrent(TenantContextHolder.getRequiredTenantId(), orderId);
        if (view == null) {
            throw new IllegalArgumentException("procurement order not found");
        }
        return view;
    }

    @Override
    public ProcurementOrderView requireBySourceBusiness(String sourceBusinessType, String sourceBusinessRef) {
        ProcurementOrderView view = mapper.selectCurrentBySourceBusiness(
                TenantContextHolder.getRequiredTenantId(), sourceBusinessType, sourceBusinessRef);
        if (view == null) {
            throw new IllegalArgumentException("procurement order not found");
        }
        return view;
    }
}
