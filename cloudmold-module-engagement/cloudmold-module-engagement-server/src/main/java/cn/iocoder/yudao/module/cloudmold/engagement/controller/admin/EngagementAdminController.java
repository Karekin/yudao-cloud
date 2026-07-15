package cn.iocoder.yudao.module.cloudmold.engagement.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementCommandApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Engagement")
@RestController
@RequestMapping("/cloudmold/engagement")
public class EngagementAdminController {

    @Resource private EngagementCommandApi commandApi;
    @Resource private EngagementQueryApi queryApi;

    @PostMapping("/favorites/change")
    @Operation(summary = "Collect or uncollect one canonical SPU")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:favorite:write')")
    public CommonResult<EngagementCommandApi.EngagementCommandResult> changeFavorite(
            @RequestBody EngagementCommandApi.ChangeFavoriteCommand command) {
        return success(commandApi.changeFavorite(command));
    }

    @GetMapping("/favorites")
    @Operation(summary = "Get favorite by tenant business key")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:favorite:query')")
    public CommonResult<EngagementQueryApi.FavoriteView> getFavorite(@RequestParam String principalId,
                                                                     @RequestParam String canonicalSpuId) {
        return success(queryApi.getFavorite(principalId, canonicalSpuId));
    }

    @PostMapping("/notifications/campaigns/save")
    @Operation(summary = "Create or transition one notification campaign")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:notification:write')")
    public CommonResult<EngagementCommandApi.EngagementCommandResult> saveCampaign(
            @RequestBody EngagementCommandApi.SaveNotificationCampaignCommand command) {
        return success(commandApi.saveNotificationCampaign(command));
    }

    @PostMapping("/notifications/deliveries")
    @Operation(summary = "Create one notification delivery")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:notification:write')")
    public CommonResult<EngagementCommandApi.EngagementCommandResult> createDelivery(
            @RequestBody EngagementCommandApi.CreateNotificationDeliveryCommand command) {
        return success(commandApi.createNotificationDelivery(command));
    }

    @PostMapping("/notifications/deliveries/attempts")
    @Operation(summary = "Record one notification provider attempt")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:notification:write')")
    public CommonResult<EngagementCommandApi.EngagementCommandResult> recordAttempt(
            @RequestBody EngagementCommandApi.RecordNotificationAttemptCommand command) {
        return success(commandApi.recordNotificationAttempt(command));
    }

    @PostMapping("/notifications/deliveries/receipts")
    @Operation(summary = "Record one notification effect receipt")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:notification:write')")
    public CommonResult<EngagementCommandApi.EngagementCommandResult> recordReceipt(
            @RequestBody EngagementCommandApi.RecordNotificationReceiptCommand command) {
        return success(commandApi.recordNotificationReceipt(command));
    }

    @GetMapping("/notifications/campaigns/{campaignId}")
    @Operation(summary = "Get notification campaign")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:notification:query')")
    public CommonResult<EngagementQueryApi.NotificationCampaignView> getCampaign(@PathVariable String campaignId) {
        return success(queryApi.getNotificationCampaign(campaignId));
    }

    @GetMapping("/notifications/deliveries/{deliveryId}")
    @Operation(summary = "Get notification delivery")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:notification:query')")
    public CommonResult<EngagementQueryApi.NotificationDeliveryView> getDelivery(@PathVariable String deliveryId) {
        return success(queryApi.getNotificationDelivery(deliveryId));
    }

    @PostMapping("/community/content")
    @Operation(summary = "Create community content")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:community:write')")
    public CommonResult<EngagementCommandApi.EngagementCommandResult> createContent(
            @RequestBody EngagementCommandApi.CreateCommunityContentCommand command) {
        return success(commandApi.createCommunityContent(command));
    }

    @PostMapping("/community/interactions")
    @Operation(summary = "Record community comment, like, share, or follow")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:community:write')")
    public CommonResult<EngagementCommandApi.EngagementCommandResult> recordInteraction(
            @RequestBody EngagementCommandApi.RecordCommunityInteractionCommand command) {
        return success(commandApi.recordCommunityInteraction(command));
    }

    @PostMapping("/community/moderation-cases")
    @Operation(summary = "Open a moderation case")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:community:write')")
    public CommonResult<EngagementCommandApi.EngagementCommandResult> openModerationCase(
            @RequestBody EngagementCommandApi.OpenModerationCaseCommand command) {
        return success(commandApi.openModerationCase(command));
    }

    @PostMapping("/community/moderation-cases/decide")
    @Operation(summary = "Decide an open moderation case")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:community:write')")
    public CommonResult<EngagementCommandApi.EngagementCommandResult> decideModerationCase(
            @RequestBody EngagementCommandApi.DecideModerationCaseCommand command) {
        return success(commandApi.decideModerationCase(command));
    }

    @GetMapping("/community/content/{contentId}")
    @Operation(summary = "Get community content")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:community:query')")
    public CommonResult<EngagementQueryApi.CommunityContentView> getContent(@PathVariable String contentId) {
        return success(queryApi.getCommunityContent(contentId));
    }

    @GetMapping("/community/moderation-cases/{moderationCaseId}")
    @Operation(summary = "Get moderation case")
    @PreAuthorize("@ss.hasPermission('cloudmold:engagement:community:query')")
    public CommonResult<EngagementQueryApi.ModerationCaseView> getModerationCase(@PathVariable String moderationCaseId) {
        return success(queryApi.getModerationCase(moderationCaseId));
    }
}
