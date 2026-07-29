package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PutawayDefinition {
    private String putawayId;
    private String receiptId;
    /** 上架最终库位 */
    private String targetLocationId;
    private Long expectedVersion;
}
