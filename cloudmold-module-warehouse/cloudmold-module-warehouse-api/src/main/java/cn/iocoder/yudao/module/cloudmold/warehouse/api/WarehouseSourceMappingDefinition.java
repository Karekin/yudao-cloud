package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseSourceMappingDefinition {
    private String mappingId;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String targetType;
    private String warehouseId;
    private String zoneId;
    private String locationId;
    private Instant validFrom;
    private Instant validTo;
    private String verificationRef;
    private Long expectedVersion;
}
