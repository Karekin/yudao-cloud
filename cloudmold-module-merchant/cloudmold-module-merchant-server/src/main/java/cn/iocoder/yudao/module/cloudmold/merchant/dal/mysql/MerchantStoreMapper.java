package cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.merchant.api.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MerchantStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_merchant_operation
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
            FROM cloudmold_merchant_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    MerchantOperationDO selectOperationForUpdate(@Param("operationId") Long operationId,
                                                   @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_merchant_operation
            SET status=10,aggregate_id=#{aggregateId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_merchant_legal_entity
              (legal_entity_id,tenant_id,legal_name,registration_hash_token,business_license_token,status,version,
               created_at,updated_at)
            VALUES (#{legalEntityId},#{tenantId},#{legalName},#{registrationHashToken},#{businessLicenseToken},
                    #{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertLegalEntity(MerchantLegalEntityDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_onboarding_application
              (application_id,tenant_id,run_id,legal_entity_id,owner_principal_id,channel_code,external_shop_id,status,
               decision_reason,merchant_id,shop_id,owner_assignment_id,version,created_at,updated_at)
            VALUES (#{applicationId},#{tenantId},#{runId},#{legalEntityId},#{ownerPrincipalId},#{channelCode},
                    #{externalShopId},#{status},#{decisionReason},#{merchantId},#{shopId},#{ownerAssignmentId},
                    #{version},#{createdAt},#{updatedAt})
            """)
    int insertApplication(MerchantOnboardingApplicationDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_account
              (merchant_id,tenant_id,merchant_code,legal_entity_id,status,version,created_at,updated_at)
            VALUES (#{merchantId},#{tenantId},#{merchantCode},#{legalEntityId},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertMerchant(MerchantAccountDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_shop
              (shop_id,tenant_id,merchant_id,channel_code,external_shop_id,status,version,created_at,updated_at)
            VALUES (#{shopId},#{tenantId},#{merchantId},#{channelCode},#{externalShopId},#{status},#{version},
                    #{createdAt},#{updatedAt})
            """)
    int insertShop(MerchantShopDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_operator_assignment
              (assignment_id,tenant_id,merchant_id,shop_id,principal_id,role_code,status,version,valid_from,valid_to,
               created_at,updated_at)
            VALUES (#{assignmentId},#{tenantId},#{merchantId},#{shopId},#{principalId},#{roleCode},#{status},#{version},
                    #{validFrom},#{validTo},#{createdAt},#{updatedAt})
            """)
    int insertAssignment(MerchantOperatorAssignmentDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_status_history
              (tenant_id,aggregate_type,aggregate_id,aggregate_version,previous_status,current_status,operation_id,
               reason,occurred_at,created_at)
            VALUES (#{tenantId},#{aggregateType},#{aggregateId},#{aggregateVersion},#{previousStatus},#{currentStatus},
                    #{operationId},#{reason},#{occurredAt},#{createdAt})
            """)
    int insertHistory(MerchantStatusHistoryDO value);

    @Insert("""
            INSERT INTO cloudmold_merchant_source_mapping
              (mapping_id,tenant_id,source_system,source_type,source_id,target_type,target_id,legal_entity_id,
               merchant_id,shop_id,valid_from,valid_to,verification_ref,migration_run_id,status,version,
               created_at,updated_at)
            VALUES (#{mappingId},#{tenantId},#{sourceSystem},#{sourceType},#{sourceId},#{targetType},#{targetId},
                    #{legalEntityId},#{merchantId},#{shopId},#{validFrom},#{validTo},#{verificationRef},
                    #{migrationRunId},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertSourceMapping(MerchantSourceMappingDO value);

    @Select("""
            SELECT application_id,tenant_id,run_id,legal_entity_id,owner_principal_id,channel_code,external_shop_id,
                   status,decision_reason,merchant_id,shop_id,owner_assignment_id,version,created_at,updated_at
            FROM cloudmold_merchant_onboarding_application
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}
            FOR UPDATE
            """)
    MerchantOnboardingApplicationDO selectApplicationForUpdate(@Param("tenantId") Long tenantId,
                                                                @Param("applicationId") String applicationId);

    @Select("""
            SELECT legal_entity_id,tenant_id,legal_name,registration_hash_token,business_license_token,status,version,
                   created_at,updated_at
            FROM cloudmold_merchant_legal_entity
            WHERE tenant_id=#{tenantId} AND legal_entity_id=#{legalEntityId}
            FOR UPDATE
            """)
    MerchantLegalEntityDO selectLegalEntityForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("legalEntityId") String legalEntityId);

    @Select("""
            SELECT merchant_id,tenant_id,merchant_code,legal_entity_id,status,version,created_at,updated_at
            FROM cloudmold_merchant_account
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId}
            FOR UPDATE
            """)
    MerchantAccountDO selectMerchantForUpdate(@Param("tenantId") Long tenantId,
                                               @Param("merchantId") String merchantId);

    @Select("""
            SELECT shop_id,tenant_id,merchant_id,channel_code,external_shop_id,status,version,created_at,updated_at
            FROM cloudmold_merchant_shop
            WHERE tenant_id=#{tenantId} AND shop_id=#{shopId}
            FOR UPDATE
            """)
    MerchantShopDO selectShopForUpdate(@Param("tenantId") Long tenantId, @Param("shopId") String shopId);

    @Select("""
            SELECT mapping_id,tenant_id,source_system,source_type,source_id,target_type,target_id,legal_entity_id,
                   merchant_id,shop_id,valid_from,valid_to,verification_ref,migration_run_id,status,version,
                   created_at,updated_at
            FROM cloudmold_merchant_source_mapping
            WHERE tenant_id=#{tenantId} AND mapping_id=#{mappingId}
            FOR UPDATE
            """)
    MerchantSourceMappingDO selectSourceMappingForUpdate(@Param("tenantId") Long tenantId,
                                                          @Param("mappingId") String mappingId);

    @Select("""
            SELECT mapping_id,tenant_id,source_system,source_type,source_id,target_type,target_id,legal_entity_id,
                   merchant_id,shop_id,valid_from,valid_to,verification_ref,migration_run_id,status,version,
                   created_at,updated_at
            FROM cloudmold_merchant_source_mapping
            WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem} AND source_type=#{sourceType}
              AND source_id=#{sourceId} AND status='ACTIVE'
            FOR UPDATE
            """)
    List<MerchantSourceMappingDO> selectActiveSourceMappingsForUpdate(@Param("tenantId") Long tenantId,
                                                                      @Param("sourceSystem") String sourceSystem,
                                                                      @Param("sourceType") String sourceType,
                                                                      @Param("sourceId") String sourceId);

    @Select("""
            SELECT mapping_id,tenant_id,source_system,source_type,source_id,target_type,target_id,legal_entity_id,
                   merchant_id,shop_id,valid_from,valid_to,verification_ref,migration_run_id,status,version,
                   created_at,updated_at
            FROM cloudmold_merchant_source_mapping
            WHERE tenant_id=#{tenantId} AND source_system=#{sourceSystem} AND source_type=#{sourceType}
              AND source_id=#{sourceId} AND status='ACTIVE' AND valid_from<=#{effectiveAt}
              AND (valid_to IS NULL OR valid_to>#{effectiveAt})
            """)
    List<MerchantSourceMappingDO> selectActiveSourceMappings(@Param("tenantId") Long tenantId,
                                                             @Param("sourceSystem") String sourceSystem,
                                                             @Param("sourceType") String sourceType,
                                                             @Param("sourceId") String sourceId,
                                                             @Param("effectiveAt") LocalDateTime effectiveAt);

    @Select("""
            SELECT merchant_id,status AS merchant_status,legal_entity_id
            FROM cloudmold_merchant_account
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND status='ACTIVE'
            """)
    MerchantOwnerView selectActiveMerchant(@Param("tenantId") Long tenantId,
                                           @Param("merchantId") String merchantId);

    @Update("""
            UPDATE cloudmold_merchant_onboarding_application
            SET status=#{after},decision_reason=#{reason},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionApplication(@Param("tenantId") Long tenantId, @Param("applicationId") String applicationId,
                              @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                              @Param("after") String after, @Param("reason") String reason,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_onboarding_application
            SET merchant_id=#{merchantId},shop_id=#{shopId},owner_assignment_id=#{assignmentId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND version=#{approvedVersion}
              AND status='APPROVED' AND merchant_id IS NULL AND shop_id IS NULL AND owner_assignment_id IS NULL
            """)
    int attachApprovedEntities(@Param("tenantId") Long tenantId, @Param("applicationId") String applicationId,
                               @Param("approvedVersion") Long approvedVersion, @Param("merchantId") String merchantId,
                               @Param("shopId") String shopId, @Param("assignmentId") String assignmentId,
                               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_legal_entity
            SET status=#{after},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND legal_entity_id=#{legalEntityId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionLegalEntity(@Param("tenantId") Long tenantId, @Param("legalEntityId") String legalEntityId,
                              @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                              @Param("after") String after, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_account
            SET status=#{after},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionMerchant(@Param("tenantId") Long tenantId, @Param("merchantId") String merchantId,
                           @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                           @Param("after") String after, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_shop
            SET status=#{after},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND shop_id=#{shopId} AND version=#{expectedVersion} AND status=#{before}
            """)
    int transitionShop(@Param("tenantId") Long tenantId, @Param("shopId") String shopId,
                       @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                       @Param("after") String after, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_merchant_source_mapping
            SET status='REVOKED',valid_to=#{validTo},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND mapping_id=#{mappingId} AND version=#{expectedVersion}
              AND status='ACTIVE'
            """)
    int revokeSourceMapping(@Param("tenantId") Long tenantId, @Param("mappingId") String mappingId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("validTo") LocalDateTime validTo, @Param("now") LocalDateTime now);

    @Select("""
            SELECT m.merchant_id,m.status AS merchant_status,m.legal_entity_id,
                   s.shop_id,s.status AS shop_status,s.channel_code
            FROM cloudmold_merchant_account m
            JOIN cloudmold_merchant_shop s ON s.tenant_id=m.tenant_id AND s.merchant_id=m.merchant_id
            WHERE m.tenant_id=#{tenantId} AND m.merchant_id=#{merchantId} AND s.shop_id=#{shopId}
              AND m.status='ACTIVE' AND s.status='ACTIVE'
            """)
    MerchantReferenceView selectActiveReference(@Param("tenantId") Long tenantId,
                                                @Param("merchantId") String merchantId,
                                                @Param("shopId") String shopId);

    @Select("""
            SELECT assignment_id,merchant_id,shop_id,principal_id,role_code,status AS assignment_status
            FROM cloudmold_merchant_operator_assignment
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND shop_id=#{shopId}
              AND principal_id=#{principalId} AND role_code=#{roleCode} AND status='ACTIVE'
              AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6))
            LIMIT 1
            """)
    MerchantOperatorAuthorizationView selectActiveAssignment(@Param("tenantId") Long tenantId,
                                                              @Param("merchantId") String merchantId,
                                                              @Param("shopId") String shopId,
                                                              @Param("principalId") String principalId,
                                                              @Param("roleCode") String roleCode);

    @Select("""
            SELECT application_id,tenant_id,run_id,legal_entity_id,owner_principal_id,channel_code,external_shop_id,
                   status,decision_reason,merchant_id,shop_id,owner_assignment_id,version,created_at,updated_at
            FROM cloudmold_merchant_onboarding_application
            WHERE tenant_id=#{tenantId} AND application_id=#{applicationId}
            """)
    MerchantOnboardingApplicationDO selectApplication(@Param("tenantId") Long tenantId,
                                                       @Param("applicationId") String applicationId);

    @Select("""
            SELECT merchant_id,tenant_id,merchant_code,legal_entity_id,status,version,created_at,updated_at
            FROM cloudmold_merchant_account
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId}
            """)
    MerchantAccountDO selectMerchant(@Param("tenantId") Long tenantId, @Param("merchantId") String merchantId);

    @Select("""
            SELECT shop_id,tenant_id,merchant_id,channel_code,external_shop_id,status,version,created_at,updated_at
            FROM cloudmold_merchant_shop
            WHERE tenant_id=#{tenantId} AND shop_id=#{shopId}
            """)
    MerchantShopDO selectShop(@Param("tenantId") Long tenantId, @Param("shopId") String shopId);

    @Select("""
            SELECT assignment_id,tenant_id,merchant_id,shop_id,principal_id,role_code,status,version,valid_from,
                   valid_to,created_at,updated_at
            FROM cloudmold_merchant_operator_assignment
            WHERE tenant_id=#{tenantId} AND assignment_id=#{assignmentId}
            """)
    MerchantOperatorAssignmentDO selectAssignment(@Param("tenantId") Long tenantId,
                                                   @Param("assignmentId") String assignmentId);
}
