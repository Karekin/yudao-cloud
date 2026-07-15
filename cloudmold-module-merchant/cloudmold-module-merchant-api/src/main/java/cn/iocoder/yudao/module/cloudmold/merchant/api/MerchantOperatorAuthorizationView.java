package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantOperatorAuthorizationView {
    private String assignmentId;
    private String merchantId;
    private String shopId;
    private String principalId;
    private String roleCode;
    private String assignmentStatus;
}
