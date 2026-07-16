package cn.iocoder.yudao.module.cloudmold.metadata.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.metadata.dal.dataobject.MetadataRecords.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface MetadataStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_metadata_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()") Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_metadata_operation WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_metadata_operation SET status=10,aggregate_id=#{aggregateId},
              result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_metadata_graph_guard (tenant_id,graph_type,revision,updated_at)
            VALUES (#{tenantId},#{graphType},0,#{now})
            ON DUPLICATE KEY UPDATE updated_at=#{now}
            """)
    int ensureGraphGuard(@Param("tenantId") Long tenantId, @Param("graphType") String graphType,
                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT revision FROM cloudmold_metadata_graph_guard
            WHERE tenant_id=#{tenantId} AND graph_type=#{graphType} FOR UPDATE
            """)
    Long selectGraphRevisionForUpdate(@Param("tenantId") Long tenantId, @Param("graphType") String graphType);

    @Update("""
            UPDATE cloudmold_metadata_graph_guard SET revision=revision+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND graph_type=#{graphType} AND revision=#{expectedRevision}
            """)
    int advanceGraphRevision(@Param("tenantId") Long tenantId, @Param("graphType") String graphType,
                             @Param("expectedRevision") Long expectedRevision, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_metadata_definition
              (definition_id,tenant_id,definition_kind,definition_code,display_name,status,current_version,
               owner_principal_id,created_at,updated_at)
            VALUES (#{definitionId},#{tenantId},#{definitionKind},#{definitionCode},#{displayName},#{status},
                    #{currentVersion},#{ownerPrincipalId},#{createdAt},#{updatedAt})
            """) int insertDefinition(Definition value);

    @Select("""
            SELECT definition_id,tenant_id,definition_kind,definition_code,display_name,status,current_version,
                   owner_principal_id,created_at,updated_at
            FROM cloudmold_metadata_definition
            WHERE tenant_id=#{tenantId} AND definition_id=#{definitionId} FOR UPDATE
            """) Definition selectDefinitionForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("definitionId") String definitionId);

    @Select("""
            SELECT definition_id,tenant_id,definition_kind,definition_code,display_name,status,current_version,
                   owner_principal_id,created_at,updated_at
            FROM cloudmold_metadata_definition
            WHERE tenant_id=#{tenantId} AND definition_id=#{definitionId}
            """) Definition selectDefinition(@Param("tenantId") Long tenantId,
                                               @Param("definitionId") String definitionId);

    @Update("""
            UPDATE cloudmold_metadata_definition
            SET display_name=#{displayName},status='PUBLISHED',current_version=current_version+1,
                owner_principal_id=#{ownerPrincipalId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND definition_id=#{definitionId}
              AND definition_kind=#{definitionKind} AND current_version=#{expectedVersion}
            """)
    int publishDefinition(@Param("tenantId") Long tenantId, @Param("definitionId") String definitionId,
                          @Param("definitionKind") String definitionKind,
                          @Param("expectedVersion") Long expectedVersion, @Param("displayName") String displayName,
                          @Param("ownerPrincipalId") String ownerPrincipalId, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_metadata_definition_version
              (tenant_id,definition_id,definition_version,specification_sha256,artifact_ref,
               published_by_principal_id,published_at)
            VALUES (#{tenantId},#{definitionId},#{definitionVersion},#{specificationSha256},#{artifactRef},
                    #{publishedByPrincipalId},#{publishedAt})
            """) int insertDefinitionVersion(DefinitionVersion value);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_metadata_definition d
            JOIN cloudmold_metadata_definition_version v
              ON v.tenant_id=d.tenant_id AND v.definition_id=d.definition_id
            WHERE d.tenant_id=#{tenantId} AND d.definition_id=#{definitionId}
              AND d.definition_kind=#{definitionKind} AND v.definition_version=#{definitionVersion}
            """)
    int countDefinitionVersion(@Param("tenantId") Long tenantId, @Param("definitionId") String definitionId,
                               @Param("definitionKind") String definitionKind,
                               @Param("definitionVersion") Long definitionVersion);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_metadata_field_version
            WHERE tenant_id=#{tenantId} AND dataset_id=#{datasetId} AND dataset_version=#{datasetVersion}
              AND field_code=#{fieldCode}
            """)
    int countFieldVersion(@Param("tenantId") Long tenantId, @Param("datasetId") String datasetId,
                          @Param("datasetVersion") Long datasetVersion, @Param("fieldCode") String fieldCode);

    @Insert("""
            INSERT INTO cloudmold_metadata_data_source_version
              (tenant_id,definition_id,definition_version,source_type,environment,endpoint_ref,credential_ref,namespace_ref)
            VALUES (#{tenantId},#{definitionId},#{definitionVersion},#{sourceType},#{environment},#{endpointRef},
                    #{credentialRef},#{namespaceRef})
            """) int insertDataSourceVersion(DataSourceVersion value);

    @Insert("""
            INSERT INTO cloudmold_metadata_dataset_version
              (tenant_id,definition_id,definition_version,data_source_id,data_source_version,dataset_type,qualified_name,
               layer_code,grain_code,schema_sha256,storage_location_ref,retention_days)
            VALUES (#{tenantId},#{definitionId},#{definitionVersion},#{dataSourceId},#{dataSourceVersion},#{datasetType},
                    #{qualifiedName},#{layerCode},#{grainCode},#{schemaSha256},#{storageLocationRef},#{retentionDays})
            """) int insertDatasetVersion(DatasetVersion value);

    @Insert("""
            INSERT INTO cloudmold_metadata_field_version
              (tenant_id,dataset_id,dataset_version,ordinal_position,field_code,data_type,nullable,primary_key_part,
               semantic_type,classification)
            VALUES (#{tenantId},#{datasetId},#{datasetVersion},#{ordinalPosition},#{fieldCode},#{dataType},#{nullable},
                    #{primaryKeyPart},#{semanticType},#{classification})
            """) int insertFieldVersion(FieldVersion value);

    @Insert("""
            INSERT INTO cloudmold_metadata_task_version
              (tenant_id,definition_id,definition_version,task_type,executable_artifact_ref,code_sha256,schedule_sha256,
               resource_group_ref)
            VALUES (#{tenantId},#{definitionId},#{definitionVersion},#{taskType},#{executableArtifactRef},#{codeSha256},
                    #{scheduleSha256},#{resourceGroupRef})
            """) int insertTaskVersion(TaskVersion value);

    @Insert("""
            INSERT INTO cloudmold_metadata_task_dependency
              (tenant_id,task_id,task_version,dependency_sequence,upstream_task_id,upstream_task_version,
               dependency_type,required_dependency)
            VALUES (#{tenantId},#{taskId},#{taskVersion},#{dependencySequence},#{upstreamTaskId},#{upstreamTaskVersion},
                    #{dependencyType},#{required})
            """) int insertTaskDependency(TaskDependency value);

    @Select("""
            WITH RECURSIVE dependency_path (task_id, task_version) AS (
                SELECT upstream_task_id, upstream_task_version
                FROM cloudmold_metadata_task_dependency
                WHERE tenant_id=#{tenantId} AND task_id=#{upstreamTaskId} AND task_version=#{upstreamTaskVersion}
                UNION DISTINCT
                SELECT d.upstream_task_id, d.upstream_task_version
                FROM cloudmold_metadata_task_dependency d
                JOIN dependency_path p ON d.tenant_id=#{tenantId}
                  AND d.task_id=p.task_id AND d.task_version=p.task_version
            )
            SELECT COUNT(*) FROM dependency_path WHERE task_id=#{candidateTaskId}
            """)
    int countTaskDependencyPath(@Param("tenantId") Long tenantId,
                                @Param("upstreamTaskId") String upstreamTaskId,
                                @Param("upstreamTaskVersion") Long upstreamTaskVersion,
                                @Param("candidateTaskId") String candidateTaskId);

    @Insert("""
            INSERT INTO cloudmold_metadata_task_sla
              (tenant_id,task_id,task_version,service_level_code,deadline_minute_utc,maximum_duration_millis,
               maximum_freshness_millis,approved_by_principal_id)
            VALUES (#{tenantId},#{taskId},#{taskVersion},#{serviceLevelCode},#{deadlineMinuteUtc},
                    #{maximumDurationMillis},#{maximumFreshnessMillis},#{approvedByPrincipalId})
            """) int insertTaskSla(TaskSla value);

    @Select("""
            SELECT tenant_id,run_id,task_id,task_version,attempt,observation_sequence,status,scheduled_at,started_at,
                   finished_at,duration_millis,compute_cost_minor,cost_currency,resource_millis,rows_read,rows_written,
                   source_checkpoint_ref,output_snapshot_ref,error_ref,observed_at,operation_id
            FROM cloudmold_metadata_task_run_observation
            WHERE tenant_id=#{tenantId} AND run_id=#{runId}
            ORDER BY observation_sequence DESC LIMIT 1 FOR UPDATE
            """) TaskRunObservation selectLatestTaskRunForUpdate(@Param("tenantId") Long tenantId,
                                                                  @Param("runId") String runId);

    @Select("""
            SELECT tenant_id,run_id,task_id,task_version,attempt,observation_sequence,status,scheduled_at,started_at,
                   finished_at,duration_millis,compute_cost_minor,cost_currency,resource_millis,rows_read,rows_written,
                   source_checkpoint_ref,output_snapshot_ref,error_ref,observed_at,operation_id
            FROM cloudmold_metadata_task_run_observation
            WHERE tenant_id=#{tenantId} AND run_id=#{runId}
            ORDER BY observation_sequence DESC LIMIT 1
            """) TaskRunObservation selectLatestTaskRun(@Param("tenantId") Long tenantId,
                                                         @Param("runId") String runId);

    @Select("""
            SELECT tenant_id,run_id,task_id,task_version,attempt,observation_sequence,status,scheduled_at,started_at,
                   finished_at,duration_millis,compute_cost_minor,cost_currency,resource_millis,rows_read,rows_written,
                   source_checkpoint_ref,output_snapshot_ref,error_ref,observed_at,operation_id
            FROM cloudmold_metadata_task_run_observation
            WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND observation_sequence=#{observationSequence}
            """)
    TaskRunObservation selectTaskRunObservation(@Param("tenantId") Long tenantId, @Param("runId") String runId,
                                                @Param("observationSequence") Long observationSequence);

    @Insert("""
            INSERT INTO cloudmold_metadata_task_run_observation
              (tenant_id,run_id,task_id,task_version,attempt,observation_sequence,status,scheduled_at,started_at,
               finished_at,duration_millis,compute_cost_minor,cost_currency,resource_millis,rows_read,rows_written,
               source_checkpoint_ref,output_snapshot_ref,error_ref,observed_at,operation_id)
            VALUES (#{tenantId},#{runId},#{taskId},#{taskVersion},#{attempt},#{observationSequence},#{status},
                    #{scheduledAt},#{startedAt},#{finishedAt},#{durationMillis},#{computeCostMinor},#{costCurrency},
                    #{resourceMillis},#{rowsRead},#{rowsWritten},#{sourceCheckpointRef},#{outputSnapshotRef},
                    #{errorRef},#{observedAt},#{operationId})
            """) int insertTaskRunObservation(TaskRunObservation value);

    @Insert("""
            INSERT INTO cloudmold_metadata_lineage_version
              (tenant_id,definition_id,definition_version,source_dataset_id,source_dataset_version,target_dataset_id,
               target_dataset_version,direction,transform_sha256,transformation_ref)
            VALUES (#{tenantId},#{definitionId},#{definitionVersion},#{sourceDatasetId},#{sourceDatasetVersion},
                    #{targetDatasetId},#{targetDatasetVersion},#{direction},#{transformSha256},#{transformationRef})
            """) int insertLineageVersion(LineageVersion value);

    @Select("""
            WITH RECURSIVE lineage_path (dataset_id, dataset_version) AS (
                SELECT target_dataset_id, target_dataset_version
                FROM cloudmold_metadata_lineage_version
                WHERE tenant_id=#{tenantId} AND source_dataset_id=#{targetDatasetId}
                  AND source_dataset_version=#{targetDatasetVersion}
                UNION DISTINCT
                SELECT l.target_dataset_id, l.target_dataset_version
                FROM cloudmold_metadata_lineage_version l
                JOIN lineage_path p ON l.tenant_id=#{tenantId}
                  AND l.source_dataset_id=p.dataset_id AND l.source_dataset_version=p.dataset_version
            )
            SELECT COUNT(*) FROM lineage_path
            WHERE dataset_id=#{sourceDatasetId} AND dataset_version=#{sourceDatasetVersion}
            """)
    int countLineagePath(@Param("tenantId") Long tenantId,
                         @Param("targetDatasetId") String targetDatasetId,
                         @Param("targetDatasetVersion") Long targetDatasetVersion,
                         @Param("sourceDatasetId") String sourceDatasetId,
                         @Param("sourceDatasetVersion") Long sourceDatasetVersion);

    @Insert("""
            INSERT INTO cloudmold_metadata_dqc_rule_version
              (tenant_id,definition_id,definition_version,dataset_id,dataset_version,dataset_field,rule_type,severity,
               expression_sha256,threshold_value,threshold_comparator)
            VALUES (#{tenantId},#{definitionId},#{definitionVersion},#{datasetId},#{datasetVersion},#{datasetField},
                    #{ruleType},#{severity},#{expressionSha256},#{thresholdValue},#{thresholdComparator})
            """) int insertDqcRuleVersion(DqcRuleVersion value);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_metadata_dqc_rule_version
            WHERE tenant_id=#{tenantId} AND definition_id=#{ruleId} AND definition_version=#{ruleVersion}
              AND dataset_id=#{datasetId} AND dataset_version=#{datasetVersion}
            """)
    int countDqcRuleTarget(@Param("tenantId") Long tenantId, @Param("ruleId") String ruleId,
                           @Param("ruleVersion") Long ruleVersion, @Param("datasetId") String datasetId,
                           @Param("datasetVersion") Long datasetVersion);

    @Insert("""
            INSERT INTO cloudmold_metadata_dqc_result
              (tenant_id,result_id,dqc_rule_id,dqc_rule_version,dataset_id,dataset_version,task_run_id,
               task_run_observation_sequence,status,expected_value,actual_value,evaluated_rows,violation_count,
               evidence_ref,observed_at,operation_id)
            VALUES (#{tenantId},#{resultId},#{dqcRuleId},#{dqcRuleVersion},#{datasetId},#{datasetVersion},#{taskRunId},
                    #{taskRunObservationSequence},#{status},#{expectedValue},#{actualValue},#{evaluatedRows},
                    #{violationCount},#{evidenceRef},#{observedAt},#{operationId})
            """) int insertDqcResult(DqcResult value);

    @Select("""
            SELECT tenant_id,result_id,dqc_rule_id,dqc_rule_version,dataset_id,dataset_version,task_run_id,
                   task_run_observation_sequence,status,expected_value,actual_value,evaluated_rows,violation_count,
                   evidence_ref,observed_at,operation_id
            FROM cloudmold_metadata_dqc_result WHERE tenant_id=#{tenantId} AND result_id=#{resultId}
            """) DqcResult selectDqcResult(@Param("tenantId") Long tenantId, @Param("resultId") String resultId);

    @Insert("""
            INSERT INTO cloudmold_metadata_metric_version
              (tenant_id,definition_id,definition_version,grain_code,metric_unit,aggregation_type,expression_sha256,
               filter_sha256,dimensions_sha256,semantic_version)
            VALUES (#{tenantId},#{definitionId},#{definitionVersion},#{grainCode},#{metricUnit},#{aggregationType},
                    #{expressionSha256},#{filterSha256},#{dimensionsSha256},#{semanticVersion})
            """) int insertMetricVersion(MetricVersion value);

    @Insert("""
            INSERT INTO cloudmold_metadata_status_history
              (tenant_id,aggregate_type,aggregate_id,aggregate_version,previous_status,current_status,operation_id,
               reason_code,occurred_at,created_at)
            VALUES (#{tenantId},#{aggregateType},#{aggregateId},#{aggregateVersion},#{previousStatus},#{currentStatus},
                    #{operationId},#{reasonCode},#{occurredAt},#{createdAt})
            """) int insertStatusHistory(StatusHistory value);
}
