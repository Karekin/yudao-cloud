package cn.iocoder.yudao.module.cloudmold.agentcontrol.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Agent Authority Governance")
@RestController
@RequestMapping("/cloudmold/agent-control/governance/authorities")
@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
public class AgentAuthorityGovernanceAdminController {
    @Resource
    private AgentAuthorityGovernanceApi governanceApi;

    @Resource
    private AgentControlQueryApi queryApi;

    @GetMapping("/grants")
    @Operation(summary = "查询岗位角色授予记录（管理员治理只读）")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:govern')")
    public CommonResult<List<ActorRoleGrantView>> listGrants(
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Integer limit) {
        return success(queryApi.listActorRoleGrants(roleCode, status, actorUserId, limit));
    }

    @PostMapping("/commands")
    @Operation(summary = "Grant or revoke tenant-scoped actor-role and exact approver authority")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:govern')")
    public CommonResult<AgentControlResult> execute(@RequestBody AgentAuthorityCommand command) {
        return success(governanceApi.executeAuthorityGovernance(command, getLoginUserId()));
    }
}
