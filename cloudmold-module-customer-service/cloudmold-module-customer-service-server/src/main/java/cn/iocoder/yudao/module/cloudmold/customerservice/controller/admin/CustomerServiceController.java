package cn.iocoder.yudao.module.cloudmold.customerservice.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Customer Service")
@RestController
@RequestMapping("/cloudmold/customer-service")
public class CustomerServiceController {

    @Resource
    private CustomerServiceCommandApi commandApi;
    @Resource
    private CustomerServiceQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one governed customer-service command")
    @PreAuthorize("@ss.hasPermission('cloudmold:customer-service:command')")
    public CommonResult<CustomerServiceView> execute(@RequestBody CustomerServiceCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/ticket/get")
    @Operation(summary = "Get one tenant-scoped customer-service ticket")
    @PreAuthorize("@ss.hasPermission('cloudmold:customer-service:query')")
    public CommonResult<CustomerServiceView> getTicket(@RequestParam("ticketId") String ticketId) {
        return success(queryApi.getTicket(ticketId));
    }

    @GetMapping("/claim/get")
    @Operation(summary = "Get one tenant-scoped customer-service claim")
    @PreAuthorize("@ss.hasPermission('cloudmold:customer-service:query')")
    public CommonResult<CustomerServiceView> getClaim(@RequestParam("claimId") String claimId) {
        return success(queryApi.getClaim(claimId));
    }
}
