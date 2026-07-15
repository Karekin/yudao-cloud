package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryMigrationShadowComparisonDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryMigrationShadowRoundDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryMigrationShadowWindowDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryMigrationShadowMapper {

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_shadow_window
              (window_id,tenant_id,batch_id,migration_run_id,environment,environment_fingerprint,
               manifest_hash,expected_item_set_hash,admission_checkpoint_id,admission_checkpoint_hash,
               admission_event_id,admission_batch_version,policy_version,policy_hash,target_projection_kind,target_projection_version,
               target_materialized,expected_item_count,required_round_count,minimum_duration_seconds,
               max_round_interval_seconds,max_watermark_lag_seconds,collector_id,status,verification_result,
               version,aggregate_version,observed_round_count,total_match_count,total_different_count,
               total_uncomparable_count,started_at,created_at,updated_at)
            VALUES
              (#{windowId},#{tenantId},#{batchId},#{migrationRunId},#{environment},#{environmentFingerprint},
               #{manifestHash},#{expectedItemSetHash},#{admissionCheckpointId},#{admissionCheckpointHash},
               #{admissionEventId},#{admissionBatchVersion},#{policyVersion},#{policyHash},#{targetProjectionKind},#{targetProjectionVersion},
               #{targetMaterialized},#{expectedItemCount},#{requiredRoundCount},#{minimumDurationSeconds},
               #{maxRoundIntervalSeconds},#{maxWatermarkLagSeconds},#{collectorId},'OPEN','PENDING',
               1,1,0,0,0,0,#{startedAt},#{createdAt},#{updatedAt})
            """)
    int insertWindow(InventoryMigrationShadowWindowDO value);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_shadow_window
            WHERE tenant_id=#{tenantId} AND window_id=#{windowId}
            """)
    InventoryMigrationShadowWindowDO selectWindow(@Param("tenantId") Long tenantId,
                                                    @Param("windowId") String windowId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_shadow_window
            WHERE tenant_id=#{tenantId} AND window_id=#{windowId} FOR UPDATE
            """)
    InventoryMigrationShadowWindowDO selectWindowForUpdate(@Param("tenantId") Long tenantId,
                                                             @Param("windowId") String windowId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_shadow_window
            WHERE tenant_id=#{tenantId} AND batch_id=#{batchId} AND status IN ('OPEN','OBSERVING')
            ORDER BY started_at DESC LIMIT 1 FOR UPDATE
            """)
    InventoryMigrationShadowWindowDO selectActiveWindowForBatch(@Param("tenantId") Long tenantId,
                                                                  @Param("batchId") String batchId);

    @Select("""
            SELECT event_id FROM cloudmold_event_outbox
            WHERE tenant_id=#{tenantId}
              AND event_type='inventory.migration.pilot_batch_status_changed'
              AND aggregate_type='inventory_migration_pilot_batch'
              AND aggregate_id=#{batchId} AND aggregate_version=4
            ORDER BY created_at DESC LIMIT 1
            """)
    String selectAdmissionEventId(@Param("tenantId") Long tenantId, @Param("batchId") String batchId);

    @Select("""
            SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.environment_fingerprint'))
            FROM cloudmold_event_outbox
            WHERE tenant_id=#{tenantId} AND event_id=#{eventId}
              AND event_type='inventory.migration.pilot_batch_status_changed'
              AND aggregate_version=4
            """)
    String selectAdmissionEnvironmentFingerprint(@Param("tenantId") Long tenantId,
                                                  @Param("eventId") String eventId);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_shadow_round
              (round_id,tenant_id,window_id,round_number,observed_at,
               previous_source_watermark_value,previous_source_watermark_hash,source_watermark_kind,
               source_watermark_value,source_watermark_hash,source_watermark_captured_at,source_monotonic,
               previous_target_watermark_value,previous_target_watermark_hash,target_watermark_kind,
               target_watermark_value,target_watermark_hash,target_watermark_applied_at,target_monotonic,
               target_contains_source,watermark_validator,watermark_validation_evidence_ref,watermark_valid,
               watermark_lag_seconds,round_gap_seconds,denominator_hash,expected_item_count,match_count,different_count,
               uncomparable_count,collector_id,evidence_ref,status,aggregate_version,created_at)
            VALUES
              (#{roundId},#{tenantId},#{windowId},#{roundNumber},#{observedAt},
               #{previousSourceWatermarkValue},#{previousSourceWatermarkHash},#{sourceWatermarkKind},
               #{sourceWatermarkValue},#{sourceWatermarkHash},#{sourceWatermarkCapturedAt},#{sourceMonotonic},
               #{previousTargetWatermarkValue},#{previousTargetWatermarkHash},#{targetWatermarkKind},
               #{targetWatermarkValue},#{targetWatermarkHash},#{targetWatermarkAppliedAt},#{targetMonotonic},
               #{targetContainsSource},#{watermarkValidator},#{watermarkValidationEvidenceRef},#{watermarkValid},
               #{watermarkLagSeconds},#{roundGapSeconds},#{denominatorHash},#{expectedItemCount},#{matchCount},#{differentCount},
               #{uncomparableCount},#{collectorId},#{evidenceRef},'COMPLETED',1,#{createdAt})
            """)
    int insertRound(InventoryMigrationShadowRoundDO value);

    @Insert("""
            INSERT INTO cloudmold_inventory_migration_shadow_comparison
              (comparison_id,tenant_id,window_id,round_id,round_number,pilot_item_id,manifest_ordinal,
               item_scope_hash,canonical_grain_hash,source_watermark_hash,target_watermark_hash,
               source_id,source_version,source_updated_at,source_snapshot_hash,source_on_hand_quantity,
               source_reserved_quantity,source_in_transit_quantity,active_reservation_count,
               active_reservation_quantity,source_evidence_ref,target_available,target_record_version,target_canonical_grain_hash,
               target_on_hand_quantity,target_reserved_quantity,target_in_transit_quantity,target_projection_hash,
               target_evidence_ref,comparable,comparison_result,difference_fields,reason_codes,aggregate_version,created_at)
            VALUES
              (#{comparisonId},#{tenantId},#{windowId},#{roundId},#{roundNumber},#{pilotItemId},#{manifestOrdinal},
               #{itemScopeHash},#{canonicalGrainHash},#{sourceWatermarkHash},#{targetWatermarkHash},
               #{sourceId},#{sourceVersion},#{sourceUpdatedAt},#{sourceSnapshotHash},#{sourceOnHandQuantity},
               #{sourceReservedQuantity},#{sourceInTransitQuantity},#{activeReservationCount},
               #{activeReservationQuantity},#{sourceEvidenceRef},#{targetAvailable},#{targetRecordVersion},#{targetCanonicalGrainHash},
               #{targetOnHandQuantity},#{targetReservedQuantity},#{targetInTransitQuantity},#{targetProjectionHash},
               #{targetEvidenceRef},#{comparable},#{comparisonResult},CAST(#{differenceFields} AS JSON),
               CAST(#{reasonCodes} AS JSON),1,#{createdAt})
            """)
    int insertComparison(InventoryMigrationShadowComparisonDO value);

    @Update("""
            UPDATE cloudmold_inventory_migration_shadow_window
            SET status='OBSERVING',verification_result='PENDING',aggregate_version=2,version=version+1,
                observed_round_count=observed_round_count+1,
                total_match_count=total_match_count+#{matchCount},
                total_different_count=total_different_count+#{differentCount},
                total_uncomparable_count=total_uncomparable_count+#{uncomparableCount},
                last_source_watermark_kind='MYSQL_GTID_SET',last_source_watermark_value=#{sourceValue},
                last_source_watermark_hash=#{sourceHash},last_source_watermark_captured_at=#{sourceCapturedAt},
                last_target_watermark_kind='MYSQL_GTID_SET',last_target_watermark_value=#{targetValue},
                last_target_watermark_hash=#{targetHash},last_target_watermark_applied_at=#{targetAppliedAt},
                last_observed_at=#{observedAt},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND window_id=#{windowId} AND version=#{expectedVersion}
              AND status IN ('OPEN','OBSERVING')
            """)
    int advanceWindow(@Param("tenantId") Long tenantId,
                      @Param("windowId") String windowId,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("matchCount") int matchCount,
                      @Param("differentCount") int differentCount,
                      @Param("uncomparableCount") int uncomparableCount,
                      @Param("sourceValue") String sourceValue,
                      @Param("sourceHash") String sourceHash,
                      @Param("sourceCapturedAt") LocalDateTime sourceCapturedAt,
                      @Param("targetValue") String targetValue,
                      @Param("targetHash") String targetHash,
                      @Param("targetAppliedAt") LocalDateTime targetAppliedAt,
                      @Param("observedAt") LocalDateTime observedAt,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_inventory_migration_shadow_window
            SET status='VERIFIED',verification_result=#{verificationResult},verifier_id=#{verifierId},
                aggregate_version=3,version=version+1,finalized_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND window_id=#{windowId} AND version=#{expectedVersion}
              AND status='OBSERVING' AND verification_result='PENDING'
            """)
    int finalizeWindow(@Param("tenantId") Long tenantId,
                       @Param("windowId") String windowId,
                       @Param("expectedVersion") Long expectedVersion,
                       @Param("verificationResult") String verificationResult,
                       @Param("verifierId") Long verifierId,
                       @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_shadow_round
            WHERE tenant_id=#{tenantId} AND window_id=#{windowId} ORDER BY round_number
            """)
    List<InventoryMigrationShadowRoundDO> selectRounds(@Param("tenantId") Long tenantId,
                                                        @Param("windowId") String windowId);

    @Select("""
            SELECT * FROM cloudmold_inventory_migration_shadow_comparison
            WHERE tenant_id=#{tenantId} AND round_id=#{roundId} ORDER BY manifest_ordinal
            """)
    List<InventoryMigrationShadowComparisonDO> selectComparisons(@Param("tenantId") Long tenantId,
                                                                  @Param("roundId") String roundId);

    /** MySQL canonical GTID containment; callers must not infer ordering from text. */
    @Select("SELECT GTID_SUBSET(#{subset},#{superset})")
    Integer gtidSubset(@Param("subset") String subset, @Param("superset") String superset);

    @Select("SELECT @@GLOBAL.gtid_executed")
    String selectCurrentGtidSet();
}
