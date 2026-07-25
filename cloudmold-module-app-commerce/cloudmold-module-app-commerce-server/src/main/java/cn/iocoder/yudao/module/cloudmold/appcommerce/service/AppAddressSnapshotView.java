package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AppAddressSnapshotView {
    String addressRef;
    Long snapshotVersion;
    String destinationRegionCode;
    String receiverSummary;
    String mobileSummary;
    Boolean duplicate;
}
