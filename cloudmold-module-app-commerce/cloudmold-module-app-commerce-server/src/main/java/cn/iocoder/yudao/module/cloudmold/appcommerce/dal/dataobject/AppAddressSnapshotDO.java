package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppAddressSnapshotDO {
    private String addressRef;
    private Long tenantId;
    private String ownerPrincipalId;
    private Long memberUserId;
    private Long sourceAddressId;
    private String idempotencyKey;
    private String requestHash;
    private String sourceFingerprintSha256;
    private Long snapshotVersion;
    private String destinationRegionCode;
    private String keyId;
    private byte[] initializationVector;
    private byte[] ciphertext;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
