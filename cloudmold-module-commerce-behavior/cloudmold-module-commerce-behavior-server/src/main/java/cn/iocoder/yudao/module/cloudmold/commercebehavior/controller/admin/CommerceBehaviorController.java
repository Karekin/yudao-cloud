package cn.iocoder.yudao.module.cloudmold.commercebehavior.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.CommerceBehaviorCommandResult;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.AttributePaidOrderCommand;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.LinkCommerceSessionIdentityCommand;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.RecordCommerceBehaviorCommand;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.api.CommerceBehaviorCommandApi.StartCommerceSessionCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Commerce Behavior")
@RestController
@RequestMapping("/cloudmold/commerce-behavior")
public class CommerceBehaviorController {

    @Resource
    private CommerceBehaviorCommandApi commandApi;

    @PostMapping("/session/start")
    @Operation(summary = "Start one tenant-scoped commerce session")
    @PreAuthorize("@ss.hasPermission('cloudmold:commerce-behavior:command')")
    public CommonResult<CommerceBehaviorCommandResult> startSession(
            @RequestBody StartCommerceSessionCommand command) {
        return success(commandApi.startSession(command));
    }

    @PostMapping("/session/link-identity")
    @Operation(summary = "Link an authenticated principal to a commerce session")
    @PreAuthorize("@ss.hasPermission('cloudmold:commerce-behavior:command')")
    public CommonResult<CommerceBehaviorCommandResult> linkSessionIdentity(
            @RequestBody LinkCommerceSessionIdentityCommand command) {
        return success(commandApi.linkSessionIdentity(command));
    }

    @PostMapping("/event/record")
    @Operation(summary = "Record one governed commerce behavior event")
    @PreAuthorize("@ss.hasPermission('cloudmold:commerce-behavior:command')")
    public CommonResult<CommerceBehaviorCommandResult> recordBehavior(
            @RequestBody RecordCommerceBehaviorCommand command) {
        return success(commandApi.recordBehavior(command));
    }

    @PostMapping("/session/attribute-payment")
    @Operation(summary = "Attribute one paid canonical order/payment back to a governed session checkout")
    @PreAuthorize("@ss.hasPermission('cloudmold:commerce-behavior:command')")
    public CommonResult<CommerceBehaviorCommandResult> attributePaidOrder(
            @RequestBody AttributePaidOrderCommand command) {
        return success(commandApi.attributePaidOrder(command));
    }
}
