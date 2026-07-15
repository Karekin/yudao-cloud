package cn.iocoder.yudao.module.cloudmold.engagement.api.reference;

/** Adapter boundary for canonical Principal authority. */
public interface PrincipalReferenceValidationPort {
    void requireActive(Long tenantId, String principalId);
}
