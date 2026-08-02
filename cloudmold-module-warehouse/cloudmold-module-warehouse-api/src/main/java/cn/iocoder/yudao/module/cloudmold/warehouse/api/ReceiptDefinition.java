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
public class ReceiptDefinition {
    private String receiptId;
    private String receiptNo;
    private String asnId;
    private String procurementOrderId;
    private String supplierId;
    private String warehouseId;
    private String remark;
    private List<ReceiptLineDefinition> lines;
}
