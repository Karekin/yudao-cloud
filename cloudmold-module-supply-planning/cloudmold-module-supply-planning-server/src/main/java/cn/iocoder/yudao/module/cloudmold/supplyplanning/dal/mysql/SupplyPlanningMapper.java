package cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionProposalView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.SupplyPlanningRecords.*;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query.SupplyPlanningWorkItem;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupplyPlanningMapper {

    @Insert("""
            INSERT INTO cloudmold_supply_planning_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_type,aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_supply_planning_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_supply_planning_operation
            SET status=10,aggregate_type=#{aggregateType},aggregate_id=#{aggregateId},
                result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId,
                               @Param("tenantId") Long tenantId,
                               @Param("aggregateType") String aggregateType,
                               @Param("aggregateId") String aggregateId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_demand_forecast
              (forecast_id,tenant_id,forecast_code,horizon_start,horizon_end,bucket_type,model_ref,
               baseline_sha256,status,version,created_at,updated_at)
            VALUES (#{forecastId},#{tenantId},#{forecastCode},#{horizonStart},#{horizonEnd},#{bucketType},
                    #{modelRef},#{baselineSha256},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertForecast(Forecast value);

    @Insert("""
            INSERT INTO cloudmold_demand_forecast_point
              (tenant_id,forecast_id,canonical_sku_id,warehouse_id,bucket_start,forecast_quantity,
               lower_quantity,upper_quantity,uom_code,created_at)
            VALUES (#{tenantId},#{forecastId},#{canonicalSkuId},#{warehouseId},#{bucketStart},
                    #{forecastQuantity},#{lowerQuantity},#{upperQuantity},#{uomCode},#{createdAt})
            """)
    int insertForecastPoint(ForecastPoint value);

    @Select("""
            SELECT tenant_id,forecast_id,canonical_sku_id,warehouse_id,bucket_start,forecast_quantity,
                   lower_quantity,upper_quantity,uom_code,created_at
            FROM cloudmold_demand_forecast_point
            WHERE tenant_id=#{tenantId} AND forecast_id=#{forecastId}
              AND canonical_sku_id=#{canonicalSkuId}
              AND ((warehouse_id IS NULL AND #{warehouseId} IS NULL) OR warehouse_id=#{warehouseId})
              AND bucket_start=#{bucketStart}
            """)
    ForecastPoint selectForecastPoint(@Param("tenantId") Long tenantId,
                                      @Param("forecastId") String forecastId,
                                      @Param("canonicalSkuId") String canonicalSkuId,
                                      @Param("warehouseId") String warehouseId,
                                      @Param("bucketStart") java.time.LocalDate bucketStart);

    @Insert("""
            INSERT INTO cloudmold_forecast_evaluation
              (evaluation_id,tenant_id,forecast_id,actuals_sha256,point_count,forecast_quantity,
               actual_quantity,absolute_error,signed_error,wape_basis_points,bias_basis_points,
               mae,status,version,evaluated_at,created_at)
            VALUES (#{evaluationId},#{tenantId},#{forecastId},#{actualsSha256},#{pointCount},
                    #{forecastQuantity},#{actualQuantity},#{absoluteError},#{signedError},
                    #{wapeBasisPoints},#{biasBasisPoints},#{mae},#{status},#{version},
                    #{evaluatedAt},#{createdAt})
            """)
    int insertForecastEvaluation(ForecastEvaluation value);

    @Insert("""
            INSERT INTO cloudmold_forecast_actual
              (tenant_id,evaluation_id,canonical_sku_id,warehouse_id,bucket_start,forecast_quantity,
               actual_quantity,absolute_error,signed_error,uom_code,created_at)
            VALUES (#{tenantId},#{evaluationId},#{canonicalSkuId},#{warehouseId},#{bucketStart},
                    #{forecastQuantity},#{actualQuantity},#{absoluteError},#{signedError},
                    #{uomCode},#{createdAt})
            """)
    int insertForecastActual(ForecastActual value);

    @Select("""
            SELECT forecast_id,tenant_id,forecast_code,horizon_start,horizon_end,bucket_type,model_ref,
                   baseline_sha256,status,version,published_at,created_at,updated_at
            FROM cloudmold_demand_forecast
            WHERE tenant_id=#{tenantId} AND forecast_id=#{forecastId} FOR UPDATE
            """)
    Forecast selectForecastForUpdate(@Param("tenantId") Long tenantId,
                                     @Param("forecastId") String forecastId);

    @Update("""
            UPDATE cloudmold_demand_forecast
            SET status='PUBLISHED',version=version+1,published_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND forecast_id=#{forecastId}
              AND status='DRAFT' AND version=#{expectedVersion}
            """)
    int publishForecast(@Param("tenantId") Long tenantId,
                        @Param("forecastId") String forecastId,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_supply_plan
              (plan_id,tenant_id,plan_code,demand_forecast_id,horizon_start,horizon_end,
               target_service_level_basis_points,budget_amount_minor,currency_code,constraints_sha256,
               status,version,created_at,updated_at)
            VALUES (#{planId},#{tenantId},#{planCode},#{demandForecastId},#{horizonStart},#{horizonEnd},
                    #{targetServiceLevelBasisPoints},#{budgetAmountMinor},#{currencyCode},#{constraintsSha256},
                    #{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertSupplyPlan(SupplyPlan value);

    @Insert("""
            INSERT INTO cloudmold_supply_plan_scenario
              (scenario_id,tenant_id,plan_id,scenario_code,canonical_sku_id,warehouse_id,
               forecast_quantity,safety_stock_quantity,on_hand_quantity,inbound_quantity,
               capacity_quantity,minimum_order_quantity,unit_cost_minor,constrained_order_quantity,
               projected_shortage_quantity,projected_service_level_basis_points,projected_cost_minor,
               uom_code,parameters_sha256,solver_type,status,version,evaluated_at,created_at,updated_at)
            VALUES (#{scenarioId},#{tenantId},#{planId},#{scenarioCode},#{canonicalSkuId},#{warehouseId},
                    #{forecastQuantity},#{safetyStockQuantity},#{onHandQuantity},#{inboundQuantity},
                    #{capacityQuantity},#{minimumOrderQuantity},#{unitCostMinor},#{constrainedOrderQuantity},
                    #{projectedShortageQuantity},#{projectedServiceLevelBasisPoints},#{projectedCostMinor},
                    #{uomCode},#{parametersSha256},#{solverType},#{status},#{version},#{evaluatedAt},
                    #{createdAt},#{updatedAt})
            """)
    int insertPlanScenario(PlanScenario value);

    @Select("""
            <script>
            SELECT scenario_id,tenant_id,plan_id,scenario_code,canonical_sku_id,warehouse_id,
                   forecast_quantity,safety_stock_quantity,on_hand_quantity,inbound_quantity,
                   capacity_quantity,minimum_order_quantity,unit_cost_minor,constrained_order_quantity,
                   projected_shortage_quantity,projected_service_level_basis_points,projected_cost_minor,
                   uom_code,parameters_sha256,solver_type,status,selected_by_principal_id,version,
                   evaluated_at,selected_at,created_at,updated_at
            FROM cloudmold_supply_plan_scenario
            WHERE tenant_id=#{tenantId} AND plan_id=#{planId}
              AND scenario_id IN
              <foreach collection="scenarioIds" item="scenarioId" open="(" separator="," close=")">
                #{scenarioId}
              </foreach>
            ORDER BY scenario_id
            FOR UPDATE
            </script>
            """)
    List<PlanScenario> selectPlanScenariosForRecommendation(
            @Param("tenantId") Long tenantId,
            @Param("planId") String planId,
            @Param("scenarioIds") List<String> scenarioIds);

    @Insert("""
            INSERT INTO cloudmold_supply_plan_scenario_recommendation
              (recommendation_id,tenant_id,plan_id,recommended_scenario_id,candidate_set_sha256,
               candidate_scenario_ids_json,policy_sha256,target_service_level_floor_basis_points,
               max_projected_cost_minor,demand_stress_basis_points,supply_availability_basis_points,
               worst_case_service_level_basis_points,projected_cost_minor,
               projected_shortage_quantity,sensitivity_basis_points,violation_count,
               constraint_violations_json,rationale_json,solver_type,status,version,
               recommended_at,created_at,updated_at)
            VALUES (#{recommendationId},#{tenantId},#{planId},#{recommendedScenarioId},
                    #{candidateSetSha256},#{candidateScenarioIdsJson},#{policySha256},
                    #{targetServiceLevelFloorBasisPoints},#{maxProjectedCostMinor},
                    #{demandStressBasisPoints},#{supplyAvailabilityBasisPoints},
                    #{worstCaseServiceLevelBasisPoints},#{projectedCostMinor},
                    #{projectedShortageQuantity},#{sensitivityBasisPoints},#{violationCount},
                    #{constraintViolationsJson},#{rationaleJson},#{solverType},#{status},#{version},
                    #{recommendedAt},#{createdAt},#{updatedAt})
            """)
    int insertPlanScenarioRecommendation(PlanScenarioRecommendation value);

    @Select("""
            SELECT plan_id,tenant_id,plan_code,demand_forecast_id,horizon_start,horizon_end,
                   target_service_level_basis_points,budget_amount_minor,currency_code,constraints_sha256,
                   status,approver_principal_id,selected_scenario_id,release_principal_id,
                   version,approved_at,released_at,created_at,updated_at
            FROM cloudmold_supply_plan
            WHERE tenant_id=#{tenantId} AND plan_id=#{planId} FOR UPDATE
            """)
    SupplyPlan selectSupplyPlanForUpdate(@Param("tenantId") Long tenantId,
                                         @Param("planId") String planId);

    @Update("""
            UPDATE cloudmold_supply_plan
            SET status='APPROVED',approver_principal_id=#{approverPrincipalId},version=version+1,
                approved_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND plan_id=#{planId}
              AND status='DRAFT' AND version=#{expectedVersion}
            """)
    int approveSupplyPlan(@Param("tenantId") Long tenantId,
                          @Param("planId") String planId,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("approverPrincipalId") String approverPrincipalId,
                          @Param("now") LocalDateTime now);

    @Select("""
            SELECT scenario_id,tenant_id,plan_id,scenario_code,canonical_sku_id,warehouse_id,
                   forecast_quantity,safety_stock_quantity,on_hand_quantity,inbound_quantity,
                   capacity_quantity,minimum_order_quantity,unit_cost_minor,constrained_order_quantity,
                   projected_shortage_quantity,projected_service_level_basis_points,projected_cost_minor,
                   uom_code,parameters_sha256,solver_type,status,selected_by_principal_id,version,
                   evaluated_at,selected_at,created_at,updated_at
            FROM cloudmold_supply_plan_scenario
            WHERE tenant_id=#{tenantId} AND scenario_id=#{scenarioId} FOR UPDATE
            """)
    PlanScenario selectPlanScenarioForUpdate(@Param("tenantId") Long tenantId,
                                             @Param("scenarioId") String scenarioId);

    @Select("""
            SELECT scenario_id,tenant_id,plan_id,scenario_code,canonical_sku_id,warehouse_id,
                   forecast_quantity,safety_stock_quantity,on_hand_quantity,inbound_quantity,
                   capacity_quantity,minimum_order_quantity,unit_cost_minor,constrained_order_quantity,
                   projected_shortage_quantity,projected_service_level_basis_points,projected_cost_minor,
                   uom_code,parameters_sha256,solver_type,status,selected_by_principal_id,version,
                   evaluated_at,selected_at,created_at,updated_at
            FROM cloudmold_supply_plan_scenario
            WHERE tenant_id=#{tenantId} AND plan_id=#{planId} AND status='SELECTED'
            LIMIT 1 FOR UPDATE
            """)
    PlanScenario selectSelectedPlanScenarioForUpdate(@Param("tenantId") Long tenantId,
                                                     @Param("planId") String planId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_supply_plan_scenario
            WHERE tenant_id=#{tenantId} AND plan_id=#{planId} AND status='SELECTED'
            """)
    long countSelectedPlanScenarios(@Param("tenantId") Long tenantId,
                                    @Param("planId") String planId);

    @Update("""
            UPDATE cloudmold_supply_plan_scenario
            SET status='SELECTED',selected_by_principal_id=#{selectedByPrincipalId},
                selected_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND scenario_id=#{scenarioId}
              AND status='EVALUATED' AND version=#{expectedVersion}
            """)
    int selectPlanScenario(@Param("tenantId") Long tenantId,
                           @Param("scenarioId") String scenarioId,
                           @Param("expectedVersion") Long expectedVersion,
                           @Param("selectedByPrincipalId") String selectedByPrincipalId,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_supply_plan
            SET status='RELEASED',selected_scenario_id=#{selectedScenarioId},
                release_principal_id=#{releasePrincipalId},released_at=#{now},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND plan_id=#{planId}
              AND status='APPROVED' AND version=#{expectedVersion}
            """)
    int releaseSupplyPlan(@Param("tenantId") Long tenantId,
                          @Param("planId") String planId,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("selectedScenarioId") String selectedScenarioId,
                          @Param("releasePrincipalId") String releasePrincipalId,
                          @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_replenishment_recommendation
              (recommendation_id,tenant_id,plan_id,canonical_sku_id,warehouse_id,suggested_quantity,
               uom_code,need_by_date,reason_code,status,version,created_at,updated_at)
            VALUES (#{recommendationId},#{tenantId},#{planId},#{canonicalSkuId},#{warehouseId},
                    #{suggestedQuantity},#{uomCode},#{needByDate},#{reasonCode},#{status},#{version},
                    #{createdAt},#{updatedAt})
            """)
    int insertReplenishment(Replenishment value);

    @Select("""
            SELECT recommendation_id,tenant_id,plan_id,canonical_sku_id,warehouse_id,suggested_quantity,
                   uom_code,need_by_date,reason_code,status,decision_principal_id,decision_at,version,
                   created_at,updated_at
            FROM cloudmold_replenishment_recommendation
            WHERE tenant_id=#{tenantId} AND recommendation_id=#{recommendationId} FOR UPDATE
            """)
    Replenishment selectReplenishmentForUpdate(@Param("tenantId") Long tenantId,
                                               @Param("recommendationId") String recommendationId);

    @Update("""
            UPDATE cloudmold_replenishment_recommendation
            SET status=#{decision},decision_principal_id=#{decisionPrincipalId},decision_at=#{now},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND recommendation_id=#{recommendationId}
              AND status='PROPOSED' AND version=#{expectedVersion}
            """)
    int decideReplenishment(@Param("tenantId") Long tenantId,
                            @Param("recommendationId") String recommendationId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("decision") String decision,
                            @Param("decisionPrincipalId") String decisionPrincipalId,
                            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_replenishment_execution_proposal
              (proposal_id,tenant_id,recommendation_id,expected_recommendation_version,target_type,
               owner_type,owner_id,source_warehouse_id,target_warehouse_id,
               proposed_by_principal_id,policy_code,policy_sha256,status,version,proposed_at,
               created_at,updated_at)
            VALUES (#{proposalId},#{tenantId},#{recommendationId},#{expectedRecommendationVersion},
                    #{targetType},#{ownerType},#{ownerId},#{sourceWarehouseId},
                    #{targetWarehouseId},#{proposedByPrincipalId},#{policyCode},
                    #{policySha256},#{status},#{version},#{proposedAt},#{createdAt},#{updatedAt})
            """)
    int insertReplenishmentExecutionProposal(ReplenishmentExecutionProposal value);

    @Select("""
            SELECT proposal_id,tenant_id,recommendation_id,expected_recommendation_version,target_type,
                   owner_type,owner_id,source_warehouse_id,target_warehouse_id,
                   proposed_by_principal_id,policy_code,policy_sha256,status,version,
                   consumed_by_conversion_id,proposed_at,consumed_at,created_at,updated_at
            FROM cloudmold_replenishment_execution_proposal
            WHERE tenant_id=#{tenantId} AND recommendation_id=#{recommendationId}
              AND status='READY'
            LIMIT 1 FOR UPDATE
            """)
    ReplenishmentExecutionProposal selectReadyReplenishmentExecutionProposalForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("recommendationId") String recommendationId);

    @Update("""
            UPDATE cloudmold_replenishment_execution_proposal
            SET status='CONSUMED',version=version+1,consumed_by_conversion_id=#{conversionId},
                consumed_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND proposal_id=#{proposalId}
              AND status='READY' AND version=#{expectedVersion}
            """)
    int consumeReplenishmentExecutionProposal(@Param("tenantId") Long tenantId,
                                              @Param("proposalId") String proposalId,
                                              @Param("expectedVersion") Long expectedVersion,
                                              @Param("conversionId") String conversionId,
                                              @Param("now") LocalDateTime now);

    @Select("""
            SELECT proposal.proposal_id proposalId,
                   proposal.recommendation_id recommendationId,
                   proposal.expected_recommendation_version expectedRecommendationVersion,
                   proposal.target_type targetType,
                   proposal.owner_type ownerType,
                   proposal.owner_id ownerId,
                   proposal.source_warehouse_id sourceWarehouseId,
                   proposal.target_warehouse_id targetWarehouseId,
                   proposal.proposed_by_principal_id proposedByPrincipalId,
                   proposal.policy_code policyCode,
                   proposal.policy_sha256 policySha256,
                   proposal.proposed_at proposedAt
            FROM cloudmold_replenishment_execution_proposal proposal
            JOIN cloudmold_replenishment_recommendation recommendation
              ON recommendation.tenant_id=proposal.tenant_id
             AND recommendation.recommendation_id=proposal.recommendation_id
             AND recommendation.status='APPROVED'
             AND recommendation.version=proposal.expected_recommendation_version
            LEFT JOIN cloudmold_replenishment_conversion conversion
              ON conversion.tenant_id=proposal.tenant_id
             AND conversion.recommendation_id=proposal.recommendation_id
            WHERE proposal.tenant_id=#{tenantId}
              AND proposal.status='READY'
              AND conversion.conversion_id IS NULL
            ORDER BY proposal.proposed_at, proposal.proposal_id
            LIMIT #{limit}
            """)
    List<ReplenishmentExecutionProposalView> selectReadyReplenishmentExecutionProposals(
            @Param("tenantId") Long tenantId,
            @Param("limit") int limit);

    @Select("""
            SELECT proposal.proposal_id proposalId,
                   proposal.recommendation_id recommendationId,
                   proposal.expected_recommendation_version expectedRecommendationVersion,
                   proposal.target_type targetType,
                   proposal.owner_type ownerType,
                   proposal.owner_id ownerId,
                   proposal.source_warehouse_id sourceWarehouseId,
                   proposal.target_warehouse_id targetWarehouseId,
                   proposal.proposed_by_principal_id proposedByPrincipalId,
                   proposal.policy_code policyCode,
                   proposal.policy_sha256 policySha256,
                   proposal.proposed_at proposedAt
            FROM cloudmold_replenishment_execution_proposal proposal
            JOIN cloudmold_replenishment_recommendation recommendation
              ON recommendation.tenant_id=proposal.tenant_id
             AND recommendation.recommendation_id=proposal.recommendation_id
             AND recommendation.status='APPROVED'
             AND recommendation.version=proposal.expected_recommendation_version
            LEFT JOIN cloudmold_replenishment_conversion conversion
              ON conversion.tenant_id=proposal.tenant_id
             AND conversion.recommendation_id=proposal.recommendation_id
            WHERE proposal.tenant_id=#{tenantId}
              AND proposal.proposal_id=#{proposalId}
              AND proposal.status='READY'
              AND conversion.conversion_id IS NULL
            """)
    ReplenishmentExecutionProposalView selectReadyReplenishmentExecutionProposal(
            @Param("tenantId") Long tenantId,
            @Param("proposalId") String proposalId);

    @Insert("""
            INSERT INTO cloudmold_replenishment_conversion
              (conversion_id,tenant_id,recommendation_id,target_type,target_aggregate_type,
               target_aggregate_id,target_aggregate_no,target_aggregate_status,requested_quantity,uom_code,status,
               converted_by_principal_id,version,converted_at,created_at)
            VALUES (#{conversionId},#{tenantId},#{recommendationId},#{targetType},#{targetAggregateType},
                    #{targetAggregateId},#{targetAggregateNo},#{targetAggregateStatus},
                    #{requestedQuantity},#{uomCode},#{status},#{convertedByPrincipalId},#{version},
                    #{convertedAt},#{createdAt})
            """)
    int insertReplenishmentConversion(ReplenishmentConversion value);

    @Select("""
            SELECT recommendation.recommendation_id recommendationId,
                   recommendation.plan_id planId,
                   recommendation.status recommendationStatus,
                   conversion.target_type targetType,
                   conversion.target_aggregate_type targetAggregateType,
                   conversion.target_aggregate_id targetAggregateId,
                   conversion.target_aggregate_no targetAggregateNo,
                   conversion.target_aggregate_status targetAggregateStatus,
                   conversion.requested_quantity requestedQuantity,
                   conversion.uom_code uomCode,
                   recommendation.need_by_date needByDate,
                   conversion.converted_by_principal_id convertedByPrincipalId,
                   conversion.converted_at convertedAt
            FROM cloudmold_replenishment_recommendation recommendation
            LEFT JOIN cloudmold_replenishment_conversion conversion
              ON conversion.tenant_id=recommendation.tenant_id
             AND conversion.recommendation_id=recommendation.recommendation_id
            WHERE recommendation.tenant_id=#{tenantId}
              AND recommendation.recommendation_id=#{recommendationId}
            """)
    ReplenishmentExecutionView selectReplenishmentExecution(@Param("tenantId") Long tenantId,
                                                            @Param("recommendationId") String recommendationId);

    @Update("""
            UPDATE cloudmold_replenishment_recommendation
            SET status='CONVERTED',version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND recommendation_id=#{recommendationId}
              AND status='APPROVED' AND version=#{expectedVersion}
            """)
    int convertReplenishment(@Param("tenantId") Long tenantId,
                             @Param("recommendationId") String recommendationId,
                             @Param("expectedVersion") Long expectedVersion,
                             @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_inventory_health_issue
              (issue_id,tenant_id,source_balance_id,issue_type,severity,status,scan_id,detection_source,
               version,opened_at,created_at,updated_at)
            VALUES (#{issueId},#{tenantId},#{sourceBalanceId},#{issueType},#{severity},#{status},
                    #{scanId},#{detectionSource},#{version},#{openedAt},#{createdAt},#{updatedAt})
            """)
    int insertInventoryIssue(InventoryIssue value);

    @Insert("""
            INSERT INTO cloudmold_inventory_health_scan
              (scan_id,tenant_id,policy_code,policy_sha256,observation_count,issue_count,
               skipped_active_count,status,version,evaluated_at,created_at)
            VALUES (#{scanId},#{tenantId},#{policyCode},#{policySha256},#{observationCount},
                    #{issueCount},#{skippedActiveCount},#{status},#{version},#{evaluatedAt},#{createdAt})
            """)
    int insertInventoryHealthScan(InventoryHealthScan value);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_inventory_health_issue
            WHERE tenant_id=#{tenantId} AND source_balance_id=#{sourceBalanceId}
              AND issue_type=#{issueType} AND status IN ('OPEN', 'ACKNOWLEDGED')
            """)
    long countActiveInventoryIssues(@Param("tenantId") Long tenantId,
                                    @Param("sourceBalanceId") String sourceBalanceId,
                                    @Param("issueType") String issueType);

    @Select("""
            SELECT issue_id,tenant_id,source_balance_id,issue_type,severity,status,owner_principal_id,
                   resolution_code,scan_id,detection_source,version,opened_at,acknowledged_at,
                   resolved_at,created_at,updated_at
            FROM cloudmold_inventory_health_issue
            WHERE tenant_id=#{tenantId} AND issue_id=#{issueId} FOR UPDATE
            """)
    InventoryIssue selectInventoryIssueForUpdate(@Param("tenantId") Long tenantId,
                                                 @Param("issueId") String issueId);

    @Update("""
            UPDATE cloudmold_inventory_health_issue
            SET status='ACKNOWLEDGED',owner_principal_id=#{ownerPrincipalId},acknowledged_at=#{now},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND issue_id=#{issueId}
              AND status='OPEN' AND version=#{expectedVersion}
            """)
    int acknowledgeInventoryIssue(@Param("tenantId") Long tenantId,
                                  @Param("issueId") String issueId,
                                  @Param("expectedVersion") Long expectedVersion,
                                  @Param("ownerPrincipalId") String ownerPrincipalId,
                                  @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_health_issue
            SET status='RESOLVED',resolution_code=#{resolutionCode},resolved_at=#{now},
                owner_principal_id=COALESCE(owner_principal_id,#{ownerPrincipalId}),
                acknowledged_at=COALESCE(acknowledged_at,#{now}),version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND issue_id=#{issueId}
              AND status IN ('OPEN','ACKNOWLEDGED') AND version=#{expectedVersion}
            """)
    int resolveInventoryIssue(@Param("tenantId") Long tenantId,
                              @Param("issueId") String issueId,
                              @Param("expectedVersion") Long expectedVersion,
                              @Param("ownerPrincipalId") String ownerPrincipalId,
                              @Param("resolutionCode") String resolutionCode,
                              @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT COUNT(*) FROM (
                SELECT 'FORECAST' item_type,status FROM cloudmold_demand_forecast WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'FORECAST_EVALUATION',status FROM cloudmold_forecast_evaluation WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'SUPPLY_PLAN',status FROM cloudmold_supply_plan WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'PLAN_SCENARIO',status FROM cloudmold_supply_plan_scenario WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'SCENARIO_RECOMMENDATION',status
                FROM cloudmold_supply_plan_scenario_recommendation WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'REPLENISHMENT',status FROM cloudmold_replenishment_recommendation WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'REPLENISHMENT_CONVERSION',target_aggregate_status
                FROM cloudmold_replenishment_conversion WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'INVENTORY_ISSUE',status FROM cloudmold_inventory_health_issue WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT 'INVENTORY_SCAN',status FROM cloudmold_inventory_health_scan WHERE tenant_id=#{tenantId}
            ) work_item
            WHERE 1=1
            <if test="itemType != null">AND work_item.item_type=#{itemType}</if>
            <if test="status != null">AND work_item.status=#{status}</if>
            </script>
            """)
    long countWorkItems(@Param("tenantId") Long tenantId,
                        @Param("itemType") String itemType,
                        @Param("status") String status);

    @Select("""
            <script>
            SELECT * FROM (
                SELECT forecast_id aggregate_id,'FORECAST' item_type,forecast_code code,model_ref related_ref,
                       status,version aggregate_version,horizon_start business_date,updated_at
                FROM cloudmold_demand_forecast WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT evaluation_id,'FORECAST_EVALUATION',
                       CONCAT('WAPE=',COALESCE(wape_basis_points,'N/A'),'bp'),
                       forecast_id,status,version,DATE(evaluated_at),evaluated_at
                FROM cloudmold_forecast_evaluation WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT plan_id,'SUPPLY_PLAN',plan_code,demand_forecast_id,status,version,horizon_start,updated_at
                FROM cloudmold_supply_plan WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT scenario_id,'PLAN_SCENARIO',scenario_code,plan_id,status,version,
                       DATE(evaluated_at),updated_at
                FROM cloudmold_supply_plan_scenario WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT recommendation_id,'SCENARIO_RECOMMENDATION',
                       CONCAT('worst_service=',worst_case_service_level_basis_points,'bp'),
                       recommended_scenario_id,status,version,DATE(recommended_at),updated_at
                FROM cloudmold_supply_plan_scenario_recommendation WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT recommendation_id,'REPLENISHMENT',reason_code,plan_id,status,version,need_by_date,updated_at
                FROM cloudmold_replenishment_recommendation WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT conversion_id,'REPLENISHMENT_CONVERSION',
                       COALESCE(target_aggregate_no,target_aggregate_id),
                       target_aggregate_type,target_aggregate_status,version,DATE(converted_at),converted_at
                FROM cloudmold_replenishment_conversion WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT issue_id,'INVENTORY_ISSUE',issue_type,source_balance_id,status,version,
                       DATE(opened_at),updated_at
                FROM cloudmold_inventory_health_issue WHERE tenant_id=#{tenantId}
                UNION ALL
                SELECT scan_id,'INVENTORY_SCAN',policy_code,
                       CONCAT('issues=',issue_count,', skipped=',skipped_active_count),
                       status,version,DATE(evaluated_at),evaluated_at
                FROM cloudmold_inventory_health_scan WHERE tenant_id=#{tenantId}
            ) work_item
            WHERE 1=1
            <if test="itemType != null">AND work_item.item_type=#{itemType}</if>
            <if test="status != null">AND work_item.status=#{status}</if>
            ORDER BY work_item.updated_at DESC,work_item.aggregate_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<SupplyPlanningWorkItem> selectWorkItems(@Param("tenantId") Long tenantId,
                                                 @Param("itemType") String itemType,
                                                 @Param("status") String status,
                                                 @Param("offset") long offset,
                                                 @Param("limit") int limit);
}
