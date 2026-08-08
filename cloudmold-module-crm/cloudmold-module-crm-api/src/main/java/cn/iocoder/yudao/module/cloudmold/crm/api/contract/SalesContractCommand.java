package cn.iocoder.yudao.module.cloudmold.crm.api.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesContractCommand implements Serializable {
    private SalesContractOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private String reasonCode;
    private String salesContractId;
    private Long expectedVersion;
    private String contractCode;
    private String contractName;
    private String customerId;
    private String sellerMerchantId;
    private String sellerShopId;
    private String currencyCode;
    private LocalDate effectiveDate;
    private LocalDate expiresOn;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item implements Serializable {
        private String salesContractItemId;
        private String canonicalSkuId;
        private String itemName;
        private String uomCode;
        private String quantity;
        private Long unitPriceMinor;
        private Long lineAmountMinor;
    }
}
