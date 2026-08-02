package cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileView;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierProfileRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierProfileRecords.Profile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SupplierProfileMapper {
    @Insert("""
            INSERT INTO cloudmold_supplier_profile_operation
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
                   aggregate_type,aggregate_id,result_json
            FROM cloudmold_supplier_profile_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_supplier_profile_operation
            SET status=10,aggregate_type='supplier_profile',aggregate_id=#{supplierId},
                result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId,
                               @Param("tenantId") Long tenantId,
                               @Param("supplierId") String supplierId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_supplier_profile
              (supplier_id,tenant_id,supplier_code,supplier_name,country_code,capability_summary,risk_level,
               status,admission_status,created_by_principal_id,reason_code,version,created_at,updated_at)
            VALUES (#{supplierId},#{tenantId},#{supplierCode},#{supplierName},#{countryCode},
                    #{capabilitySummary},#{riskLevel},#{status},#{admissionStatus},#{createdByPrincipalId},
                    #{reasonCode},#{version},#{createdAt},#{updatedAt})
            """)
    int insertProfile(Profile value);

    @Select("""
            SELECT supplier_id,tenant_id,supplier_code,supplier_name,country_code,capability_summary,risk_level,
                   status,admission_status,qualification_evidence_sha256,risk_evidence_sha256,
                   created_by_principal_id,submitted_by_principal_id,admitted_by_principal_id,reason_code,
                   version,created_at,updated_at,admission_submitted_at,admitted_at
            FROM cloudmold_supplier_profile
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId} FOR UPDATE
            """)
    Profile selectProfileForUpdate(@Param("tenantId") Long tenantId,
                                   @Param("supplierId") String supplierId);

    @Update("""
            UPDATE cloudmold_supplier_profile
            SET admission_status='UNDER_REVIEW',submitted_by_principal_id=#{actorPrincipalId},
                qualification_evidence_sha256=#{qualificationEvidenceSha256},
                risk_evidence_sha256=#{riskEvidenceSha256},reason_code=#{reasonCode},
                admission_submitted_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}
              AND status='CANDIDATE' AND admission_status='DRAFT' AND version=#{expectedVersion}
            """)
    int submitAdmission(@Param("tenantId") Long tenantId,
                        @Param("supplierId") String supplierId,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("actorPrincipalId") String actorPrincipalId,
                        @Param("qualificationEvidenceSha256") String qualificationEvidenceSha256,
                        @Param("riskEvidenceSha256") String riskEvidenceSha256,
                        @Param("reasonCode") String reasonCode,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_supplier_profile
            SET status='ACTIVE',admission_status='ADMITTED',admitted_by_principal_id=#{actorPrincipalId},
                reason_code=#{reasonCode},admitted_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}
              AND status='CANDIDATE' AND admission_status='UNDER_REVIEW' AND version=#{expectedVersion}
            """)
    int approveAdmission(@Param("tenantId") Long tenantId,
                         @Param("supplierId") String supplierId,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("actorPrincipalId") String actorPrincipalId,
                         @Param("reasonCode") String reasonCode,
                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT supplier_id supplierId,supplier_code supplierCode,supplier_name supplierName,
                   country_code countryCode,capability_summary capabilitySummary,risk_level riskLevel,
                   status,admission_status admissionStatus,
                   qualification_evidence_sha256 qualificationEvidenceSha256,
                   risk_evidence_sha256 riskEvidenceSha256,version,
                   created_by_principal_id createdByPrincipalId,
                   admitted_by_principal_id admittedByPrincipalId,
                   created_at createdAt,admitted_at admittedAt
            FROM cloudmold_supplier_profile
            WHERE tenant_id=#{tenantId} AND supplier_id=#{supplierId}
            """)
    SupplierProfileView selectProfile(@Param("tenantId") Long tenantId,
                                      @Param("supplierId") String supplierId);
}
