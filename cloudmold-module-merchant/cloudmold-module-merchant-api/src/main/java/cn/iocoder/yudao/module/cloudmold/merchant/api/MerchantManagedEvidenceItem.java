package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantManagedEvidenceItem {
    private String itemCode;
    private String itemLabel;
    private String evidenceRef;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String note;
}
