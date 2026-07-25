package cn.iocoder.yudao.module.cloudmold.skilltask.api.approval;

import java.io.Serializable;
import java.util.function.Supplier;

/**
 * Narrow cross-module guard used immediately before a mission-bound Skill Task
 * is accepted or executed. Implementations must validate the work order and
 * current lease in one database transaction.
 */
public interface SkillTaskMissionLeaseFencePort {

    void validateCurrentLease(MissionLeaseFence fence);

    /**
     * Executes an actuator call while the implementation keeps the current
     * mission fence serialized against takeover. Implementations backed by a
     * database lease must hold the lease row lock until {@code operation}
     * returns.
     */
    <T> T executeWhileCurrent(MissionLeaseFence fence, Supplier<T> operation);

    record MissionLeaseFence(
            long tenantId,
            String workOrderId,
            String missionRunId,
            String leaseOwner,
            long leaseEpoch,
            long fencingToken) implements Serializable {
    }
}
