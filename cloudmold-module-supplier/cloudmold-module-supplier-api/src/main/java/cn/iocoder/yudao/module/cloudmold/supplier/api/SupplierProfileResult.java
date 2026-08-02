package cn.iocoder.yudao.module.cloudmold.supplier.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProfileResult {
    /** Decimal int64 representation; never serialized through a JavaScript number. */
    private String operationId;
    private boolean duplicate;
    private String supplierId;
    private Long aggregateVersion;
    private String supplierStatus;
    private String admissionStatus;
}
