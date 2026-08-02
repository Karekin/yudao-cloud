package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAgingSnapshotResult {
    private Long operationId;
    private String snapshotId;
    private String snapshotCode;
    private boolean duplicate;
    private Long snapshotVersion;
    private Integer lineCount;
    private String status;
}
