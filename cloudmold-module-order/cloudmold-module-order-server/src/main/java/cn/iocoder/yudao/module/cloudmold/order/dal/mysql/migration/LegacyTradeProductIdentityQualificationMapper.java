package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration;

import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface LegacyTradeProductIdentityQualificationMapper {

    @Select("""
            SELECT item.tenant_id,item.migration_run_id source_migration_run_id,
                   run.policy_version,run.item_evidence_complete,run.product_snapshot_evidence_complete,
                   item.item_evidence_id,
                   item.legacy_order_item_id,item.legacy_spu_id,item.legacy_sku_id,
                   item.legacy_item_snapshot_hash source_item_evidence_hash,
                   item.historical_product_snapshot_hash,item.product_snapshot_status,
                   item.is_deleted deleted,candidate.is_deleted order_deleted
            FROM cloudmold_order_benefit_migration_item item
            JOIN cloudmold_order_benefit_migration_run run
              ON run.tenant_id=item.tenant_id
             AND BINARY run.migration_run_id=BINARY item.migration_run_id
            JOIN cloudmold_order_benefit_migration_candidate candidate
              ON candidate.tenant_id=item.tenant_id
             AND BINARY candidate.migration_run_id=BINARY item.migration_run_id
             AND BINARY candidate.candidate_id=BINARY item.candidate_id
            WHERE item.tenant_id=#{tenantId} AND item.migration_run_id=#{sourceRunId}
              AND item.item_evidence_id=#{itemEvidenceId}
            """)
    LegacyTradeProductIdentityQualificationSourceDO selectSourceItem(
            @Param("tenantId") Long tenantId,
            @Param("sourceRunId") String sourceRunId,
            @Param("itemEvidenceId") String itemEvidenceId);

    @Insert("""
            INSERT INTO cloudmold_order_product_identity_qualification_request
              (request_id,tenant_id,idempotency_key,request_hash,action_type,target_qualification_id,
               source_migration_run_id,item_evidence_id,legacy_order_item_id,historical_spu_id,
               historical_sku_id,source_item_evidence_hash,historical_product_snapshot_hash,
               source_evidence_uri,evidence_verification_status,evidence_verifier_version,
               evidence_content_length,evidence_verified_at,qualification_ref,scope_hash,
               requester_id,approval_count,status,
               qualification_id,version,requested_at,applied_at,created_at,updated_at)
            VALUES
              (#{requestId},#{tenantId},#{idempotencyKey},#{requestHash},#{actionType},#{targetQualificationId},
               #{sourceMigrationRunId},#{itemEvidenceId},#{legacyOrderItemId},#{historicalSpuId},
               #{historicalSkuId},#{sourceItemEvidenceHash},#{historicalProductSnapshotHash},
               #{sourceEvidenceUri},#{evidenceVerificationStatus},#{evidenceVerifierVersion},
               #{evidenceContentLength},#{evidenceVerifiedAt},#{qualificationRef},#{scopeHash},
               #{requesterId},#{approvalCount},#{status},
               #{qualificationId},#{version},#{requestedAt},#{appliedAt},#{createdAt},#{updatedAt})
            """)
    int insertRequest(LegacyTradeProductIdentityQualificationRequestDO value);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_qualification_request
            WHERE tenant_id=#{tenantId} AND idempotency_key=#{idempotencyKey} FOR UPDATE
            """)
    LegacyTradeProductIdentityQualificationRequestDO selectRequestByIdempotencyForUpdate(
            @Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_qualification_request
            WHERE tenant_id=#{tenantId} AND request_id=#{requestId} FOR UPDATE
            """)
    LegacyTradeProductIdentityQualificationRequestDO selectRequestForUpdate(
            @Param("tenantId") Long tenantId, @Param("requestId") String requestId);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_qualification_request
            WHERE tenant_id=#{tenantId} AND request_id=#{requestId}
            """)
    LegacyTradeProductIdentityQualificationRequestDO selectRequest(
            @Param("tenantId") Long tenantId, @Param("requestId") String requestId);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_qualification_approval
            WHERE tenant_id=#{tenantId} AND idempotency_key=#{idempotencyKey} FOR UPDATE
            """)
    LegacyTradeProductIdentityQualificationApprovalDO selectApprovalByIdempotencyForUpdate(
            @Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_qualification_approval
            WHERE tenant_id=#{tenantId} AND request_id=#{requestId}
            ORDER BY approved_at,approval_id FOR UPDATE
            """)
    List<LegacyTradeProductIdentityQualificationApprovalDO> selectApprovalsForUpdate(
            @Param("tenantId") Long tenantId, @Param("requestId") String requestId);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_qualification_approval
            WHERE tenant_id=#{tenantId} AND request_id=#{requestId}
            ORDER BY approved_at,approval_id
            """)
    List<LegacyTradeProductIdentityQualificationApprovalDO> selectApprovals(
            @Param("tenantId") Long tenantId, @Param("requestId") String requestId);

    @Insert("""
            INSERT INTO cloudmold_order_product_identity_qualification_approval
              (approval_id,tenant_id,request_id,approval_role,approver_id,scope_hash,
               expected_request_version,evidence_ref,idempotency_key,request_hash,status,version,
               approved_at,created_at)
            VALUES
              (#{approvalId},#{tenantId},#{requestId},#{approvalRole},#{approverId},#{scopeHash},
               #{expectedRequestVersion},#{evidenceRef},#{idempotencyKey},#{requestHash},#{status},#{version},
               #{approvedAt},#{createdAt})
            """)
    int insertApproval(LegacyTradeProductIdentityQualificationApprovalDO value);

    @Update("""
            UPDATE cloudmold_order_product_identity_qualification_request
            SET approval_count=#{approvalCount},status=#{status},qualification_id=#{qualificationId},
                version=version+1,applied_at=#{appliedAt},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND request_id=#{requestId}
              AND version=#{expectedVersion} AND status IN ('PENDING','PARTIALLY_APPROVED')
            """)
    int advanceRequest(@Param("tenantId") Long tenantId, @Param("requestId") String requestId,
                       @Param("expectedVersion") Long expectedVersion,
                       @Param("approvalCount") int approvalCount, @Param("status") String status,
                       @Param("qualificationId") String qualificationId,
                       @Param("appliedAt") LocalDateTime appliedAt, @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_qualification
            WHERE tenant_id=#{tenantId} AND qualification_id=#{qualificationId} FOR UPDATE
            """)
    LegacyTradeProductIdentityQualificationDO selectQualificationForUpdate(
            @Param("tenantId") Long tenantId, @Param("qualificationId") String qualificationId);

    @Select("""
            SELECT * FROM cloudmold_order_product_identity_qualification
            WHERE tenant_id=#{tenantId} AND source_migration_run_id=#{sourceRunId}
              AND item_evidence_id=#{itemEvidenceId} AND status='QUALIFIED' FOR UPDATE
            """)
    LegacyTradeProductIdentityQualificationDO selectActiveQualificationForUpdate(
            @Param("tenantId") Long tenantId, @Param("sourceRunId") String sourceRunId,
            @Param("itemEvidenceId") String itemEvidenceId);

    @Insert("""
            INSERT INTO cloudmold_order_product_identity_qualification
              (qualification_id,tenant_id,source_migration_run_id,item_evidence_id,legacy_order_item_id,
               historical_spu_id,historical_sku_id,source_item_evidence_hash,
               historical_product_snapshot_hash,source_evidence_uri,evidence_verification_status,
               evidence_verifier_version,evidence_content_length,evidence_verified_at,
               qualification_ref,request_id,
               approval_set_hash,qualified_by,qualified_at,status,version,created_at,updated_at)
            VALUES
              (#{qualificationId},#{tenantId},#{sourceMigrationRunId},#{itemEvidenceId},#{legacyOrderItemId},
               #{historicalSpuId},#{historicalSkuId},#{sourceItemEvidenceHash},
               #{historicalProductSnapshotHash},#{sourceEvidenceUri},#{evidenceVerificationStatus},
               #{evidenceVerifierVersion},#{evidenceContentLength},#{evidenceVerifiedAt},
               #{qualificationRef},#{requestId},
               #{approvalSetHash},#{qualifiedBy},#{qualifiedAt},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertQualification(LegacyTradeProductIdentityQualificationDO value);

    @Update("""
            UPDATE cloudmold_order_product_identity_qualification
            SET status='REVOKED',revocation_request_id=#{revocationRequestId},
                revocation_approval_set_hash=#{revocationApprovalSetHash},revoked_at=#{now},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND qualification_id=#{qualificationId}
              AND status='QUALIFIED' AND version=#{expectedVersion}
            """)
    int revokeQualification(@Param("tenantId") Long tenantId,
                            @Param("qualificationId") String qualificationId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("revocationRequestId") String revocationRequestId,
                            @Param("revocationApprovalSetHash") String revocationApprovalSetHash,
                            @Param("now") LocalDateTime now);
}
