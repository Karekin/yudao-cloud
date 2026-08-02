package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryHealthSnapshotIssueRefView {
    private String issueId;
    private String issueType;
    private String severity;
    private String status;
    private String sourceBalanceId;
}
