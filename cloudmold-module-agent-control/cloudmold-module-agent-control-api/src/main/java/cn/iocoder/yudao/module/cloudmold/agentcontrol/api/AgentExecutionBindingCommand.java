package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionBindingCommand implements Serializable {
    private String bindingId;
    private String workOrderId;
    private Long workOrderExpectedVersion;
    private Integer executionGeneration;
    private String skillTaskId;
    private String supersededBindingId;
    private String supersedeReasonCode;
    private String outcomeCode;
    private String summary;
}
