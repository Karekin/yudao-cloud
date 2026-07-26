package cn.iocoder.yudao.module.cloudmold.supplyplanning.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.*;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.SupplyPlanningRecords.*;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.actor.SupplyPlanningActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SupplyPlanningServiceImpl implements SupplyPlanningCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-supply-planning";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> BUCKET_TYPES = Set.of("DAY", "WEEK", "MONTH");
    private static final Set<String> ISSUE_TYPES =
            Set.of("STOCKOUT", "LOW_STOCK", "EXCESS", "AGED", "PENDING_QC");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> CONVERSION_TARGET_TYPES =
            Set.of("PURCHASE_REQUEST", "TRANSFER_REQUEST");

    private final SupplyPlanningMapper mapper;
    private final OutboxAppender outboxAppender;
    private final SupplyPlanningActorPrincipalPort actorPrincipalPort;
    private final ReplenishmentExecutionPort replenishmentExecutionPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplyPlanningResult execute(SupplyPlanningCommand command, String actorPrincipalId) {
        validateEnvelope(command);
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
        attestActor(command, actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(
                actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve supply-planning operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "supply-planning operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing supply-planning operation is incomplete");
            SupplyPlanningResult replay =
                    JsonUtils.parseObject(operation.getResultJson(), SupplyPlanningResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_FORECAST -> createForecast(tenantId, command, now);
            case PUBLISH_FORECAST -> publishForecast(tenantId, command, now);
            case EVALUATE_FORECAST -> evaluateForecast(tenantId, command, now);
            case CREATE_SUPPLY_PLAN -> createSupplyPlan(tenantId, command, now);
            case EVALUATE_PLAN_SCENARIO -> evaluatePlanScenario(tenantId, command, now);
            case RECOMMEND_PLAN_SCENARIO -> recommendPlanScenario(tenantId, command, now);
            case SELECT_PLAN_SCENARIO -> selectPlanScenario(tenantId, command, now);
            case APPROVE_SUPPLY_PLAN -> approveSupplyPlan(tenantId, command, now);
            case RELEASE_SUPPLY_PLAN -> releaseSupplyPlan(tenantId, command, now);
            case CREATE_REPLENISHMENT -> createReplenishment(tenantId, command, now);
            case DECIDE_REPLENISHMENT -> decideReplenishment(tenantId, command, now);
            case CONVERT_REPLENISHMENT -> convertReplenishment(tenantId, command, now);
            case OPEN_INVENTORY_ISSUE -> openInventoryIssue(tenantId, command, now);
            case RUN_INVENTORY_HEALTH_SCAN -> runInventoryHealthScan(tenantId, command, now);
            case ACKNOWLEDGE_INVENTORY_ISSUE -> acknowledgeInventoryIssue(tenantId, command, now);
            case RESOLVE_INVENTORY_ISSUE -> resolveInventoryIssue(tenantId, command, now);
        };
        appendEvent(tenantId, command, outcome);
        SupplyPlanningResult result = SupplyPlanningResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version())
                .status(outcome.status())
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(),
                        outcome.aggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "supply-planning operation completion conflict");
        return result;
    }

    private Outcome createForecast(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.ForecastDefinition input =
                nonNull(command.getForecast(), "forecast is required");
        String id = valueOrUuid(input.getForecastId());
        requireRef(input.getForecastCode(), "forecastCode", 64);
        require(input.getHorizonStart() != null && input.getHorizonEnd() != null
                        && !input.getHorizonEnd().isBefore(input.getHorizonStart()),
                "forecast horizon is invalid");
        String bucketType = upper(input.getBucketType());
        require(BUCKET_TYPES.contains(bucketType), "unsupported forecast bucketType");
        requireRef(input.getModelRef(), "modelRef", 128);
        requireSha256(input.getBaselineSha256(), "baselineSha256");
        List<SupplyPlanningCommand.ForecastPointDefinition> points =
                nonNull(input.getPoints(), "forecast points are required");
        require(!points.isEmpty() && points.size() <= 500,
                "forecast points must contain between 1 and 500 rows");

        Forecast row = new Forecast().setForecastId(id).setTenantId(tenantId)
                .setForecastCode(input.getForecastCode()).setHorizonStart(input.getHorizonStart())
                .setHorizonEnd(input.getHorizonEnd()).setBucketType(bucketType)
                .setModelRef(input.getModelRef()).setBaselineSha256(input.getBaselineSha256())
                .setStatus("DRAFT").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertForecast(row) == 1, "failed to persist demand forecast");
        Set<String> pointKeys = new HashSet<>();
        for (SupplyPlanningCommand.ForecastPointDefinition point : points) {
            validateForecastPoint(input, point);
            String key = point.getCanonicalSkuId() + "|" + Objects.toString(point.getWarehouseId(), "")
                    + "|" + point.getBucketStart();
            require(pointKeys.add(key), "forecast contains a duplicate point");
            ForecastPoint record = new ForecastPoint().setTenantId(tenantId).setForecastId(id)
                    .setCanonicalSkuId(point.getCanonicalSkuId()).setWarehouseId(point.getWarehouseId())
                    .setBucketStart(point.getBucketStart()).setForecastQuantity(point.getForecastQuantity())
                    .setLowerQuantity(point.getLowerQuantity()).setUpperQuantity(point.getUpperQuantity())
                    .setUomCode(upper(point.getUomCode())).setCreatedAt(now);
            require(mapper.insertForecastPoint(record) == 1, "failed to persist forecast point");
        }
        return outcome("supply_planning.forecast.created", "demand_forecast", id, 1L, "DRAFT",
                payload("forecast_id", id, "forecast_code", row.getForecastCode(),
                        "horizon_start", row.getHorizonStart().toString(),
                        "horizon_end", row.getHorizonEnd().toString(), "bucket_type", bucketType,
                        "model_ref", row.getModelRef(), "baseline_sha256", row.getBaselineSha256(),
                        "point_count", points.size(), "points", points));
    }

    private Outcome publishForecast(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.ForecastDefinition input =
                nonNull(command.getForecast(), "forecast is required");
        requireRef(input.getForecastId(), "forecastId", 128);
        Forecast row = nonNull(mapper.selectForecastForUpdate(tenantId, input.getForecastId()),
                "demand forecast not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("DRAFT".equals(row.getStatus()), "only a draft forecast can be published");
        require(mapper.publishForecast(tenantId, row.getForecastId(), row.getVersion(), now) == 1,
                "demand forecast publish conflict");
        return outcome("supply_planning.forecast.published", "demand_forecast", row.getForecastId(),
                row.getVersion() + 1, "PUBLISHED",
                payload("forecast_id", row.getForecastId(), "forecast_code", row.getForecastCode(),
                        "previous_status", "DRAFT", "current_status", "PUBLISHED"));
    }

    private Outcome evaluateForecast(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.ForecastEvaluationDefinition input =
                nonNull(command.getForecastEvaluation(), "forecastEvaluation is required");
        String evaluationId = valueOrUuid(input.getEvaluationId());
        requireRef(input.getForecastId(), "forecastId", 128);
        requireSha256(input.getActualsSha256(), "actualsSha256");
        Forecast forecast = nonNull(mapper.selectForecastForUpdate(tenantId, input.getForecastId()),
                "demand forecast not found");
        require("PUBLISHED".equals(forecast.getStatus()),
                "only a published demand forecast can be evaluated");
        List<SupplyPlanningCommand.ForecastActualDefinition> actuals =
                nonNull(input.getActuals(), "forecast actuals are required");
        require(!actuals.isEmpty() && actuals.size() <= 500,
                "forecast actuals must contain between 1 and 500 rows");

        List<SupplyPlanningCalculations.ForecastPair> pairs = new ArrayList<>();
        List<ForecastActual> rows = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (SupplyPlanningCommand.ForecastActualDefinition actual : actuals) {
            requireRef(actual.getCanonicalSkuId(), "canonicalSkuId", 128);
            if (actual.getWarehouseId() != null) requireRef(actual.getWarehouseId(), "warehouseId", 128);
            require(actual.getBucketStart() != null, "bucketStart is required");
            require(actual.getActualQuantity() != null && actual.getActualQuantity().signum() >= 0,
                    "actualQuantity must not be negative");
            requireCode(actual.getUomCode(), "uomCode");
            String key = actual.getCanonicalSkuId() + "|" + Objects.toString(actual.getWarehouseId(), "")
                    + "|" + actual.getBucketStart();
            require(keys.add(key), "forecast evaluation contains a duplicate actual point");
            ForecastPoint point = nonNull(mapper.selectForecastPoint(tenantId, forecast.getForecastId(),
                            actual.getCanonicalSkuId(), actual.getWarehouseId(), actual.getBucketStart()),
                    "actual point has no matching forecast point");
            require(upper(actual.getUomCode()).equals(point.getUomCode()),
                    "actual point UOM does not match forecast point");
            BigDecimal signedError = point.getForecastQuantity().subtract(actual.getActualQuantity());
            pairs.add(new SupplyPlanningCalculations.ForecastPair(
                    point.getForecastQuantity(), actual.getActualQuantity()));
            rows.add(new ForecastActual().setTenantId(tenantId).setEvaluationId(evaluationId)
                    .setCanonicalSkuId(actual.getCanonicalSkuId()).setWarehouseId(actual.getWarehouseId())
                    .setBucketStart(actual.getBucketStart()).setForecastQuantity(point.getForecastQuantity())
                    .setActualQuantity(actual.getActualQuantity()).setAbsoluteError(signedError.abs())
                    .setSignedError(signedError).setUomCode(point.getUomCode()).setCreatedAt(now));
        }
        SupplyPlanningCalculations.ForecastMetrics metrics =
                SupplyPlanningCalculations.evaluateForecast(pairs);
        ForecastEvaluation evaluation = new ForecastEvaluation().setEvaluationId(evaluationId)
                .setTenantId(tenantId).setForecastId(forecast.getForecastId())
                .setActualsSha256(input.getActualsSha256()).setPointCount(rows.size())
                .setForecastQuantity(metrics.forecastQuantity()).setActualQuantity(metrics.actualQuantity())
                .setAbsoluteError(metrics.absoluteError()).setSignedError(metrics.signedError())
                .setWapeBasisPoints(metrics.wapeBasisPoints()).setBiasBasisPoints(metrics.biasBasisPoints())
                .setMae(metrics.mae()).setStatus("COMPLETED").setVersion(1L)
                .setEvaluatedAt(now).setCreatedAt(now);
        require(mapper.insertForecastEvaluation(evaluation) == 1,
                "failed to persist forecast evaluation");
        for (ForecastActual row : rows) {
            require(mapper.insertForecastActual(row) == 1, "failed to persist forecast actual");
        }
        return outcome("supply_planning.forecast.evaluated", "forecast_evaluation",
                evaluationId, 1L, "COMPLETED",
                payload("evaluation_id", evaluationId, "forecast_id", forecast.getForecastId(),
                        "actuals_sha256", input.getActualsSha256(), "point_count", rows.size(),
                        "forecast_quantity", metrics.forecastQuantity(),
                        "actual_quantity", metrics.actualQuantity(),
                        "absolute_error", metrics.absoluteError(), "signed_error", metrics.signedError(),
                        "wape_basis_points", metrics.wapeBasisPoints(),
                        "bias_basis_points", metrics.biasBasisPoints(), "mae", metrics.mae(),
                        "current_status", "COMPLETED"));
    }

    private Outcome createSupplyPlan(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.SupplyPlanDefinition input =
                nonNull(command.getSupplyPlan(), "supplyPlan is required");
        String id = valueOrUuid(input.getPlanId());
        requireRef(input.getPlanCode(), "planCode", 64);
        requireRef(input.getDemandForecastId(), "demandForecastId", 128);
        Forecast forecast = nonNull(mapper.selectForecastForUpdate(tenantId, input.getDemandForecastId()),
                "demand forecast not found");
        require("PUBLISHED".equals(forecast.getStatus()), "supply plan requires a published forecast");
        require(input.getHorizonStart() != null && input.getHorizonEnd() != null
                        && !input.getHorizonEnd().isBefore(input.getHorizonStart()),
                "supply-plan horizon is invalid");
        require(!input.getHorizonStart().isBefore(forecast.getHorizonStart())
                        && !input.getHorizonEnd().isAfter(forecast.getHorizonEnd()),
                "supply-plan horizon must stay inside the forecast horizon");
        require(input.getTargetServiceLevelBasisPoints() != null
                        && input.getTargetServiceLevelBasisPoints() >= 0
                        && input.getTargetServiceLevelBasisPoints() <= 10_000,
                "target service level must be between 0 and 10000 basis points");
        require(input.getBudgetAmountMinor() != null && input.getBudgetAmountMinor() >= 0,
                "budgetAmountMinor must not be negative");
        String currency = upper(input.getCurrencyCode());
        require(currency != null && currency.matches("[A-Z]{3}"), "currencyCode must be ISO-4217");
        requireSha256(input.getConstraintsSha256(), "constraintsSha256");
        SupplyPlan row = new SupplyPlan().setPlanId(id).setTenantId(tenantId)
                .setPlanCode(input.getPlanCode()).setDemandForecastId(input.getDemandForecastId())
                .setHorizonStart(input.getHorizonStart()).setHorizonEnd(input.getHorizonEnd())
                .setTargetServiceLevelBasisPoints(input.getTargetServiceLevelBasisPoints())
                .setBudgetAmountMinor(input.getBudgetAmountMinor()).setCurrencyCode(currency)
                .setConstraintsSha256(input.getConstraintsSha256()).setStatus("DRAFT").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertSupplyPlan(row) == 1, "failed to persist supply plan");
        return outcome("supply_planning.plan.created", "supply_plan", id, 1L, "DRAFT",
                payload("plan_id", id, "plan_code", row.getPlanCode(),
                        "demand_forecast_id", row.getDemandForecastId(),
                        "target_service_level_basis_points", row.getTargetServiceLevelBasisPoints(),
                        "budget_amount_minor", row.getBudgetAmountMinor(), "currency_code", currency,
                        "constraints_sha256", row.getConstraintsSha256()));
    }

    private Outcome evaluatePlanScenario(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.PlanScenarioDefinition input =
                nonNull(command.getPlanScenario(), "planScenario is required");
        String scenarioId = valueOrUuid(input.getScenarioId());
        requireRef(input.getPlanId(), "planId", 128);
        requireCode(input.getScenarioCode(), "scenarioCode");
        requireRef(input.getCanonicalSkuId(), "canonicalSkuId", 128);
        requireRef(input.getWarehouseId(), "warehouseId", 128);
        requireCode(input.getUomCode(), "uomCode");
        requireSha256(input.getParametersSha256(), "parametersSha256");
        requireNonNegative(input.getForecastQuantity(), "forecastQuantity");
        requireNonNegative(input.getSafetyStockQuantity(), "safetyStockQuantity");
        requireNonNegative(input.getOnHandQuantity(), "onHandQuantity");
        requireNonNegative(input.getInboundQuantity(), "inboundQuantity");
        requireNonNegative(input.getCapacityQuantity(), "capacityQuantity");
        requireNonNegative(input.getMinimumOrderQuantity(), "minimumOrderQuantity");
        require(input.getUnitCostMinor() != null && input.getUnitCostMinor() >= 0,
                "unitCostMinor must not be negative");
        SupplyPlan plan = nonNull(mapper.selectSupplyPlanForUpdate(tenantId, input.getPlanId()),
                "supply plan not found");
        require("DRAFT".equals(plan.getStatus()),
                "plan scenarios can only be evaluated before supply-plan approval");
        SupplyPlanningCalculations.ScenarioResult result =
                SupplyPlanningCalculations.evaluateScenario(
                        input.getForecastQuantity(), input.getSafetyStockQuantity(),
                        input.getOnHandQuantity(), input.getInboundQuantity(),
                        input.getCapacityQuantity(), input.getMinimumOrderQuantity(),
                        input.getUnitCostMinor(), plan.getBudgetAmountMinor());
        PlanScenario row = new PlanScenario().setScenarioId(scenarioId).setTenantId(tenantId)
                .setPlanId(plan.getPlanId()).setScenarioCode(upper(input.getScenarioCode()))
                .setCanonicalSkuId(input.getCanonicalSkuId()).setWarehouseId(input.getWarehouseId())
                .setForecastQuantity(input.getForecastQuantity())
                .setSafetyStockQuantity(input.getSafetyStockQuantity())
                .setOnHandQuantity(input.getOnHandQuantity()).setInboundQuantity(input.getInboundQuantity())
                .setCapacityQuantity(input.getCapacityQuantity())
                .setMinimumOrderQuantity(input.getMinimumOrderQuantity())
                .setUnitCostMinor(input.getUnitCostMinor())
                .setConstrainedOrderQuantity(result.constrainedOrderQuantity())
                .setProjectedShortageQuantity(result.projectedShortageQuantity())
                .setProjectedServiceLevelBasisPoints(result.projectedServiceLevelBasisPoints())
                .setProjectedCostMinor(result.projectedCostMinor()).setUomCode(upper(input.getUomCode()))
                .setParametersSha256(input.getParametersSha256())
                .setSolverType("DETERMINISTIC_HEURISTIC_V1").setStatus("EVALUATED").setVersion(1L)
                .setEvaluatedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertPlanScenario(row) == 1, "failed to persist supply-plan scenario");
        return outcome("supply_planning.plan_scenario.evaluated", "supply_plan_scenario",
                scenarioId, 1L, "EVALUATED",
                payload("scenario_id", scenarioId, "plan_id", plan.getPlanId(),
                        "scenario_code", row.getScenarioCode(),
                        "canonical_sku_id", row.getCanonicalSkuId(), "warehouse_id", row.getWarehouseId(),
                        "constrained_order_quantity", row.getConstrainedOrderQuantity(),
                        "projected_shortage_quantity", row.getProjectedShortageQuantity(),
                        "projected_service_level_basis_points",
                        row.getProjectedServiceLevelBasisPoints(),
                        "projected_cost_minor", row.getProjectedCostMinor(),
                        "solver_type", row.getSolverType(), "parameters_sha256", row.getParametersSha256(),
                        "current_status", "EVALUATED"));
    }

    private Outcome selectPlanScenario(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.PlanScenarioDefinition input =
                nonNull(command.getPlanScenario(), "planScenario is required");
        requireRef(input.getScenarioId(), "scenarioId", 128);
        requireRef(input.getSelectedByPrincipalId(), "selectedByPrincipalId", 128);
        PlanScenario row = nonNull(mapper.selectPlanScenarioForUpdate(
                tenantId, input.getScenarioId()), "supply-plan scenario not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("EVALUATED".equals(row.getStatus()), "only an evaluated scenario can be selected");
        SupplyPlan plan = nonNull(mapper.selectSupplyPlanForUpdate(tenantId, row.getPlanId()),
                "supply plan not found");
        require("DRAFT".equals(plan.getStatus()),
                "scenario selection must be completed before supply-plan approval");
        require(mapper.countSelectedPlanScenarios(tenantId, plan.getPlanId()) == 0,
                "supply plan already has a selected scenario");
        require(mapper.selectPlanScenario(tenantId, row.getScenarioId(), row.getVersion(),
                        input.getSelectedByPrincipalId(), now) == 1,
                "supply-plan scenario selection conflict");
        return outcome("supply_planning.plan_scenario.selected", "supply_plan_scenario",
                row.getScenarioId(), row.getVersion() + 1, "SELECTED",
                payload("scenario_id", row.getScenarioId(), "plan_id", row.getPlanId(),
                        "scenario_code", row.getScenarioCode(),
                        "selected_by_principal_id", input.getSelectedByPrincipalId(),
                        "projected_service_level_basis_points",
                        row.getProjectedServiceLevelBasisPoints(),
                        "projected_cost_minor", row.getProjectedCostMinor(),
                        "previous_status", "EVALUATED", "current_status", "SELECTED"));
    }

    private Outcome recommendPlanScenario(Long tenantId, SupplyPlanningCommand command,
                                          LocalDateTime now) {
        SupplyPlanningCommand.ScenarioRecommendationDefinition input =
                nonNull(command.getScenarioRecommendation(),
                        "scenarioRecommendation is required");
        String recommendationId = valueOrUuid(input.getRecommendationId());
        requireRef(input.getPlanId(), "planId", 128);
        require(input.getTargetServiceLevelFloorBasisPoints() != null
                        && input.getTargetServiceLevelFloorBasisPoints() >= 0
                        && input.getTargetServiceLevelFloorBasisPoints() <= 10_000,
                "target service level floor must be between 0 and 10000 basis points");
        require(input.getMaxProjectedCostMinor() != null
                        && input.getMaxProjectedCostMinor() >= 0,
                "maxProjectedCostMinor must not be negative");
        require(input.getDemandStressBasisPoints() != null
                        && input.getDemandStressBasisPoints() >= 10_000
                        && input.getDemandStressBasisPoints() <= 20_000,
                "demand stress must be between 10000 and 20000 basis points");
        require(input.getSupplyAvailabilityBasisPoints() != null
                        && input.getSupplyAvailabilityBasisPoints() >= 0
                        && input.getSupplyAvailabilityBasisPoints() <= 10_000,
                "supply availability must be between 0 and 10000 basis points");
        requireSha256(input.getPolicySha256(), "policySha256");

        SupplyPlan plan = nonNull(mapper.selectSupplyPlanForUpdate(tenantId, input.getPlanId()),
                "supply plan not found");
        requireExpectedVersion(input.getExpectedPlanVersion(), plan.getVersion());
        require("DRAFT".equals(plan.getStatus()),
                "scenario recommendation must be completed before supply-plan approval");

        List<String> requestedIds =
                nonNull(input.getCandidateScenarioIds(), "candidateScenarioIds are required");
        require(requestedIds.size() >= 2 && requestedIds.size() <= 20,
                "candidateScenarioIds must contain between 2 and 20 scenarios");
        Set<String> uniqueIds = new HashSet<>();
        for (String scenarioId : requestedIds) {
            requireRef(scenarioId, "candidateScenarioId", 128);
            require(uniqueIds.add(scenarioId), "candidateScenarioIds must be unique");
        }
        List<String> sortedIds = uniqueIds.stream().sorted().toList();
        List<PlanScenario> candidates = mapper.selectPlanScenariosForRecommendation(
                tenantId, plan.getPlanId(), sortedIds);
        require(candidates.size() == sortedIds.size(),
                "one or more candidate scenarios were not found in the supply plan");

        PlanScenario scope = candidates.get(0);
        List<SupplyPlanningCalculations.ScenarioCandidate> calculationCandidates =
                new ArrayList<>();
        for (PlanScenario candidate : candidates) {
            require("EVALUATED".equals(candidate.getStatus()),
                    "only evaluated scenarios can be recommended");
            require(Objects.equals(scope.getCanonicalSkuId(), candidate.getCanonicalSkuId())
                            && Objects.equals(scope.getWarehouseId(), candidate.getWarehouseId())
                            && Objects.equals(scope.getUomCode(), candidate.getUomCode()),
                    "candidate scenarios must share SKU, warehouse and UOM");
            requireSha256(candidate.getParametersSha256(), "candidate parametersSha256");
            calculationCandidates.add(new SupplyPlanningCalculations.ScenarioCandidate(
                    candidate.getScenarioId(), candidate.getForecastQuantity(),
                    candidate.getSafetyStockQuantity(), candidate.getOnHandQuantity(),
                    candidate.getInboundQuantity(), candidate.getCapacityQuantity(),
                    candidate.getMinimumOrderQuantity(), candidate.getUnitCostMinor()));
        }
        String candidateSetSha256 = DigestUtil.sha256Hex(candidates.stream()
                .sorted(Comparator.comparing(PlanScenario::getScenarioId))
                .map(candidate -> candidate.getScenarioId() + ":" + candidate.getParametersSha256())
                .reduce((left, right) -> left + "\n" + right).orElseThrow());
        SupplyPlanningCalculations.RecommendationResult result =
                SupplyPlanningCalculations.recommendScenario(
                        calculationCandidates, plan.getBudgetAmountMinor(),
                        input.getTargetServiceLevelFloorBasisPoints(),
                        input.getMaxProjectedCostMinor(), input.getDemandStressBasisPoints(),
                        input.getSupplyAvailabilityBasisPoints());
        SupplyPlanningCalculations.ScenarioAssessment recommended = result.recommended();
        List<Map<String, Object>> rationale = result.rankedAssessments().stream()
                .map(SupplyPlanningServiceImpl::assessmentPayload)
                .toList();

        PlanScenarioRecommendation row = new PlanScenarioRecommendation()
                .setRecommendationId(recommendationId).setTenantId(tenantId)
                .setPlanId(plan.getPlanId())
                .setRecommendedScenarioId(recommended.scenarioId())
                .setCandidateSetSha256(candidateSetSha256)
                .setCandidateScenarioIdsJson(JsonUtils.toJsonString(sortedIds))
                .setPolicySha256(input.getPolicySha256())
                .setTargetServiceLevelFloorBasisPoints(
                        input.getTargetServiceLevelFloorBasisPoints())
                .setMaxProjectedCostMinor(input.getMaxProjectedCostMinor())
                .setDemandStressBasisPoints(input.getDemandStressBasisPoints())
                .setSupplyAvailabilityBasisPoints(input.getSupplyAvailabilityBasisPoints())
                .setWorstCaseServiceLevelBasisPoints(
                        recommended.stressed().projectedServiceLevelBasisPoints())
                .setProjectedCostMinor(recommended.stressed().projectedCostMinor())
                .setProjectedShortageQuantity(
                        recommended.stressed().projectedShortageQuantity())
                .setSensitivityBasisPoints(recommended.sensitivityBasisPoints())
                .setViolationCount(recommended.constraintViolations().size())
                .setConstraintViolationsJson(
                        JsonUtils.toJsonString(recommended.constraintViolations()))
                .setRationaleJson(JsonUtils.toJsonString(rationale))
                .setSolverType("ROBUST_LEXICOGRAPHIC_V1")
                .setStatus("PROPOSED").setVersion(1L)
                .setRecommendedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertPlanScenarioRecommendation(row) == 1,
                "failed to persist supply-plan scenario recommendation");
        return outcome("supply_planning.plan_scenario.recommended",
                "supply_plan_scenario_recommendation", recommendationId, 1L, "PROPOSED",
                payload("recommendation_id", recommendationId, "plan_id", plan.getPlanId(),
                        "recommended_scenario_id", recommended.scenarioId(),
                        "candidate_scenario_ids", sortedIds,
                        "candidate_set_sha256", candidateSetSha256,
                        "policy_sha256", input.getPolicySha256(),
                        "target_service_level_floor_basis_points",
                        input.getTargetServiceLevelFloorBasisPoints(),
                        "max_projected_cost_minor", input.getMaxProjectedCostMinor(),
                        "demand_stress_basis_points", input.getDemandStressBasisPoints(),
                        "supply_availability_basis_points",
                        input.getSupplyAvailabilityBasisPoints(),
                        "worst_case_service_level_basis_points",
                        recommended.stressed().projectedServiceLevelBasisPoints(),
                        "projected_shortage_quantity",
                        recommended.stressed().projectedShortageQuantity(),
                        "projected_cost_minor", recommended.stressed().projectedCostMinor(),
                        "sensitivity_basis_points", recommended.sensitivityBasisPoints(),
                        "constraint_violations", recommended.constraintViolations(),
                        "solver_type", row.getSolverType(), "ranked_assessments", rationale,
                        "current_status", "PROPOSED", "execution_authorized", false));
    }

    private static Map<String, Object> assessmentPayload(
            SupplyPlanningCalculations.ScenarioAssessment assessment) {
        return payload("scenario_id", assessment.scenarioId(),
                "baseline_service_level_basis_points",
                assessment.baseline().projectedServiceLevelBasisPoints(),
                "worst_case_service_level_basis_points",
                assessment.stressed().projectedServiceLevelBasisPoints(),
                "projected_shortage_quantity",
                assessment.stressed().projectedShortageQuantity(),
                "projected_cost_minor", assessment.stressed().projectedCostMinor(),
                "sensitivity_basis_points", assessment.sensitivityBasisPoints(),
                "constraint_violations", assessment.constraintViolations());
    }

    private Outcome approveSupplyPlan(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.SupplyPlanDefinition input =
                nonNull(command.getSupplyPlan(), "supplyPlan is required");
        requireRef(input.getPlanId(), "planId", 128);
        requireRef(input.getApproverPrincipalId(), "approverPrincipalId", 128);
        SupplyPlan row = nonNull(mapper.selectSupplyPlanForUpdate(tenantId, input.getPlanId()),
                "supply plan not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("DRAFT".equals(row.getStatus()), "only a draft supply plan can be approved");
        require(mapper.countSelectedPlanScenarios(tenantId, row.getPlanId()) == 1,
                "supply plan requires exactly one selected scenario before approval");
        require(mapper.approveSupplyPlan(tenantId, row.getPlanId(), row.getVersion(),
                        input.getApproverPrincipalId(), now) == 1,
                "supply-plan approval conflict");
        return outcome("supply_planning.plan.approved", "supply_plan", row.getPlanId(),
                row.getVersion() + 1, "APPROVED",
                payload("plan_id", row.getPlanId(), "plan_code", row.getPlanCode(),
                        "demand_forecast_id", row.getDemandForecastId(),
                        "approver_principal_id", input.getApproverPrincipalId(),
                        "previous_status", "DRAFT", "current_status", "APPROVED"));
    }

    private Outcome releaseSupplyPlan(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.SupplyPlanDefinition input =
                nonNull(command.getSupplyPlan(), "supplyPlan is required");
        requireRef(input.getPlanId(), "planId", 128);
        requireRef(input.getReleasePrincipalId(), "releasePrincipalId", 128);
        SupplyPlan plan = nonNull(mapper.selectSupplyPlanForUpdate(tenantId, input.getPlanId()),
                "supply plan not found");
        requireExpectedVersion(input.getExpectedVersion(), plan.getVersion());
        require("APPROVED".equals(plan.getStatus()), "only an approved supply plan can be released");
        PlanScenario scenario = nonNull(mapper.selectSelectedPlanScenarioForUpdate(
                tenantId, plan.getPlanId()), "supply plan has no selected scenario");
        require(scenario.getProjectedShortageQuantity().signum() == 0
                        || scenario.getConstrainedOrderQuantity().signum() > 0,
                "selected scenario has unresolved shortage and no feasible replenishment quantity");

        String recommendationId = null;
        if (scenario.getConstrainedOrderQuantity().signum() > 0) {
            recommendationId = UUID.randomUUID().toString();
            Replenishment recommendation = new Replenishment()
                    .setRecommendationId(recommendationId).setTenantId(tenantId)
                    .setPlanId(plan.getPlanId()).setCanonicalSkuId(scenario.getCanonicalSkuId())
                    .setWarehouseId(scenario.getWarehouseId())
                    .setSuggestedQuantity(scenario.getConstrainedOrderQuantity())
                    .setUomCode(scenario.getUomCode()).setNeedByDate(plan.getHorizonStart())
                    .setReasonCode("SELECTED_SCENARIO").setStatus("PROPOSED").setVersion(1L)
                    .setCreatedAt(now).setUpdatedAt(now);
            require(mapper.insertReplenishment(recommendation) == 1,
                    "failed to create replenishment from selected scenario");
            appendEvent(tenantId, command, replenishmentCreatedOutcome(recommendation));
        }
        require(mapper.releaseSupplyPlan(tenantId, plan.getPlanId(), plan.getVersion(),
                        scenario.getScenarioId(), input.getReleasePrincipalId(), now) == 1,
                "supply-plan release conflict");
        return outcome("supply_planning.plan.released", "supply_plan", plan.getPlanId(),
                plan.getVersion() + 1, "RELEASED",
                payload("plan_id", plan.getPlanId(), "plan_code", plan.getPlanCode(),
                        "selected_scenario_id", scenario.getScenarioId(),
                        "recommendation_id", recommendationId,
                        "release_principal_id", input.getReleasePrincipalId(),
                        "previous_status", "APPROVED", "current_status", "RELEASED"));
    }

    private Outcome createReplenishment(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.ReplenishmentDefinition input =
                nonNull(command.getReplenishment(), "replenishment is required");
        String id = valueOrUuid(input.getRecommendationId());
        requireRef(input.getPlanId(), "planId", 128);
        SupplyPlan plan = nonNull(mapper.selectSupplyPlanForUpdate(tenantId, input.getPlanId()),
                "supply plan not found");
        require("APPROVED".equals(plan.getStatus()), "replenishment requires an approved supply plan");
        requireRef(input.getCanonicalSkuId(), "canonicalSkuId", 128);
        requireRef(input.getWarehouseId(), "warehouseId", 128);
        requirePositive(input.getSuggestedQuantity(), "suggestedQuantity");
        requireCode(input.getUomCode(), "uomCode");
        require(upper(input.getUomCode()).length() <= 16, "uomCode exceeds 16 characters");
        require(input.getNeedByDate() != null, "needByDate is required");
        requireCode(input.getReasonCode(), "reasonCode");
        Replenishment row = new Replenishment().setRecommendationId(id).setTenantId(tenantId)
                .setPlanId(input.getPlanId()).setCanonicalSkuId(input.getCanonicalSkuId())
                .setWarehouseId(input.getWarehouseId()).setSuggestedQuantity(input.getSuggestedQuantity())
                .setUomCode(upper(input.getUomCode())).setNeedByDate(input.getNeedByDate())
                .setReasonCode(upper(input.getReasonCode())).setStatus("PROPOSED").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertReplenishment(row) == 1, "failed to persist replenishment recommendation");
        return replenishmentCreatedOutcome(row);
    }

    private Outcome decideReplenishment(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.ReplenishmentDefinition input =
                nonNull(command.getReplenishment(), "replenishment is required");
        requireRef(input.getRecommendationId(), "recommendationId", 128);
        String decision = upper(input.getDecision());
        require(Set.of("APPROVED", "REJECTED").contains(decision),
                "replenishment decision must be APPROVED or REJECTED");
        requireRef(input.getDecisionPrincipalId(), "decisionPrincipalId", 128);
        Replenishment row = nonNull(
                mapper.selectReplenishmentForUpdate(tenantId, input.getRecommendationId()),
                "replenishment recommendation not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("PROPOSED".equals(row.getStatus()), "replenishment has already been decided");
        require(mapper.decideReplenishment(tenantId, row.getRecommendationId(), row.getVersion(),
                        decision, input.getDecisionPrincipalId(), now) == 1,
                "replenishment decision conflict");
        return outcome("supply_planning.replenishment.decided", "replenishment_recommendation",
                row.getRecommendationId(), row.getVersion() + 1, decision,
                payload("recommendation_id", row.getRecommendationId(), "plan_id", row.getPlanId(),
                        "canonical_sku_id", row.getCanonicalSkuId(), "warehouse_id", row.getWarehouseId(),
                        "decision", decision, "decision_principal_id", input.getDecisionPrincipalId(),
                        "previous_status", "PROPOSED", "current_status", decision));
    }

    private Outcome convertReplenishment(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.ReplenishmentConversionDefinition input =
                nonNull(command.getReplenishmentConversion(), "replenishmentConversion is required");
        String conversionId = valueOrUuid(input.getConversionId());
        requireRef(input.getRecommendationId(), "recommendationId", 128);
        requireRef(input.getConvertedByPrincipalId(), "convertedByPrincipalId", 128);
        String targetType = upper(input.getTargetType());
        require(CONVERSION_TARGET_TYPES.contains(targetType), "unsupported replenishment targetType");
        requireSha256(input.getMappingEvidenceSha256(), "mappingEvidenceSha256");
        Replenishment row = nonNull(mapper.selectReplenishmentForUpdate(
                tenantId, input.getRecommendationId()), "replenishment recommendation not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("APPROVED".equals(row.getStatus()),
                "only an approved replenishment recommendation can be converted");
        ReplenishmentExecutionPort.ExecutionResult execution = nonNull(
                replenishmentExecutionPort.createDraft(
                        new ReplenishmentExecutionPort.ExecutionCommand(
                                "supply-conversion:" + conversionId, conversionId, targetType,
                                row.getCanonicalSkuId(), row.getWarehouseId(),
                                row.getSuggestedQuantity(), row.getUomCode(), row.getNeedByDate(),
                                input.getMappingEvidenceSha256(), input.getSupplierId(),
                                input.getAccountId(), input.getErpProductId(),
                                input.getErpProductUnitId(), input.getUnitCostMinor(),
                                input.getTaxPercent(), input.getSourceWarehouseId(),
                                input.getTargetWarehouseId(), input.getWmsSkuId(),
                                command.getOccurredAt())),
                "replenishment execution returned no draft evidence");
        requireRef(execution.sourceSystem(), "execution sourceSystem", 64);
        requireRef(execution.documentType(), "execution documentType", 64);
        requireRef(execution.externalDocumentId(), "externalDocumentId", 128);
        require("PREPARE".equals(execution.status()),
                "replenishment execution must return a PREPARE draft");
        String targetReference = execution.sourceSystem() + ":"
                + execution.documentType() + ":" + execution.externalDocumentId();
        requireRef(targetReference, "targetReference", 128);
        ReplenishmentConversion conversion = new ReplenishmentConversion()
                .setConversionId(conversionId).setTenantId(tenantId)
                .setRecommendationId(row.getRecommendationId()).setTargetType(targetType)
                .setTargetReference(targetReference).setRequestedQuantity(row.getSuggestedQuantity())
                .setUomCode(row.getUomCode()).setStatus("CREATED")
                .setConvertedByPrincipalId(input.getConvertedByPrincipalId())
                .setVersion(1L).setConvertedAt(now).setCreatedAt(now);
        require(mapper.insertReplenishmentConversion(conversion) == 1,
                "failed to persist replenishment conversion");
        require(mapper.convertReplenishment(tenantId, row.getRecommendationId(),
                        row.getVersion(), now) == 1,
                "replenishment conversion conflict");
        return outcome("supply_planning.replenishment.converted",
                "replenishment_recommendation", row.getRecommendationId(),
                row.getVersion() + 1, "CONVERTED",
                payload("recommendation_id", row.getRecommendationId(), "plan_id", row.getPlanId(),
                        "conversion_id", conversionId, "target_type", targetType,
                        "target_reference", targetReference, "requested_quantity",
                        row.getSuggestedQuantity(), "uom_code", row.getUomCode(),
                        "mapping_evidence_sha256", input.getMappingEvidenceSha256(),
                        "external_document_no", execution.externalDocumentNo(),
                        "converted_by_principal_id", input.getConvertedByPrincipalId(),
                        "previous_status", "APPROVED", "current_status", "CONVERTED"));
    }

    private Outcome openInventoryIssue(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.InventoryIssueDefinition input =
                nonNull(command.getInventoryIssue(), "inventoryIssue is required");
        String id = valueOrUuid(input.getIssueId());
        requireRef(input.getSourceBalanceId(), "sourceBalanceId", 128);
        String issueType = upper(input.getIssueType());
        String severity = upper(input.getSeverity());
        require(ISSUE_TYPES.contains(issueType), "unsupported inventory issueType");
        require(SEVERITIES.contains(severity), "unsupported inventory severity");
        require(mapper.countActiveInventoryIssues(
                        tenantId, input.getSourceBalanceId(), issueType) == 0,
                "an active inventory-health issue already exists for this source and type");
        InventoryIssue row = new InventoryIssue().setIssueId(id).setTenantId(tenantId)
                .setSourceBalanceId(input.getSourceBalanceId()).setIssueType(issueType)
                .setSeverity(severity).setStatus("OPEN").setDetectionSource("MANUAL")
                .setVersion(1L).setOpenedAt(now)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertInventoryIssue(row) == 1, "failed to persist inventory-health issue");
        return inventoryIssueOutcome("supply_planning.inventory_issue.opened", row, null, "OPEN");
    }

    private Outcome runInventoryHealthScan(Long tenantId, SupplyPlanningCommand command,
                                           LocalDateTime now) {
        SupplyPlanningCommand.InventoryHealthScanDefinition input =
                nonNull(command.getInventoryHealthScan(), "inventoryHealthScan is required");
        String scanId = valueOrUuid(input.getScanId());
        requireCode(input.getPolicyCode(), "policyCode");
        requireSha256(input.getPolicySha256(), "policySha256");
        require(input.getAgedThresholdDays() != null && input.getAgedThresholdDays() > 0,
                "agedThresholdDays must be positive");
        List<SupplyPlanningCommand.InventoryHealthObservationDefinition> observations =
                nonNull(input.getObservations(), "inventory health observations are required");
        require(!observations.isEmpty() && observations.size() <= 500,
                "inventory health observations must contain between 1 and 500 rows");
        int issueCount = 0;
        int skippedCount = 0;
        Set<String> sources = new HashSet<>();
        List<Map<String, Object>> createdIssues = new ArrayList<>();
        List<InventoryIssue> issues = new ArrayList<>();
        for (SupplyPlanningCommand.InventoryHealthObservationDefinition observation : observations) {
            requireRef(observation.getSourceBalanceId(), "sourceBalanceId", 128);
            require(sources.add(observation.getSourceBalanceId()),
                    "inventory health scan contains a duplicate source balance");
            require(observation.getAvailableQuantity() != null, "availableQuantity is required");
            requireNonNegative(observation.getReorderPointQuantity(), "reorderPointQuantity");
            requireNonNegative(observation.getMaximumStockQuantity(), "maximumStockQuantity");
            require(observation.getAgeDays() != null && observation.getAgeDays() >= 0,
                    "ageDays must not be negative");
            IssueClassification classification = classifyInventoryIssue(
                    observation, input.getAgedThresholdDays());
            if (classification == null) continue;
            if (mapper.countActiveInventoryIssues(tenantId, observation.getSourceBalanceId(),
                    classification.issueType()) > 0) {
                skippedCount++;
                continue;
            }
            String issueId = UUID.randomUUID().toString();
            InventoryIssue issue = new InventoryIssue().setIssueId(issueId).setTenantId(tenantId)
                    .setSourceBalanceId(observation.getSourceBalanceId())
                    .setIssueType(classification.issueType()).setSeverity(classification.severity())
                    .setStatus("OPEN").setScanId(scanId).setDetectionSource("RULE_SCAN")
                    .setVersion(1L).setOpenedAt(now).setCreatedAt(now).setUpdatedAt(now);
            issues.add(issue);
            createdIssues.add(payload("issue_id", issueId,
                    "source_balance_id", observation.getSourceBalanceId(),
                    "issue_type", classification.issueType(), "severity", classification.severity()));
            issueCount++;
        }
        InventoryHealthScan scan = new InventoryHealthScan().setScanId(scanId).setTenantId(tenantId)
                .setPolicyCode(upper(input.getPolicyCode())).setPolicySha256(input.getPolicySha256())
                .setObservationCount(observations.size()).setIssueCount(issueCount)
                .setSkippedActiveCount(skippedCount).setStatus("COMPLETED").setVersion(1L)
                .setEvaluatedAt(now).setCreatedAt(now);
        require(mapper.insertInventoryHealthScan(scan) == 1,
                "failed to persist inventory-health scan");
        for (InventoryIssue issue : issues) {
            require(mapper.insertInventoryIssue(issue) == 1,
                    "failed to persist scanned inventory-health issue");
            appendEvent(tenantId, command,
                    inventoryIssueOutcome("supply_planning.inventory_issue.opened",
                            issue, null, "OPEN"));
        }
        return outcome("supply_planning.inventory_health.scanned", "inventory_health_scan",
                scanId, 1L, "COMPLETED",
                payload("scan_id", scanId, "policy_code", scan.getPolicyCode(),
                        "policy_sha256", scan.getPolicySha256(),
                        "observation_count", observations.size(), "issue_count", issueCount,
                        "skipped_active_count", skippedCount, "created_issues", createdIssues,
                        "current_status", "COMPLETED"));
    }

    private static IssueClassification classifyInventoryIssue(
            SupplyPlanningCommand.InventoryHealthObservationDefinition observation,
            int agedThresholdDays) {
        if (Boolean.TRUE.equals(observation.getPendingQualityInspection())) {
            return new IssueClassification("PENDING_QC", "CRITICAL");
        }
        if (observation.getAvailableQuantity().signum() <= 0) {
            return new IssueClassification("STOCKOUT", "CRITICAL");
        }
        if (observation.getAvailableQuantity().compareTo(observation.getReorderPointQuantity()) <= 0) {
            return new IssueClassification("LOW_STOCK", "HIGH");
        }
        if (observation.getMaximumStockQuantity().signum() > 0
                && observation.getAvailableQuantity()
                .compareTo(observation.getMaximumStockQuantity()) > 0) {
            return new IssueClassification("EXCESS", "MEDIUM");
        }
        if (observation.getAgeDays() >= agedThresholdDays) {
            return new IssueClassification("AGED", "MEDIUM");
        }
        return null;
    }

    private Outcome acknowledgeInventoryIssue(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.InventoryIssueDefinition input =
                nonNull(command.getInventoryIssue(), "inventoryIssue is required");
        requireRef(input.getIssueId(), "issueId", 128);
        requireRef(input.getOwnerPrincipalId(), "ownerPrincipalId", 128);
        InventoryIssue row = nonNull(mapper.selectInventoryIssueForUpdate(tenantId, input.getIssueId()),
                "inventory-health issue not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("OPEN".equals(row.getStatus()), "only an open inventory issue can be acknowledged");
        require(mapper.acknowledgeInventoryIssue(tenantId, row.getIssueId(), row.getVersion(),
                        input.getOwnerPrincipalId(), now) == 1,
                "inventory issue acknowledge conflict");
        row.setOwnerPrincipalId(input.getOwnerPrincipalId()).setStatus("ACKNOWLEDGED")
                .setVersion(row.getVersion() + 1).setAcknowledgedAt(now).setUpdatedAt(now);
        return inventoryIssueOutcome("supply_planning.inventory_issue.acknowledged",
                row, "OPEN", "ACKNOWLEDGED");
    }

    private Outcome resolveInventoryIssue(Long tenantId, SupplyPlanningCommand command, LocalDateTime now) {
        SupplyPlanningCommand.InventoryIssueDefinition input =
                nonNull(command.getInventoryIssue(), "inventoryIssue is required");
        requireRef(input.getIssueId(), "issueId", 128);
        requireRef(input.getOwnerPrincipalId(), "ownerPrincipalId", 128);
        requireCode(input.getResolutionCode(), "resolutionCode");
        InventoryIssue row = nonNull(mapper.selectInventoryIssueForUpdate(tenantId, input.getIssueId()),
                "inventory-health issue not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require(Set.of("OPEN", "ACKNOWLEDGED").contains(row.getStatus()),
                "inventory issue is already terminal");
        require(row.getOwnerPrincipalId() == null
                        || row.getOwnerPrincipalId().equals(input.getOwnerPrincipalId()),
                "only the inventory issue owner can resolve it");
        String before = row.getStatus();
        require(mapper.resolveInventoryIssue(tenantId, row.getIssueId(), row.getVersion(),
                        input.getOwnerPrincipalId(), upper(input.getResolutionCode()), now) == 1,
                "inventory issue resolution conflict");
        row.setOwnerPrincipalId(row.getOwnerPrincipalId() == null
                        ? input.getOwnerPrincipalId() : row.getOwnerPrincipalId())
                .setResolutionCode(upper(input.getResolutionCode())).setStatus("RESOLVED")
                .setVersion(row.getVersion() + 1).setResolvedAt(now).setUpdatedAt(now);
        return inventoryIssueOutcome("supply_planning.inventory_issue.resolved",
                row, before, "RESOLVED");
    }

    private void validateForecastPoint(SupplyPlanningCommand.ForecastDefinition forecast,
                                       SupplyPlanningCommand.ForecastPointDefinition point) {
        require(point != null, "forecast point is required");
        requireRef(point.getCanonicalSkuId(), "canonicalSkuId", 128);
        if (point.getWarehouseId() != null) {
            requireRef(point.getWarehouseId(), "warehouseId", 128);
        }
        require(point.getBucketStart() != null
                        && !point.getBucketStart().isBefore(forecast.getHorizonStart())
                        && !point.getBucketStart().isAfter(forecast.getHorizonEnd()),
                "forecast point bucket is outside the horizon");
        requireNonNegative(point.getForecastQuantity(), "forecastQuantity");
        if (point.getLowerQuantity() != null) {
            requireNonNegative(point.getLowerQuantity(), "lowerQuantity");
            require(point.getLowerQuantity().compareTo(point.getForecastQuantity()) <= 0,
                    "lowerQuantity must not exceed forecastQuantity");
        }
        if (point.getUpperQuantity() != null) {
            require(point.getUpperQuantity().compareTo(point.getForecastQuantity()) >= 0,
                    "upperQuantity must not be below forecastQuantity");
        }
        requireCode(point.getUomCode(), "uomCode");
        require(upper(point.getUomCode()).length() <= 16, "uomCode exceeds 16 characters");
    }

    private Outcome inventoryIssueOutcome(String eventType, InventoryIssue row,
                                          String previousStatus, String currentStatus) {
        return outcome(eventType, "inventory_health_issue", row.getIssueId(), row.getVersion(),
                currentStatus, payload("issue_id", row.getIssueId(),
                        "source_balance_id", row.getSourceBalanceId(), "issue_type", row.getIssueType(),
                        "severity", row.getSeverity(), "previous_status", previousStatus,
                        "current_status", currentStatus, "owner_principal_id", row.getOwnerPrincipalId(),
                        "resolution_code", row.getResolutionCode(), "scan_id", row.getScanId(),
                        "detection_source", row.getDetectionSource()));
    }

    private static Outcome replenishmentCreatedOutcome(Replenishment row) {
        return outcome("supply_planning.replenishment.created",
                "replenishment_recommendation", row.getRecommendationId(), 1L, "PROPOSED",
                payload("recommendation_id", row.getRecommendationId(), "plan_id", row.getPlanId(),
                        "canonical_sku_id", row.getCanonicalSkuId(),
                        "warehouse_id", row.getWarehouseId(),
                        "suggested_quantity", row.getSuggestedQuantity(),
                        "uom_code", row.getUomCode(),
                        "need_by_date", row.getNeedByDate().toString(),
                        "reason_code", row.getReasonCode(),
                        "current_status", "PROPOSED"));
    }

    private void appendEvent(Long tenantId, SupplyPlanningCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType(outcome.eventType()).schemaVersion(1).sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId).aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId()).aggregateVersion(outcome.version())
                .eventSequence(outcome.version().shortValue()).occurredAt(command.getOccurredAt())
                .traceId(command.getRunId()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(outcome.aggregateType() + ":" + outcome.aggregateId()
                        + ":event:" + outcome.version())
                .payload(outcome.payload())
                .headers(Map.of("pii_safe", true, "planning_authority", true))
                .destination("lakehouse").build());
    }

    private static Outcome outcome(String eventType, String aggregateType, String aggregateId,
                                   Long version, String status, Map<String, Object> payload) {
        return new Outcome(eventType, aggregateType, aggregateId, version, status, payload);
    }

    private static void validateEnvelope(SupplyPlanningCommand command) {
        require(command != null, "supply-planning command is required");
        require(command.getOperation() != null, "operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (command.getRunId() != null) requireRef(command.getRunId(), "runId", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void attestActor(SupplyPlanningCommand command, String actorPrincipalId) {
        switch (command.getOperation()) {
            case SELECT_PLAN_SCENARIO -> {
                if (command.getPlanScenario() != null) {
                    command.getPlanScenario().setSelectedByPrincipalId(actorPrincipalId);
                }
            }
            case APPROVE_SUPPLY_PLAN -> {
                if (command.getSupplyPlan() != null) {
                    command.getSupplyPlan().setApproverPrincipalId(actorPrincipalId);
                }
            }
            case RELEASE_SUPPLY_PLAN -> {
                if (command.getSupplyPlan() != null) {
                    command.getSupplyPlan().setReleasePrincipalId(actorPrincipalId);
                }
            }
            case DECIDE_REPLENISHMENT -> {
                if (command.getReplenishment() != null) {
                    command.getReplenishment().setDecisionPrincipalId(actorPrincipalId);
                }
            }
            case CONVERT_REPLENISHMENT -> {
                if (command.getReplenishmentConversion() != null) {
                    command.getReplenishmentConversion().setConvertedByPrincipalId(actorPrincipalId);
                }
            }
            case ACKNOWLEDGE_INVENTORY_ISSUE, RESOLVE_INVENTORY_ISSUE -> {
                if (command.getInventoryIssue() != null) {
                    command.getInventoryIssue().setOwnerPrincipalId(actorPrincipalId);
                }
            }
            default -> {
                // The remaining operations do not persist an actor field.
            }
        }
    }

    private static String valueOrUuid(String value) {
        if (value == null) return UUID.randomUUID().toString();
        requireRef(value, "aggregate id", 128);
        return value;
    }

    private static void requireExpectedVersion(Long expected, Long actual) {
        require(expected != null && expected.equals(actual), "aggregate version conflict");
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
    }

    private static void requireCode(String value, String field) {
        String normalized = upper(value);
        require(normalized != null && SAFE_CODE.matcher(normalized).matches(),
                field + " must be an uppercase code");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(),
                field + " must be a lowercase SHA-256");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        require(value != null && value.compareTo(BigDecimal.ZERO) > 0, field + " must be positive");
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        require(value != null && value.compareTo(BigDecimal.ZERO) >= 0,
                field + " must not be negative");
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> T nonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            payload.put((String) values[index], values[index + 1]);
        }
        return payload;
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId,
                           Long version, String status, Map<String, Object> payload) {
    }

    private record IssueClassification(String issueType, String severity) {
    }
}
