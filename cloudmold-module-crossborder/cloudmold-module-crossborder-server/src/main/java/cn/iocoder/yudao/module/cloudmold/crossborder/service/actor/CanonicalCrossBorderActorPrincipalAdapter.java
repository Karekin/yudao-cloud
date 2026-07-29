package cn.iocoder.yudao.module.cloudmold.crossborder.service.actor;

import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CanonicalCrossBorderActorPrincipalAdapter implements CrossBorderActorPrincipalPort {
    private final PrincipalValidationApi principalValidationApi;

    @Override
    public void requireActive(String principalId) {
        principalValidationApi.requireActivePrincipal(principalId);
    }
}
