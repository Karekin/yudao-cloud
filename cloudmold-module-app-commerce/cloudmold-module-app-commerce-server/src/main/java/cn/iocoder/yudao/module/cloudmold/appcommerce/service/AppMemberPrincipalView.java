package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AppMemberPrincipalView {
    Long memberUserId;
    String principalId;
    String principalStatus;
    String sourceSystem;
    String sourceType;
}
