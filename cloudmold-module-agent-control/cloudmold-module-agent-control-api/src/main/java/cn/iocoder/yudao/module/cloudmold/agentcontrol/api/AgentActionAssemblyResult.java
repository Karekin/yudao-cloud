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
public class AgentActionAssemblyResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String workOrderId;
    private String actionCode;
    private String skillTaskId;
    private String executionBindingId;
    private String status;
    private boolean duplicate;

}
