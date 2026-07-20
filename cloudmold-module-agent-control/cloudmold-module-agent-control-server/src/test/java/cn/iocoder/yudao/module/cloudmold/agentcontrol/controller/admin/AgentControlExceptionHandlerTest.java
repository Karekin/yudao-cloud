package cn.iocoder.yudao.module.cloudmold.agentcontrol.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentControlExceptionHandlerTest {

    private final AgentControlExceptionHandler handler = new AgentControlExceptionHandler();

    @Test
    void exposesBusinessRejectionWithoutReturningAnInternalError() {
        CommonResult<Void> result = handler.handleBusinessRejection(
                new IllegalStateException("approval scope drifted after the request was frozen"));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("approval scope drifted after the request was frozen");
    }
}
