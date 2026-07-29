package cn.iocoder.yudao.module.cloudmold.crossborder.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossBorderResult implements Serializable {
    private Long operationId;
    private Boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private String caseId;
    private String caseNo;
    private String approvalRef;
    private String bookingRef;
    private String trackingNumber;
    private String customsDeclarationRef;
    private String customsReleaseRef;
}
