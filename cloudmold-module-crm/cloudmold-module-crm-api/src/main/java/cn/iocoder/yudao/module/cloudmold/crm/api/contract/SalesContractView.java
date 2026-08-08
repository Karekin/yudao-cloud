package cn.iocoder.yudao.module.cloudmold.crm.api.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesContractView implements Serializable {
    private String salesContractId;
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
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
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
        private Integer lineNo;
    }
}
