package cn.iocoder.yudao.module.cloudmold.supplier.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPerformanceScorecardView {
    private String scorecardId;
    private String supplierId;
    private String supplierName;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer otifBps;
    private Integer qualityBps;
    private Integer capacityBps;
    private Integer capaBps;
    private Integer overallBps;
    private String assessment;
    private Integer scorecardVersion;
    private String evidenceSnapshotSha256;
    private LocalDateTime generatedAt;
}
