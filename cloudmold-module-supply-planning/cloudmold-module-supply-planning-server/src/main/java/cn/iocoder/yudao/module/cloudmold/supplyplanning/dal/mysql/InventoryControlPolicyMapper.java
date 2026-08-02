package cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotIssueRefView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyVersionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.InventoryHealthSnapshot;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.InventoryHealthSnapshotIssueRef;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.InventoryIssueReference;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.SafetyStockPolicy;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.SafetyStockPolicyVersion;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryControlPolicyMapper {

    @Insert("""
            INSERT INTO cloudmold_inventory_control_policy_operation
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
            FROM cloudmold_inventory_control_policy_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_inventory_control_policy_operation
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
            INSERT INTO cloudmold_safety_stock_policy
              (policy_id,tenant_id,policy_code,owner_type,owner_id,canonical_sku_id,warehouse_network_id,
               effective_from,effective_to,target_service_level_basis_points,safety_stock_quantity,
               reorder_point_quantity,maximum_stock_quantity,replenishment_cycle_days,lead_time_days,
               policy_basis_code,policy_sha256,evidence_ref,status,current_version,
               created_by_principal_id,created_at,updated_at)
            VALUES (#{policyId},#{tenantId},#{policyCode},#{ownerType},#{ownerId},#{canonicalSkuId},
                    #{warehouseNetworkId},#{effectiveFrom},#{effectiveTo},
                    #{targetServiceLevelBasisPoints},#{safetyStockQuantity},#{reorderPointQuantity},
                    #{maximumStockQuantity},#{replenishmentCycleDays},#{leadTimeDays},#{policyBasisCode},
                    #{policySha256},#{evidenceRef},#{status},#{currentVersion},#{createdByPrincipalId},
                    #{createdAt},#{updatedAt})
            """)
    int insertPolicy(SafetyStockPolicy value);

    @Select("""
            SELECT policy_id,tenant_id,policy_code,owner_type,owner_id,canonical_sku_id,warehouse_network_id,
                   effective_from,effective_to,target_service_level_basis_points,safety_stock_quantity,
                   reorder_point_quantity,maximum_stock_quantity,replenishment_cycle_days,lead_time_days,
                   policy_basis_code,policy_sha256,evidence_ref,status,current_version,approved_version,
                   published_version,active_version_id,created_by_principal_id,approved_by_principal_id,
                   published_by_principal_id,retired_by_principal_id,approved_at,published_at,retired_at,
                   created_at,updated_at
            FROM cloudmold_safety_stock_policy
            WHERE tenant_id=#{tenantId} AND policy_id=#{policyId} FOR UPDATE
            """)
    SafetyStockPolicy selectPolicyForUpdate(@Param("tenantId") Long tenantId,
                                            @Param("policyId") String policyId);

    @Update("""
            UPDATE cloudmold_safety_stock_policy
            SET owner_type=#{ownerType},owner_id=#{ownerId},canonical_sku_id=#{canonicalSkuId},
                warehouse_network_id=#{warehouseNetworkId},effective_from=#{effectiveFrom},
                effective_to=#{effectiveTo},
                target_service_level_basis_points=#{targetServiceLevelBasisPoints},
                safety_stock_quantity=#{safetyStockQuantity},reorder_point_quantity=#{reorderPointQuantity},
                maximum_stock_quantity=#{maximumStockQuantity},
                replenishment_cycle_days=#{replenishmentCycleDays},lead_time_days=#{leadTimeDays},
                policy_basis_code=#{policyBasisCode},policy_sha256=#{policySha256},evidence_ref=#{evidenceRef},
                current_version=#{currentVersion},updated_at=#{updatedAt}
            WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}
              AND status='DRAFT' AND current_version=#{expectedVersion}
            """)
    int updatePolicyDraft(SafetyStockPolicy value);

    @Insert("""
            INSERT INTO cloudmold_safety_stock_policy_version
              (policy_version_id,tenant_id,policy_id,policy_code,version,status,owner_type,owner_id,
               canonical_sku_id,warehouse_network_id,effective_from,effective_to,
               target_service_level_basis_points,safety_stock_quantity,reorder_point_quantity,
               maximum_stock_quantity,replenishment_cycle_days,lead_time_days,policy_basis_code,
               policy_sha256,evidence_ref,actor_principal_id,source_operation_id,created_at)
            VALUES (#{policyVersionId},#{tenantId},#{policyId},#{policyCode},#{version},#{status},
                    #{ownerType},#{ownerId},#{canonicalSkuId},#{warehouseNetworkId},#{effectiveFrom},
                    #{effectiveTo},#{targetServiceLevelBasisPoints},#{safetyStockQuantity},
                    #{reorderPointQuantity},#{maximumStockQuantity},#{replenishmentCycleDays},
                    #{leadTimeDays},#{policyBasisCode},#{policySha256},#{evidenceRef},
                    #{actorPrincipalId},#{sourceOperationId},#{createdAt})
            """)
    int insertPolicyVersion(SafetyStockPolicyVersion value);

    @Update("""
            UPDATE cloudmold_safety_stock_policy
            SET status='APPROVED',current_version=#{nextVersion},approved_version=#{nextVersion},
                approved_by_principal_id=#{actorPrincipalId},approved_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}
              AND status='DRAFT' AND current_version=#{expectedVersion}
            """)
    int approvePolicy(@Param("tenantId") Long tenantId,
                      @Param("policyId") String policyId,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("nextVersion") Long nextVersion,
                      @Param("actorPrincipalId") String actorPrincipalId,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_safety_stock_policy
            SET status='PUBLISHED',current_version=#{nextVersion},published_version=#{nextVersion},
                active_version_id=#{policyVersionId},published_by_principal_id=#{actorPrincipalId},
                published_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}
              AND status='APPROVED' AND current_version=#{expectedVersion}
            """)
    int publishPolicy(@Param("tenantId") Long tenantId,
                      @Param("policyId") String policyId,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("nextVersion") Long nextVersion,
                      @Param("policyVersionId") String policyVersionId,
                      @Param("actorPrincipalId") String actorPrincipalId,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_safety_stock_policy
            SET status='RETIRED',current_version=#{nextVersion},active_version_id=NULL,
                retired_by_principal_id=#{actorPrincipalId},retired_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}
              AND status='PUBLISHED' AND current_version=#{expectedVersion}
            """)
    int retirePolicy(@Param("tenantId") Long tenantId,
                     @Param("policyId") String policyId,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("nextVersion") Long nextVersion,
                     @Param("actorPrincipalId") String actorPrincipalId,
                     @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_safety_stock_policy
            WHERE tenant_id=#{tenantId}
              AND policy_id&lt;&gt;#{policyId}
              AND status='PUBLISHED'
              AND owner_type=#{ownerType}
              AND owner_id=#{ownerId}
              AND canonical_sku_id=#{canonicalSkuId}
              AND warehouse_network_id=#{warehouseNetworkId}
              AND NOT (
                    (effective_to IS NOT NULL AND effective_to &lt; #{effectiveFrom})
                 OR (#{effectiveTo} IS NOT NULL AND effective_from &gt; #{effectiveTo})
              )
            </script>
            """)
    long countOverlappingPublishedPolicies(@Param("tenantId") Long tenantId,
                                           @Param("policyId") String policyId,
                                           @Param("ownerType") String ownerType,
                                           @Param("ownerId") String ownerId,
                                           @Param("canonicalSkuId") String canonicalSkuId,
                                           @Param("warehouseNetworkId") String warehouseNetworkId,
                                           @Param("effectiveFrom") LocalDate effectiveFrom,
                                           @Param("effectiveTo") LocalDate effectiveTo);

    @Select("""
            SELECT policy_id policyId,policy_code policyCode,owner_type ownerType,owner_id ownerId,
                   canonical_sku_id canonicalSkuId,warehouse_network_id warehouseNetworkId,
                   effective_from effectiveFrom,effective_to effectiveTo,
                   target_service_level_basis_points targetServiceLevelBasisPoints,
                   safety_stock_quantity safetyStockQuantity,reorder_point_quantity reorderPointQuantity,
                   maximum_stock_quantity maximumStockQuantity,
                   replenishment_cycle_days replenishmentCycleDays,lead_time_days leadTimeDays,
                   policy_basis_code policyBasisCode,policy_sha256 policySha256,evidence_ref evidenceRef,
                   status,current_version currentVersion,approved_version approvedVersion,
                   published_version publishedVersion,created_by_principal_id createdByPrincipalId,
                   approved_by_principal_id approvedByPrincipalId,
                   published_by_principal_id publishedByPrincipalId,
                   retired_by_principal_id retiredByPrincipalId,approved_at approvedAt,
                   published_at publishedAt,retired_at retiredAt,created_at createdAt,updated_at updatedAt
            FROM cloudmold_safety_stock_policy
            WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}
            """)
    SafetyStockPolicyView selectPolicy(@Param("tenantId") Long tenantId,
                                       @Param("policyId") String policyId);

    @Select("""
            SELECT policy_version_id policyVersionId,version,status,owner_type ownerType,owner_id ownerId,
                   canonical_sku_id canonicalSkuId,warehouse_network_id warehouseNetworkId,
                   effective_from effectiveFrom,effective_to effectiveTo,
                   target_service_level_basis_points targetServiceLevelBasisPoints,
                   safety_stock_quantity safetyStockQuantity,reorder_point_quantity reorderPointQuantity,
                   maximum_stock_quantity maximumStockQuantity,
                   replenishment_cycle_days replenishmentCycleDays,lead_time_days leadTimeDays,
                   policy_basis_code policyBasisCode,policy_sha256 policySha256,evidence_ref evidenceRef,
                   actor_principal_id actorPrincipalId,created_at createdAt
            FROM cloudmold_safety_stock_policy_version
            WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}
            ORDER BY version DESC
            """)
    List<SafetyStockPolicyVersionView> selectPolicyVersions(@Param("tenantId") Long tenantId,
                                                            @Param("policyId") String policyId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_safety_stock_policy
            WHERE tenant_id=#{tenantId}
            <if test="status != null">AND status=#{status}</if>
            <if test="keyword != null and keyword != ''">
                AND (
                    policy_code LIKE CONCAT('%',#{keyword},'%')
                    OR owner_id LIKE CONCAT('%',#{keyword},'%')
                    OR canonical_sku_id LIKE CONCAT('%',#{keyword},'%')
                    OR warehouse_network_id LIKE CONCAT('%',#{keyword},'%')
                )
            </if>
            </script>
            """)
    long countPolicyPage(@Param("tenantId") Long tenantId,
                         @Param("status") String status,
                         @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT policy_id policyId,policy_code policyCode,owner_type ownerType,owner_id ownerId,
                   canonical_sku_id canonicalSkuId,warehouse_network_id warehouseNetworkId,
                   effective_from effectiveFrom,effective_to effectiveTo,
                   target_service_level_basis_points targetServiceLevelBasisPoints,
                   safety_stock_quantity safetyStockQuantity,reorder_point_quantity reorderPointQuantity,
                   maximum_stock_quantity maximumStockQuantity,
                   replenishment_cycle_days replenishmentCycleDays,lead_time_days leadTimeDays,
                   policy_basis_code policyBasisCode,policy_sha256 policySha256,evidence_ref evidenceRef,
                   status,current_version currentVersion,approved_version approvedVersion,
                   published_version publishedVersion,created_by_principal_id createdByPrincipalId,
                   approved_by_principal_id approvedByPrincipalId,
                   published_by_principal_id publishedByPrincipalId,
                   retired_by_principal_id retiredByPrincipalId,approved_at approvedAt,
                   published_at publishedAt,retired_at retiredAt,created_at createdAt,updated_at updatedAt
            FROM cloudmold_safety_stock_policy
            WHERE tenant_id=#{tenantId}
            <if test="status != null">AND status=#{status}</if>
            <if test="keyword != null and keyword != ''">
                AND (
                    policy_code LIKE CONCAT('%',#{keyword},'%')
                    OR owner_id LIKE CONCAT('%',#{keyword},'%')
                    OR canonical_sku_id LIKE CONCAT('%',#{keyword},'%')
                    OR warehouse_network_id LIKE CONCAT('%',#{keyword},'%')
                )
            </if>
            ORDER BY updated_at DESC, policy_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<SafetyStockPolicyView> selectPolicyPage(@Param("tenantId") Long tenantId,
                                                 @Param("status") String status,
                                                 @Param("keyword") String keyword,
                                                 @Param("offset") long offset,
                                                 @Param("limit") int limit);

    @Select("""
            SELECT policy_version_id,tenant_id,policy_id,policy_code,version,status,owner_type,owner_id,
                   canonical_sku_id,warehouse_network_id,effective_from,effective_to,
                   target_service_level_basis_points,safety_stock_quantity,reorder_point_quantity,
                   maximum_stock_quantity,replenishment_cycle_days,lead_time_days,policy_basis_code,
                   policy_sha256,evidence_ref,actor_principal_id,source_operation_id,created_at
            FROM cloudmold_safety_stock_policy_version
            WHERE tenant_id=#{tenantId} AND policy_version_id=#{policyVersionId}
            """)
    SafetyStockPolicyVersion selectPolicyVersion(@Param("tenantId") Long tenantId,
                                                 @Param("policyVersionId") String policyVersionId);

    @Select("""
            SELECT issue_id,tenant_id,source_balance_id,issue_type,severity,status
            FROM cloudmold_inventory_health_issue
            WHERE tenant_id=#{tenantId} AND issue_id=#{issueId}
            """)
    InventoryIssueReference selectInventoryIssueReference(@Param("tenantId") Long tenantId,
                                                          @Param("issueId") String issueId);

    @Insert("""
            INSERT INTO cloudmold_inventory_health_snapshot
              (snapshot_id,tenant_id,snapshot_code,policy_id,policy_code,policy_version_id,policy_version,
               ledger_watermark_ref,ledger_watermark_occurred_at,stockout_count,low_stock_count,
               overstock_count,obsolete_count,aged_count,shelf_life_risk_count,
               shortage_quantity,excess_quantity,at_risk_quantity,issue_count,snapshot_sha256,status,
               created_by_principal_id,created_at)
            VALUES (#{snapshotId},#{tenantId},#{snapshotCode},#{policyId},#{policyCode},#{policyVersionId},
                    #{policyVersion},#{ledgerWatermarkRef},#{ledgerWatermarkOccurredAt},
                    #{stockoutCount},#{lowStockCount},#{overstockCount},#{obsoleteCount},#{agedCount},
                    #{shelfLifeRiskCount},#{shortageQuantity},#{excessQuantity},#{atRiskQuantity},
                    #{issueCount},#{snapshotSha256},#{status},#{createdByPrincipalId},#{createdAt})
            """)
    int insertSnapshot(InventoryHealthSnapshot value);

    @Insert("""
            INSERT INTO cloudmold_inventory_health_snapshot_issue_ref
              (tenant_id,snapshot_id,issue_id,issue_type,severity,status,source_balance_id,created_at)
            VALUES (#{tenantId},#{snapshotId},#{issueId},#{issueType},#{severity},#{status},
                    #{sourceBalanceId},#{createdAt})
            """)
    int insertSnapshotIssueRef(InventoryHealthSnapshotIssueRef value);

    @Select("""
            SELECT snapshot_id snapshotId,snapshot_code snapshotCode,policy_id policyId,policy_code policyCode,
                   policy_version_id policyVersionId,policy_version policyVersion,
                   ledger_watermark_ref ledgerWatermarkRef,
                   ledger_watermark_occurred_at ledgerWatermarkOccurredAt,
                   stockout_count stockoutCount,low_stock_count lowStockCount,
                   overstock_count overstockCount,obsolete_count obsoleteCount,aged_count agedCount,
                   shelf_life_risk_count shelfLifeRiskCount,shortage_quantity shortageQuantity,
                   excess_quantity excessQuantity,at_risk_quantity atRiskQuantity,issue_count issueCount,
                   snapshot_sha256 snapshotSha256,status,created_by_principal_id createdByPrincipalId,
                   created_at createdAt
            FROM cloudmold_inventory_health_snapshot
            WHERE tenant_id=#{tenantId} AND snapshot_id=#{snapshotId}
            """)
    InventoryHealthSnapshotView selectSnapshot(@Param("tenantId") Long tenantId,
                                               @Param("snapshotId") String snapshotId);

    @Select("""
            SELECT issue_id issueId,issue_type issueType,severity,status,source_balance_id sourceBalanceId
            FROM cloudmold_inventory_health_snapshot_issue_ref
            WHERE tenant_id=#{tenantId} AND snapshot_id=#{snapshotId}
            ORDER BY snapshot_issue_ref_id ASC
            """)
    List<InventoryHealthSnapshotIssueRefView> selectSnapshotIssues(@Param("tenantId") Long tenantId,
                                                                   @Param("snapshotId") String snapshotId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_inventory_health_snapshot
            WHERE tenant_id=#{tenantId}
            <if test="policyId != null and policyId != ''">AND policy_id=#{policyId}</if>
            <if test="keyword != null and keyword != ''">
                AND (
                    snapshot_code LIKE CONCAT('%',#{keyword},'%')
                    OR policy_id LIKE CONCAT('%',#{keyword},'%')
                    OR policy_version_id LIKE CONCAT('%',#{keyword},'%')
                    OR ledger_watermark_ref LIKE CONCAT('%',#{keyword},'%')
                )
            </if>
            </script>
            """)
    long countSnapshotPage(@Param("tenantId") Long tenantId,
                           @Param("policyId") String policyId,
                           @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT snapshot_id snapshotId,snapshot_code snapshotCode,policy_id policyId,policy_code policyCode,
                   policy_version_id policyVersionId,policy_version policyVersion,
                   ledger_watermark_ref ledgerWatermarkRef,
                   ledger_watermark_occurred_at ledgerWatermarkOccurredAt,
                   stockout_count stockoutCount,low_stock_count lowStockCount,
                   overstock_count overstockCount,obsolete_count obsoleteCount,aged_count agedCount,
                   shelf_life_risk_count shelfLifeRiskCount,shortage_quantity shortageQuantity,
                   excess_quantity excessQuantity,at_risk_quantity atRiskQuantity,issue_count issueCount,
                   snapshot_sha256 snapshotSha256,status,created_by_principal_id createdByPrincipalId,
                   created_at createdAt
            FROM cloudmold_inventory_health_snapshot
            WHERE tenant_id=#{tenantId}
            <if test="policyId != null and policyId != ''">AND policy_id=#{policyId}</if>
            <if test="keyword != null and keyword != ''">
                AND (
                    snapshot_code LIKE CONCAT('%',#{keyword},'%')
                    OR policy_id LIKE CONCAT('%',#{keyword},'%')
                    OR policy_version_id LIKE CONCAT('%',#{keyword},'%')
                    OR ledger_watermark_ref LIKE CONCAT('%',#{keyword},'%')
                )
            </if>
            ORDER BY created_at DESC, snapshot_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<InventoryHealthSnapshotView> selectSnapshotPage(@Param("tenantId") Long tenantId,
                                                         @Param("policyId") String policyId,
                                                         @Param("keyword") String keyword,
                                                         @Param("offset") long offset,
                                                         @Param("limit") int limit);
}
