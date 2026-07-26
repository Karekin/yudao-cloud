package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.AgentRunLease;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskMissionLeaseFencePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
public class AgentControlSkillTaskMissionLeaseFence implements SkillTaskMissionLeaseFencePort {

    private final AgentControlStoreMapper mapper;
    private final Clock clock;

    @Autowired
    public AgentControlSkillTaskMissionLeaseFence(AgentControlStoreMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    AgentControlSkillTaskMissionLeaseFence(AgentControlStoreMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void validateCurrentLease(MissionLeaseFence fence) {
        validateCurrentLeaseLocked(fence);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public <T> T executeWhileCurrent(MissionLeaseFence fence, Supplier<T> operation) {
        require(operation != null, "mission fenced operation is required");
        validateCurrentLeaseLocked(fence);
        return operation.get();
    }

    private void validateCurrentLeaseLocked(MissionLeaseFence fence) {
        require(fence != null, "mission lease fence is required");
        long tenantId = TenantContextHolder.getRequiredTenantId();
        require(tenantId == fence.tenantId(), "mission lease fence tenant is invalid");
        WorkOrder workOrder = mapper.selectWorkOrderForUpdate(tenantId,
                requireText(fence.workOrderId(), "workOrderId"));
        require(workOrder != null, "mission lease fence work order does not exist");
        if (workOrder.getMissionId() == null) {
            require(fence.missionRunId() == null && fence.leaseOwner() == null
                            && fence.leaseEpoch() == 0 && fence.fencingToken() == 0,
                    "non-mission work order must not carry a mission lease fence");
            return;
        }

        require(fence.missionRunId() != null && fence.leaseOwner() != null
                        && fence.leaseEpoch() > 0 && fence.fencingToken() > 0,
                "mission approvalRef is missing its complete lease fence");
        AgentRunLease lease = mapper.selectRunLeaseForUpdate(tenantId, workOrder.getWorkOrderId());
        LocalDateTime now = LocalDateTime.now(clock);
        require(lease != null && "ACTIVE".equals(lease.getStatus())
                        && lease.getLeaseUntil() != null && lease.getLeaseUntil().isAfter(now),
                "mission lease is not active");
        require("IN_PROGRESS".equals(workOrder.getStatus())
                        && Objects.equals(workOrder.getActiveRunId(), lease.getRunId())
                        && Objects.equals(workOrder.getMissionId(), lease.getMissionId())
                        && Objects.equals(fence.missionRunId(), lease.getRunId())
                        && Objects.equals(fence.leaseOwner(), lease.getLeaseOwner())
                        && fence.fencingToken() == lease.getFencingToken()
                        && fence.leaseEpoch() == lease.getVersion(),
                "mission lease fence is stale");
    }

    private static String requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        return value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new SecurityException(message);
        }
    }
}
