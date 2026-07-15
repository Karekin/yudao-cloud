package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class MerchantCommand {
    private MerchantOperation operation;
    private String idempotencyKey;
    private String runId;
    private String applicationId;
    private String merchantId;
    private String shopId;
    private Long expectedVersion;
    private String legalName;
    /** One-way digest or external restricted-store token. Never a raw registration number. */
    private String registrationHashToken;
    /** External restricted-store token. Never raw license content. */
    private String businessLicenseToken;
    private String ownerPrincipalId;
    private String channelCode;
    private String externalShopId;
    private String reason;
    private SourceReference sourceReference;
    private String sourceMappingId;
    private String targetType;
    private String targetId;
    private Instant validFrom;
    private Instant validTo;
    /** Opaque evidence reference only; never raw verification content or PII. */
    private String verificationRef;
    private String migrationRunId;
    private String sourceSystem;
    private String traceId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
