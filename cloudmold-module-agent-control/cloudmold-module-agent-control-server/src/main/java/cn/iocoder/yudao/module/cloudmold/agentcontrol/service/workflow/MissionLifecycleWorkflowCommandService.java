package cn.iocoder.yudao.module.cloudmold.agentcontrol.service.workflow;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.MissionRuntimeApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.StockoutMissionCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowCommandResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MissionLifecycleWorkflowCommandService {

    static final String WORKFLOW_SCOPE = "INVENTORY_STOCKOUT_FIXED_TEMPLATE";
    static final String MISSION_TEMPLATE = "mission.inventory-stockout-response.v1";
    private static final String COMMAND_TYPE = "MISSION_LIFECYCLE_START_STOCKOUT";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");

    private final AgentControlStoreMapper mapper;
    private final ObjectProvider<MissionRuntimeApi> missionRuntimeApiProvider;
    private final Clock clock;

    @Autowired
    public MissionLifecycleWorkflowCommandService(AgentControlStoreMapper mapper,
                                                  ObjectProvider<MissionRuntimeApi> missionRuntimeApiProvider) {
        this(mapper, missionRuntimeApiProvider, Clock.systemUTC());
    }

    MissionLifecycleWorkflowCommandService(AgentControlStoreMapper mapper,
                                          ObjectProvider<MissionRuntimeApi> missionRuntimeApiProvider,
                                          Clock clock) {
        this.mapper = mapper;
        this.missionRuntimeApiProvider = missionRuntimeApiProvider;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public MissionLifecycleWorkflowCommandResult startStockoutMission(MissionLifecycleWorkflowCommand command) {
        require(command != null, "command is required");
        String idempotencyKey = requireRef(command.getIdempotencyKey(), "idempotencyKey");
        MissionRuntimeApi missionRuntimeApi = missionRuntimeApiProvider.getIfAvailable();
        require(missionRuntimeApi != null,
                "cloudmold.agent-control.enabled is false; stockout mission lifecycle is unavailable");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "command", command,
                "workflow_scope", WORKFLOW_SCOPE)));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, idempotencyKey, COMMAND_TYPE, requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve mission lifecycle operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "mission lifecycle operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different stockout mission payload");
            require(operation.getStatus() == 10 && operation.getResultJson() != null,
                    "existing stockout mission operation is incomplete");
            MissionLifecycleWorkflowCommandResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), MissionLifecycleWorkflowCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        AgentControlResult started = missionRuntimeApi.startStockoutMission(
                StockoutMissionCommand.builder()
                        .missionId(command.getMissionId())
                        .title(command.getTitle())
                        .objectiveJson(command.getObjectiveJson())
                        .correlationId(command.getCorrelationId())
                        .inventoryAgentUserId(command.getInventoryAgentUserId())
                        .buyerAgentUserId(command.getBuyerAgentUserId())
                        .customerServiceAgentUserId(command.getCustomerServiceAgentUserId())
                        .deadlineAt(command.getDeadlineAt())
                        .build(),
                requireActor(command.getSupervisorUserId(), "supervisorUserId"));
        MissionLifecycleWorkflowCommandResult result = MissionLifecycleWorkflowCommandResult.builder()
                .workflowScope(WORKFLOW_SCOPE)
                .missionId(started.getAggregateId())
                .missionType(MISSION_TEMPLATE)
                .aggregateVersion(started.getAggregateVersion())
                .status(started.getStatus())
                .duplicate(false)
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, started.getAggregateType(),
                started.getAggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "mission lifecycle operation completion conflict");
        return result;
    }

    private static Long requireActor(Long value, String field) {
        require(value != null && value > 0, field + " is required");
        return value;
    }

    private static String requireRef(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        String trimmed = value.trim();
        require(SAFE_REF.matcher(trimmed).matches(), field + " is invalid");
        return trimmed;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
