package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwardReleaseResult {
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String awardId;
    private Long awardVersion;
    private List<ProcurementOrderView> purchaseOrders;
}
