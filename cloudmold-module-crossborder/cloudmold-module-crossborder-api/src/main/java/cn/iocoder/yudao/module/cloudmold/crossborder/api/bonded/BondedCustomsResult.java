package cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BondedCustomsResult implements Serializable {
    private Long operationId;
    private Boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private String tripleMatchStatus;
    private String customsStatus;
    private String bondedReleaseStatus;
    private String deliveryStatus;
    private String caseId;
    private String caseNo;
    private String approvalRef;
    private String declarationRef;
    private String customsAcceptanceRef;
    private String bondedReleaseRef;
    private String deliveryConfirmationRef;
}
