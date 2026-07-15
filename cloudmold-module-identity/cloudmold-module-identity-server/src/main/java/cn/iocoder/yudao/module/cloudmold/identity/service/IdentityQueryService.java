package cn.iocoder.yudao.module.cloudmold.identity.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.identity.api.*;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.PrincipalDO;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.SourceIdentityDO;
import cn.iocoder.yudao.module.cloudmold.identity.dal.mysql.PrincipalMapper;
import cn.iocoder.yudao.module.cloudmold.identity.dal.mysql.SourceIdentityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class IdentityQueryService implements IdentityQueryApi, PrincipalValidationApi {

    private final PrincipalMapper principalMapper;
    private final SourceIdentityMapper sourceIdentityMapper;

    @Override
    public SourceIdentityView resolveActiveSource(SourceIdentityReference reference) {
        validateReference(reference);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        SourceIdentityDO source = sourceIdentityMapper.selectActiveBySource(tenantId,
                reference.getSourceSystem(), reference.getSourceType(), reference.getSourceId());
        require(source != null, "active source identity does not exist");
        PrincipalDO principal = requireActive(tenantId, source.getPrincipalId());
        return SourceIdentityView.builder().sourceIdentityId(source.getSourceIdentityId())
                .principalId(principal.getPrincipalId()).principalType(principal.getPrincipalType())
                .principalStatus(principal.getStatus()).sourceSystem(source.getSourceSystem())
                .sourceType(source.getSourceType()).sourceId(source.getSourceId()).sourceStatus(source.getStatus())
                .principalVersion(principal.getVersion()).sourceVersion(source.getVersion())
                .validFrom(source.getValidFrom() == null ? null : source.getValidFrom().toInstant(ZoneOffset.UTC))
                .validTo(source.getValidTo() == null ? null : source.getValidTo().toInstant(ZoneOffset.UTC)).build();
    }

    @Override
    public void requireActivePrincipal(String principalId) {
        requireText(principalId, "principalId", 36);
        requireActive(TenantContextHolder.getRequiredTenantId(), principalId);
    }

    private PrincipalDO requireActive(Long tenantId, String principalId) {
        PrincipalDO principal = principalMapper.selectByTenantAndId(tenantId, principalId);
        require(principal != null, "canonical Principal does not exist");
        require("ACTIVE".equals(principal.getStatus()), "canonical Principal is not ACTIVE");
        return principal;
    }

    private static void validateReference(SourceIdentityReference reference) {
        require(reference != null, "source identity reference is required");
        requireText(reference.getSourceSystem(), "sourceSystem", 32);
        requireText(reference.getSourceType(), "sourceType", 32);
        requireText(reference.getSourceId(), "sourceId", 128);
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
