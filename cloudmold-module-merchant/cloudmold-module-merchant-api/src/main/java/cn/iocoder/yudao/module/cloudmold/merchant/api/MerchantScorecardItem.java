package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantScorecardItem {
    private String obligationCode;
    private String assessmentStatus;
    private String thresholdConfigRef;
    private String evidenceRef;
    private String note;
}
