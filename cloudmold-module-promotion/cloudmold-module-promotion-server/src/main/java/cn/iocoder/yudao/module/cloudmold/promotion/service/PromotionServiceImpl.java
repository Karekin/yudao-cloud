package cn.iocoder.yudao.module.cloudmold.promotion.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAttributionView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderQueryApi;
import cn.iocoder.yudao.module.cloudmold.promotion.api.*;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionCommandApi, PromotionQueryApi {

    static final int OPERATION_SUCCEEDED = 10;

    private final PromotionOperationMapper operationMapper;
    private final PromotionCampaignMapper campaignMapper;
    private final CanonicalCouponTemplateMapper templateMapper;
    private final CouponEntitlementMapper entitlementMapper;
    private final CouponEntitlementLedgerMapper ledgerMapper;
    private final AdvertisingPlacementMapper placementMapper;
    private final AdvertisingInteractionMapper interactionMapper;
    private final AdvertisingLedgerEntryMapper advertisingLedgerEntryMapper;
    private final PromotionExperimentResultMapper promotionExperimentResultMapper;
    private final GrowthExperimentMapper growthExperimentMapper;
    private final GrowthExperimentVariantMapper growthExperimentVariantMapper;
    private final GrowthExperimentExposureMapper growthExperimentExposureMapper;
    private final GrowthExperimentMetricSnapshotMapper growthExperimentMetricSnapshotMapper;
    private final OrderQueryApi orderQueryApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionCommandResult execute(PromotionCommand command) {
        validateEnvelope(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String hash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                hash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve promotion operation");
        PromotionOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "promotion operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(hash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing promotion operation is not complete");
            PromotionCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), PromotionCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_CAMPAIGN -> createCampaign(tenantId, command);
            case UPDATE_CAMPAIGN -> updateCampaign(tenantId, command, now);
            case ACTIVATE_CAMPAIGN, PAUSE_CAMPAIGN, COMPLETE_CAMPAIGN, CANCEL_CAMPAIGN ->
                    changeCampaign(tenantId, command, now);
            case CREATE_COUPON_TEMPLATE -> createTemplate(tenantId, command);
            case ACTIVATE_COUPON_TEMPLATE, SUSPEND_COUPON_TEMPLATE, RETIRE_COUPON_TEMPLATE ->
                    changeTemplate(tenantId, command, now);
            case ISSUE_COUPON_ENTITLEMENT -> issueEntitlement(tenantId, operationId, command);
            case COLLECT_COUPON_ENTITLEMENT, RESERVE_COUPON_ENTITLEMENT, REDEEM_COUPON_ENTITLEMENT,
                 RETURN_COUPON_ENTITLEMENT, EXPIRE_COUPON_ENTITLEMENT, VOID_COUPON_ENTITLEMENT ->
                    changeEntitlement(tenantId, operationId, command, now);
            case CREATE_ADVERTISING_PLACEMENT -> createPlacement(tenantId, command);
            case ACTIVATE_ADVERTISING_PLACEMENT, PAUSE_ADVERTISING_PLACEMENT, RETIRE_ADVERTISING_PLACEMENT ->
                    changePlacement(tenantId, command, now);
            case RECORD_IMPRESSION, RECORD_CLICK, RECORD_ATTRIBUTION -> recordInteraction(tenantId, command);
            case RECORD_ADVERTISING_LEDGER_ENTRY -> recordAdvertisingLedgerEntry(tenantId, command);
            case UPSERT_PROMOTION_EXPERIMENT_RESULT -> upsertPromotionExperimentResult(tenantId, command, now);
            case CREATE_GROWTH_EXPERIMENT -> createGrowthExperiment(tenantId, command);
            case START_GROWTH_EXPERIMENT -> startGrowthExperiment(tenantId, command);
            case RECORD_GROWTH_EXPERIMENT_EXPOSURE -> recordGrowthExperimentExposure(tenantId, command);
            case RECORD_GROWTH_EXPERIMENT_METRIC_SNAPSHOT ->
                    recordGrowthExperimentMetricSnapshot(tenantId, command);
            case CONCLUDE_GROWTH_EXPERIMENT -> concludeGrowthExperiment(tenantId, command);
            case CANCEL_GROWTH_EXPERIMENT -> cancelGrowthExperiment(tenantId, command);
        };
        appendEvent(tenantId, command, outcome);
        PromotionCommandResult result = outcome.result();
        result.setOperationId(operationId);
        require(operationMapper.markSucceeded(operationId, tenantId, outcome.aggregateType(), outcome.aggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "promotion operation completion conflict");
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionAggregateView get(String aggregateType, String aggregateId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(aggregateId, "aggregateId", 64);
        return switch (normalized(aggregateType)) {
            case "PROMOTION_CAMPAIGN" -> campaignView(requireNonNull(
                    campaignMapper.selectOneById(tenantId, aggregateId), "campaign not found"));
            case "PROMOTION_COUPON_TEMPLATE" -> templateView(requireNonNull(
                    templateMapper.selectOneById(tenantId, aggregateId), "coupon template not found"));
            case "PROMOTION_COUPON_ENTITLEMENT" -> entitlementView(requireNonNull(
                    entitlementMapper.selectOneById(tenantId, aggregateId), "coupon entitlement not found"));
            case "PROMOTION_ADVERTISING_PLACEMENT" -> placementView(requireNonNull(
                    placementMapper.selectOneById(tenantId, aggregateId), "advertising placement not found"));
            case "PROMOTION_ADVERTISING_INTERACTION" -> interactionView(requireNonNull(
                    interactionMapper.selectById(tenantId, aggregateId), "advertising interaction not found"));
            case "PROMOTION_ADVERTISING_LEDGER_ENTRY" -> advertisingLedgerView(requireNonNull(
                    advertisingLedgerEntryMapper.selectById(tenantId, aggregateId), "advertising ledger entry not found"));
            case "PROMOTION_EXPERIMENT_RESULT" -> experimentResultView(requireNonNull(
                    promotionExperimentResultMapper.selectById(tenantId, aggregateId), "promotion experiment result not found"));
            case "PROMOTION_GROWTH_EXPERIMENT" -> getGrowthExperimentView(tenantId, aggregateId);
            default -> throw new IllegalArgumentException("unsupported aggregateType");
        };
    }

    private PromotionAggregateView getGrowthExperimentView(Long tenantId, String experimentId) {
        GrowthExperimentDO experiment = requireNonNull(
                growthExperimentMapper.selectById(tenantId, experimentId), "growth experiment not found");
        return growthExperimentView(experiment,
                growthExperimentVariantMapper.selectByExperiment(tenantId, experimentId),
                growthExperimentMetricSnapshotMapper.selectLatestByVariant(
                        tenantId, experimentId, experiment.getPrimaryMetricCode()), tenantId);
    }

    private Outcome createCampaign(Long tenantId, PromotionCommand command) {
        PromotionCommand.CampaignDefinition input = requireNonNull(command.getCampaign(), "campaign is required");
        requireText(input.getCampaignCode(), "campaignCode", 64);
        requireText(input.getName(), "campaign name", 128);
        String kind = normalized(input.getCampaignKind());
        require(Set.of("ACTIVITY", "COUPON", "ADVERTISING", "GENERAL").contains(kind), "invalid campaignKind");
        requireInterval(input.getStartsAt(), input.getEndsAt(), "campaign");
        require(campaignMapper.selectByCode(tenantId, input.getCampaignCode()) == null,
                "campaignCode already exists");
        String id = valueOrUuid(input.getCampaignId());
        LocalDateTime now = at(command.getOccurredAt());
        PromotionCampaignDO row = new PromotionCampaignDO().setCampaignId(id).setTenantId(tenantId)
                .setCampaignCode(input.getCampaignCode()).setCampaignKind(kind).setName(input.getName())
                .setStatus("DRAFT").setStartsAt(at(input.getStartsAt())).setEndsAt(at(input.getEndsAt()))
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        campaignMapper.insert(row);
        Map<String, Object> payload = campaignPayload(row, null, "DRAFT", command.getOperation());
        return outcome("promotion.campaign.state_changed", "promotion_campaign", id, 1L, "DRAFT", payload);
    }

    private Outcome changeCampaign(Long tenantId, PromotionCommand command, LocalDateTime now) {
        PromotionCommand.CampaignDefinition input = requireNonNull(command.getCampaign(), "campaign is required");
        requireText(input.getCampaignId(), "campaignId", 64);
        PromotionCampaignDO row = requireNonNull(campaignMapper.selectForUpdate(tenantId, input.getCampaignId()),
                "campaign not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = campaignTransition(row.getStatus(), command.getOperation());
        require(campaignMapper.updateStatusCas(tenantId, row.getCampaignId(), row.getVersion(), next, now) == 1,
                "campaign version conflict");
        return outcome("promotion.campaign.state_changed", "promotion_campaign", row.getCampaignId(),
                row.getVersion() + 1, next, campaignPayload(row, row.getStatus(), next, command.getOperation()));
    }

    /** 编辑活动业务字段：仅 DRAFT/PAUSED 可改 name/campaignKind/起止时间（campaignCode 业务键不可改，status 不变） */
    private Outcome updateCampaign(Long tenantId, PromotionCommand command, LocalDateTime now) {
        PromotionCommand.CampaignDefinition input = requireNonNull(command.getCampaign(), "campaign is required");
        requireText(input.getCampaignId(), "campaignId", 64);
        requireText(input.getName(), "campaign name", 128);
        String kind = normalized(input.getCampaignKind());
        require(Set.of("ACTIVITY", "COUPON", "ADVERTISING", "GENERAL").contains(kind), "invalid campaignKind");
        requireInterval(input.getStartsAt(), input.getEndsAt(), "campaign");
        PromotionCampaignDO row = requireNonNull(campaignMapper.selectForUpdate(tenantId, input.getCampaignId()),
                "campaign not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        require(Set.of("DRAFT", "PAUSED").contains(row.getStatus()), "campaign is not editable");
        require(campaignMapper.updateFieldsCas(tenantId, row.getCampaignId(), row.getVersion(),
                input.getName(), kind, at(input.getStartsAt()), at(input.getEndsAt()), now) == 1,
                "campaign version conflict");
        return outcome("promotion.campaign.fields_updated", "promotion_campaign", row.getCampaignId(),
                row.getVersion() + 1, row.getStatus(),
                campaignPayload(row, row.getStatus(), row.getStatus(), command.getOperation()));
    }

    private Outcome createTemplate(Long tenantId, PromotionCommand command) {
        PromotionCommand.CouponTemplateDefinition input = requireNonNull(command.getCouponTemplate(),
                "couponTemplate is required");
        validateTemplate(input);
        require(templateMapper.selectByCode(tenantId, input.getTemplateCode()) == null,
                "templateCode already exists");
        if (input.getCampaignId() != null) requireCampaign(tenantId, input.getCampaignId());
        String id = valueOrUuid(input.getTemplateId());
        LocalDateTime now = at(command.getOccurredAt());
        CouponTemplateDO row = new CouponTemplateDO().setTemplateId(id).setTenantId(tenantId)
                .setTemplateCode(input.getTemplateCode()).setCampaignId(input.getCampaignId()).setTitle(input.getTitle())
                .setBenefitType(normalized(input.getBenefitType())).setFaceAmountMinor(input.getFaceAmountMinor())
                .setThresholdMinor(input.getThresholdMinor()).setDiscountBasisPoints(input.getDiscountBasisPoints())
                .setCapAmountMinor(input.getCapAmountMinor()).setCurrencyCode(currency(input.getCurrencyCode()))
                .setFunderType(normalized(input.getFunderType())).setMerchantId(input.getMerchantId())
                .setStatus("DRAFT").setValidFrom(at(input.getValidFrom())).setValidTo(at(input.getValidTo()))
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        templateMapper.insert(row);
        return outcome("promotion.coupon_template.state_changed", "promotion_coupon_template", id, 1L,
                "DRAFT", templatePayload(row, null, "DRAFT", command.getOperation()));
    }

    private Outcome changeTemplate(Long tenantId, PromotionCommand command, LocalDateTime now) {
        PromotionCommand.CouponTemplateDefinition input = requireNonNull(command.getCouponTemplate(),
                "couponTemplate is required");
        requireText(input.getTemplateId(), "templateId", 64);
        CouponTemplateDO row = requireNonNull(templateMapper.selectForUpdate(tenantId, input.getTemplateId()),
                "coupon template not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = templateTransition(row.getStatus(), command.getOperation());
        if ("ACTIVE".equals(next) && row.getCampaignId() != null) {
            require("ACTIVE".equals(requireCampaign(tenantId, row.getCampaignId()).getStatus()),
                    "campaign must be ACTIVE");
        }
        require(templateMapper.updateStatusCas(tenantId, row.getTemplateId(), row.getVersion(), next, now) == 1,
                "coupon template version conflict");
        return outcome("promotion.coupon_template.state_changed", "promotion_coupon_template", row.getTemplateId(),
                row.getVersion() + 1, next, templatePayload(row, row.getStatus(), next, command.getOperation()));
    }

    private Outcome issueEntitlement(Long tenantId, Long operationId, PromotionCommand command) {
        PromotionCommand.CouponEntitlementDefinition input = requireNonNull(command.getCouponEntitlement(),
                "couponEntitlement is required");
        requireText(input.getEntitlementCode(), "entitlementCode", 64);
        requireText(input.getTemplateId(), "templateId", 64);
        requireText(input.getPrincipalId(), "principalId", 64);
        require(entitlementMapper.selectByCode(tenantId, input.getEntitlementCode()) == null,
                "entitlementCode already exists");
        CouponTemplateDO template = requireNonNull(templateMapper.selectForUpdate(tenantId, input.getTemplateId()),
                "coupon template not found");
        require("ACTIVE".equals(template.getStatus()), "coupon template must be ACTIVE");
        requireWithin(command.getOccurredAt(), template.getValidFrom(), template.getValidTo(), "coupon template");
        String id = valueOrUuid(input.getEntitlementId());
        LocalDateTime now = at(command.getOccurredAt());
        CouponEntitlementDO row = new CouponEntitlementDO().setEntitlementId(id).setTenantId(tenantId)
                .setEntitlementCode(input.getEntitlementCode()).setTemplateId(template.getTemplateId())
                .setCampaignId(template.getCampaignId()).setPrincipalId(input.getPrincipalId())
                .setFaceAmountMinor(template.getFaceAmountMinor()).setThresholdMinor(template.getThresholdMinor())
                .setCurrencyCode(template.getCurrencyCode()).setStatus("ISSUED").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        entitlementMapper.insert(row);
        String ledgerId = appendLedger(tenantId, operationId, command, row, null, "ISSUED", input.getReason());
        return outcome("promotion.coupon_entitlement.state_changed", "promotion_coupon_entitlement", id, 1L,
                "ISSUED", entitlementPayload(row, null, "ISSUED", command.getOperation(), ledgerId, input.getReason()));
    }

    private Outcome changeEntitlement(Long tenantId, Long operationId, PromotionCommand command, LocalDateTime now) {
        PromotionCommand.CouponEntitlementDefinition input = requireNonNull(command.getCouponEntitlement(),
                "couponEntitlement is required");
        requireText(input.getEntitlementId(), "entitlementId", 64);
        CouponEntitlementDO row = requireNonNull(entitlementMapper.selectForUpdate(tenantId, input.getEntitlementId()),
                "coupon entitlement not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = entitlementTransition(row, input, command.getOperation());
        String orderRef = input.getOrderRef() != null ? input.getOrderRef() : row.getOrderRef();
        require(entitlementMapper.updateStatusCas(tenantId, row.getEntitlementId(), row.getVersion(), next,
                orderRef, now) == 1, "coupon entitlement version conflict");
        row.setOrderRef(orderRef);
        String ledgerId = appendLedger(tenantId, operationId, command, row, row.getStatus(), next, input.getReason());
        return outcome("promotion.coupon_entitlement.state_changed", "promotion_coupon_entitlement",
                row.getEntitlementId(), row.getVersion() + 1, next,
                entitlementPayload(row, row.getStatus(), next, command.getOperation(), ledgerId, input.getReason()));
    }

    private Outcome createPlacement(Long tenantId, PromotionCommand command) {
        PromotionCommand.AdvertisingPlacementDefinition input = requireNonNull(command.getAdvertisingPlacement(),
                "advertisingPlacement is required");
        requireText(input.getPlacementCode(), "placementCode", 64);
        requireText(input.getCampaignId(), "campaignId", 64);
        requireText(input.getName(), "placement name", 128);
        requireText(input.getChannelCode(), "channelCode", 32);
        requireText(input.getPageCode(), "pageCode", 64);
        requireText(input.getSlotCode(), "slotCode", 64);
        requireText(input.getCreativeRef(), "creativeRef", 128);
        requireInterval(input.getValidFrom(), input.getValidTo(), "advertising placement");
        requireCampaign(tenantId, input.getCampaignId());
        require(placementMapper.selectByCode(tenantId, input.getPlacementCode()) == null,
                "placementCode already exists");
        String id = valueOrUuid(input.getPlacementId());
        LocalDateTime now = at(command.getOccurredAt());
        AdvertisingPlacementDO row = new AdvertisingPlacementDO().setPlacementId(id).setTenantId(tenantId)
                .setPlacementCode(input.getPlacementCode()).setCampaignId(input.getCampaignId()).setName(input.getName())
                .setChannelCode(input.getChannelCode()).setPageCode(input.getPageCode()).setSlotCode(input.getSlotCode())
                .setCreativeRef(input.getCreativeRef()).setStatus("DRAFT").setValidFrom(at(input.getValidFrom()))
                .setValidTo(at(input.getValidTo())).setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        placementMapper.insert(row);
        return outcome("promotion.advertising_placement.state_changed", "promotion_advertising_placement",
                id, 1L, "DRAFT", placementPayload(row, null, "DRAFT", command.getOperation()));
    }

    private Outcome changePlacement(Long tenantId, PromotionCommand command, LocalDateTime now) {
        PromotionCommand.AdvertisingPlacementDefinition input = requireNonNull(command.getAdvertisingPlacement(),
                "advertisingPlacement is required");
        requireText(input.getPlacementId(), "placementId", 64);
        AdvertisingPlacementDO row = requireNonNull(placementMapper.selectForUpdate(tenantId, input.getPlacementId()),
                "advertising placement not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = placementTransition(row.getStatus(), command.getOperation());
        if ("ACTIVE".equals(next)) {
            require("ACTIVE".equals(requireCampaign(tenantId, row.getCampaignId()).getStatus()),
                    "campaign must be ACTIVE");
        }
        require(placementMapper.updateStatusCas(tenantId, row.getPlacementId(), row.getVersion(), next, now) == 1,
                "advertising placement version conflict");
        return outcome("promotion.advertising_placement.state_changed", "promotion_advertising_placement",
                row.getPlacementId(), row.getVersion() + 1, next,
                placementPayload(row, row.getStatus(), next, command.getOperation()));
    }

    private Outcome recordInteraction(Long tenantId, PromotionCommand command) {
        PromotionCommand.AdvertisingInteractionDefinition input = requireNonNull(command.getAdvertisingInteraction(),
                "advertisingInteraction is required");
        requireText(input.getDeduplicationKey(), "deduplicationKey", 128);
        requireText(input.getPlacementId(), "placementId", 64);
        require(interactionMapper.selectByDeduplicationKey(tenantId, input.getDeduplicationKey()) == null,
                "deduplicationKey already exists");
        AdvertisingPlacementDO placement = requireNonNull(placementMapper.selectForUpdate(tenantId, input.getPlacementId()),
                "advertising placement not found");
        require("ACTIVE".equals(placement.getStatus()), "advertising placement must be ACTIVE");
        requireWithin(command.getOccurredAt(), placement.getValidFrom(), placement.getValidTo(), "advertising placement");
        PromotionCampaignDO campaign = requireCampaign(tenantId, placement.getCampaignId());
        require("ACTIVE".equals(campaign.getStatus()), "campaign must be ACTIVE");
        requireWithin(command.getOccurredAt(), campaign.getStartsAt(), campaign.getEndsAt(), "campaign");
        String type = interactionType(command.getOperation());
        validateLineage(tenantId, type, input);
        if ("ATTRIBUTION".equals(type)) {
            require(input.getAttributionAmountMinor() != null && input.getAttributionAmountMinor() >= 0,
                    "attributionAmountMinor must be non-negative");
            require("CNY".equals(currency(input.getCurrencyCode())), "attribution currency must be CNY");
            requireText(input.getOrderRef(), "orderRef", 128);
            OrderAttributionView order = orderQueryApi.requireAttributedOrder(input.getOrderRef());
            if (input.getPrincipalId() != null) {
                require(Objects.equals(input.getPrincipalId(), order.getBuyerId()),
                        "attribution principalId does not match canonical order buyer");
            }
        } else {
            require(input.getAttributionAmountMinor() == null && input.getCurrencyCode() == null,
                    "amount and currency are only valid for attribution");
        }
        String id = valueOrUuid(input.getInteractionId());
        AdvertisingInteractionDO row = new AdvertisingInteractionDO().setInteractionId(id).setTenantId(tenantId)
                .setDeduplicationKey(input.getDeduplicationKey()).setInteractionType(type)
                .setPlacementId(placement.getPlacementId()).setCampaignId(placement.getCampaignId())
                .setPrincipalId(input.getPrincipalId()).setSessionId(input.getSessionId())
                .setSourceInteractionId(input.getSourceInteractionId()).setOrderRef(input.getOrderRef())
                .setAttributionAmountMinor(input.getAttributionAmountMinor())
                .setCurrencyCode(input.getCurrencyCode() == null ? null : currency(input.getCurrencyCode()))
                .setOccurredAt(at(command.getOccurredAt())).setCreatedAt(at(command.getOccurredAt()));
        interactionMapper.insert(row);
        Map<String, Object> payload = interactionPayload(row);
        return outcome("promotion.advertising_interaction.recorded", "promotion_advertising_interaction",
                id, 1L, "RECORDED", payload);
    }

    private Outcome recordAdvertisingLedgerEntry(Long tenantId, PromotionCommand command) {
        PromotionCommand.AdvertisingLedgerDefinition input = requireNonNull(command.getAdvertisingLedger(),
                "advertisingLedger is required");
        requireText(input.getLedgerEntryCode(), "ledgerEntryCode", 128);
        requireText(input.getCampaignId(), "campaignId", 64);
        requireText(input.getMerchantId(), "merchantId", 64);
        requireText(input.getChargeModel(), "chargeModel", 32);
        require(input.getAmountMinor() != null && input.getAmountMinor() > 0,
                "amountMinor must be positive");
        require(advertisingLedgerEntryMapper.selectByCode(tenantId, input.getLedgerEntryCode()) == null,
                "ledgerEntryCode already exists");
        PromotionCampaignDO campaign = requireCampaign(tenantId, input.getCampaignId());
        require("ACTIVE".equals(campaign.getStatus()), "campaign must be ACTIVE");
        requireWithin(command.getOccurredAt(), campaign.getStartsAt(), campaign.getEndsAt(), "campaign");
        String entryType = advertisingLedgerEntryType(input.getEntryType());
        String chargeModel = chargeModel(input.getChargeModel());
        String revenueType = null;
        String placementId = null;
        if (input.getPlacementId() != null) {
            AdvertisingPlacementDO placement = requireNonNull(
                    placementMapper.selectForUpdate(tenantId, input.getPlacementId()), "advertising placement not found");
            require(Objects.equals(input.getCampaignId(), placement.getCampaignId()),
                    "advertising ledger placement must belong to campaign");
            placementId = placement.getPlacementId();
        }
        String sourceInteractionId = null;
        AdvertisingInteractionDO sourceInteraction = null;
        if (input.getSourceInteractionId() != null) {
            sourceInteraction = requireNonNull(
                    interactionMapper.selectById(tenantId, input.getSourceInteractionId()), "source interaction not found");
            require(Objects.equals(input.getCampaignId(), sourceInteraction.getCampaignId()),
                    "advertising ledger source interaction campaign mismatch");
            if (placementId != null) {
                require(Objects.equals(placementId, sourceInteraction.getPlacementId()),
                        "advertising ledger source interaction placement mismatch");
            }
            sourceInteractionId = sourceInteraction.getInteractionId();
        }
        String orderRef = null;
        if ("REVENUE".equals(entryType)) {
            revenueType = revenueType(input.getRevenueType());
            if (sourceInteraction != null) {
                require("ATTRIBUTION".equals(sourceInteraction.getInteractionType()),
                        "REVENUE ledger entry must reference an ATTRIBUTION interaction");
                require("ADVERTISING".equals(revenueType),
                        "interaction-linked revenue must use ADVERTISING revenueType");
            }
            if (requiresOrderBoundRevenue(revenueType)) {
                requireText(input.getOrderRef(), "orderRef", 128);
            }
            if (input.getOrderRef() != null) {
                OrderAttributionView order = orderQueryApi.requireAttributedOrder(input.getOrderRef());
                require(Objects.equals(input.getMerchantId(), order.getMerchantId()),
                        "REVENUE ledger merchantId does not match canonical order merchant");
                if (sourceInteraction != null && sourceInteraction.getOrderRef() != null) {
                    require(Objects.equals(sourceInteraction.getOrderRef(), input.getOrderRef()),
                            "REVENUE ledger orderRef does not match attribution interaction");
                }
                orderRef = input.getOrderRef();
            }
        } else {
            require(input.getOrderRef() == null, "SPEND ledger entry must not bind orderRef");
            require(input.getRevenueType() == null, "SPEND ledger entry must not set revenueType");
        }
        String id = valueOrUuid(input.getLedgerEntryId());
        AdvertisingLedgerEntryDO row = new AdvertisingLedgerEntryDO()
                .setLedgerEntryId(id)
                .setTenantId(tenantId)
                .setLedgerEntryCode(input.getLedgerEntryCode())
                .setCampaignId(input.getCampaignId())
                .setPlacementId(placementId)
                .setMerchantId(input.getMerchantId())
                .setEntryType(entryType)
                .setChargeModel(chargeModel)
                .setRevenueType(revenueType)
                .setSourceInteractionId(sourceInteractionId)
                .setOrderRef(orderRef)
                .setAmountMinor(input.getAmountMinor())
                .setCurrencyCode("CNY")
                .setOccurredAt(at(command.getOccurredAt()))
                .setCreatedAt(at(command.getOccurredAt()));
        advertisingLedgerEntryMapper.insert(row);
        return outcome("promotion.advertising_ledger.recorded", "promotion_advertising_ledger_entry",
                id, 1L, "RECORDED", advertisingLedgerPayload(row));
    }

    private Outcome upsertPromotionExperimentResult(Long tenantId, PromotionCommand command, LocalDateTime now) {
        PromotionCommand.PromotionExperimentResultDefinition input = requireNonNull(
                command.getPromotionExperimentResult(), "promotionExperimentResult is required");
        requireText(input.getExperimentCode(), "experimentCode", 128);
        requireText(input.getCampaignId(), "campaignId", 64);
        requireText(input.getMerchantId(), "merchantId", 64);
        requireInterval(input.getMeasuredFrom(), input.getMeasuredTo(), "promotion experiment result");
        require(input.getBaselineContributionProfitMinor() != null && input.getBaselineContributionProfitMinor() >= 0,
                "baselineContributionProfitMinor must be non-negative");
        require(input.getTreatmentContributionProfitMinor() != null && input.getTreatmentContributionProfitMinor() >= 0,
                "treatmentContributionProfitMinor must be non-negative");
        require(input.getIncrementalContributionProfitMinor() != null,
                "incrementalContributionProfitMinor is required");
        require(Objects.equals(input.getIncrementalContributionProfitMinor(),
                        input.getTreatmentContributionProfitMinor() - input.getBaselineContributionProfitMinor()),
                "incrementalContributionProfitMinor must equal treatment minus normalized baseline");
        require(input.getPromotionCostMinor() != null && input.getPromotionCostMinor() > 0,
                "promotionCostMinor must be positive");
        require("CNY".equals(currency(input.getCurrencyCode())), "promotion experiment currency must be CNY");
        requirePositivePopulation(input.getEligiblePopulationCount(), "eligiblePopulationCount");
        requirePositivePopulation(input.getTreatmentPopulationCount(), "treatmentPopulationCount");
        requirePositivePopulation(input.getControlPopulationCount(), "controlPopulationCount");
        require(input.getTreatmentPopulationCount() + input.getControlPopulationCount()
                        <= input.getEligiblePopulationCount(),
                "treatment and control populations cannot exceed eligiblePopulationCount");
        requireText(input.getMethodologyRef(), "methodologyRef", 256);
        PromotionCampaignDO campaign = requireCampaign(tenantId, input.getCampaignId());
        require(Set.of("ACTIVE", "PAUSED", "COMPLETED").contains(campaign.getStatus()),
                "promotion experiment campaign is not measurable");
        require(at(input.getMeasuredFrom()) != null && !at(input.getMeasuredFrom()).isBefore(campaign.getStartsAt()),
                "promotion experiment must not start before campaign starts");
        require(!input.getMeasuredTo().isAfter(command.getOccurredAt()),
                "promotion experiment must not include future observations");
        String experimentId = valueOrUuid(input.getExperimentId());
        PromotionExperimentResultDO byCode = promotionExperimentResultMapper.selectByCode(tenantId, input.getExperimentCode());
        PromotionExperimentResultDO existing = promotionExperimentResultMapper.selectById(tenantId, experimentId);
        if (existing == null) {
            require(byCode == null,
                    "experimentCode already exists");
            PromotionExperimentResultDO row = experimentResultRow(tenantId, experimentId, input, now, 1L);
            promotionExperimentResultMapper.insert(row);
            return outcome("promotion.experiment_result.upserted", "promotion_experiment_result", experimentId, 1L,
                    "MEASURED", experimentResultPayload(row));
        }
        require(byCode == null || Objects.equals(byCode.getExperimentId(), existing.getExperimentId()),
                "experimentCode already exists");
        requireVersion(existing.getVersion(), input.getExpectedVersion());
        PromotionExperimentResultDO row = experimentResultRow(tenantId, experimentId, input, existing.getCreatedAt(),
                existing.getVersion());
        require(promotionExperimentResultMapper.updateCas(tenantId, experimentId, existing.getVersion(), row, now) == 1,
                "promotion experiment result version conflict");
        row.setVersion(existing.getVersion() + 1).setCreatedAt(existing.getCreatedAt()).setUpdatedAt(now);
        return outcome("promotion.experiment_result.upserted", "promotion_experiment_result", experimentId,
                existing.getVersion() + 1, "MEASURED", experimentResultPayload(row));
    }

    private Outcome createGrowthExperiment(Long tenantId, PromotionCommand command) {
        PromotionCommand.GrowthExperimentDefinition input = requireNonNull(
                command.getGrowthExperiment(), "growthExperiment is required");
        requireText(input.getExperimentCode(), "experimentCode", 128);
        requireText(input.getCampaignId(), "campaignId", 64);
        requireText(input.getName(), "experiment name", 128);
        requireText(input.getHypothesis(), "hypothesis", 512);
        requireText(input.getPrimaryMetricCode(), "primaryMetricCode", 128);
        require(input.getMinimumSampleSizePerVariant() != null
                        && input.getMinimumSampleSizePerVariant() >= 30,
                "minimumSampleSizePerVariant must be at least 30");
        requireInterval(input.getStartsAt(), input.getEndsAt(), "growth experiment");
        require(!input.getStartsAt().isBefore(command.getOccurredAt()),
                "growth experiment must be created before startsAt");
        require(growthExperimentMapper.selectByCode(tenantId, input.getExperimentCode()) == null,
                "experimentCode already exists");
        PromotionCampaignDO campaign = requireCampaign(tenantId, input.getCampaignId());
        require(Set.of("DRAFT", "ACTIVE").contains(campaign.getStatus()),
                "growth experiment campaign must be DRAFT or ACTIVE");

        List<PromotionCommand.GrowthExperimentVariantDefinition> variants =
                requireNonNull(input.getVariants(), "growth experiment variants are required");
        require(variants.size() >= 2, "growth experiment requires at least two variants");
        Set<String> codes = new HashSet<>();
        int allocation = 0;
        int controls = 0;
        for (PromotionCommand.GrowthExperimentVariantDefinition variant : variants) {
            requireText(variant.getVariantCode(), "variantCode", 64);
            String code = normalized(variant.getVariantCode());
            require(codes.add(code), "variantCode must be unique");
            String kind = normalized(variant.getVariantKind());
            require(Set.of("CONTROL", "TREATMENT").contains(kind), "invalid variantKind");
            controls += "CONTROL".equals(kind) ? 1 : 0;
            require(variant.getAllocationBasisPoints() != null
                            && variant.getAllocationBasisPoints() > 0
                            && variant.getAllocationBasisPoints() < 10000,
                    "allocationBasisPoints must be between 1 and 9999");
            allocation += variant.getAllocationBasisPoints();
        }
        require(controls == 1, "growth experiment requires exactly one CONTROL variant");
        require(allocation == 10000, "variant allocationBasisPoints must total 10000");

        String experimentId = valueOrUuid(input.getExperimentId());
        LocalDateTime now = at(command.getOccurredAt());
        GrowthExperimentDO row = new GrowthExperimentDO()
                .setExperimentId(experimentId).setTenantId(tenantId)
                .setExperimentCode(input.getExperimentCode()).setCampaignId(input.getCampaignId())
                .setName(input.getName()).setHypothesis(input.getHypothesis())
                .setPrimaryMetricCode(normalized(input.getPrimaryMetricCode()))
                .setMinimumSampleSizePerVariant(input.getMinimumSampleSizePerVariant())
                .setStatus("DRAFT").setStartsAt(at(input.getStartsAt())).setEndsAt(at(input.getEndsAt()))
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        growthExperimentMapper.insert(row);
        for (PromotionCommand.GrowthExperimentVariantDefinition variant : variants) {
            growthExperimentVariantMapper.insert(new GrowthExperimentVariantDO()
                    .setVariantId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setExperimentId(experimentId).setVariantCode(normalized(variant.getVariantCode()))
                    .setVariantKind(normalized(variant.getVariantKind()))
                    .setAllocationBasisPoints(variant.getAllocationBasisPoints()).setCreatedAt(now));
        }
        return outcome("promotion.growth_experiment.created", "promotion_growth_experiment",
                experimentId, 1L, "DRAFT", growthExperimentPayload(row));
    }

    private Outcome startGrowthExperiment(Long tenantId, PromotionCommand command) {
        PromotionCommand.GrowthExperimentDefinition input = requireNonNull(
                command.getGrowthExperiment(), "growthExperiment is required");
        requireText(input.getExperimentId(), "experimentId", 64);
        GrowthExperimentDO row = requireNonNull(
                growthExperimentMapper.selectForUpdate(tenantId, input.getExperimentId()),
                "growth experiment not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        require("DRAFT".equals(row.getStatus()), "growth experiment must be DRAFT");
        requireWithin(command.getOccurredAt(), row.getStartsAt(), row.getEndsAt(), "growth experiment");
        require("ACTIVE".equals(requireCampaign(tenantId, row.getCampaignId()).getStatus()),
                "growth experiment campaign must be ACTIVE");
        LocalDateTime occurredAt = at(command.getOccurredAt());
        require(growthExperimentMapper.startCas(tenantId, row.getExperimentId(), row.getVersion(),
                "RUNNING", occurredAt) == 1, "growth experiment version conflict");
        row.setStatus("RUNNING").setStartedAt(occurredAt).setVersion(row.getVersion() + 1)
                .setUpdatedAt(occurredAt);
        return outcome("promotion.growth_experiment.started", "promotion_growth_experiment",
                row.getExperimentId(), row.getVersion(), row.getStatus(), growthExperimentPayload(row));
    }

    private Outcome recordGrowthExperimentExposure(Long tenantId, PromotionCommand command) {
        PromotionCommand.GrowthExperimentExposureDefinition input = requireNonNull(
                command.getGrowthExperimentExposure(), "growthExperimentExposure is required");
        requireText(input.getExposureKey(), "exposureKey", 128);
        requireText(input.getExperimentId(), "experimentId", 64);
        requireText(input.getVariantCode(), "variantCode", 64);
        requireText(input.getPrincipalId(), "principalId", 256);
        requireText(input.getAssignmentVersion(), "assignmentVersion", 64);
        require(growthExperimentExposureMapper.selectByKey(tenantId, input.getExposureKey()) == null,
                "exposureKey already exists");
        GrowthExperimentDO experiment = requireNonNull(
                growthExperimentMapper.selectById(tenantId, input.getExperimentId()),
                "growth experiment not found");
        require("RUNNING".equals(experiment.getStatus()), "growth experiment must be RUNNING");
        requireWithin(command.getOccurredAt(), experiment.getStartsAt(), experiment.getEndsAt(),
                "growth experiment");
        String variantCode = normalized(input.getVariantCode());
        require(growthExperimentVariantMapper.selectByCode(tenantId, experiment.getExperimentId(),
                variantCode) != null, "growth experiment variant not found");
        LocalDateTime occurredAt = at(command.getOccurredAt());
        GrowthExperimentExposureDO row = new GrowthExperimentExposureDO()
                .setExposureId(valueOrUuid(input.getExposureId())).setTenantId(tenantId)
                .setExposureKey(input.getExposureKey()).setExperimentId(experiment.getExperimentId())
                .setVariantCode(variantCode)
                .setPrincipalHash(DigestUtil.sha256Hex(tenantId + ":" + input.getPrincipalId()))
                .setAssignmentVersion(input.getAssignmentVersion()).setExposedAt(occurredAt)
                .setCreatedAt(occurredAt);
        growthExperimentExposureMapper.insert(row);
        Map<String, Object> payload = payload("exposure_id", row.getExposureId());
        put(payload, "exposure_key", row.getExposureKey());
        put(payload, "experiment_id", row.getExperimentId());
        put(payload, "variant_code", row.getVariantCode());
        put(payload, "assignment_version", row.getAssignmentVersion());
        put(payload, "exposed_at", instant(row.getExposedAt()));
        return outcome("promotion.growth_experiment.exposure_recorded",
                "promotion_growth_experiment_exposure", row.getExposureId(), 1L, "RECORDED", payload);
    }

    private Outcome recordGrowthExperimentMetricSnapshot(Long tenantId, PromotionCommand command) {
        PromotionCommand.GrowthExperimentMetricSnapshotDefinition input = requireNonNull(
                command.getGrowthExperimentMetricSnapshot(), "growthExperimentMetricSnapshot is required");
        requireText(input.getSnapshotKey(), "snapshotKey", 128);
        requireText(input.getExperimentId(), "experimentId", 64);
        requireText(input.getVariantCode(), "variantCode", 64);
        requireText(input.getMetricCode(), "metricCode", 128);
        requireText(input.getEvidenceRef(), "evidenceRef", 512);
        require(growthExperimentMetricSnapshotMapper.selectByKey(tenantId, input.getSnapshotKey()) == null,
                "snapshotKey already exists");
        GrowthExperimentDO experiment = requireNonNull(
                growthExperimentMapper.selectById(tenantId, input.getExperimentId()),
                "growth experiment not found");
        require("RUNNING".equals(experiment.getStatus()), "growth experiment must be RUNNING");
        String variantCode = normalized(input.getVariantCode());
        require(growthExperimentVariantMapper.selectByCode(tenantId, experiment.getExperimentId(),
                variantCode) != null, "growth experiment variant not found");
        require(normalized(input.getMetricCode()).equals(experiment.getPrimaryMetricCode()),
                "metricCode must equal the experiment primary metric");
        requireInterval(input.getMeasuredFrom(), input.getMeasuredTo(), "growth experiment metric snapshot");
        require(!input.getMeasuredFrom().isBefore(experiment.getStartsAt().toInstant(ZoneOffset.UTC))
                        && !input.getMeasuredTo().isAfter(command.getOccurredAt()),
                "metric snapshot window must be inside observed experiment time");
        require(input.getDataFreshUntil() != null
                        && !input.getDataFreshUntil().isBefore(command.getOccurredAt()),
                "metric snapshot data is stale");
        int exposureCount = growthExperimentExposureMapper.countByVariant(
                tenantId, experiment.getExperimentId(), variantCode);
        require(input.getSampleCount() != null && input.getSampleCount() > 0
                        && input.getSampleCount() <= exposureCount,
                "sampleCount must be positive and cannot exceed recorded exposures");
        require(input.getMetricValueMicros() != null && input.getMetricValueMicros() >= 0,
                "metricValueMicros must be non-negative");
        LocalDateTime now = at(command.getOccurredAt());
        GrowthExperimentMetricSnapshotDO row = new GrowthExperimentMetricSnapshotDO()
                .setSnapshotId(valueOrUuid(input.getSnapshotId())).setTenantId(tenantId)
                .setSnapshotKey(input.getSnapshotKey()).setExperimentId(experiment.getExperimentId())
                .setVariantCode(variantCode).setMetricCode(experiment.getPrimaryMetricCode())
                .setMeasuredFrom(at(input.getMeasuredFrom())).setMeasuredTo(at(input.getMeasuredTo()))
                .setSampleCount(input.getSampleCount()).setMetricValueMicros(input.getMetricValueMicros())
                .setDataFreshUntil(at(input.getDataFreshUntil())).setEvidenceRef(input.getEvidenceRef())
                .setCreatedAt(now);
        growthExperimentMetricSnapshotMapper.insert(row);
        return outcome("promotion.growth_experiment.metric_snapshot_recorded",
                "promotion_growth_experiment_metric_snapshot", row.getSnapshotId(), 1L,
                "RECORDED", growthExperimentMetricSnapshotPayload(row));
    }

    private Outcome concludeGrowthExperiment(Long tenantId, PromotionCommand command) {
        PromotionCommand.GrowthExperimentConclusionDefinition input = requireNonNull(
                command.getGrowthExperimentConclusion(), "growthExperimentConclusion is required");
        requireText(input.getExperimentId(), "experimentId", 64);
        requireText(input.getEvidenceRef(), "evidenceRef", 512);
        requireText(input.getReason(), "reason", 512);
        String decision = normalized(input.getDecision());
        require(Set.of("CONTROL", "TREATMENT", "NO_WINNER").contains(decision),
                "invalid growth experiment decision");
        require(input.getConfidenceBasisPoints() != null
                        && input.getConfidenceBasisPoints() >= 9500
                        && input.getConfidenceBasisPoints() <= 10000,
                "confidenceBasisPoints must be between 9500 and 10000");
        String guardrailStatus = normalized(input.getGuardrailStatus());
        require("PASSED".equals(guardrailStatus), "all experiment guardrails must be PASSED");
        GrowthExperimentDO experiment = requireNonNull(
                growthExperimentMapper.selectForUpdate(tenantId, input.getExperimentId()),
                "growth experiment not found");
        requireVersion(experiment.getVersion(), input.getExpectedVersion());
        require("RUNNING".equals(experiment.getStatus()), "growth experiment must be RUNNING");
        require(!command.getOccurredAt().isBefore(experiment.getEndsAt().toInstant(ZoneOffset.UTC)),
                "growth experiment cannot conclude before endsAt");
        List<GrowthExperimentVariantDO> variants =
                growthExperimentVariantMapper.selectByExperiment(tenantId, experiment.getExperimentId());
        List<GrowthExperimentMetricSnapshotDO> snapshots =
                growthExperimentMetricSnapshotMapper.selectLatestByVariant(tenantId,
                        experiment.getExperimentId(), experiment.getPrimaryMetricCode());
        require(snapshots.size() == variants.size(),
                "every experiment variant requires a primary metric snapshot");
        Map<String, GrowthExperimentMetricSnapshotDO> byVariant = new HashMap<>();
        for (GrowthExperimentMetricSnapshotDO snapshot : snapshots) {
            byVariant.put(snapshot.getVariantCode(), snapshot);
        }
        for (GrowthExperimentVariantDO variant : variants) {
            GrowthExperimentMetricSnapshotDO snapshot = byVariant.get(variant.getVariantCode());
            require(snapshot != null, "missing metric snapshot for variant " + variant.getVariantCode());
            require(snapshot.getSampleCount() >= experiment.getMinimumSampleSizePerVariant(),
                    "minimum sample size not reached for variant " + variant.getVariantCode());
            require(!snapshot.getDataFreshUntil().isBefore(at(command.getOccurredAt())),
                    "metric snapshot is stale for variant " + variant.getVariantCode());
            require(growthExperimentExposureMapper.countByVariant(tenantId, experiment.getExperimentId(),
                            variant.getVariantCode()) >= snapshot.getSampleCount(),
                    "metric sample exceeds recorded exposure evidence");
        }
        LocalDateTime occurredAt = at(command.getOccurredAt());
        require(growthExperimentMapper.concludeCas(tenantId, experiment.getExperimentId(),
                experiment.getVersion(), decision, input.getConfidenceBasisPoints(), guardrailStatus,
                input.getEvidenceRef(), input.getReason(), occurredAt) == 1,
                "growth experiment version conflict");
        experiment.setStatus("CONCLUDED").setConcludedAt(occurredAt).setDecision(decision)
                .setConfidenceBasisPoints(input.getConfidenceBasisPoints())
                .setGuardrailStatus(guardrailStatus).setConclusionEvidenceRef(input.getEvidenceRef())
                .setConclusionReason(input.getReason()).setVersion(experiment.getVersion() + 1)
                .setUpdatedAt(occurredAt);
        return outcome("promotion.growth_experiment.concluded", "promotion_growth_experiment",
                experiment.getExperimentId(), experiment.getVersion(), experiment.getStatus(),
                growthExperimentPayload(experiment));
    }

    private Outcome cancelGrowthExperiment(Long tenantId, PromotionCommand command) {
        PromotionCommand.GrowthExperimentDefinition input = requireNonNull(
                command.getGrowthExperiment(), "growthExperiment is required");
        requireText(input.getExperimentId(), "experimentId", 64);
        requireText(input.getReason(), "reason", 512);
        GrowthExperimentDO experiment = requireNonNull(
                growthExperimentMapper.selectForUpdate(tenantId, input.getExperimentId()),
                "growth experiment not found");
        requireVersion(experiment.getVersion(), input.getExpectedVersion());
        require(Set.of("DRAFT", "RUNNING").contains(experiment.getStatus()),
                "growth experiment must be DRAFT or RUNNING");
        LocalDateTime occurredAt = at(command.getOccurredAt());
        require(growthExperimentMapper.cancelCas(tenantId, experiment.getExperimentId(),
                experiment.getVersion(), input.getReason(), occurredAt) == 1,
                "growth experiment version conflict");
        experiment.setStatus("CANCELLED").setConcludedAt(occurredAt)
                .setConclusionReason(input.getReason()).setVersion(experiment.getVersion() + 1)
                .setUpdatedAt(occurredAt);
        return outcome("promotion.growth_experiment.cancelled", "promotion_growth_experiment",
                experiment.getExperimentId(), experiment.getVersion(), experiment.getStatus(),
                growthExperimentPayload(experiment));
    }

    private void validateLineage(Long tenantId, String type,
                                 PromotionCommand.AdvertisingInteractionDefinition input) {
        if ("IMPRESSION".equals(type)) {
            require(input.getSourceInteractionId() == null, "impression must not have sourceInteractionId");
            return;
        }
        requireText(input.getSourceInteractionId(), "sourceInteractionId", 64);
        AdvertisingInteractionDO source = requireNonNull(
                interactionMapper.selectById(tenantId, input.getSourceInteractionId()), "source interaction not found");
        String requiredType = "CLICK".equals(type) ? "IMPRESSION" : "CLICK";
        require(requiredType.equals(source.getInteractionType()), type + " must reference a " + requiredType);
        require(Objects.equals(input.getPlacementId(), source.getPlacementId()), "interaction lineage placement mismatch");
        if (input.getSessionId() != null && source.getSessionId() != null) {
            require(Objects.equals(input.getSessionId(), source.getSessionId()), "interaction lineage session mismatch");
        }
        if (input.getPrincipalId() != null && source.getPrincipalId() != null) {
            require(Objects.equals(input.getPrincipalId(), source.getPrincipalId()), "interaction lineage principal mismatch");
        }
    }

    private String appendLedger(Long tenantId, Long operationId, PromotionCommand command, CouponEntitlementDO row,
                                String previous, String current, String reason) {
        String ledgerId = UUID.randomUUID().toString();
        ledgerMapper.insert(new CouponEntitlementLedgerDO().setLedgerEntryId(ledgerId).setTenantId(tenantId)
                .setEntitlementId(row.getEntitlementId()).setEntitlementVersion(row.getVersion() + (previous == null ? 0 : 1))
                .setOperationId(operationId).setOperationType(command.getOperation().name()).setPreviousStatus(previous)
                .setCurrentStatus(current).setOrderRef(row.getOrderRef()).setReason(reason)
                .setFaceAmountMinor(row.getFaceAmountMinor()).setThresholdMinor(row.getThresholdMinor())
                .setCurrencyCode(row.getCurrencyCode()).setOccurredAt(at(command.getOccurredAt()))
                .setCreatedAt(at(command.getOccurredAt())));
        return ledgerId;
    }

    private void appendEvent(Long tenantId, PromotionCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(outcome.eventType()).schemaVersion(1)
                .sourceSystem("cloudmold-promotion").tenantId(tenantId).aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId()).aggregateVersion(outcome.aggregateVersion()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(command.getIdempotencyKey() + ":event")
                .payload(outcome.payload()).headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse").build());
    }

    static String fingerprint(Long tenantId, PromotionCommand command) {
        return DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
    }

    private static void validateEnvelope(PromotionCommand command) {
        require(command != null && command.getOperation() != null, "operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void validateTemplate(PromotionCommand.CouponTemplateDefinition input) {
        requireText(input.getTemplateCode(), "templateCode", 64);
        requireText(input.getTitle(), "template title", 128);
        requireInterval(input.getValidFrom(), input.getValidTo(), "coupon template");
        String benefit = normalized(input.getBenefitType());
        require(Set.of("FIXED_AMOUNT", "PERCENTAGE").contains(benefit), "invalid benefitType");
        require(input.getThresholdMinor() != null && input.getThresholdMinor() >= 0,
                "thresholdMinor must be non-negative");
        currency(input.getCurrencyCode());
        if ("FIXED_AMOUNT".equals(benefit)) {
            require(input.getFaceAmountMinor() != null && input.getFaceAmountMinor() > 0,
                    "faceAmountMinor must be positive");
            require(input.getDiscountBasisPoints() == null, "discountBasisPoints is not valid for fixed amount");
        } else {
            require(input.getDiscountBasisPoints() != null && input.getDiscountBasisPoints() > 0
                    && input.getDiscountBasisPoints() < 10000, "discountBasisPoints must be between 1 and 9999");
            require(input.getFaceAmountMinor() == null, "faceAmountMinor is not valid for percentage benefit");
            require(input.getCapAmountMinor() == null || input.getCapAmountMinor() > 0,
                    "capAmountMinor must be positive");
        }
        String funder = normalized(input.getFunderType());
        require(Set.of("PLATFORM", "MERCHANT", "SHARED").contains(funder), "invalid funderType");
        if (!"PLATFORM".equals(funder)) requireText(input.getMerchantId(), "merchantId", 64);
    }

    private PromotionCampaignDO requireCampaign(Long tenantId, String id) {
        return requireNonNull(campaignMapper.selectForUpdate(tenantId, id), "campaign not found");
    }

    private static String campaignTransition(String status, PromotionOperation op) {
        return switch (op) {
            case ACTIVATE_CAMPAIGN -> transition(status, Set.of("DRAFT", "PAUSED"), "ACTIVE", "campaign");
            case PAUSE_CAMPAIGN -> transition(status, Set.of("ACTIVE"), "PAUSED", "campaign");
            case COMPLETE_CAMPAIGN -> transition(status, Set.of("ACTIVE", "PAUSED"), "COMPLETED", "campaign");
            case CANCEL_CAMPAIGN -> transition(status, Set.of("DRAFT", "ACTIVE", "PAUSED"), "CANCELLED", "campaign");
            default -> throw new IllegalArgumentException("invalid campaign operation");
        };
    }

    private static String templateTransition(String status, PromotionOperation op) {
        return switch (op) {
            case ACTIVATE_COUPON_TEMPLATE -> transition(status, Set.of("DRAFT", "SUSPENDED"), "ACTIVE", "coupon template");
            case SUSPEND_COUPON_TEMPLATE -> transition(status, Set.of("ACTIVE"), "SUSPENDED", "coupon template");
            case RETIRE_COUPON_TEMPLATE -> transition(status, Set.of("DRAFT", "ACTIVE", "SUSPENDED"), "RETIRED", "coupon template");
            default -> throw new IllegalArgumentException("invalid coupon template operation");
        };
    }

    private static String entitlementTransition(CouponEntitlementDO row,
                                                PromotionCommand.CouponEntitlementDefinition input,
                                                PromotionOperation op) {
        return switch (op) {
            case COLLECT_COUPON_ENTITLEMENT -> transition(row.getStatus(), Set.of("ISSUED"), "AVAILABLE", "coupon entitlement");
            case RESERVE_COUPON_ENTITLEMENT -> {
                requireText(input.getOrderRef(), "orderRef", 128);
                yield transition(row.getStatus(), Set.of("AVAILABLE"), "RESERVED", "coupon entitlement");
            }
            case REDEEM_COUPON_ENTITLEMENT -> {
                requireText(input.getOrderRef(), "orderRef", 128);
                require(Objects.equals(row.getOrderRef(), input.getOrderRef()), "orderRef does not match reservation");
                yield transition(row.getStatus(), Set.of("RESERVED"), "USED", "coupon entitlement");
            }
            case RETURN_COUPON_ENTITLEMENT -> {
                if (input.getOrderRef() != null) require(Objects.equals(row.getOrderRef(), input.getOrderRef()),
                        "orderRef does not match entitlement");
                requireText(input.getReason(), "reason", 256);
                yield transition(row.getStatus(), Set.of("RESERVED", "USED"), "RETURNED", "coupon entitlement");
            }
            case EXPIRE_COUPON_ENTITLEMENT -> transition(row.getStatus(), Set.of("ISSUED", "AVAILABLE"),
                    "EXPIRED", "coupon entitlement");
            case VOID_COUPON_ENTITLEMENT -> {
                requireText(input.getReason(), "reason", 256);
                yield transition(row.getStatus(), Set.of("ISSUED", "AVAILABLE"), "VOIDED", "coupon entitlement");
            }
            default -> throw new IllegalArgumentException("invalid coupon entitlement operation");
        };
    }

    private static String placementTransition(String status, PromotionOperation op) {
        return switch (op) {
            case ACTIVATE_ADVERTISING_PLACEMENT -> transition(status, Set.of("DRAFT", "PAUSED"), "ACTIVE", "advertising placement");
            case PAUSE_ADVERTISING_PLACEMENT -> transition(status, Set.of("ACTIVE"), "PAUSED", "advertising placement");
            case RETIRE_ADVERTISING_PLACEMENT -> transition(status, Set.of("DRAFT", "ACTIVE", "PAUSED"), "RETIRED", "advertising placement");
            default -> throw new IllegalArgumentException("invalid advertising placement operation");
        };
    }

    private static String transition(String current, Set<String> allowed, String next, String aggregate) {
        require(allowed.contains(current), "illegal " + aggregate + " transition from " + current + " to " + next);
        return next;
    }

    private static String interactionType(PromotionOperation operation) {
        return switch (operation) {
            case RECORD_IMPRESSION -> "IMPRESSION";
            case RECORD_CLICK -> "CLICK";
            case RECORD_ATTRIBUTION -> "ATTRIBUTION";
            default -> throw new IllegalArgumentException("invalid advertising interaction operation");
        };
    }

    private static String advertisingLedgerEntryType(String value) {
        String normalized = normalized(value);
        require(Set.of("SPEND", "REVENUE").contains(normalized), "invalid advertising ledger entryType");
        return normalized;
    }

    private static String chargeModel(String value) {
        String normalized = normalized(value);
        require(Set.of("CPC", "CPM", "CPA", "FIXED", "REV_SHARE", "SETTLEMENT").contains(normalized),
                "invalid chargeModel");
        return normalized;
    }

    private static String revenueType(String value) {
        String normalized = normalized(value);
        require(Set.of("ADVERTISING", "COMMISSION", "FULFILLMENT_SERVICE", "PAYMENT_SERVICE",
                "OTHER_PLATFORM_REVENUE").contains(normalized), "invalid revenueType");
        return normalized;
    }

    private static boolean requiresOrderBoundRevenue(String revenueType) {
        return Set.of("COMMISSION", "FULFILLMENT_SERVICE", "PAYMENT_SERVICE").contains(revenueType);
    }

    private static Map<String, Object> campaignPayload(PromotionCampaignDO row, String previous, String current,
                                                       PromotionOperation operation) {
        Map<String, Object> p = payload("campaign_id", row.getCampaignId());
        put(p, "campaign_code", row.getCampaignCode()); put(p, "campaign_kind", row.getCampaignKind());
        put(p, "name", row.getName()); p.put("previous_status", previous); put(p, "current_status", current);
        put(p, "starts_at", instant(row.getStartsAt())); put(p, "ends_at", instant(row.getEndsAt()));
        put(p, "operation", operation.name()); return p;
    }

    private static Map<String, Object> templatePayload(CouponTemplateDO row, String previous, String current,
                                                       PromotionOperation operation) {
        Map<String, Object> p = payload("template_id", row.getTemplateId());
        put(p, "template_code", row.getTemplateCode()); put(p, "campaign_id", row.getCampaignId());
        put(p, "title", row.getTitle()); put(p, "benefit_type", row.getBenefitType());
        put(p, "face_amount_minor", row.getFaceAmountMinor()); put(p, "threshold_minor", row.getThresholdMinor());
        put(p, "discount_basis_points", row.getDiscountBasisPoints()); put(p, "cap_amount_minor", row.getCapAmountMinor());
        put(p, "currency_code", row.getCurrencyCode()); put(p, "funder_type", row.getFunderType());
        put(p, "merchant_id", row.getMerchantId()); put(p, "valid_from", instant(row.getValidFrom()));
        put(p, "valid_to", instant(row.getValidTo())); p.put("previous_status", previous);
        put(p, "current_status", current); put(p, "operation", operation.name()); return p;
    }

    private static Map<String, Object> entitlementPayload(CouponEntitlementDO row, String previous, String current,
                                                          PromotionOperation operation, String ledgerId, String reason) {
        Map<String, Object> p = payload("entitlement_id", row.getEntitlementId());
        put(p, "entitlement_code", row.getEntitlementCode()); put(p, "template_id", row.getTemplateId());
        put(p, "campaign_id", row.getCampaignId()); put(p, "principal_id", row.getPrincipalId());
        put(p, "order_ref", row.getOrderRef()); put(p, "face_amount_minor", row.getFaceAmountMinor());
        put(p, "threshold_minor", row.getThresholdMinor()); put(p, "currency_code", row.getCurrencyCode());
        p.put("previous_status", previous); put(p, "current_status", current); put(p, "operation", operation.name());
        put(p, "ledger_entry_id", ledgerId); put(p, "reason", reason); return p;
    }

    private static Map<String, Object> placementPayload(AdvertisingPlacementDO row, String previous, String current,
                                                        PromotionOperation operation) {
        Map<String, Object> p = payload("placement_id", row.getPlacementId());
        put(p, "placement_code", row.getPlacementCode()); put(p, "campaign_id", row.getCampaignId());
        put(p, "name", row.getName()); put(p, "channel_code", row.getChannelCode());
        put(p, "page_code", row.getPageCode()); put(p, "slot_code", row.getSlotCode());
        put(p, "creative_ref", row.getCreativeRef()); put(p, "valid_from", instant(row.getValidFrom()));
        put(p, "valid_to", instant(row.getValidTo())); p.put("previous_status", previous);
        put(p, "current_status", current); put(p, "operation", operation.name()); return p;
    }

    private static Map<String, Object> interactionPayload(AdvertisingInteractionDO row) {
        Map<String, Object> p = payload("interaction_id", row.getInteractionId());
        put(p, "interaction_type", row.getInteractionType()); put(p, "deduplication_key", row.getDeduplicationKey());
        put(p, "placement_id", row.getPlacementId()); put(p, "campaign_id", row.getCampaignId());
        put(p, "principal_id", row.getPrincipalId()); put(p, "session_id", row.getSessionId());
        put(p, "source_interaction_id", row.getSourceInteractionId()); put(p, "order_ref", row.getOrderRef());
        put(p, "attribution_amount_minor", row.getAttributionAmountMinor()); put(p, "currency_code", row.getCurrencyCode());
        put(p, "occurred_at", instant(row.getOccurredAt())); return p;
    }

    private static Map<String, Object> advertisingLedgerPayload(AdvertisingLedgerEntryDO row) {
        Map<String, Object> p = payload("ledger_entry_id", row.getLedgerEntryId());
        put(p, "ledger_entry_code", row.getLedgerEntryCode());
        put(p, "campaign_id", row.getCampaignId());
        put(p, "placement_id", row.getPlacementId());
        put(p, "merchant_id", row.getMerchantId());
        put(p, "entry_type", row.getEntryType());
        put(p, "charge_model", row.getChargeModel());
        put(p, "revenue_type", row.getRevenueType());
        put(p, "source_interaction_id", row.getSourceInteractionId());
        put(p, "order_ref", row.getOrderRef());
        put(p, "amount_minor", row.getAmountMinor());
        put(p, "currency_code", row.getCurrencyCode());
        put(p, "occurred_at", instant(row.getOccurredAt()));
        return p;
    }

    private static PromotionExperimentResultDO experimentResultRow(Long tenantId, String experimentId,
                                                                   PromotionCommand.PromotionExperimentResultDefinition input,
                                                                   LocalDateTime now, Long version) {
        return new PromotionExperimentResultDO()
                .setExperimentId(experimentId)
                .setTenantId(tenantId)
                .setExperimentCode(input.getExperimentCode())
                .setCampaignId(input.getCampaignId())
                .setMerchantId(input.getMerchantId())
                .setMeasuredFrom(at(input.getMeasuredFrom()))
                .setMeasuredTo(at(input.getMeasuredTo()))
                .setBaselineContributionProfitMinor(input.getBaselineContributionProfitMinor())
                .setTreatmentContributionProfitMinor(input.getTreatmentContributionProfitMinor())
                .setIncrementalContributionProfitMinor(input.getIncrementalContributionProfitMinor())
                .setPromotionCostMinor(input.getPromotionCostMinor())
                .setEligiblePopulationCount(input.getEligiblePopulationCount())
                .setTreatmentPopulationCount(input.getTreatmentPopulationCount())
                .setControlPopulationCount(input.getControlPopulationCount())
                .setCurrencyCode("CNY")
                .setMethodologyRef(input.getMethodologyRef())
                .setVersion(version)
                .setCreatedAt(now)
                .setUpdatedAt(now);
    }

    private static Map<String, Object> experimentResultPayload(PromotionExperimentResultDO row) {
        Map<String, Object> p = payload("experiment_id", row.getExperimentId());
        put(p, "experiment_code", row.getExperimentCode());
        put(p, "campaign_id", row.getCampaignId());
        put(p, "merchant_id", row.getMerchantId());
        put(p, "measured_from", instant(row.getMeasuredFrom()));
        put(p, "measured_to", instant(row.getMeasuredTo()));
        put(p, "baseline_contribution_profit_minor", row.getBaselineContributionProfitMinor());
        put(p, "treatment_contribution_profit_minor", row.getTreatmentContributionProfitMinor());
        put(p, "incremental_contribution_profit_minor", row.getIncrementalContributionProfitMinor());
        put(p, "promotion_cost_minor", row.getPromotionCostMinor());
        put(p, "eligible_population_count", row.getEligiblePopulationCount());
        put(p, "treatment_population_count", row.getTreatmentPopulationCount());
        put(p, "control_population_count", row.getControlPopulationCount());
        put(p, "currency_code", row.getCurrencyCode());
        put(p, "methodology_ref", row.getMethodologyRef());
        return p;
    }

    private static Map<String, Object> growthExperimentPayload(GrowthExperimentDO row) {
        Map<String, Object> p = payload("experiment_id", row.getExperimentId());
        put(p, "experiment_code", row.getExperimentCode());
        put(p, "campaign_id", row.getCampaignId());
        put(p, "name", row.getName());
        put(p, "hypothesis", row.getHypothesis());
        put(p, "primary_metric_code", row.getPrimaryMetricCode());
        put(p, "minimum_sample_size_per_variant", row.getMinimumSampleSizePerVariant());
        put(p, "status", row.getStatus());
        put(p, "starts_at", instant(row.getStartsAt()));
        put(p, "ends_at", instant(row.getEndsAt()));
        put(p, "started_at", instant(row.getStartedAt()));
        put(p, "concluded_at", instant(row.getConcludedAt()));
        put(p, "decision", row.getDecision());
        put(p, "confidence_basis_points", row.getConfidenceBasisPoints());
        put(p, "guardrail_status", row.getGuardrailStatus());
        put(p, "conclusion_evidence_ref", row.getConclusionEvidenceRef());
        put(p, "conclusion_reason", row.getConclusionReason());
        return p;
    }

    private static Map<String, Object> growthExperimentMetricSnapshotPayload(
            GrowthExperimentMetricSnapshotDO row) {
        Map<String, Object> p = payload("snapshot_id", row.getSnapshotId());
        put(p, "snapshot_key", row.getSnapshotKey());
        put(p, "experiment_id", row.getExperimentId());
        put(p, "variant_code", row.getVariantCode());
        put(p, "metric_code", row.getMetricCode());
        put(p, "measured_from", instant(row.getMeasuredFrom()));
        put(p, "measured_to", instant(row.getMeasuredTo()));
        put(p, "sample_count", row.getSampleCount());
        put(p, "metric_value_micros", row.getMetricValueMicros());
        put(p, "data_fresh_until", instant(row.getDataFreshUntil()));
        put(p, "evidence_ref", row.getEvidenceRef());
        return p;
    }

    private static PromotionAggregateView campaignView(PromotionCampaignDO row) {
        Map<String, Object> a = payload("campaign_kind", row.getCampaignKind()); put(a, "name", row.getName());
        put(a, "starts_at", instant(row.getStartsAt())); put(a, "ends_at", instant(row.getEndsAt()));
        return view("promotion_campaign", row.getCampaignId(), row.getCampaignCode(), row.getStatus(), row.getVersion(), a);
    }

    private static PromotionAggregateView templateView(CouponTemplateDO row) {
        Map<String, Object> a = templatePayload(row, null, row.getStatus(), PromotionOperation.CREATE_COUPON_TEMPLATE);
        a.remove("template_id"); a.remove("template_code"); a.remove("previous_status"); a.remove("current_status"); a.remove("operation");
        return view("promotion_coupon_template", row.getTemplateId(), row.getTemplateCode(), row.getStatus(), row.getVersion(), a);
    }

    private static PromotionAggregateView entitlementView(CouponEntitlementDO row) {
        Map<String, Object> a = entitlementPayload(row, null, row.getStatus(), PromotionOperation.ISSUE_COUPON_ENTITLEMENT, null, null);
        a.remove("entitlement_id"); a.remove("entitlement_code"); a.remove("previous_status"); a.remove("current_status"); a.remove("operation");
        return view("promotion_coupon_entitlement", row.getEntitlementId(), row.getEntitlementCode(), row.getStatus(), row.getVersion(), a);
    }

    private static PromotionAggregateView placementView(AdvertisingPlacementDO row) {
        Map<String, Object> a = placementPayload(row, null, row.getStatus(), PromotionOperation.CREATE_ADVERTISING_PLACEMENT);
        a.remove("placement_id"); a.remove("placement_code"); a.remove("previous_status"); a.remove("current_status"); a.remove("operation");
        return view("promotion_advertising_placement", row.getPlacementId(), row.getPlacementCode(), row.getStatus(), row.getVersion(), a);
    }

    private static PromotionAggregateView interactionView(AdvertisingInteractionDO row) {
        Map<String, Object> a = interactionPayload(row); a.remove("interaction_id");
        return view("promotion_advertising_interaction", row.getInteractionId(), row.getDeduplicationKey(), "RECORDED", 1L, a);
    }

    private static PromotionAggregateView advertisingLedgerView(AdvertisingLedgerEntryDO row) {
        Map<String, Object> a = advertisingLedgerPayload(row);
        a.remove("ledger_entry_id");
        return view("promotion_advertising_ledger_entry", row.getLedgerEntryId(), row.getLedgerEntryCode(),
                "RECORDED", 1L, a);
    }

    private static PromotionAggregateView experimentResultView(PromotionExperimentResultDO row) {
        Map<String, Object> a = experimentResultPayload(row);
        a.remove("experiment_id");
        a.remove("experiment_code");
        return view("promotion_experiment_result", row.getExperimentId(), row.getExperimentCode(),
                "MEASURED", row.getVersion(), a);
    }

    private PromotionAggregateView growthExperimentView(
            GrowthExperimentDO row, List<GrowthExperimentVariantDO> variants,
            List<GrowthExperimentMetricSnapshotDO> snapshots, Long tenantId) {
        Map<String, Object> attributes = growthExperimentPayload(row);
        attributes.remove("experiment_id");
        attributes.remove("experiment_code");
        List<Map<String, Object>> variantFacts = new ArrayList<>();
        for (GrowthExperimentVariantDO variant : variants) {
            Map<String, Object> fact = payload("variant_code", variant.getVariantCode());
            put(fact, "variant_kind", variant.getVariantKind());
            put(fact, "allocation_basis_points", variant.getAllocationBasisPoints());
            put(fact, "exposure_count", growthExperimentExposureMapper.countByVariant(
                    tenantId, row.getExperimentId(), variant.getVariantCode()));
            snapshots.stream().filter(snapshot ->
                            Objects.equals(snapshot.getVariantCode(), variant.getVariantCode()))
                    .findFirst().ifPresent(snapshot -> {
                        put(fact, "latest_sample_count", snapshot.getSampleCount());
                        put(fact, "latest_metric_value_micros", snapshot.getMetricValueMicros());
                        put(fact, "latest_measured_to", instant(snapshot.getMeasuredTo()));
                        put(fact, "data_fresh_until", instant(snapshot.getDataFreshUntil()));
                        put(fact, "metric_evidence_ref", snapshot.getEvidenceRef());
                    });
            variantFacts.add(fact);
        }
        attributes.put("variants", variantFacts);
        return view("promotion_growth_experiment", row.getExperimentId(), row.getExperimentCode(),
                row.getStatus(), row.getVersion(), attributes);
    }

    private static PromotionAggregateView view(String type, String id, String code, String status, Long version,
                                               Map<String, Object> attributes) {
        return PromotionAggregateView.builder().aggregateType(type).aggregateId(id).businessCode(code)
                .status(status).version(version).attributes(attributes).build();
    }

    private static Outcome outcome(String eventType, String aggregateType, String aggregateId, Long version,
                                   String status, Map<String, Object> payload) {
        return new Outcome(eventType, aggregateType, aggregateId, version, payload,
                PromotionCommandResult.builder().aggregateType(aggregateType).aggregateId(aggregateId)
                        .aggregateVersion(version).status(status).duplicate(false).build());
    }

    private static void requireVersion(Long actual, Long expected) {
        require(expected != null, "expectedVersion is required");
        require(Objects.equals(actual, expected), "aggregate version conflict");
    }

    private static void requireInterval(Instant from, Instant to, String name) {
        require(from != null && to != null && from.isBefore(to), name + " validity interval is invalid");
    }

    private static void requirePositivePopulation(Integer value, String name) {
        require(value != null && value > 0, name + " must be positive");
    }

    private static void requireWithin(Instant occurredAt, LocalDateTime from, LocalDateTime to, String name) {
        LocalDateTime at = at(occurredAt);
        require((from == null || !at.isBefore(from)) && (to == null || at.isBefore(to)),
                name + " is not effective at occurredAt");
    }

    private static String currency(String value) {
        require(value != null && value.matches("[A-Za-z]{3}"), "currencyCode must be ISO-4217 alpha-3");
        return value.toUpperCase(Locale.ROOT);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static LocalDateTime at(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static Map<String, Object> payload(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>(); put(result, key, value); return result;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private static void requireText(String value, String name, int maxLength) {
        require(value != null && !value.isBlank(), name + " is required");
        require(value.trim().length() <= maxLength, name + " is too long");
    }

    private static void requireUuid(String value, String name) {
        requireText(value, name, 36);
        try { UUID.fromString(value); } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(name + " must be a UUID");
        }
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message); return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId, Long aggregateVersion,
                           Map<String, Object> payload, PromotionCommandResult result) {
    }
}
