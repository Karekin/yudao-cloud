package cn.iocoder.yudao.module.cloudmold.supplier.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierAdmissionStatus;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierStatus;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierProfileQueryService implements SupplierProfileQueryApi {
    private final SupplierProfileMapper mapper;

    @Override
    public SupplierProfileView requireSupplier(String supplierId) {
        if (supplierId == null || supplierId.isBlank()) {
            throw new IllegalArgumentException("supplierId is required");
        }
        SupplierProfileView profile = mapper.selectProfile(TenantContextHolder.getRequiredTenantId(), supplierId);
        if (profile == null) {
            throw new IllegalArgumentException("supplier not found");
        }
        return profile;
    }

    @Override
    public SupplierProfileView requireProcurementEligibleSupplier(String supplierId) {
        SupplierProfileView profile = requireSupplier(supplierId);
        if (profile.getStatus() != SupplierStatus.ACTIVE
                || profile.getAdmissionStatus() != SupplierAdmissionStatus.ADMITTED) {
            throw new IllegalArgumentException("supplier is not ACTIVE and ADMITTED");
        }
        return profile;
    }
}
