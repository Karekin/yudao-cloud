package cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceScorecardView;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.MetricEvidence;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.Scorecard;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupplierPerformanceMapper {
    @Insert("""
            INSERT INTO cloudmold_supplier_performance_operation
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
            SELECT operation_id,tenant_id,request_hash,attempt_token,status,result_json
            FROM cloudmold_supplier_performance_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_supplier_performance_operation
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
            INSERT INTO cloudmold_supplier_metric_evidence
              (metric_evidence_id,tenant_id,supplier_id,period_start,period_end,source_system,source_record_id,
               metric_code,numerator,denominator,evidence_sha256,observed_at,recorded_by_principal_id,created_at)
            VALUES (#{metricEvidenceId},#{tenantId},#{supplierId},#{periodStart},#{periodEnd},#{sourceSystem},
                    #{sourceRecordId},#{metricCode},#{numerator},#{denominator},#{evidenceSha256},#{observedAt},
                    #{recordedByPrincipalId},#{createdAt})
            """)
    int insertMetricEvidence(MetricEvidence value);

    @Select("""
            SELECT metric_evidence_id,tenant_id,supplier_id,period_start,period_end,source_system,source_record_id,
                   metric_code,numerator,denominator,evidence_sha256,observed_at,recorded_by_principal_id,created_at
            FROM cloudmold_supplier_metric_evidence
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}
              AND period_start=#{periodStart} AND period_end=#{periodEnd}
            ORDER BY metric_code,observed_at,metric_evidence_id
            """)
    List<MetricEvidence> selectMetricEvidence(@Param("tenantId") Long tenantId,
                                               @Param("supplierId") String supplierId,
                                               @Param("periodStart") LocalDate periodStart,
                                               @Param("periodEnd") LocalDate periodEnd);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_supplier_profile
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} AND status='ACTIVE' AND admission_status='ADMITTED'
            """)
    int countActiveAdmittedSupplier(@Param("tenantId") Long tenantId, @Param("supplierId") String supplierId);

    @Select("""
            SELECT COALESCE(MAX(scorecard_version),0)
            FROM cloudmold_supplier_performance_scorecard
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}
              AND period_start=#{periodStart} AND period_end=#{periodEnd}
            """)
    int selectMaxScorecardVersion(@Param("tenantId") Long tenantId,
                                  @Param("supplierId") String supplierId,
                                  @Param("periodStart") LocalDate periodStart,
                                  @Param("periodEnd") LocalDate periodEnd);

    @Insert("""
            INSERT INTO cloudmold_supplier_performance_scorecard
              (scorecard_id,tenant_id,supplier_id,period_start,period_end,scorecard_version,otif_bps,quality_bps,
               capacity_bps,capa_bps,overall_bps,assessment,evidence_snapshot_sha256,generated_by_principal_id,generated_at)
            VALUES (#{scorecardId},#{tenantId},#{supplierId},#{periodStart},#{periodEnd},#{scorecardVersion},
                    #{otifBps},#{qualityBps},#{capacityBps},#{capaBps},#{overallBps},#{assessment},
                    #{evidenceSnapshotSha256},#{generatedByPrincipalId},#{generatedAt})
            """)
    int insertScorecard(Scorecard value);

    @Select("""
            SELECT s.scorecard_id scorecardId,s.supplier_id supplierId,p.supplier_name supplierName,
                   s.period_start periodStart,s.period_end periodEnd,s.otif_bps otifBps,s.quality_bps qualityBps,
                   s.capacity_bps capacityBps,s.capa_bps capaBps,s.overall_bps overallBps,s.assessment,
                   s.scorecard_version scorecardVersion,s.evidence_snapshot_sha256 evidenceSnapshotSha256,
                   s.generated_at generatedAt
            FROM cloudmold_supplier_performance_scorecard s
            JOIN cloudmold_supplier_profile p ON p.tenant_id=s.tenant_id AND p.supplier_id=s.supplier_id
            WHERE s.tenant_id=#{tenantId} AND s.supplier_id=#{supplierId}
            ORDER BY s.period_end DESC,s.scorecard_version DESC LIMIT 1
            """)
    SupplierPerformanceScorecardView selectLatestScorecard(@Param("tenantId") Long tenantId,
                                                            @Param("supplierId") String supplierId);
}
