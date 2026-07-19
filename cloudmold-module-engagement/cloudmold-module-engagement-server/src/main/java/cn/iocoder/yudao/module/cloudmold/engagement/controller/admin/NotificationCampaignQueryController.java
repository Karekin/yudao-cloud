package cn.iocoder.yudao.module.cloudmold.engagement.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.engagement.controller.admin.vo.NotificationCampaignPageReqVO;
import cn.iocoder.yudao.module.cloudmold.engagement.service.query.NotificationCampaignPageItem;
import cn.iocoder.yudao.module.cloudmold.engagement.service.query.NotificationCampaignQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Notification Campaign Query")
@RestController
@RequestMapping("/cloudmold/engagement")
public class NotificationCampaignQueryController {

    @Resource
    private NotificationCampaignQueryService notificationCampaignQueryService;

    @GetMapping("/notifications/campaigns/page")
    @Operation(summary = "分页查询规范通知活动，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:notification:query')")
    public CommonResult<PageResult<NotificationCampaignPageItem>> getPage(@Valid NotificationCampaignPageReqVO request) {
        return success(notificationCampaignQueryService.getPage(request));
    }
}
