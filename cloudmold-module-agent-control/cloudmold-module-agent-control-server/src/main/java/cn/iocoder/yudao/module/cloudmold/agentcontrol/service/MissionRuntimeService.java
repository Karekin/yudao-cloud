package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
@Service
public class MissionRuntimeService implements MissionRuntimeApi {
    static final String STOCKOUT_TEMPLATE = "mission.inventory-stockout-response.v1";
    private static final String TEMPLATE_VERSION = "1.0.0";
    private static final List<Stage> STOCKOUT_STAGES = List.of(
            new Stage("diagnose", "inventory-control", "inventory.detect-size-stockout", "缺断码诊断"),
            new Stage("prepare", "buyer", "buyer.prepare-replenishment", "制定补货方案"),
            new Stage("execute", "buyer", "buyer.execute-replenishment", "执行补货"),
            new Stage("verify", "inventory-control", "inventory.verify-replenishment", "验证到货与库存"),
            new Stage("customer", "customer-service", "customer-service.prepare-stockout-impact-response", "客服影响处置"));

    private final AgentControlStoreMapper mapper;
    private final Clock clock;

    @Autowired
    public MissionRuntimeService(AgentControlStoreMapper mapper) { this(mapper, Clock.systemUTC()); }
    MissionRuntimeService(AgentControlStoreMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult startStockoutMission(StockoutMissionCommand command, Long supervisorUserId) {
        require(command != null, "command is required");
        requireActor(supervisorUserId, "supervisorUserId");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = now();
        String objectiveJson = AgentControlJson.canonicalBusinessObject(command.getObjectiveJson(), "objectiveJson");
        String missionId = valueOrUuid(command.getMissionId());
        String title = requireText(command.getTitle(), "title", 256);
        String correlationId = requireRef(command.getCorrelationId(), "correlationId");
        Map<String, Long> assignees = Map.of("inventory-control", requireActor(command.getInventoryAgentUserId(),
                        "inventoryAgentUserId"), "buyer", requireActor(command.getBuyerAgentUserId(), "buyerAgentUserId"),
                "customer-service", requireActor(command.getCustomerServiceAgentUserId(), "customerServiceAgentUserId"));
        require(assignees.values().stream().noneMatch(supervisorUserId::equals),
                "mission supervisor must be separate from operational assignees");
        Map<String, RoleActionPolicy> policies = new LinkedHashMap<>();
        for (Stage stage : STOCKOUT_STAGES) {
            require(mapper.selectRole(tenantId, stage.roleCode()) != null, "required mission role is not configured: " + stage.roleCode());
            require(mapper.selectEffectiveActorRoleGrant(tenantId, assignees.get(stage.roleCode()), stage.roleCode(), now) != null,
                    "mission assignee has no effective role grant: " + stage.roleCode());
            RoleActionPolicy policy = mapper.selectActionPolicy(tenantId, stage.roleCode(), stage.actionCode());
            require(policy != null && Boolean.TRUE.equals(policy.getEnabled()) && "ALLOW".equals(policy.getPermissionMode()),
                    "required mission action policy is not enabled: " + stage.actionCode());
            policies.put(stage.code(), policy);
        }
        LocalDateTime deadline = command.getDeadlineAt() == null ? now.plusDays(30)
                : LocalDateTime.ofInstant(command.getDeadlineAt(), ZoneOffset.UTC);
        require(deadline.isAfter(now), "deadlineAt must be in the future");
        Mission mission = new Mission().setMissionId(missionId).setTenantId(tenantId).setMissionType(STOCKOUT_TEMPLATE)
                .setTemplateVersion(TEMPLATE_VERSION).setTitle(title).setObjectiveJson(objectiveJson)
                .setCorrelationId(correlationId).setStatus("ACTIVE").setSupervisorUserId(supervisorUserId)
                .setVersion(1L).setStartedAt(now).setDeadlineAt(deadline).setUpdatedAt(now);
        require(mapper.insertMission(mission) == 1, "failed to persist stockout mission");
        audit(tenantId, "business_mission", missionId, 1L, "agent_control.mission.started",
                supervisorUserId, Map.of("missionType", STOCKOUT_TEMPLATE), now);

        Map<String, WorkOrder> workOrders = new LinkedHashMap<>();
        WorkOrder previous = null;
        for (int index = 0; index < STOCKOUT_STAGES.size(); index++) {
            Stage stage = STOCKOUT_STAGES.get(index);
            String goalId = UUID.randomUUID().toString();
            require(mapper.insertMissionGoal(new MissionGoal().setGoalId(goalId).setTenantId(tenantId)
                    .setMissionId(missionId).setGoalCode(stage.code()).setTitle(stage.title()).setStatus("ACTIVE")
                    .setVersion(1L).setCreatedAt(now).setUpdatedAt(now)) == 1, "failed to persist mission goal");
            RoleActionPolicy policy = policies.get(stage.code());
            String status = index == 0 ? "READY" : "WAITING_DEPENDENCY";
            WorkOrder workOrder = new WorkOrder().setWorkOrderId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setRoleCode(stage.roleCode()).setActionCode(stage.actionCode()).setTitle(stage.title())
                    .setBusinessContextJson(objectiveJson).setStatus(status).setRequesterUserId(supervisorUserId)
                    .setAssigneeUserId(assignees.get(stage.roleCode())).setActionPolicyId(policy.getPolicyId())
                    .setActionPolicyVersion(policy.getVersion()).setRiskLevel(policy.getRiskLevel())
                    .setExecutionRequired(Boolean.TRUE.equals(policy.getExecutionRequired())).setSkillId(policy.getSkillId())
                    .setSkillVersion(policy.getSkillVersion())
                    .setSkillDefinitionClosureSha256(policy.getSkillDefinitionClosureSha256())
                    .setExecutionInputSha256(AgentControlJson.sha256(objectiveJson)).setMissionId(missionId).setGoalId(goalId)
                    .setParentWorkOrderId(previous == null ? null : previous.getWorkOrderId()).setDeadlineAt(deadline)
                    .setReadyAt(index == 0 ? now : null).setWaitingReasonCode(index == 0 ? null : "PREDECESSOR")
                    .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
            require(mapper.insertWorkOrder(workOrder) == 1, "failed to persist mission work order");
            audit(tenantId, "role_work_order", workOrder.getWorkOrderId(), 1L,
                    "agent_control.work_order.created", supervisorUserId,
                    Map.of("missionId", missionId, "roleCode", stage.roleCode(), "actionCode", stage.actionCode()), now);
            workOrders.put(stage.code(), workOrder);
            if (previous != null) {
                require(mapper.insertWorkDependency(new WorkDependency().setDependencyId(UUID.randomUUID().toString())
                        .setTenantId(tenantId).setMissionId(missionId)
                        .setPredecessorWorkOrderId(previous.getWorkOrderId())
                        .setSuccessorWorkOrderId(workOrder.getWorkOrderId()).setStatus("WAITING").setVersion(1L)
                        .setCreatedAt(now).setUpdatedAt(now)) == 1, "failed to persist work dependency");
                if (!previous.getRoleCode().equals(workOrder.getRoleCode())) {
                    Handoff handoff = new Handoff().setHandoffId(UUID.randomUUID().toString()).setTenantId(tenantId)
                            .setWorkOrderId(previous.getWorkOrderId()).setTargetWorkOrderId(workOrder.getWorkOrderId())
                            .setFromRoleCode(previous.getRoleCode()).setFromActionCode(previous.getActionCode())
                            .setToRoleCode(workOrder.getRoleCode()).setToActionCode(workOrder.getActionCode())
                            .setSummary(stage.title()).setStatus("PENDING").setRequestedByUserId(supervisorUserId)
                            .setVersion(1L).setRequestedAt(now);
                    require(mapper.insertHandoff(handoff) == 1, "failed to persist successor handoff");
                    audit(tenantId, "role_handoff", handoff.getHandoffId(), 1L,
                            "agent_control.handoff.created", supervisorUserId,
                            Map.of("sourceWorkOrderId", previous.getWorkOrderId(),
                                    "targetWorkOrderId", workOrder.getWorkOrderId()), now);
                }
            }
            previous = workOrder;
        }
        outbox(tenantId, "business_mission", missionId, "agent_control.mission.started",
                Map.of("missionType", STOCKOUT_TEMPLATE, "firstWorkOrderId", workOrders.get("diagnose").getWorkOrderId()), now);
        return result("business_mission", missionId, 1L, "ACTIVE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentRunLeaseView claim(AgentRunClaimCommand command, Long actorUserId) {
        require(command != null, "command is required");
        requireActor(actorUserId, "actorUserId");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = now();
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId,
                requireRef(command.getWorkOrderId(), "workOrderId")), "work order not found");
        require(workOrder.getMissionId() != null, "work order is not mission-managed");
        require(Objects.equals(command.getWorkOrderExpectedVersion(), workOrder.getVersion()), "work order version is stale");
        require(actorUserId.equals(workOrder.getAssigneeUserId()), "actor is not the mission work assignee");
        require(mapper.selectEffectiveActorRoleGrant(tenantId, actorUserId, workOrder.getRoleCode(), now) != null,
                "actor has no effective role grant");
        Mission mission = requireNonNull(mapper.selectMissionForUpdate(tenantId, workOrder.getMissionId()), "mission not found");
        require("ACTIVE".equals(mission.getStatus()), "mission is not ACTIVE");
        AgentRunLease previous = mapper.selectRunLeaseForUpdate(tenantId, workOrder.getWorkOrderId());
        boolean directClaim = "READY".equals(workOrder.getStatus());
        boolean takeoverClaim = "IN_PROGRESS".equals(workOrder.getStatus());
        require(directClaim || takeoverClaim, "only READY or recoverable IN_PROGRESS work can be claimed");
        require(previous == null || !"ACTIVE".equals(previous.getStatus()) || !previous.getLeaseUntil().isAfter(now),
                "work order already has an active run lease");
        if (takeoverClaim) {
            require(previous != null, "recoverable work order is missing its run lease");
            require(Objects.equals(workOrder.getActiveRunId(), previous.getRunId()),
                    "recoverable work order active run does not match the persisted lease");
            expireRunIfNeeded(tenantId, workOrder, previous, now);
        }
        int leaseSeconds = command.getLeaseSeconds() == null ? 60 : command.getLeaseSeconds();
        require(leaseSeconds >= 10 && leaseSeconds <= 300, "leaseSeconds must be between 10 and 300");
        String runId = UUID.randomUUID().toString();
        String leaseToken = UUID.randomUUID().toString();
        long fencingToken = previous == null ? 1L : previous.getFencingToken() + 1L;
        AgentRunLease lease = new AgentRunLease().setTenantId(tenantId).setWorkOrderId(workOrder.getWorkOrderId())
                .setMissionId(workOrder.getMissionId()).setRunId(runId)
                .setTriggerType(optional(command.getTriggerType(), "READY")).setTriggerId(command.getTriggerId())
                .setActorUserId(actorUserId).setRoleCode(workOrder.getRoleCode())
                .setLeaseOwner(requireRef(command.getLeaseOwner(), "leaseOwner")).setLeaseToken(leaseToken)
                .setFencingToken(fencingToken).setLeaseUntil(now.plusSeconds(leaseSeconds)).setStatus("ACTIVE")
                .setVersion(previous == null ? 1L : previous.getVersion() + 1L).setStartedAt(now).setUpdatedAt(now);
        require(mapper.upsertRunLease(lease) == 1 || previous != null, "failed to persist run lease");
        require(mapper.insertAgentRun(new AgentRun().setRunId(runId).setTenantId(tenantId)
                .setMissionId(workOrder.getMissionId()).setWorkOrderId(workOrder.getWorkOrderId())
                .setTriggerType(lease.getTriggerType()).setTriggerId(lease.getTriggerId()).setActorUserId(actorUserId)
                .setRoleCode(workOrder.getRoleCode()).setFencingToken(fencingToken).setStatus("ACTIVE")
                .setStartedAt(now).setUpdatedAt(now)) == 1, "failed to persist immutable Agent run");
        require(mapper.transitionMissionWorkOrder(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(),
                directClaim ? "READY" : "IN_PROGRESS", "IN_PROGRESS", null, runId, now) == 1,
                "work claim conflict");
        outbox(tenantId, "role_work_order", workOrder.getWorkOrderId(), "agent_control.run.claimed",
                Map.of("runId", runId, "fencingToken", fencingToken), now);
        return AgentRunLeaseView.builder().missionId(workOrder.getMissionId()).workOrderId(workOrder.getWorkOrderId())
                .runId(runId).leaseToken(leaseToken).fencingToken(fencingToken).roleCode(workOrder.getRoleCode())
                .actionCode(workOrder.getActionCode()).leaseUntil(toInstant(lease.getLeaseUntil())).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult checkpoint(AgentCheckpointCommand command, Long actorUserId) {
        require(command != null, "command is required"); requireActor(actorUserId, "actorUserId");
        Long tenantId = TenantContextHolder.getRequiredTenantId(); LocalDateTime now = now();
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId,
                requireRef(command.getWorkOrderId(), "workOrderId")), "work order not found");
        require("IN_PROGRESS".equals(workOrder.getStatus()) && actorUserId.equals(workOrder.getAssigneeUserId()),
                "only the active assignee can checkpoint");
        AgentRunLease lease = requireNonNull(mapper.selectRunLeaseForUpdate(tenantId, workOrder.getWorkOrderId()),
                "run lease not found");
        verifyLease(command.getRunId(), command.getLeaseOwner(), command.getLeaseToken(),
                command.getFencingToken(), lease, now);
        requireJson(command.getDecisionJson(), "decisionJson");
        MissionCheckpoint checkpoint = new MissionCheckpoint().setCheckpointId(valueOrUuid(command.getCheckpointId()))
                .setTenantId(tenantId).setMissionId(workOrder.getMissionId()).setWorkOrderId(workOrder.getWorkOrderId())
                .setRunId(command.getRunId()).setFencingToken(command.getFencingToken())
                .setDecisionCode(requireRef(command.getDecisionCode(), "decisionCode"))
                .setDecisionJson(command.getDecisionJson()).setDecisionSha256(DigestUtil.sha256Hex(command.getDecisionJson()))
                .setCreatedAt(now);
        require(mapper.insertMissionCheckpoint(checkpoint) == 1, "failed to persist Agent checkpoint");
        return result("mission_checkpoint", checkpoint.getCheckpointId(), 1L, "RECORDED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult waitFor(AgentWaitCommand command, Long actorUserId) {
        require(command != null, "command is required"); requireActor(actorUserId, "actorUserId");
        Long tenantId = TenantContextHolder.getRequiredTenantId(); LocalDateTime now = now();
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId,
                requireRef(command.getWorkOrderId(), "workOrderId")), "work order not found");
        require("IN_PROGRESS".equals(workOrder.getStatus()) && actorUserId.equals(workOrder.getAssigneeUserId()),
                "only the active assignee can wait");
        AgentRunLease lease = requireNonNull(mapper.selectRunLeaseForUpdate(tenantId, workOrder.getWorkOrderId()),
                "run lease not found");
        verifyLease(command.getRunId(), command.getLeaseOwner(), command.getLeaseToken(),
                command.getFencingToken(), lease, now);
        String nextStatus;
        String waitId;
        if ("EVENT".equals(command.getWaitType())) {
            waitId = UUID.randomUUID().toString(); nextStatus = "WAITING_EVENT";
            EventSubscription subscription = new EventSubscription().setSubscriptionId(waitId).setTenantId(tenantId)
                    .setMissionId(workOrder.getMissionId()).setWorkOrderId(workOrder.getWorkOrderId())
                    .setEventType(requireRef(command.getEventType(), "eventType")).setSchemaVersion("v1")
                    .setSourceSystem(requireRef(command.getSourceSystem(), "sourceSystem"))
                    .setAggregateType(requireRef(command.getAggregateType(), "aggregateType"))
                    .setAggregateId(requireRef(command.getAggregateId(), "aggregateId"))
                    .setCorrelationId(command.getCorrelationId()).setMatcherCode(requireMatcher(command.getMatcherCode()))
                    .setStatus("ACTIVE").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
            require(mapper.insertEventSubscription(subscription) == 1, "failed to persist event subscription");
        } else if ("TIMER".equals(command.getWaitType())) {
            waitId = UUID.randomUUID().toString(); nextStatus = "WAITING_TIMER";
            LocalDateTime dueAt = command.getDueAt() == null ? null : LocalDateTime.ofInstant(command.getDueAt(), ZoneOffset.UTC);
            require(dueAt != null && dueAt.isAfter(now), "dueAt must be in the future");
            require(mapper.insertMissionTimer(new MissionTimer().setTimerId(waitId).setTenantId(tenantId)
                    .setMissionId(workOrder.getMissionId()).setWorkOrderId(workOrder.getWorkOrderId())
                    .setTimerType("WAKEUP").setDueAt(dueAt).setGeneration(1).setStatus("SCHEDULED")
                    .setVersion(1L).setCreatedAt(now).setUpdatedAt(now)) == 1, "failed to persist mission timer");
        } else throw new IllegalStateException("waitType must be EVENT or TIMER");
        require(mapper.releaseRunLease(tenantId, workOrder.getWorkOrderId(), command.getRunId(), command.getLeaseOwner(),
                command.getLeaseToken(), command.getFencingToken(), now) == 1, "stale run cannot release lease");
        require(mapper.finishAgentRun(tenantId, command.getRunId(), "WAITING", now) == 1,
                "failed to close waiting Agent run");
        require(mapper.transitionMissionWorkOrder(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(),
                "IN_PROGRESS", nextStatus, command.getWaitType(), null, now) == 1, "wait transition conflict");
        outbox(tenantId, "role_work_order", workOrder.getWorkOrderId(), "agent_control.work.waiting",
                Map.of("waitType", command.getWaitType(), "waitId", waitId), now);
        return result("role_work_order", workOrder.getWorkOrderId(), workOrder.getVersion() + 1, nextStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult matchEvent(MissionEventCommand command) {
        require(command != null, "command is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        require(Objects.equals(tenantId, command.getTenantId()), "event tenant does not match dispatcher context");
        LocalDateTime now = now();
        String eventId = requireRef(command.getEventId(), "eventId");
        String payloadHash = requireSha256(command.getPayloadSha256(), "payloadSha256");
        if (mapper.insertAgentEventInbox(tenantId, eventId, requireRef(command.getEventType(), "eventType"),
                payloadHash, now) == 0) {
            require(Objects.equals(payloadHash, mapper.selectAgentEventInboxPayloadForUpdate(tenantId, eventId)),
                    "eventId was already used with a different payload");
            return AgentControlResult.builder().aggregateType("mission_event")
                    .aggregateId(eventId).aggregateVersion(1L).status("DUPLICATE").duplicate(true).build();
        }
        List<EventSubscription> matches = mapper.selectMatchingSubscriptions(tenantId, command.getEventType(),
                requireRef(command.getSchemaVersion(), "schemaVersion"), requireRef(command.getSourceSystem(), "sourceSystem"),
                requireRef(command.getAggregateType(), "aggregateType"), requireRef(command.getAggregateId(), "aggregateId"),
                command.getCorrelationId());
        int awakened = 0;
        for (EventSubscription subscription : matches) {
            require("EXACT_AGGREGATE".equals(subscription.getMatcherCode()), "unsupported event matcher");
            if (mapper.matchSubscription(tenantId, subscription.getSubscriptionId(), subscription.getVersion(),
                    eventId, payloadHash, now) != 1) continue;
            WorkOrder workOrder = mapper.selectWorkOrderForUpdate(tenantId, subscription.getWorkOrderId());
            if (workOrder != null && mapper.transitionMissionWorkOrder(tenantId, workOrder.getWorkOrderId(),
                    workOrder.getVersion(), "WAITING_EVENT", "READY", null, null, now) == 1) awakened++;
        }
        outbox(tenantId, "mission_event", eventId, "agent_control.event.matched", Map.of("awakened", awakened), now);
        return result("mission_event", eventId, 1L, awakened > 0 ? "MATCHED" : "NO_MATCH");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult fireTimer(String timerId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId(); LocalDateTime now = now();
        MissionTimer timer = requireNonNull(mapper.selectMissionTimerForUpdate(tenantId, requireRef(timerId, "timerId")),
                "timer not found");
        require(mapper.fireMissionTimer(tenantId, timer.getTimerId(), timer.getVersion(), now) == 1,
                "timer is not due or already fired");
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, timer.getWorkOrderId()),
                "timer work order not found");
        require(mapper.transitionMissionWorkOrder(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(),
                "WAITING_TIMER", "READY", null, null, now) == 1, "timer wakeup conflict");
        outbox(tenantId, "mission_timer", timer.getTimerId(), "agent_control.timer.fired",
                Map.of("workOrderId", workOrder.getWorkOrderId()), now);
        return result("mission_timer", timer.getTimerId(), timer.getVersion() + 1, "FIRED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult resolveCompletedWorkOrder(String workOrderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId(); LocalDateTime now = now();
        WorkOrder completed = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, requireRef(workOrderId, "workOrderId")),
                "work order not found");
        require("COMPLETED".equals(completed.getStatus()) && completed.getMissionId() != null,
                "work order is not a completed mission work item");
        Mission mission = requireNonNull(mapper.selectMissionForUpdate(tenantId, completed.getMissionId()),
                "mission not found");
        BusinessResult predecessorResult = requireNonNull(
                mapper.selectBusinessResultByWorkOrderForUpdate(tenantId, completed.getWorkOrderId()),
                "completed mission work item has no business result");
        MissionCheckpoint predecessorCheckpoint = completed.getActiveRunId() == null ? null
                : mapper.selectMissionCheckpointForRunForUpdate(tenantId, completed.getWorkOrderId(),
                completed.getActiveRunId());
        if (completed.getActiveRunId() != null) {
            AgentRunLease lease = mapper.selectRunLeaseForUpdate(tenantId, completed.getWorkOrderId());
            if (lease != null && "ACTIVE".equals(lease.getStatus())
                    && Objects.equals(completed.getActiveRunId(), lease.getRunId())) {
                require(mapper.releaseRunLease(tenantId, completed.getWorkOrderId(), lease.getRunId(),
                        lease.getLeaseOwner(), lease.getLeaseToken(), lease.getFencingToken(), now) == 1,
                        "failed to release completed Agent run lease");
            }
            mapper.finishAgentRun(tenantId, completed.getActiveRunId(), "COMPLETED", now);
        }
        mapper.achieveMissionGoal(tenantId, completed.getGoalId(), now);
        int readyCount = 0;
        for (WorkDependency dependency : mapper.selectWaitingDependenciesByPredecessor(tenantId, completed.getWorkOrderId())) {
            require(mapper.satisfyDependency(tenantId, dependency.getDependencyId(), dependency.getVersion(), now) == 1,
                    "dependency satisfaction conflict");
            if (mapper.countUnsatisfiedDependencies(tenantId, dependency.getSuccessorWorkOrderId()) == 0) {
                WorkOrder successor = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId,
                        dependency.getSuccessorWorkOrderId()), "successor work order not found");
                RoleActionPolicy policy = requireNonNull(mapper.selectActionPolicy(tenantId, successor.getRoleCode(),
                        successor.getActionCode()), "successor action policy not found");
                require(Objects.equals(successor.getActionPolicyId(), policy.getPolicyId())
                                && Objects.equals(successor.getActionPolicyVersion(), policy.getVersion()),
                        "successor action policy snapshot drifted");
                if (Boolean.TRUE.equals(successor.getExecutionRequired())) {
                    require(predecessorCheckpoint != null,
                            "executable successor requires a structured predecessor checkpoint");
                }
                String derivedContext = derivedSuccessorContext(mission, completed, predecessorResult,
                        predecessorCheckpoint);
                derivedContext = AgentControlJson.canonicalBusinessObject(derivedContext, "derivedBusinessContextJson");
                String derivedInputSha256 = AgentControlJson.sha256(derivedContext);
                require(mapper.deriveSuccessorContext(tenantId, successor.getWorkOrderId(), successor.getVersion(),
                        derivedContext, derivedInputSha256, now) == 1, "successor context derivation conflict");
                successor.setBusinessContextJson(derivedContext).setExecutionInputSha256(derivedInputSha256)
                        .setVersion(successor.getVersion() + 1);
                String next = Boolean.TRUE.equals(policy.getApprovalRequired()) ? "WAITING_APPROVAL" : "READY";
                if (mapper.transitionMissionWorkOrder(tenantId, successor.getWorkOrderId(), successor.getVersion(),
                        "WAITING_DEPENDENCY", next, null, null, now) == 1) {
                    successor.setStatus(next).setVersion(successor.getVersion() + 1);
                    mapper.acceptSuccessorHandoff(tenantId, completed.getWorkOrderId(), successor.getWorkOrderId(),
                            successor.getAssigneeUserId(), now);
                    if ("WAITING_APPROVAL".equals(next)) {
                        createMissionApproval(tenantId, mission, successor, now);
                    }
                    readyCount++;
                }
            }
        }
        String status = "ADVANCED";
        if (mapper.countIncompleteMissionWorkOrders(tenantId, completed.getMissionId()) == 0) {
            if ("ACTIVE".equals(mission.getStatus())) {
                require(mapper.completeMission(tenantId, completed.getMissionId(), now) == 1,
                        "mission completion conflict");
                status = "MISSION_COMPLETED";
            } else if ("COMPLETED".equals(mission.getStatus())) {
                status = "MISSION_COMPLETED";
            }
        }
        outbox(tenantId, "role_work_order", completed.getWorkOrderId(), "agent_control.dependency.resolved",
                Map.of("readySuccessors", readyCount, "status", status), now);
        return result("role_work_order", completed.getWorkOrderId(), completed.getVersion(), status);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult recoverExpiredRun(String workOrderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = now();
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, requireRef(workOrderId, "workOrderId")),
                "work order not found");
        require("IN_PROGRESS".equals(workOrder.getStatus()), "only IN_PROGRESS work can recover an expired run");
        AgentRunLease lease = requireNonNull(mapper.selectRunLeaseForUpdate(tenantId, workOrder.getWorkOrderId()),
                "run lease not found");
        require(Objects.equals(workOrder.getActiveRunId(), lease.getRunId()),
                "active run does not match persisted lease");
        if (!"ACTIVE".equals(lease.getStatus()) || lease.getLeaseUntil().isAfter(now)) {
            return result("role_work_order", workOrder.getWorkOrderId(), workOrder.getVersion(), "NO_ACTION");
        }
        expireRunIfNeeded(tenantId, workOrder, lease, now);
        outbox(tenantId, "role_work_order", workOrder.getWorkOrderId(), "agent_control.run.expired",
                Map.of("runId", lease.getRunId(), "fencingToken", lease.getFencingToken()), now);
        return result("role_work_order", workOrder.getWorkOrderId(), workOrder.getVersion(), "RUN_EXPIRED");
    }

    private String derivedSuccessorContext(Mission mission, WorkOrder completed, BusinessResult result,
                                           MissionCheckpoint checkpoint) {
        Map<String, Object> predecessor = new LinkedHashMap<>();
        predecessor.put("workOrderId", completed.getWorkOrderId());
        predecessor.put("roleCode", completed.getRoleCode());
        predecessor.put("actionCode", completed.getActionCode());
        predecessor.put("outcomeCode", result.getOutcomeCode());
        predecessor.put("summary", result.getSummary());
        predecessor.put("evidenceRef", result.getEvidenceRef());
        if (checkpoint != null) {
            predecessor.put("decisionCode", checkpoint.getDecisionCode());
            predecessor.put("decision", JsonUtils.parseObject(checkpoint.getDecisionJson(), Object.class));
            predecessor.put("decisionSha256", checkpoint.getDecisionSha256());
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("missionId", mission.getMissionId());
        context.put("missionObjective", JsonUtils.parseObject(mission.getObjectiveJson(), Object.class));
        context.put("predecessor", predecessor);
        return JsonUtils.toJsonString(context);
    }

    private void createMissionApproval(Long tenantId, Mission mission, WorkOrder workOrder, LocalDateTime now) {
        String approvalId = UUID.randomUUID().toString();
        Approval approval = new Approval().setApprovalId(approvalId).setTenantId(tenantId)
                .setWorkOrderId(workOrder.getWorkOrderId()).setActionCode(workOrder.getActionCode())
                .setRequesterUserId(mission.getSupervisorUserId()).setScopeHash(approvalScopeHash(workOrder))
                .setStatus("PENDING").setReasonCode("MISSION_POLICY_APPROVAL").setVersion(1L).setRequestedAt(now);
        require(mapper.insertApproval(approval) == 1, "failed to create mission approval request");
        require(mapper.attachApproval(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(), approvalId, now) == 1,
                "failed to attach mission approval request");
        audit(tenantId, "role_approval", approvalId, 1L, "agent_control.approval.requested",
                mission.getSupervisorUserId(), Map.of("missionId", mission.getMissionId(),
                        "workOrderId", workOrder.getWorkOrderId()), now);
        outbox(tenantId, "role_approval", approvalId, "agent_control.approval.requested",
                Map.of("missionId", mission.getMissionId(), "workOrderId", workOrder.getWorkOrderId()), now);
    }

    private String approvalScopeHash(WorkOrder workOrder) {
        return DigestUtil.sha256Hex(String.join("\n",
                String.valueOf(workOrder.getTenantId()), workOrder.getWorkOrderId(), workOrder.getRoleCode(),
                workOrder.getActionCode(), Objects.toString(workOrder.getActionPolicyId(), "-"),
                Objects.toString(workOrder.getActionPolicyVersion(), "-"),
                Objects.toString(workOrder.getSkillId(), "-"), Objects.toString(workOrder.getSkillVersion(), "-"),
                Objects.toString(workOrder.getSkillDefinitionClosureSha256(), "-"),
                Objects.toString(workOrder.getExecutionInputSha256(), "-"),
                Objects.toString(workOrder.getRiskLevel(), "-")));
    }

    private void expireRunIfNeeded(Long tenantId, WorkOrder workOrder, AgentRunLease lease, LocalDateTime now) {
        if (!"ACTIVE".equals(lease.getStatus()) || lease.getLeaseUntil().isAfter(now)) return;
        require(Objects.equals(workOrder.getActiveRunId(), lease.getRunId()),
                "expired lease does not match the work order active run");
        require(mapper.expireRunLease(tenantId, workOrder.getWorkOrderId(), lease.getRunId(),
                lease.getFencingToken(), now) == 1, "failed to expire stale run lease");
        require(mapper.finishAgentRun(tenantId, lease.getRunId(), "EXPIRED", now) == 1,
                "failed to expire stale Agent run");
    }

    private void verifyLease(String runId, String leaseOwner, String leaseToken, Long fencingToken,
                             AgentRunLease lease, LocalDateTime now) {
        require("ACTIVE".equals(lease.getStatus()) && lease.getLeaseUntil().isAfter(now), "run lease expired");
        require(Objects.equals(runId, lease.getRunId()) && Objects.equals(leaseOwner, lease.getLeaseOwner())
                && Objects.equals(leaseToken, lease.getLeaseToken())
                && Objects.equals(fencingToken, lease.getFencingToken()), "stale fencing token or lease identity");
    }
    private void outbox(Long tenantId,String aggregateType,String aggregateId,String eventType,Map<String,?> payload,LocalDateTime now) {
        require(mapper.insertAgentOutbox(UUID.randomUUID().toString(), tenantId, aggregateType, aggregateId,
                eventType, JsonUtils.toJsonString(payload), now) == 1, "failed to persist Agent Control outbox");
    }
    private void audit(Long tenantId,String aggregateType,String aggregateId,Long version,String eventType,
                       Long actorId,Map<String,?> detail,LocalDateTime now) {
        AuditEvent event = new AuditEvent().setAuditEventId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setAggregateType(aggregateType).setAggregateId(aggregateId).setAggregateVersion(version)
                .setEventType(eventType).setActorUserId(actorId).setDetailJson(JsonUtils.toJsonString(detail))
                .setOccurredAt(now).setCreatedAt(now);
        require(mapper.insertAuditEvent(event) == 1, "failed to append Mission Runtime audit");
    }
    private LocalDateTime now(){ return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private Instant toInstant(LocalDateTime value){ return value.toInstant(ZoneOffset.UTC); }
    private static String requireMatcher(String value){ require("EXACT_AGGREGATE".equals(value), "matcherCode must be EXACT_AGGREGATE"); return value; }
    private static String requireSha256(String value,String field){ require(value != null && value.matches("[0-9a-f]{64}"), "invalid " + field); return value; }
    private static Long requireActor(Long value,String field){ require(value != null && value > 0, field + " is required"); return value; }
    private static String requireRef(String value,String field){ require(value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}"), "invalid " + field); return value; }
    private static String requireText(String value,String field,int max){ require(value != null && !value.isBlank() && value.length() <= max, "invalid " + field); return value; }
    private static String optional(String value,String fallback){ return value == null || value.isBlank() ? fallback : value; }
    private static String valueOrUuid(String value){ return value == null || value.isBlank() ? UUID.randomUUID().toString() : requireRef(value,"id"); }
    private static void requireJson(String value,String field){ requireText(value,field,16_384); try { JsonUtils.parseObject(value,Object.class); } catch(RuntimeException e){ throw new IllegalArgumentException(field + " must contain valid JSON",e); } }
    private static <T> T requireNonNull(T value,String message){ require(value != null,message); return value; }
    private static void require(boolean condition,String message){ if(!condition) throw new IllegalStateException(message); }
    private static AgentControlResult result(String type,String id,Long version,String status){ return AgentControlResult.builder().aggregateType(type).aggregateId(id).aggregateVersion(version).status(status).duplicate(false).build(); }
    private record Stage(String code,String roleCode,String actionCode,String title) {}
}
