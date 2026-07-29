package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporalAutomationCandidate implements Serializable {
    private String candidateId;
    private String businessKey;
    private String inputJson;
    private boolean synthetic;
}
