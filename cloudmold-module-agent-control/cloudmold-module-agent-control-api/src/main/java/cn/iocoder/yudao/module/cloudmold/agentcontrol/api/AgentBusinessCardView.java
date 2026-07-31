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
    private String scopeHash;
    private String status;
    private String workflowStatus;
    private String riskLevel;
    private Long requesterUserId;
    private Long approverUserId;
    private String processInstanceId;
    /** 当前 BPM 待办的实际办理人；审批流已推进时不再使用初始审批人。 */
    private String activeAssigneeUserIds;
    private String outcomeCode;
    private String summary;
    private Instant occurredAt;
}
