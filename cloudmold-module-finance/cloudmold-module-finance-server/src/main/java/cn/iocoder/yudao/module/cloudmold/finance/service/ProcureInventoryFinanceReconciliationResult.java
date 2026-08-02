package cn.iocoder.yudao.module.cloudmold.finance.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcureInventoryFinanceReconciliationResult {
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private String runCode;
    private String status;
    private Integer lineCount;
    private Integer matchedCount;
    private Integer differentCount;
    private Integer missingCount;
    private Integer uncomparableCount;
}
