package cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SalesContractRecords {

    private SalesContractRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String commandType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateType;
        private String aggregateId;
        private String resultJson;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SalesContract {
        private String salesContractId;
        private Long tenantId;
        private String contractCode;
        private String contractName;
        private String customerId;
        private String sellerMerchantId;
        private String sellerShopId;
        private String sellerLegalEntityId;
        private String status;
        private String currencyCode;
        private Long totalAmountMinor;
        private LocalDate effectiveDate;
        private LocalDate expiresOn;
        private String approvalProcessInstanceId;
        private String createdByPrincipalId;
        private String updatedByPrincipalId;
        private String submittedByPrincipalId;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime submittedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SalesContractItem {
        private String salesContractItemId;
        private Long tenantId;
        private String salesContractId;
        private Integer lineNo;
        private String canonicalSkuId;
        private String itemName;
        private String uomCode;
        private BigDecimal quantity;
        private Long unitPriceMinor;
        private Long lineAmountMinor;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class StatusHistory {
        private Long historyId;
        private Long tenantId;
        private String salesContractId;
        private Long operationId;
        private Long aggregateVersion;
        private String status;
        private String actorPrincipalId;
        private Long actorAdminUserId;
        private String reasonCode;
        private String approvalProcessInstanceId;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }
}
