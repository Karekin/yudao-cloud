package cn.iocoder.yudao.module.cloudmold.finance.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol.InventoryControlAccountingCommandApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol.InventoryControlAccountingCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol.InventoryControlAccountingResult;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.AdminCommandResult;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.InventoryControlCommandRequest;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.InventoryControlJournalReverseRequest;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingDetail;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingPageItem;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingPageRequest;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.finance.service.query.InventoryControlFinanceQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Inventory Control Finance")
@RestController
@RequestMapping("/cloudmold/finance")
public class InventoryControlFinanceAdminController {

    @Resource
    private InventoryControlAccountingCommandApi commandApi;
    @Resource
    private FinanceActorPrincipalPort actorPort;
    @Resource
    private InventoryControlFinanceQueryService queryService;

    @GetMapping("/financial-impacts/page")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:financial-impact:query')")
    public CommonResult<PageResult<PostingPageItem>> page(PostingPageRequest request) {
        return success(queryService.page(request));
    }

    @GetMapping("/financial-impacts/{postingId}")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:financial-impact:query')")
    public CommonResult<PostingDetail> detail(@PathVariable("postingId") String postingId) {
        return success(queryService.detail(postingId));
    }

    @PostMapping("/inventory-control/command")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:inventory-control:command')")
    public CommonResult<AdminCommandResult> execute(@RequestBody InventoryControlCommandRequest request) {
        String actor = actor();
        String operation = required(request.getOperation());
        InventoryControlAccountingResult result = switch (operation) {
            case "SUBMIT_STOCK_COUNT_GAIN_BASIS" -> commandApi.submitStockCountGainBasis(
                    InventoryControlAccountingCommands.SubmitStockCountGainBasis.builder()
                            .envelope(request.getEnvelope())
                            .stockCountGainBasisId(request.getStockCountGainBasisId())
                            .basisCode(request.getBasisCode())
                            .stockCountId(request.getStockCountId())
                            .stockCountLineId(request.getStockCountLineId())
                            .expectedStockCountVersion(request.getExpectedStockCountVersion())
                            .expectedLineVersion(request.getExpectedLineVersion())
                            .valuationPolicyId(request.getValuationPolicyId())
                            .valuationPolicyVersion(request.getValuationPolicyVersion())
                            .valuationPolicyHash(request.getValuationPolicyHash())
                            .unitOfMeasure(request.getUnitOfMeasure())
                            .unitCostAmountMinor(request.getUnitCostAmountMinor())
                            .currencyCode(request.getCurrencyCode())
                            .evidenceSha256(request.getEvidenceSha256())
                            .build(),
                    actor);
            case "APPROVE_STOCK_COUNT_GAIN_BASIS" -> commandApi.approveStockCountGainBasis(
                    InventoryControlAccountingCommands.ApproveStockCountGainBasis.builder()
                            .envelope(request.getEnvelope())
                            .stockCountGainBasisId(request.getStockCountGainBasisId())
                            .expectedVersion(request.getExpectedVersion())
                            .note(request.getNote())
                            .build(),
                    actor);
            case "POST_STOCK_COUNT_ADJUSTMENT" -> commandApi.postStockCountAdjustment(
                    InventoryControlAccountingCommands.PostStockCountAdjustment.builder()
                            .envelope(request.getEnvelope())
                            .stockCountId(request.getStockCountId())
                            .stockCountLineId(request.getStockCountLineId())
                            .expectedStockCountVersion(request.getExpectedStockCountVersion())
                            .expectedLineVersion(request.getExpectedLineVersion())
                            .ledgerId(request.getLedgerId())
                            .accountingPeriodId(request.getAccountingPeriodId())
                            .accountingDate(request.getAccountingDate())
                            .postingRuleId(request.getPostingRuleId())
                            .postingRuleVersion(request.getPostingRuleVersion())
                            .journalEntryId(request.getJournalEntryId())
                            .journalCode(request.getJournalCode())
                            .postingEvidenceSha256(request.getPostingEvidenceSha256())
                            .journalDimensions(request.getJournalDimensions())
                            .build(),
                    actor);
            case "POST_INVENTORY_SCRAP" -> commandApi.postInventoryScrap(
                    InventoryControlAccountingCommands.PostInventoryScrap.builder()
                            .envelope(request.getEnvelope())
                            .scrapId(request.getScrapId())
                            .scrapLineId(request.getScrapLineId())
                            .dispositionLineId(request.getDispositionLineId())
                            .expectedScrapVersion(request.getExpectedScrapVersion())
                            .expectedLineVersion(request.getExpectedLineVersion())
                            .ledgerId(request.getLedgerId())
                            .accountingPeriodId(request.getAccountingPeriodId())
                            .accountingDate(request.getAccountingDate())
                            .postingRuleId(request.getPostingRuleId())
                            .postingRuleVersion(request.getPostingRuleVersion())
                            .journalEntryId(request.getJournalEntryId())
                            .journalCode(request.getJournalCode())
                            .postingEvidenceSha256(request.getPostingEvidenceSha256())
                            .journalDimensions(request.getJournalDimensions())
                            .build(),
                    actor);
            default -> throw new IllegalArgumentException("unsupported inventory control finance operation");
        };
        return success(admin(result));
    }

    @PostMapping("/inventory-control/journals/reverse")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:inventory-control:command')")
    public CommonResult<AdminCommandResult> reverse(@RequestBody InventoryControlJournalReverseRequest request) {
        InventoryControlAccountingResult result = commandApi.reverse(
                InventoryControlAccountingCommands.Reverse.builder()
                        .envelope(request.getEnvelope())
                        .inventoryControlPostingId(request.getInventoryControlPostingId())
                        .expectedVersion(request.getExpectedVersion())
                        .reversalJournalEntryId(request.getReversalJournalEntryId())
                        .reversalJournalCode(request.getReversalJournalCode())
                        .accountingPeriodId(request.getAccountingPeriodId())
                        .accountingDate(request.getAccountingDate())
                        .reversalEvidenceSha256(request.getReversalEvidenceSha256())
                        .reasonCode(request.getReasonCode())
                        .build(),
                actor());
        return success(admin(result));
    }

    private String actor() {
        return actorPort.resolveSystemAdmin(getLoginUserId());
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static AdminCommandResult admin(InventoryControlAccountingResult result) {
        AdminCommandResult value = new AdminCommandResult();
        value.setOperationId(result.getOperationId() == null ? null : String.valueOf(result.getOperationId()));
        value.setDuplicate(result.isDuplicate());
        value.setInventoryControlPostingId(result.getInventoryControlPostingId());
        value.setSourceType(result.getSourceType());
        value.setSourceDocumentId(result.getSourceDocumentId());
        value.setSourceLineId(result.getSourceLineId());
        value.setSourceReferenceId(result.getSourceReferenceId());
        value.setAggregateVersion(result.getAggregateVersion());
        value.setStatus(result.getStatus());
        value.setJournalEntryId(result.getJournalEntryId());
        value.setJournalCode(result.getJournalCode());
        value.setReversalJournalEntryId(result.getReversalJournalEntryId());
        value.setCurrencyCode(result.getCurrencyCode());
        value.setTotalAmountMinor(result.getTotalAmountMinor() == null ? null : String.valueOf(result.getTotalAmountMinor()));
        return value;
    }
}
