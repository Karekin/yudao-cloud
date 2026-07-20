package cn.iocoder.yudao.module.cloudmold.agentcontrol.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.error;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        AgentControlAdminController.class,
        AgentAuthorityGovernanceAdminController.class,
        AgentActionAdminController.class
})
@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
public class AgentControlExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public CommonResult<Void> handleBusinessRejection(RuntimeException exception) {
        return error(400, exception.getMessage());
    }
}
