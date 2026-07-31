package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.Data;

import java.io.Serializable;

/** Approval-gate counts derived from the authoritative approval, BPM-binding, and work-order states. */
@Data
public class AgentApprovalBoardStatsView implements Serializable {
    private static final long serialVersionUID = 1L;

    private long pendingTotal;
    private long bpmInProgress;
    private long approverAssignmentRequired;
    private long startUncertain;
    private long bpmTerminalPendingSafety;
    private long releasedWaitingExecution;
}
