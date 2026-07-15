package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantCommandResult {
    private Long operationId;
    private String applicationId;
    private String onboardingStatus;
    private Long applicationVersion;
    private String legalEntityId;
    private String legalEntityStatus;
    private Long legalEntityVersion;
    private String merchantId;
    private String merchantStatus;
    private Long merchantVersion;
    private String shopId;
    private String shopStatus;
    private Long shopVersion;
    private String ownerAssignmentId;
    private String ownerAssignmentStatus;
    private String sourceMappingId;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String targetType;
    private String targetId;
    private java.time.Instant validFrom;
    private java.time.Instant validTo;
    private String verificationRef;
    private String migrationRunId;
    private String sourceMappingStatus;
    private Long sourceMappingVersion;
    private String listingUnpublishSagaId;
    private Integer affectedListingCount;
    private boolean duplicate;
}
