package cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo.EventOutboxPageReqVO;
import cn.iocoder.yudao.module.cloudmold.datacontract.service.query.EventOutboxPageItem;
import cn.iocoder.yudao.module.cloudmold.datacontract.service.query.EventOutboxQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Event Outbox Query")
@RestController
@RequestMapping("/cloudmold/event-outbox")
public class EventOutboxQueryController {

    @Resource
    private EventOutboxQueryService eventOutboxQueryService;

    @GetMapping("/page")
    @Operation(summary = "分页查询规范事件外发，只返回当前租户 CloudMold 权威数据（不含 payload/headers/error）")
    @PreAuthorize("@ss.hasPermission('cloudmold:data-readiness:query')")
    public CommonResult<PageResult<EventOutboxPageItem>> getPage(@Valid EventOutboxPageReqVO request) {
        return success(eventOutboxQueryService.getPage(request));
    }
}
