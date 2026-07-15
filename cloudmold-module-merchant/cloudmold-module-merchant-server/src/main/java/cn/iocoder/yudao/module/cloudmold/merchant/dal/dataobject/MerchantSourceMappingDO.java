package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class MerchantSourceMappingDO {
    private String mappingId;
    private Long tenantId;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String targetType;
    private String targetId;
    private String legalEntityId;
    private String merchantId;
    private String shopId;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String verificationRef;
    private String migrationRunId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
