package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class AgentExecutionTicketCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    private String workOrderId;
    private String approvalId;
    private Long workOrderExpectedVersion;
    private Long validForSeconds;
    private String missionRunId;
    private String leaseOwner;
    private String leaseToken;
    private Long fencingToken;

}
