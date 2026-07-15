package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryLotSourceMappingDO {
    private String mappingId;
    private Long tenantId;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String lotId;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String verificationRef;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
