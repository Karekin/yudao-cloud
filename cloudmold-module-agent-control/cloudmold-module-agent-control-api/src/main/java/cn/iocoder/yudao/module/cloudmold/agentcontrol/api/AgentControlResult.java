package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentControlResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
}
