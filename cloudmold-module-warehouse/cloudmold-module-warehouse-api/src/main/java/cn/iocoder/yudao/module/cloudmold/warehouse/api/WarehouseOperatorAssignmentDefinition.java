package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseOperatorAssignmentDefinition {
    private String assignmentId;
    private String warehouseId;
    private String zoneId;
    private String locationId;
    private String principalId;
    private String roleCode;
    private String shiftCode;
    private Instant validFrom;
    private Instant validTo;
    private Long expectedVersion;
}
