package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsnDefinition {
    private String asnId;
    private String asnNo;
    private String procurementOrderId;
    private String supplierId;
    private String warehouseId;
    private String status;
    private Long expectedVersion;
    private List<AsnLineDefinition> lines;
}
