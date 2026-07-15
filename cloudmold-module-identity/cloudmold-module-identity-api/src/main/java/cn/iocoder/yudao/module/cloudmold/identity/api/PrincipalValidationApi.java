package cn.iocoder.yudao.module.cloudmold.identity.api;

public interface PrincipalValidationApi {
    void requireActivePrincipal(String principalId);
}
