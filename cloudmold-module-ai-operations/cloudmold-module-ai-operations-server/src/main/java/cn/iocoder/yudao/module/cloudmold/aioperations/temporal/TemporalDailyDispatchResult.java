package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporalDailyDispatchResult implements Serializable {
    private String outcomeCode;
    private String businessDate;
    private Integer candidateCount;
    private Integer dispatchedCount;
    private Integer failedCount;
    private List<String> workflowIds;
}
