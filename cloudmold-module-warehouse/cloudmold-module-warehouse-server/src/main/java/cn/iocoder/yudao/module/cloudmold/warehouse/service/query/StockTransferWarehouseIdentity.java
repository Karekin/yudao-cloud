package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferWarehouseIdentity {
    private String warehouseId;
    private String warehouseCode;
    private String warehouseName;
}
