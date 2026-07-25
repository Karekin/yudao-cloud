package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppAddressSnapshotDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AppAddressSnapshotMapper {

    @Insert("""
            INSERT IGNORE INTO cloudmold_app_address_snapshot
              (address_ref,tenant_id,owner_principal_id,member_user_id,source_address_id,
               idempotency_key,request_hash,source_fingerprint_sha256,snapshot_version,
               destination_region_code,key_id,initialization_vector,ciphertext,status,
               created_at,updated_at)
            VALUES
              (#{addressRef},#{tenantId},#{ownerPrincipalId},#{memberUserId},#{sourceAddressId},
               #{idempotencyKey},#{requestHash},#{sourceFingerprintSha256},#{snapshotVersion},
               #{destinationRegionCode},#{keyId},#{initializationVector},#{ciphertext},#{status},
               #{createdAt},#{updatedAt})
            """)
    int insertIgnore(AppAddressSnapshotDO value);

    @Select("""
            SELECT * FROM cloudmold_app_address_snapshot
            WHERE tenant_id=#{tenantId} AND owner_principal_id=#{ownerPrincipalId}
              AND idempotency_key=#{idempotencyKey}
            """)
    AppAddressSnapshotDO selectByIdempotency(@Param("tenantId") Long tenantId,
                                              @Param("ownerPrincipalId") String ownerPrincipalId,
                                              @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM cloudmold_app_address_snapshot
            WHERE tenant_id=#{tenantId} AND address_ref=#{addressRef}
              AND owner_principal_id=#{ownerPrincipalId} AND status='ACTIVE'
            """)
    AppAddressSnapshotDO selectOwned(@Param("tenantId") Long tenantId,
                                     @Param("addressRef") String addressRef,
                                     @Param("ownerPrincipalId") String ownerPrincipalId);
}
