package cn.iocoder.yudao.module.cloudmold.dreamplant.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.dreamplant.api.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface DreamPlantKnowledgeMapper {

    @Insert("""
        INSERT INTO ${table} (tenant_id,map_key,asset_id,version,display_name,status,lifecycle_stage,
          owner_principal_id,canonical_key,details_json,details_sha256,source_ref,evidence_ref,effective_at,
          observed_at,created_at,updated_at)
        VALUES (#{tenantId},#{c.mapKey},#{c.assetId},1,#{c.displayName},#{c.status},#{c.lifecycleStage},
          #{c.ownerPrincipalId},#{c.canonicalKey},#{detailsJson},#{detailsSha256},#{c.sourceRef},#{c.evidenceRef},
          #{effectiveAt},#{observedAt},#{now},#{now})
        """)
    int insertAsset(@Param("table") String table, @Param("tenantId") Long tenantId,
                    @Param("c") DreamPlantAssetCommand command, @Param("detailsJson") String detailsJson,
                    @Param("detailsSha256") String detailsSha256, @Param("effectiveAt") LocalDateTime effectiveAt,
                    @Param("observedAt") LocalDateTime observedAt, @Param("now") LocalDateTime now);

    @Update("""
        UPDATE ${table} SET version=version+1,display_name=#{c.displayName},status=#{c.status},
          lifecycle_stage=#{c.lifecycleStage},owner_principal_id=#{c.ownerPrincipalId},canonical_key=#{c.canonicalKey},
          details_json=#{detailsJson},details_sha256=#{detailsSha256},source_ref=#{c.sourceRef},
          evidence_ref=#{c.evidenceRef},effective_at=#{effectiveAt},observed_at=#{observedAt},updated_at=#{now}
        WHERE tenant_id=#{tenantId} AND map_key=#{c.mapKey} AND asset_id=#{c.assetId} AND version=#{c.expectedVersion}
        """)
    int updateAsset(@Param("table") String table, @Param("tenantId") Long tenantId,
                    @Param("c") DreamPlantAssetCommand command, @Param("detailsJson") String detailsJson,
                    @Param("detailsSha256") String detailsSha256, @Param("effectiveAt") LocalDateTime effectiveAt,
                    @Param("observedAt") LocalDateTime observedAt, @Param("now") LocalDateTime now);

    @Select("""
        SELECT map_key,asset_id,version,display_name,status,lifecycle_stage,owner_principal_id,canonical_key,
          details_json,details_sha256,source_ref,evidence_ref,effective_at,observed_at,created_at,updated_at
        FROM ${table} WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} AND asset_id=#{assetId}
        """)
    DreamPlantAssetView selectAsset(@Param("table") String table, @Param("tenantId") Long tenantId,
                                    @Param("mapKey") String mapKey, @Param("assetId") String assetId);

    @Select("""
        <script>SELECT map_key,asset_id,version,display_name,status,lifecycle_stage,owner_principal_id,canonical_key,
          details_json,details_sha256,source_ref,evidence_ref,effective_at,observed_at,created_at,updated_at
        FROM ${table} WHERE tenant_id=#{tenantId} AND map_key=#{mapKey}
        <if test='status != null and status != ""'>AND status=#{status}</if>
        ORDER BY updated_at DESC,asset_id LIMIT #{limit}</script>
        """)
    List<DreamPlantAssetView> selectAssets(@Param("table") String table, @Param("tenantId") Long tenantId,
                                           @Param("mapKey") String mapKey, @Param("status") String status,
                                           @Param("limit") Integer limit);

    @Insert("""
        INSERT INTO cloudmold_dreamplant_asset_revision
          (tenant_id,map_key,asset_type,asset_id,asset_version,details_json,details_sha256,source_ref,evidence_ref,occurred_at)
        VALUES (#{tenantId},#{mapKey},#{assetType},#{assetId},#{version},#{detailsJson},#{detailsSha256},
          #{sourceRef},#{evidenceRef},#{occurredAt})
        """)
    int insertAssetRevision(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                            @Param("assetType") String assetType, @Param("assetId") String assetId,
                            @Param("version") Long version, @Param("detailsJson") String detailsJson,
                            @Param("detailsSha256") String detailsSha256, @Param("sourceRef") String sourceRef,
                            @Param("evidenceRef") String evidenceRef, @Param("occurredAt") LocalDateTime occurredAt);

    @Insert("""
        INSERT INTO ${table} (tenant_id,map_key,relation_id,from_asset_id,to_asset_id,relation_type,version,status,
          details_json,details_sha256,source_ref,evidence_ref,effective_at,observed_at,created_at,updated_at)
        VALUES (#{tenantId},#{c.mapKey},#{c.relationId},#{c.fromAssetId},#{c.toAssetId},#{c.relationType},1,
          #{c.status},#{detailsJson},#{detailsSha256},#{c.sourceRef},#{c.evidenceRef},#{effectiveAt},#{observedAt},#{now},#{now})
        ON DUPLICATE KEY UPDATE version=version+1,status=VALUES(status),details_json=VALUES(details_json),
          details_sha256=VALUES(details_sha256),source_ref=VALUES(source_ref),evidence_ref=VALUES(evidence_ref),
          effective_at=VALUES(effective_at),observed_at=VALUES(observed_at),updated_at=VALUES(updated_at)
        """)
    int upsertRelation(@Param("table") String table, @Param("tenantId") Long tenantId,
                       @Param("c") DreamPlantRelationCommand command, @Param("detailsJson") String detailsJson,
                       @Param("detailsSha256") String detailsSha256, @Param("effectiveAt") LocalDateTime effectiveAt,
                       @Param("observedAt") LocalDateTime observedAt, @Param("now") LocalDateTime now);

    @Select("""
        SELECT map_key,relation_id,version,from_asset_id,to_asset_id,relation_type,status,details_json,
          details_sha256,source_ref,evidence_ref,effective_at,observed_at,created_at,updated_at
        FROM ${table} WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} AND relation_id=#{relationId}
        """)
    DreamPlantRelationView selectRelation(@Param("table") String table, @Param("tenantId") Long tenantId,
                                          @Param("mapKey") String mapKey, @Param("relationId") String relationId);

    @Select("""
        <script>SELECT map_key,relation_id,version,from_asset_id,to_asset_id,relation_type,status,details_json,
          details_sha256,source_ref,evidence_ref,effective_at,observed_at,created_at,updated_at FROM ${table}
        WHERE tenant_id=#{tenantId} AND map_key=#{mapKey}
        <if test='assetId != null and assetId != ""'>AND (from_asset_id=#{assetId} OR to_asset_id=#{assetId})</if>
        <if test='relationType != null and relationType != ""'>AND relation_type=#{relationType}</if>
        ORDER BY updated_at DESC,relation_id LIMIT #{limit}</script>
        """)
    List<DreamPlantRelationView> selectRelations(@Param("table") String table, @Param("tenantId") Long tenantId,
                                                 @Param("mapKey") String mapKey, @Param("assetId") String assetId,
                                                 @Param("relationType") String relationType,
                                                 @Param("limit") Integer limit);

    @Insert("""
        INSERT INTO cloudmold_dreamplant_evidence (tenant_id,map_key,evidence_id,version,subject_type,subject_id,
          evidence_type,title,summary,content_ref,content_sha256,details_json,details_sha256,source_ref,observed_at,
          captured_at,created_at,updated_at)
        VALUES (#{tenantId},#{c.mapKey},#{c.evidenceId},1,#{c.subjectType},#{c.subjectId},#{c.evidenceType},
          #{c.title},#{c.summary},#{c.contentRef},#{c.contentSha256},#{detailsJson},#{detailsSha256},#{c.sourceRef},
          #{observedAt},#{capturedAt},#{now},#{now})
        ON DUPLICATE KEY UPDATE version=version+1,title=VALUES(title),summary=VALUES(summary),
          content_ref=VALUES(content_ref),content_sha256=VALUES(content_sha256),details_json=VALUES(details_json),
          details_sha256=VALUES(details_sha256),source_ref=VALUES(source_ref),observed_at=VALUES(observed_at),
          captured_at=VALUES(captured_at),updated_at=VALUES(updated_at)
        """)
    int upsertEvidence(@Param("tenantId") Long tenantId, @Param("c") DreamPlantEvidenceCommand command,
                       @Param("detailsJson") String detailsJson, @Param("detailsSha256") String detailsSha256,
                       @Param("observedAt") LocalDateTime observedAt, @Param("capturedAt") LocalDateTime capturedAt,
                       @Param("now") LocalDateTime now);

    @Select("SELECT map_key,evidence_id,version,subject_type,subject_id,evidence_type,title,summary,content_ref,content_sha256,details_json,details_sha256,source_ref,observed_at,captured_at,created_at,updated_at FROM cloudmold_dreamplant_evidence WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} AND evidence_id=#{evidenceId}")
    DreamPlantEvidenceView selectEvidence(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                                          @Param("evidenceId") String evidenceId);

    @Select("""
        SELECT map_key,evidence_id,version,subject_type,subject_id,evidence_type,title,summary,content_ref,
          content_sha256,details_json,details_sha256,source_ref,observed_at,captured_at,created_at,updated_at
        FROM cloudmold_dreamplant_evidence WHERE tenant_id=#{tenantId} AND map_key=#{mapKey}
          AND subject_type=#{subjectType} AND subject_id=#{subjectId} ORDER BY captured_at DESC LIMIT #{limit}
        """)
    List<DreamPlantEvidenceView> selectEvidenceList(@Param("tenantId") Long tenantId,
                                                    @Param("mapKey") String mapKey,
                                                    @Param("subjectType") String subjectType,
                                                    @Param("subjectId") String subjectId,
                                                    @Param("limit") Integer limit);

    @Insert("""
        INSERT INTO cloudmold_dreamplant_metric_observation (tenant_id,map_key,metric_id,version,subject_type,
          subject_id,metric_code,metric_name,metric_value,metric_unit,status,dimension_json,details_json,
          details_sha256,source_ref,evidence_ref,window_start_at,window_end_at,measured_at,created_at,updated_at)
        VALUES (#{tenantId},#{c.mapKey},#{c.metricId},1,#{c.subjectType},#{c.subjectId},#{c.metricCode},
          #{c.metricName},#{c.metricValue},#{c.metricUnit},#{c.status},#{dimensionJson},#{detailsJson},#{detailsSha256},
          #{c.sourceRef},#{c.evidenceRef},#{windowStartAt},#{windowEndAt},#{measuredAt},#{now},#{now})
        ON DUPLICATE KEY UPDATE version=version+1,metric_value=VALUES(metric_value),status=VALUES(status),
          dimension_json=VALUES(dimension_json),details_json=VALUES(details_json),details_sha256=VALUES(details_sha256),
          source_ref=VALUES(source_ref),evidence_ref=VALUES(evidence_ref),measured_at=VALUES(measured_at),updated_at=VALUES(updated_at)
        """)
    int upsertMetric(@Param("tenantId") Long tenantId, @Param("c") DreamPlantMetricCommand command,
                     @Param("dimensionJson") String dimensionJson, @Param("detailsJson") String detailsJson,
                     @Param("detailsSha256") String detailsSha256, @Param("windowStartAt") LocalDateTime windowStartAt,
                     @Param("windowEndAt") LocalDateTime windowEndAt, @Param("measuredAt") LocalDateTime measuredAt,
                     @Param("now") LocalDateTime now);

    @Select("SELECT map_key,metric_id,version,subject_type,subject_id,metric_code,metric_name,metric_value,metric_unit,status,dimension_json,details_json,details_sha256,source_ref,evidence_ref,window_start_at,window_end_at,measured_at,created_at,updated_at FROM cloudmold_dreamplant_metric_observation WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} AND metric_id=#{metricId}")
    DreamPlantMetricView selectMetric(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                                      @Param("metricId") String metricId);

    @Select("""
        <script>SELECT map_key,metric_id,version,subject_type,subject_id,metric_code,metric_name,metric_value,
          metric_unit,status,dimension_json,details_json,details_sha256,source_ref,evidence_ref,window_start_at,
          window_end_at,measured_at,created_at,updated_at FROM cloudmold_dreamplant_metric_observation
        WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} AND subject_type=#{subjectType} AND subject_id=#{subjectId}
        <if test='metricCode != null and metricCode != ""'>AND metric_code=#{metricCode}</if>
        ORDER BY measured_at DESC LIMIT #{limit}</script>
        """)
    List<DreamPlantMetricView> selectMetrics(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                                             @Param("subjectType") String subjectType,
                                             @Param("subjectId") String subjectId,
                                             @Param("metricCode") String metricCode, @Param("limit") Integer limit);

    @Insert("""
        INSERT INTO cloudmold_dreamplant_sync_checkpoint (tenant_id,map_key,sync_key,version,source_system,
          source_namespace,source_cursor,source_checkpoint_ref,target_projection,status,details_json,details_sha256,
          source_ref,evidence_ref,checkpoint_at,created_at,updated_at)
        VALUES (#{tenantId},#{c.mapKey},#{c.syncKey},1,#{c.sourceSystem},#{c.sourceNamespace},#{c.sourceCursor},
          #{c.sourceCheckpointRef},#{c.targetProjection},#{c.status},#{detailsJson},#{detailsSha256},#{c.sourceRef},
          #{c.evidenceRef},#{checkpointAt},#{now},#{now})
        ON DUPLICATE KEY UPDATE version=version+1,source_cursor=VALUES(source_cursor),
          source_checkpoint_ref=VALUES(source_checkpoint_ref),status=VALUES(status),details_json=VALUES(details_json),
          details_sha256=VALUES(details_sha256),source_ref=VALUES(source_ref),evidence_ref=VALUES(evidence_ref),
          checkpoint_at=VALUES(checkpoint_at),updated_at=VALUES(updated_at)
        """)
    int upsertSync(@Param("tenantId") Long tenantId, @Param("c") DreamPlantSyncCommand command,
                   @Param("detailsJson") String detailsJson, @Param("detailsSha256") String detailsSha256,
                   @Param("checkpointAt") LocalDateTime checkpointAt, @Param("now") LocalDateTime now);

    @Select("SELECT map_key,sync_key,version,source_system,source_namespace,source_cursor,source_checkpoint_ref,target_projection,status,details_json,details_sha256,source_ref,evidence_ref,checkpoint_at,created_at,updated_at FROM cloudmold_dreamplant_sync_checkpoint WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} AND sync_key=#{syncKey}")
    DreamPlantSyncView selectSync(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                                  @Param("syncKey") String syncKey);

    @Select("""
        <script>SELECT map_key,sync_key,version,source_system,source_namespace,source_cursor,source_checkpoint_ref,
          target_projection,status,details_json,details_sha256,source_ref,evidence_ref,checkpoint_at,created_at,updated_at
        FROM cloudmold_dreamplant_sync_checkpoint WHERE tenant_id=#{tenantId} AND map_key=#{mapKey}
        <if test='sourceSystem != null and sourceSystem != ""'>AND source_system=#{sourceSystem}</if>
        ORDER BY checkpoint_at DESC LIMIT #{limit}</script>
        """)
    List<DreamPlantSyncView> selectSyncs(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                                         @Param("sourceSystem") String sourceSystem, @Param("limit") Integer limit);

    @Insert("""
        INSERT INTO cloudmold_dreamplant_drift_record (tenant_id,map_key,drift_id,version,subject_type,subject_id,
          drift_type,severity,status,baseline_ref,observed_ref,details_json,details_sha256,source_ref,evidence_ref,
          detected_at,resolved_at,created_at,updated_at)
        VALUES (#{tenantId},#{c.mapKey},#{c.driftId},1,#{c.subjectType},#{c.subjectId},#{c.driftType},#{c.severity},
          #{c.status},#{c.baselineRef},#{c.observedRef},#{detailsJson},#{detailsSha256},#{c.sourceRef},#{c.evidenceRef},
          #{detectedAt},#{resolvedAt},#{now},#{now})
        ON DUPLICATE KEY UPDATE version=version+1,severity=VALUES(severity),status=VALUES(status),
          baseline_ref=VALUES(baseline_ref),observed_ref=VALUES(observed_ref),details_json=VALUES(details_json),
          details_sha256=VALUES(details_sha256),source_ref=VALUES(source_ref),evidence_ref=VALUES(evidence_ref),
          detected_at=VALUES(detected_at),resolved_at=VALUES(resolved_at),updated_at=VALUES(updated_at)
        """)
    int upsertDrift(@Param("tenantId") Long tenantId, @Param("c") DreamPlantDriftCommand command,
                    @Param("detailsJson") String detailsJson, @Param("detailsSha256") String detailsSha256,
                    @Param("detectedAt") LocalDateTime detectedAt, @Param("resolvedAt") LocalDateTime resolvedAt,
                    @Param("now") LocalDateTime now);

    @Select("SELECT map_key,drift_id,version,subject_type,subject_id,drift_type,severity,status,baseline_ref,observed_ref,details_json,details_sha256,source_ref,evidence_ref,detected_at,resolved_at,created_at,updated_at FROM cloudmold_dreamplant_drift_record WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} AND drift_id=#{driftId}")
    DreamPlantDriftView selectDrift(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                                    @Param("driftId") String driftId);

    @Select("""
        <script>SELECT map_key,drift_id,version,subject_type,subject_id,drift_type,severity,status,baseline_ref,
          observed_ref,details_json,details_sha256,source_ref,evidence_ref,detected_at,resolved_at,created_at,updated_at
        FROM cloudmold_dreamplant_drift_record WHERE tenant_id=#{tenantId} AND map_key=#{mapKey}
          AND subject_type=#{subjectType} AND subject_id=#{subjectId}
        <if test='severity != null and severity != ""'>AND severity=#{severity}</if>
        ORDER BY detected_at DESC LIMIT #{limit}</script>
        """)
    List<DreamPlantDriftView> selectDrifts(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                                           @Param("subjectType") String subjectType,
                                           @Param("subjectId") String subjectId,
                                           @Param("severity") String severity, @Param("limit") Integer limit);
}
