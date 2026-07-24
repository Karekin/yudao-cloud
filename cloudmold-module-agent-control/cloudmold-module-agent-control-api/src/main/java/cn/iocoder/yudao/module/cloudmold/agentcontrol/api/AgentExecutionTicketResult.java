package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionTicketResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String workOrderId;
    private String approvalId;
    private String approvalRef;
    private String approvalRefSha256;
    private Instant expiresAt;

}
