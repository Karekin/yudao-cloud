package cn.iocoder.yudao.module.cloudmold.agentcontrol.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentActionAssemblyApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentActionAssemblyCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentActionAssemblyResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - 岗位 Agent 业务动作")
@RestController
@RequestMapping("/cloudmold/agent-actions")
@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = {"enabled", "action-submission-enabled"},
        havingValue = "true")
public class AgentActionAdminController {

    @Resource
    private AgentActionAssemblyApi actionAssemblyApi;

    @PostMapping("/submit")
    @Operation(summary = "由服务端组装并提交当前岗位工作动作")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:command')")
    public CommonResult<AgentActionAssemblyResult> submit(@RequestBody AgentActionAssemblyCommand command) {
        return success(actionAssemblyApi.assembleAndSubmit(command, getLoginUserId()));
    }

}
