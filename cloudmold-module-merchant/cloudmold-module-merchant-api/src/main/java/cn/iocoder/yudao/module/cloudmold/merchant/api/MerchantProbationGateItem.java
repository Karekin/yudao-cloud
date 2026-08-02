package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantProbationGateItem {
    private String gateCode;
    private String gateStatus;
    private String thresholdConfigRef;
    private String evidenceRef;
    private String note;
}
