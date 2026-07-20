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
public class AgentBusinessCardView implements Serializable {
    private static final long serialVersionUID = 1L;
    private String cardType;
    private String cardId;
    private String missionId;
    private String workOrderId;
    private String title;
    private String roleCode;
    private String fromRoleCode;
    private String actionCode;
    private String status;
    private String riskLevel;
    private String outcomeCode;
    private String summary;
    private Instant occurredAt;
}
