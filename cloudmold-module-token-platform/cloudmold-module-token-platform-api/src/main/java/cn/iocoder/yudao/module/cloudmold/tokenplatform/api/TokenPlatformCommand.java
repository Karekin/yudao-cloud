package cn.iocoder.yudao.module.cloudmold.tokenplatform.api;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenPlatformCommand {
    private TokenPlatformOperation operation;
    private String idempotencyKey;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private ModelOfferingDefinition modelOffering;
    private AccessCredentialDefinition accessCredential;
    private QuotaAccountDefinition quotaAccount;
    private QuotaLedgerDefinition quotaLedger;
    private InvocationUsageDefinition invocationUsage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelOfferingDefinition {
        private String offeringId;
        private String offeringCode;
        private String providerCode;
        private String modelCode;
        private Long expectedVersion;
        private PricingVersionDefinition pricing;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingVersionDefinition {
        private String pricingVersionId;
        private Long inputPriceMicrounitsPerMillionTokens;
        private Long cachedInputPriceMicrounitsPerMillionTokens;
        private Long outputPriceMicrounitsPerMillionTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessCredentialDefinition {
        private String credentialId;
        private String principalId;
        private String offeringId;
        private String credentialFingerprint;
        private String secretRef;
        private String last4;
        private Integer keyVersion;
        private Instant expiresAt;
        private Long expectedVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaAccountDefinition {
        private String accountId;
        private String principalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaLedgerDefinition {
        private String accountId;
        private String ledgerEntryId;
        private String entryType;
        private Long signedDeltaMicrounits;
        private String referenceType;
        private String referenceId;
        private Long expectedVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvocationUsageDefinition {
        private String usageId;
        private String requestId;
        private String accountId;
        private String principalId;
        private String credentialId;
        private String offeringId;
        private String pricingVersionId;
        private String resultStatus;
        private Long inputTokens;
        private Long cachedInputTokens;
        private Long outputTokens;
        private Long totalTokens;
        private Long durationMillis;
        private Long quotaCostMicrounits;
        private String errorCode;
        private Long expectedAccountVersion;
    }
}
