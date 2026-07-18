package cn.iocoder.yudao.module.cloudmold.rpc;

import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;

public class CloudMoldConsumerContextFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        String capabilityId = CloudMoldCapabilityIds.forMethod(invoker.getInterface(), invocation.getMethodName(),
                invocation.getParameterTypes());
        CloudMoldRpcSecurity.sign(invocation, capabilityId, CloudMoldRpcCallContext.requireCurrent());
        return invoker.invoke(invocation);
    }
}
