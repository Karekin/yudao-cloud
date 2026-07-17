package cn.iocoder.yudao.module.cloudmold.operationsintelligence.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.operationsintelligence.dal.dataobject.OperationsIntelligenceRecords.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface OperationsIntelligenceStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_operations_intelligence_operation
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
                   aggregate_type,aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_operations_intelligence_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_operations_intelligence_operation
            SET status=10,aggregate_type=#{aggregateType},aggregate_id=#{aggregateId},
                result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateType") String aggregateType,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_intelligence_observation
              (observation_id,tenant_id,source_system,source_event_id,observation_type,classification_contract_version,
               taxonomy_id,taxonomy_version_id,taxonomy_definition_version,event_code,intelligence_level_code,
               subject_type,subject_ref,evidence_ref,content_sha256,observed_at,created_at)
            VALUES (#{observationId},#{tenantId},#{sourceSystem},#{sourceEventId},#{observationType},
                    #{classificationContractVersion},#{taxonomyId},#{taxonomyVersionId},#{taxonomyDefinitionVersion},
                    #{eventCode},#{intelligenceLevelCode},#{subjectType},#{subjectRef},#{evidenceRef},#{contentSha256},
                    #{observedAt},#{createdAt})
            """) int insertObservation(Observation value);

    @Select("""
            SELECT observation_id,tenant_id,source_system,source_event_id,observation_type,
                   classification_contract_version,taxonomy_id,taxonomy_version_id,taxonomy_definition_version,
                   event_code,intelligence_level_code,subject_type,subject_ref,evidence_ref,content_sha256,
                   observed_at,created_at
            FROM cloudmold_intelligence_observation WHERE tenant_id=#{tenantId} AND observation_id=#{observationId}
            """) Observation selectObservation(@Param("tenantId") Long tenantId,
                                                  @Param("observationId") String observationId);

    @Select("""
            SELECT observation_id,tenant_id,source_system,source_event_id,observation_type,
                   classification_contract_version,taxonomy_id,taxonomy_version_id,taxonomy_definition_version,
                   event_code,intelligence_level_code,subject_type,subject_ref,evidence_ref,content_sha256,
                   observed_at,created_at
            FROM cloudmold_intelligence_observation
            WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem} AND source_event_id=#{sourceEventId}
            """) Observation selectObservationBySource(@Param("tenantId") Long tenantId,
                                                         @Param("sourceSystem") String sourceSystem,
                                                         @Param("sourceEventId") String sourceEventId);

    @Insert("""
            INSERT INTO cloudmold_intelligence_model_result
              (model_result_id,tenant_id,observation_id,invocation_attempt_ref,model_version_ref,outcome_code,
               score_basis_points,retry_no,evidence_ref,result_sha256,occurred_at,created_at)
            VALUES (#{modelResultId},#{tenantId},#{observationId},#{invocationAttemptRef},#{modelVersionRef},
                    #{outcomeCode},#{scoreBasisPoints},#{retryNo},#{evidenceRef},#{resultSha256},#{occurredAt},#{createdAt})
            """) int insertModelResult(ModelResult value);

    @Select("""
            SELECT model_result_id,tenant_id,observation_id,invocation_attempt_ref,model_version_ref,outcome_code,
                   score_basis_points,retry_no,evidence_ref,result_sha256,occurred_at,created_at
            FROM cloudmold_intelligence_model_result
            WHERE tenant_id=#{tenantId} AND model_result_id=#{modelResultId}
            """) ModelResult selectModelResult(@Param("tenantId") Long tenantId,
                                                 @Param("modelResultId") String modelResultId);

    @Insert("""
            INSERT INTO cloudmold_intelligence_clue
              (clue_id,tenant_id,observation_id,model_result_id,clue_type,source_code,source_published_at,evidence_ref,
               evidence_sha256,status,version,created_at,updated_at)
            VALUES (#{clueId},#{tenantId},#{observationId},#{modelResultId},#{clueType},#{sourceCode},
                    #{sourcePublishedAt},#{evidenceRef},#{evidenceSha256},#{status},#{version},#{createdAt},#{updatedAt})
            """) int insertClue(Clue value);

    @Select("""
            SELECT clue_id,tenant_id,observation_id,model_result_id,clue_type,source_code,source_published_at,
                   evidence_ref,evidence_sha256,status,version,created_at,updated_at
            FROM cloudmold_intelligence_clue WHERE tenant_id=#{tenantId} AND clue_id=#{clueId}
            """) Clue selectClue(@Param("tenantId") Long tenantId, @Param("clueId") String clueId);

    @Select("""
            SELECT clue_id,tenant_id,observation_id,model_result_id,clue_type,source_code,source_published_at,
                   evidence_ref,evidence_sha256,status,version,created_at,updated_at
            FROM cloudmold_intelligence_clue WHERE tenant_id=#{tenantId} AND clue_id=#{clueId} FOR UPDATE
            """) Clue selectClueForUpdate(@Param("tenantId") Long tenantId, @Param("clueId") String clueId);

    @Update("""
            UPDATE cloudmold_intelligence_clue SET status=#{after},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND clue_id=#{clueId} AND status='OBSERVED' AND version=#{expectedVersion}
            """) int reviewClue(@Param("tenantId") Long tenantId, @Param("clueId") String clueId,
                                  @Param("expectedVersion") Long expectedVersion, @Param("after") String after,
                                  @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_intelligence_clue_review
              (review_id,tenant_id,clue_id,decision,reason_code,reviewer_principal_id,occurred_at,created_at)
            VALUES (#{reviewId},#{tenantId},#{clueId},#{decision},#{reasonCode},#{reviewerPrincipalId},
                    #{occurredAt},#{createdAt})
            """) int insertClueReview(ClueReview value);

    @Insert("""
            INSERT INTO cloudmold_operations_alert
              (alert_id,tenant_id,alert_code,source_type,source_ref,severity,category,subcategory,evidence_ref,
               title_sha256,status,current_actor_principal_id,version,opened_at,terminal_at,created_at,updated_at)
            VALUES (#{alertId},#{tenantId},#{alertCode},#{sourceType},#{sourceRef},#{severity},#{category},
                    #{subcategory},#{evidenceRef},#{titleSha256},#{status},#{currentActorPrincipalId},#{version},
                    #{openedAt},#{terminalAt},#{createdAt},#{updatedAt})
            """) int insertAlert(Alert value);

    @Select("""
            SELECT alert_id,tenant_id,alert_code,source_type,source_ref,severity,category,subcategory,evidence_ref,
                   title_sha256,status,current_actor_principal_id,version,opened_at,terminal_at,created_at,updated_at
            FROM cloudmold_operations_alert WHERE tenant_id=#{tenantId} AND alert_id=#{alertId}
            """) Alert selectAlert(@Param("tenantId") Long tenantId, @Param("alertId") String alertId);

    @Select("""
            SELECT alert_id,tenant_id,alert_code,source_type,source_ref,severity,category,subcategory,evidence_ref,
                   title_sha256,status,current_actor_principal_id,version,opened_at,terminal_at,created_at,updated_at
            FROM cloudmold_operations_alert WHERE tenant_id=#{tenantId} AND alert_id=#{alertId} FOR UPDATE
            """) Alert selectAlertForUpdate(@Param("tenantId") Long tenantId, @Param("alertId") String alertId);

    @Update("""
            UPDATE cloudmold_operations_alert
            SET status=#{after},current_actor_principal_id=#{actorPrincipalId},version=version+1,
                terminal_at=#{terminalAt},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND alert_id=#{alertId} AND status=#{before} AND version=#{expectedVersion}
            """) int transitionAlert(@Param("tenantId") Long tenantId, @Param("alertId") String alertId,
                                        @Param("expectedVersion") Long expectedVersion,
                                        @Param("before") String before, @Param("after") String after,
                                        @Param("actorPrincipalId") String actorPrincipalId,
                                        @Param("terminalAt") LocalDateTime terminalAt,
                                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_operations_alert_history
              (tenant_id,alert_id,alert_version,previous_status,current_status,actor_principal_id,reason_code,
               operation_id,occurred_at,created_at)
            VALUES (#{tenantId},#{alertId},#{alertVersion},#{previousStatus},#{currentStatus},#{actorPrincipalId},
                    #{reasonCode},#{operationId},#{occurredAt},#{createdAt})
            """) int insertAlertHistory(AlertHistory value);
}
