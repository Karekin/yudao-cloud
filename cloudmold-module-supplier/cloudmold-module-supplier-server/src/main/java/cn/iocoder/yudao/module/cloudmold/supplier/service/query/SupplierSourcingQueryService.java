package cn.iocoder.yudao.module.cloudmold.supplier.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingDecisionView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierSourcingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierSourcingQueryService implements SupplierSourcingQueryApi {
    private final SupplierSourcingMapper mapper;

    @Override
    public SupplierProfileView requireSupplier(String supplierId) {
        SupplierProfileView result = mapper.selectSupplier(TenantContextHolder.getRequiredTenantId(), supplierId);
        if (result == null) {
            throw new IllegalArgumentException("supplier not found");
        }
        return result;
    }

    @Override
    public SupplierSourcingDecisionView requireDecision(String sourcingCaseId) {
        SupplierSourcingDecisionView result = mapper.selectDecision(
                TenantContextHolder.getRequiredTenantId(), sourcingCaseId);
        if (result == null) {
            throw new IllegalArgumentException("supplier sourcing case not found");
        }
        return result;
    }
}
