package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseNetworkCommand {
    private WarehouseNetworkOperation operation;
    private String idempotencyKey;
    private String sourceEventId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private WarehouseDefinition warehouse;
    private WarehouseZoneDefinition zone;
    private WarehouseLocationDefinition location;
    private WarehouseSourceMappingDefinition sourceMapping;
    private WarehouseOperatorAssignmentDefinition operatorAssignment;
}
