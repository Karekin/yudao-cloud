package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsnDefinition {
    private String asnId;
    private String asnNo;
    /** 关联的上游业务类型，补货场景为 REPLENISHMENT */
    private String sourceBusinessType;
    /** 关联的上游业务键，补货场景为 recommendationId（接通切片 A 事件映射） */
    private String sourceBusinessRef;
    private String supplierRef;
    private String warehouseId;
    /** 仅 SEND_ASN / CANCEL_ASN 状态变迁时使用 */
    private String status;
    /** 仅状态变迁时使用（乐观锁） */
    private Long expectedVersion;
    private List<AsnLineDefinition> lines;
}
