package cn.iocoder.yudao.module.cloudmold.dreamplant.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.*;
import cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker.DreamPlantExplorationGateway.ExplorationCandidate;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface DreamPlantStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_dreamplant_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_dreamplant_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("tenantId") Long tenantId, @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_dreamplant_operation
            SET status=10,aggregate_id=#{aggregateId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId, @Param("operationId") Long operationId,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_dreamplant_world_map
              (tenant_id,map_key,current_version,publicly_readable,created_at,updated_at)
            VALUES (#{tenantId},#{mapKey},#{currentVersion},#{publiclyReadable},#{createdAt},#{updatedAt})
            """)
    int insertWorldMap(WorldMap value);

    @Select("""
            SELECT tenant_id,map_key,current_version,publicly_readable,created_at,updated_at
            FROM cloudmold_dreamplant_world_map
            WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} FOR UPDATE
            """)
    WorldMap selectWorldMapForUpdate(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey);

    @Select("""
            SELECT tenant_id,map_key,current_version,publicly_readable,created_at,updated_at
            FROM cloudmold_dreamplant_world_map
            WHERE tenant_id=#{tenantId} AND map_key=#{mapKey}
            """)
    WorldMap selectWorldMap(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey);

    @Update("""
            UPDATE cloudmold_dreamplant_world_map
            SET current_version=current_version+1,publicly_readable=#{publiclyReadable},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} AND current_version=#{expectedVersion}
            """)
    int advanceWorldMap(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("publiclyReadable") Boolean publiclyReadable,
                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_dreamplant_snapshot
              (tenant_id,map_key,version,schema_version,payload_json,payload_sha256,canonical_hash_verified,
               source_ref,published_at,operation_id)
            VALUES (#{tenantId},#{mapKey},#{version},#{schemaVersion},#{payloadJson},#{payloadSha256},
                    #{canonicalHashVerified},#{sourceRef},#{publishedAt},#{operationId})
            """)
    int insertSnapshot(Snapshot value);

    @Select("""
            SELECT tenant_id,map_key,version,schema_version,payload_json,payload_sha256,canonical_hash_verified,
                   source_ref,published_at,operation_id
            FROM cloudmold_dreamplant_snapshot
            WHERE tenant_id=#{tenantId} AND map_key=#{mapKey} AND version=#{version}
            """)
    Snapshot selectSnapshot(@Param("tenantId") Long tenantId, @Param("mapKey") String mapKey,
                            @Param("version") Long version);

    @Select("""
            SELECT s.tenant_id,s.map_key,s.version,s.schema_version,s.payload_json,s.payload_sha256,
                   s.canonical_hash_verified,s.source_ref,s.published_at,s.operation_id
            FROM cloudmold_dreamplant_world_map m
            JOIN cloudmold_dreamplant_snapshot s ON s.tenant_id=m.tenant_id AND s.map_key=m.map_key
                                                AND s.version=m.current_version
            WHERE m.map_key=#{mapKey} AND m.publicly_readable=1
            ORDER BY s.published_at DESC LIMIT 1
            """)
    Snapshot selectPublicSnapshot(@Param("mapKey") String mapKey);

    @Insert("""
            INSERT INTO cloudmold_dreamplant_exploration
              (tenant_id,exploration_run_id,map_key,intent,context_json,requested_by_principal_id,status,version,
               attempt_count,max_attempts,next_retry_at,created_at,updated_at,operation_id)
            VALUES (#{tenantId},#{explorationRunId},#{mapKey},#{intent},#{contextJson},
                    #{requestedByPrincipalId},#{status},#{version},0,#{maxAttempts},#{nextRetryAt},
                    #{createdAt},#{updatedAt},#{operationId})
            """)
    int insertExploration(ExplorationRun value);

    @Select("""
            SELECT tenant_id,exploration_run_id,map_key,intent,context_json,requested_by_principal_id,status,version,
                   outcome_json,evidence_ref,outcome_hash_verified,attempt_count,max_attempts,next_retry_at,
                   lease_owner,lease_until,last_error_code,last_error_message,started_at,completed_at,
                   created_at,updated_at,operation_id
            FROM cloudmold_dreamplant_exploration
            WHERE tenant_id=#{tenantId} AND exploration_run_id=#{explorationRunId} FOR UPDATE
            """)
    ExplorationRun selectExplorationForUpdate(@Param("tenantId") Long tenantId,
                                               @Param("explorationRunId") String explorationRunId);

    @Select("""
            SELECT tenant_id,exploration_run_id,map_key,intent,context_json,requested_by_principal_id,status,version,
                   outcome_json,evidence_ref,outcome_hash_verified,attempt_count,max_attempts,next_retry_at,
                   lease_owner,lease_until,last_error_code,last_error_message,started_at,completed_at,
                   created_at,updated_at,operation_id
            FROM cloudmold_dreamplant_exploration
            WHERE tenant_id=#{tenantId} AND exploration_run_id=#{explorationRunId}
            """)
    ExplorationRun selectExploration(@Param("tenantId") Long tenantId,
                                      @Param("explorationRunId") String explorationRunId);

    @Select("""
            SELECT tenant_id,exploration_run_id,map_key,intent,context_json,requested_by_principal_id,status,version,
                   outcome_json,evidence_ref,outcome_hash_verified,attempt_count,max_attempts,next_retry_at,
                   lease_owner,lease_until,last_error_code,last_error_message,started_at,completed_at,
                   created_at,updated_at,operation_id
            FROM cloudmold_dreamplant_exploration
            WHERE tenant_id=#{tenantId} AND status=#{status}
            ORDER BY updated_at, exploration_run_id LIMIT #{limit}
            """)
    List<ExplorationRun> selectExplorations(@Param("tenantId") Long tenantId, @Param("status") String status,
                                            @Param("limit") Integer limit);

    @Update("""
            UPDATE cloudmold_dreamplant_exploration
            SET status=#{status},outcome_json=#{outcomeJson},evidence_ref=#{evidenceRef},outcome_hash_verified=#{hashVerified},
                version=version+1,updated_at=#{now},
                completed_at=CASE WHEN #{status} IN ('SUCCEEDED','FAILED','NEEDS_REVIEW','CANCELLED') THEN #{now}
                                  ELSE completed_at END
            WHERE tenant_id=#{tenantId} AND exploration_run_id=#{explorationRunId} AND version=#{expectedVersion}
              AND status NOT IN ('SUCCEEDED','FAILED','CANCELLED')
            """)
    int updateExplorationOutcome(@Param("tenantId") Long tenantId,
                                 @Param("explorationRunId") String explorationRunId,
                                 @Param("expectedVersion") Long expectedVersion, @Param("status") String status,
                                 @Param("outcomeJson") String outcomeJson, @Param("evidenceRef") String evidenceRef,
                                 @Param("hashVerified") Boolean hashVerified,
                                 @Param("now") LocalDateTime now);

    @Select("""
            SELECT tenant_id,exploration_run_id,version,attempt_count
            FROM cloudmold_dreamplant_exploration
            WHERE next_retry_at<=#{now} AND attempt_count<max_attempts
              AND (status='QUEUED' OR (status='RUNNING' AND lease_until<#{now}))
            ORDER BY next_retry_at,updated_at,exploration_run_id LIMIT #{limit}
            """)
    List<ExplorationCandidate> selectDueExplorations(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE cloudmold_dreamplant_exploration
            SET status='RUNNING',attempt_count=attempt_count+1,lease_owner=#{leaseOwner},lease_until=#{leaseUntil},
                started_at=COALESCE(started_at,#{now}),last_error_code=NULL,last_error_message=NULL,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND exploration_run_id=#{explorationRunId} AND version=#{expectedVersion}
              AND attempt_count<max_attempts AND next_retry_at<=#{now}
              AND (status='QUEUED' OR (status='RUNNING' AND lease_until<#{now}))
            """)
    int claimExploration(@Param("tenantId") Long tenantId, @Param("explorationRunId") String explorationRunId,
                         @Param("expectedVersion") Long expectedVersion, @Param("leaseOwner") String leaseOwner,
                         @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_dreamplant_exploration_step
              (tenant_id,exploration_run_id,exploration_version,step_code,step_status,detail_json,occurred_at)
            VALUES (#{tenantId},#{explorationRunId},#{expectedVersion},#{stepCode},#{stepStatus},#{detailJson},#{occurredAt})
            """)
    int appendExplorationStep(@Param("tenantId") Long tenantId,
                              @Param("explorationRunId") String explorationRunId,
                              @Param("expectedVersion") Long expectedVersion, @Param("stepCode") String stepCode,
                              @Param("stepStatus") String stepStatus, @Param("detailJson") String detailJson,
                              @Param("occurredAt") LocalDateTime occurredAt);

    @Update("""
            UPDATE cloudmold_dreamplant_exploration
            SET status='QUEUED',version=version+1,attempt_count=#{attemptCount},next_retry_at=#{nextRunAt},
                lease_owner=NULL,lease_until=NULL,last_error_code=#{failureCode},last_error_message=#{failureMessage},
                outcome_json=#{outcomeJson},evidence_ref=#{evidenceRef},outcome_hash_verified=1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND exploration_run_id=#{explorationRunId} AND version=#{expectedVersion}
              AND status='RUNNING' AND lease_owner=#{leaseOwner}
            """)
    int markExplorationRetry(@Param("tenantId") Long tenantId,
                             @Param("explorationRunId") String explorationRunId,
                             @Param("expectedVersion") Long expectedVersion, @Param("attemptCount") Integer attemptCount,
                             @Param("leaseOwner") String leaseOwner, @Param("failureCode") String failureCode,
                             @Param("failureMessage") String failureMessage, @Param("outcomeJson") String outcomeJson,
                             @Param("evidenceRef") String evidenceRef, @Param("nextRunAt") LocalDateTime nextRunAt,
                             @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_dreamplant_exploration
            SET status=#{status},version=version+1,outcome_json=#{outcomeJson},evidence_ref=#{evidenceRef},
                outcome_hash_verified=1,lease_owner=NULL,lease_until=NULL,completed_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND exploration_run_id=#{explorationRunId} AND version=#{expectedVersion}
              AND status='RUNNING'
            """)
    int markExplorationTerminal(@Param("tenantId") Long tenantId,
                                @Param("explorationRunId") String explorationRunId,
                                @Param("expectedVersion") Long expectedVersion, @Param("status") String status,
                                @Param("outcomeJson") String outcomeJson, @Param("evidenceRef") String evidenceRef,
                                @Param("now") LocalDateTime now);
}
