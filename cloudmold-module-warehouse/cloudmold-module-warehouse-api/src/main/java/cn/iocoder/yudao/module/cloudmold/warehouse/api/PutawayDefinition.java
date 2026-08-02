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
public class PutawayDefinition {
    private String putawayId;
    private String receiptId;
    private String warehouseId;
    private List<PutawayLineDefinition> lines;
    private Long expectedVersion;
}
