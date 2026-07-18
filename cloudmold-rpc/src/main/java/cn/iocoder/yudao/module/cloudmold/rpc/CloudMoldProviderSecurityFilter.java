package cn.iocoder.yudao.module.cloudmold.rpc;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

public class CloudMoldProviderSecurityFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        Long previousTenant = TenantContextHolder.getTenantId();
        SecurityContext previousSecurity = SecurityContextHolder.getContext();
        try {
            String expectedCapabilityId = CloudMoldCapabilityIds.forMethod(invoker.getInterface(),
                    invocation.getMethodName(), invocation.getParameterTypes());
            CloudMoldRpcSecurity.VerifiedContext verified = CloudMoldRpcSecurity.verify(invocation,
                    expectedCapabilityId);
            TenantContextHolder.setTenantId(verified.tenantId());
            SecurityContext rpcSecurity = SecurityContextHolder.createEmptyContext();
            LoginUser loginUser = new LoginUser();
            loginUser.setId(verified.operatorId());
            loginUser.setUserType(verified.operatorType());
            loginUser.setTenantId(verified.tenantId());
            loginUser.setContext("cloudmoldSkillId", verified.skillId());
            loginUser.setContext("cloudmoldRunId", verified.runId());
            rpcSecurity.setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
            SecurityContextHolder.setContext(rpcSecurity);
            return invoker.invoke(invocation);
        } catch (SecurityException ex) {
            throw new RpcException(RpcException.FORBIDDEN_EXCEPTION, ex.getMessage(), ex);
        } finally {
            if (previousTenant == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.setTenantId(previousTenant);
            }
            SecurityContextHolder.setContext(previousSecurity);
        }
    }
}
