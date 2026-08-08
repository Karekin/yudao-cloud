package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommand;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommandPort;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommandResult;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractAutomationCommandApi;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractOperation;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractQueryApi;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractSourceView;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractValidationApi;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractView;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.SalesContract;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.SalesContractItem;
import cn.iocoder.yudao.module.cloudmold.crm.dal.dataobject.contract.SalesContractRecords.StatusHistory;
import cn.iocoder.yudao.module.cloudmold.crm.dal.mysql.contract.SalesContractMapper;
import cn.iocoder.yudao.module.cloudmold.crm.service.CrmQueryService;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantReferenceValidationCommand;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantReferenceView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Service
@RequiredArgsConstructor
public class SalesContractService implements SalesContractAutomationCommandApi, SalesContractCommandPort,
        SalesContractQueryApi, SalesContractValidationApi {

    static final int OPERATION_SUCCEEDED = 10;

    private static final String SOURCE_SYSTEM = "cloudmold-crm";
    private static final String DESTINATION = "lakehouse";
    private static final String AGGREGATE_TYPE = "sales_contract";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String BPM_PROCESS_DEFINITION_KEY = "cloudmold_sales_contract_approval";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern DOMAIN_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    private final SalesContractMapper mapper;
    private final OutboxAppender outboxAppender;
    private final SalesContractActorPrincipalPort actorPrincipalPort;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final MerchantReferenceValidationApi merchantReferenceValidationApi;
    private final BpmProcessInstanceApi bpmProcessInstanceApi;
    private final CrmQueryService crmQueryService;
    private final SalesContractBpmModelRegistrar bpmModelRegistrar;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesContractCommandResult execute(SalesContractCommand command) {
        Long loginUserId = getLoginUserId();
        return execute(command, actorPrincipalPort.resolveSystemAdmin(loginUserId), loginUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesContractCommandResult execute(
            SalesContractCommand command, String actorPrincipalId, Long actorAdminUserId) {
        validateEnvelope(command, actorPrincipalId, actorAdminUserId);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + actorAdminUserId + "\n"
                + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve sales contract operation");
        Operation operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "sales contract operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different sales contract payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing sales contract operation is incomplete");
            SalesContractCommandResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), SalesContractCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        SalesContractCommandResult result = switch (command.getOperation()) {
            case CREATE_DRAFT -> createDraft(tenantId, operationId, command, actorPrincipalId, actorAdminUserId, now);
            case UPDATE_DRAFT -> updateDraft(tenantId, operationId, command, actorPrincipalId, actorAdminUserId, now);
            case SUBMIT_APPROVAL -> submitApproval(
                    tenantId, operationId, command, actorPrincipalId, actorAdminUserId, now);
        };
        require(mapper.markOperationSucceeded(tenantId, operationId, AGGREGATE_TYPE,
                        result.getSalesContractId(), JsonUtils.toJsonString(result), now) == 1,
                "sales contract operation completion conflict");
        return result;
    }

    @Override
    public SalesContractView get(String salesContractId) {
        requireRef(salesContractId, "salesContractId", 128);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        SalesContract header = mapper.selectSalesContract(tenantId, salesContractId.trim());
        require(header != null, "sales contract does not exist");
        return toView(header, mapper.selectItems(tenantId, header.getSalesContractId()));
    }

    @Override
    public List<SalesContractView> list() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return mapper.selectSalesContracts(tenantId).stream()
                .map(contract -> toView(contract, mapper.selectItems(tenantId, contract.getSalesContractId())))
                .toList();
    }

    @Override
    public SalesContractSourceView requireReceivableSource(String customerId, String salesContractId) {
        requireRef(customerId, "customerId", 128);
        SalesContractView contract = get(salesContractId);
        require(customerId.trim().equals(contract.getCustomerId()), "sales contract customer mismatch");
        require(STATUS_ACTIVE.equals(contract.getStatus()),
                "sales contract must be active for Finance");
        require(contract.getTotalAmountMinor() != null && contract.getTotalAmountMinor() > 0,
                "sales contract amount must be positive");
        requireCurrency(contract.getCurrencyCode(), "currencyCode");
        return SalesContractSourceView.builder()
                .salesContractId(contract.getSalesContractId())
                .customerId(contract.getCustomerId())
                .currencyCode(contract.getCurrencyCode())
                .totalAmountMinor(contract.getTotalAmountMinor())
                .status(contract.getStatus())
                .build();
    }

    private SalesContractCommandResult createDraft(Long tenantId, Long operationId, SalesContractCommand command,
                                                   String actorPrincipalId, Long actorAdminUserId,
                                                   LocalDateTime now) {
        ValidatedDraft draft = validateDraft(command);
        validateCustomerExists(draft.customerId());
        MerchantReferenceView seller = validateSeller(draft.sellerMerchantId(), draft.sellerShopId());
        String salesContractId = valueOrUuid(command.getSalesContractId());
        Long totalAmountMinor = draft.items().stream().mapToLong(SalesContractItem::getLineAmountMinor).sum();
        List<SalesContractItem> items = withHeaderMetadata(draft.items(), tenantId, salesContractId, now);
        SalesContract header = new SalesContract()
                .setSalesContractId(salesContractId)
                .setTenantId(tenantId)
                .setContractCode(draft.contractCode())
                .setContractName(draft.contractName())
                .setCustomerId(draft.customerId())
                .setSellerMerchantId(draft.sellerMerchantId())
                .setSellerShopId(draft.sellerShopId())
                .setSellerLegalEntityId(seller.getLegalEntityId())
                .setStatus(STATUS_DRAFT)
                .setCurrencyCode(draft.currencyCode())
                .setTotalAmountMinor(totalAmountMinor)
                .setEffectiveDate(draft.effectiveDate())
                .setExpiresOn(draft.expiresOn())
                .setApprovalProcessInstanceId(null)
                .setCreatedByPrincipalId(actorPrincipalId)
                .setUpdatedByPrincipalId(actorPrincipalId)
                .setSubmittedByPrincipalId(null)
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setSubmittedAt(null);
        require(mapper.insertSalesContract(header) == 1, "failed to persist sales contract draft");
        require(mapper.insertItems(items) == draft.items().size(),
                "failed to persist sales contract items");
        insertStatusHistory(tenantId, salesContractId, operationId, 1L, STATUS_DRAFT,
                actorPrincipalId, actorAdminUserId, draft.reasonCode(), null, now);
        appendEvent("crm.sales_contract.status_changed", 1, tenantId, command, header, items, actorAdminUserId);
        return buildResult(operationId, header);
    }

    private SalesContractCommandResult updateDraft(Long tenantId, Long operationId, SalesContractCommand command,
                                                   String actorPrincipalId, Long actorAdminUserId,
                                                   LocalDateTime now) {
        ValidatedDraft draft = validateDraft(command);
        requirePositiveVersion(command.getExpectedVersion(), "expectedVersion");
        String salesContractId = requireRef(command.getSalesContractId(), "salesContractId", 128);
        SalesContract existing = mapper.selectSalesContractForUpdate(tenantId, salesContractId);
        require(existing != null, "sales contract does not exist");
        require(STATUS_DRAFT.equals(existing.getStatus()), "sales contract is not in draft status");
        require(existing.getVersion().equals(command.getExpectedVersion()), "sales contract optimistic lock conflict");
        validateCustomerExists(draft.customerId());
        MerchantReferenceView seller = validateSeller(draft.sellerMerchantId(), draft.sellerShopId());
        Long nextVersion = existing.getVersion() + 1;
        Long totalAmountMinor = draft.items().stream().mapToLong(SalesContractItem::getLineAmountMinor).sum();
        require(mapper.updateDraft(tenantId, salesContractId, existing.getVersion(), nextVersion,
                        draft.contractCode(), draft.contractName(), draft.customerId(), draft.sellerMerchantId(),
                        draft.sellerShopId(), seller.getLegalEntityId(), draft.currencyCode(), totalAmountMinor,
                        draft.effectiveDate(), draft.expiresOn(), actorPrincipalId, now) == 1,
                "sales contract update conflict");
        mapper.deleteItems(tenantId, salesContractId);
        List<SalesContractItem> items = withHeaderMetadata(draft.items(), tenantId, salesContractId, now);
        require(mapper.insertItems(items) == items.size(), "failed to persist updated sales contract items");
        existing.setContractCode(draft.contractCode())
                .setContractName(draft.contractName())
                .setCustomerId(draft.customerId())
                .setSellerMerchantId(draft.sellerMerchantId())
                .setSellerShopId(draft.sellerShopId())
                .setSellerLegalEntityId(seller.getLegalEntityId())
                .setCurrencyCode(draft.currencyCode())
                .setTotalAmountMinor(totalAmountMinor)
                .setEffectiveDate(draft.effectiveDate())
                .setExpiresOn(draft.expiresOn())
                .setUpdatedByPrincipalId(actorPrincipalId)
                .setVersion(nextVersion)
                .setUpdatedAt(now);
        insertStatusHistory(tenantId, salesContractId, operationId, nextVersion, STATUS_DRAFT,
                actorPrincipalId, actorAdminUserId, draft.reasonCode(), existing.getApprovalProcessInstanceId(), now);
        appendEvent("crm.sales_contract.status_changed", 1, tenantId, command, existing, items, actorAdminUserId);
        return buildResult(operationId, existing);
    }

    private SalesContractCommandResult submitApproval(Long tenantId, Long operationId, SalesContractCommand command,
                                                      String actorPrincipalId, Long actorAdminUserId,
                                                      LocalDateTime now) {
        requirePositiveVersion(command.getExpectedVersion(), "expectedVersion");
        String salesContractId = requireRef(command.getSalesContractId(), "salesContractId", 128);
        SalesContract existing = mapper.selectSalesContractForUpdate(tenantId, salesContractId);
        require(existing != null, "sales contract does not exist");
        require(STATUS_DRAFT.equals(existing.getStatus()), "sales contract is not in draft status");
        require(existing.getVersion().equals(command.getExpectedVersion()), "sales contract optimistic lock conflict");
        List<SalesContractItem> items = mapper.selectItems(tenantId, salesContractId);
        require(items != null && !items.isEmpty(), "sales contract draft requires at least one item");
        List<Long> reviewers = bpmModelRegistrar.registerAndResolveReviewers(actorAdminUserId);
        String processInstanceId = bpmProcessInstanceApi.createProcessInstance(
                actorAdminUserId, buildBpmRequest(existing, items, reviewers)).getCheckedData();
        require(processInstanceId != null && !processInstanceId.isBlank(),
                "BPM returned an empty process instance id");
        Long nextVersion = existing.getVersion() + 1;
        require(mapper.submitApproval(tenantId, salesContractId, existing.getVersion(), nextVersion,
                        STATUS_PENDING_APPROVAL, processInstanceId, actorPrincipalId, now) == 1,
                "sales contract approval submission conflict");
        existing.setStatus(STATUS_PENDING_APPROVAL)
                .setApprovalProcessInstanceId(processInstanceId)
                .setSubmittedByPrincipalId(actorPrincipalId)
                .setVersion(nextVersion)
                .setUpdatedAt(now)
                .setSubmittedAt(now);
        insertStatusHistory(tenantId, salesContractId, operationId, nextVersion, STATUS_PENDING_APPROVAL,
                actorPrincipalId, actorAdminUserId, requireReasonCode(command.getReasonCode()),
                processInstanceId, now);
        appendEvent("crm.sales_contract.status_changed", 1, tenantId, command, existing, items, actorAdminUserId);
        return buildResult(operationId, existing);
    }

    private BpmProcessInstanceCreateReqDTO buildBpmRequest(SalesContract header, List<SalesContractItem> items,
                                                           List<Long> reviewers) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("sales_contract_id", header.getSalesContractId());
        variables.put("contract_code", header.getContractCode());
        variables.put("contract_name", header.getContractName());
        variables.put("customer_id", header.getCustomerId());
        variables.put("seller_merchant_id", header.getSellerMerchantId());
        variables.put("seller_shop_id", header.getSellerShopId());
        variables.put("seller_legal_entity_id", header.getSellerLegalEntityId());
        variables.put("currency_code", header.getCurrencyCode());
        variables.put("total_amount_minor", header.getTotalAmountMinor());
        variables.put("effective_date", header.getEffectiveDate().toString());
        variables.put("expires_on", header.getExpiresOn() == null ? null : header.getExpiresOn().toString());
        variables.put("item_count", items.size());
        return new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(BPM_PROCESS_DEFINITION_KEY)
                .setBusinessKey(header.getSalesContractId())
                .setVariables(variables)
                .setStartUserSelectAssignees(Map.of(
                        SalesContractBpmModelRegistrar.REVIEW_TASK_KEY, reviewers));
    }

    private void validateCustomerExists(String customerId) {
        crmQueryService.requireCustomer(customerId);
    }

    private MerchantReferenceView validateSeller(String sellerMerchantId, String sellerShopId) {
        MerchantReferenceView reference = merchantReferenceValidationApi.requireActiveReference(
                new MerchantReferenceValidationCommand()
                        .setMerchantId(sellerMerchantId)
                        .setShopId(sellerShopId));
        require(reference != null, "seller merchant/shop reference is required");
        require(sellerMerchantId.equals(reference.getMerchantId()), "seller merchant mismatch");
        require(sellerShopId.equals(reference.getShopId()), "seller shop mismatch");
        requireRef(reference.getLegalEntityId(), "sellerLegalEntityId", 128);
        return reference;
    }

    private ValidatedDraft validateDraft(SalesContractCommand command) {
        String contractCode = requireRef(command.getContractCode(), "contractCode", 64);
        String contractName = requireText(command.getContractName(), "contractName", 255);
        String customerId = requireRef(command.getCustomerId(), "customerId", 128);
        String sellerMerchantId = requireRef(command.getSellerMerchantId(), "sellerMerchantId", 128);
        String sellerShopId = requireRef(command.getSellerShopId(), "sellerShopId", 128);
        String currencyCode = requireCurrency(command.getCurrencyCode(), "currencyCode");
        require(command.getEffectiveDate() != null, "effectiveDate is required");
        if (command.getExpiresOn() != null) {
            require(!command.getExpiresOn().isBefore(command.getEffectiveDate()),
                    "expiresOn cannot be before effectiveDate");
        }
        String reasonCode = requireReasonCode(command.getReasonCode());
        require(command.getItems() != null && !command.getItems().isEmpty() && command.getItems().size() <= 200,
                "sales contract must contain between 1 and 200 items");
        Set<String> itemIds = new LinkedHashSet<>();
        List<SalesContractItem> items = new ArrayList<>();
        int lineNo = 1;
        for (SalesContractCommand.Item item : command.getItems()) {
            require(item != null, "sales contract item is required");
            String itemId = valueOrUuid(item.getSalesContractItemId());
            require(itemIds.add(itemId), "duplicate salesContractItemId");
            String canonicalSkuId = requireRef(item.getCanonicalSkuId(), "canonicalSkuId", 128);
            catalogSkuValidationApi.requireActiveSku(canonicalSkuId);
            String itemName = requireText(item.getItemName(), "itemName", 255);
            String uomCode = requireCode(item.getUomCode(), "uomCode");
            BigDecimal quantity = requireQuantity(item.getQuantity(), "quantity");
            Long unitPriceMinor = requirePositiveMinor(item.getUnitPriceMinor(), "unitPriceMinor");
            Long lineAmountMinor = requirePositiveMinor(item.getLineAmountMinor(), "lineAmountMinor");
            BigDecimal computed = quantity.multiply(BigDecimal.valueOf(unitPriceMinor));
            require(computed.setScale(0, RoundingMode.HALF_UP).longValueExact() == lineAmountMinor,
                    "lineAmountMinor must equal quantity * unitPriceMinor with HALF_UP rounding");
            items.add(new SalesContractItem()
                    .setSalesContractItemId(itemId)
                    .setLineNo(lineNo++)
                    .setCanonicalSkuId(canonicalSkuId)
                    .setItemName(itemName)
                    .setUomCode(uomCode)
                    .setQuantity(quantity)
                    .setUnitPriceMinor(unitPriceMinor)
                    .setLineAmountMinor(lineAmountMinor));
        }
        return new ValidatedDraft(contractCode, contractName, customerId, sellerMerchantId, sellerShopId,
                currencyCode, command.getEffectiveDate(), command.getExpiresOn(), reasonCode, items);
    }

    private List<SalesContractItem> withHeaderMetadata(List<SalesContractItem> items, Long tenantId,
                                                       String salesContractId, LocalDateTime now) {
        List<SalesContractItem> values = new ArrayList<>(items.size());
        for (SalesContractItem item : items) {
            values.add(new SalesContractItem()
                    .setSalesContractItemId(item.getSalesContractItemId())
                    .setTenantId(tenantId)
                    .setSalesContractId(salesContractId)
                    .setLineNo(item.getLineNo())
                    .setCanonicalSkuId(item.getCanonicalSkuId())
                    .setItemName(item.getItemName())
                    .setUomCode(item.getUomCode())
                    .setQuantity(item.getQuantity())
                    .setUnitPriceMinor(item.getUnitPriceMinor())
                    .setLineAmountMinor(item.getLineAmountMinor())
                    .setCreatedAt(now)
                    .setUpdatedAt(now));
        }
        return values;
    }

    private void insertStatusHistory(Long tenantId, String salesContractId, Long operationId, Long aggregateVersion,
                                     String status, String actorPrincipalId, Long actorAdminUserId, String reasonCode,
                                     String approvalProcessInstanceId, LocalDateTime now) {
        require(mapper.insertStatusHistory(new StatusHistory()
                        .setTenantId(tenantId)
                        .setSalesContractId(salesContractId)
                        .setOperationId(operationId)
                        .setAggregateVersion(aggregateVersion)
                        .setStatus(status)
                        .setActorPrincipalId(actorPrincipalId)
                        .setActorAdminUserId(actorAdminUserId)
                        .setReasonCode(reasonCode)
                        .setApprovalProcessInstanceId(approvalProcessInstanceId)
                        .setOccurredAt(now)
                        .setCreatedAt(now)) == 1,
                "failed to persist sales contract status history");
    }

    private void appendEvent(String eventType, int schemaVersion, Long tenantId, SalesContractCommand command,
                             SalesContract header, List<SalesContractItem> items, Long actorAdminUserId) {
        List<Map<String, Object>> itemPayloads = items.stream().map(item -> Map.<String, Object>of(
                "sales_contract_item_id", item.getSalesContractItemId(),
                "line_no", item.getLineNo(),
                "canonical_sku_id", item.getCanonicalSkuId(),
                "item_name", item.getItemName(),
                "uom_code", item.getUomCode(),
                "quantity", quantityText(item.getQuantity()),
                "unit_price_minor", item.getUnitPriceMinor(),
                "line_amount_minor", item.getLineAmountMinor())).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sales_contract_id", header.getSalesContractId());
        payload.put("contract_code", header.getContractCode());
        payload.put("contract_name", header.getContractName());
        payload.put("customer_id", header.getCustomerId());
        payload.put("seller_merchant_id", header.getSellerMerchantId());
        payload.put("seller_shop_id", header.getSellerShopId());
        payload.put("seller_legal_entity_id", header.getSellerLegalEntityId());
        payload.put("status", header.getStatus());
        payload.put("currency_code", header.getCurrencyCode());
        payload.put("total_amount_minor", header.getTotalAmountMinor());
        payload.put("effective_date", header.getEffectiveDate().toString());
        payload.put("expires_on", header.getExpiresOn() == null ? null : header.getExpiresOn().toString());
        payload.put("approval_process_instance_id", header.getApprovalProcessInstanceId());
        payload.put("items", itemPayloads);
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .schemaVersion(schemaVersion)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(header.getSalesContractId())
                .aggregateVersion(header.getVersion())
                .eventSequence((short) 1)
                .occurredAt(command.getOccurredAt())
                .traceId(command.getRunId())
                .correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":" + eventType)
                .payload(payload)
                .headers(headers(command.getRunId(), header.getStatus(), actorAdminUserId, header.getApprovalProcessInstanceId()))
                .destination(DESTINATION)
                .build());
    }

    private static Map<String, Object> headers(String runId, String status, Long actorAdminUserId,
                                               String approvalProcessInstanceId) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("run_id", runId);
        headers.put("status", status);
        headers.put("actor_admin_user_id", String.valueOf(actorAdminUserId));
        if (approvalProcessInstanceId != null && !approvalProcessInstanceId.isBlank()) {
            headers.put("approval_process_instance_id", approvalProcessInstanceId);
        }
        return headers;
    }

    private static SalesContractCommandResult buildResult(Long operationId, SalesContract header) {
        return SalesContractCommandResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .salesContractId(header.getSalesContractId())
                .status(header.getStatus())
                .version(header.getVersion())
                .totalAmountMinor(header.getTotalAmountMinor())
                .currencyCode(header.getCurrencyCode())
                .approvalProcessInstanceId(header.getApprovalProcessInstanceId())
                .build();
    }

    private static SalesContractView toView(SalesContract header, List<SalesContractItem> items) {
        List<SalesContractItem> safeItems = items == null ? List.of() : items;
        return SalesContractView.builder()
                .salesContractId(header.getSalesContractId())
                .contractCode(header.getContractCode())
                .contractName(header.getContractName())
                .customerId(header.getCustomerId())
                .sellerMerchantId(header.getSellerMerchantId())
                .sellerShopId(header.getSellerShopId())
                .sellerLegalEntityId(header.getSellerLegalEntityId())
                .status(header.getStatus())
                .currencyCode(header.getCurrencyCode())
                .totalAmountMinor(header.getTotalAmountMinor())
                .effectiveDate(header.getEffectiveDate())
                .expiresOn(header.getExpiresOn())
                .approvalProcessInstanceId(header.getApprovalProcessInstanceId())
                .version(header.getVersion())
                .createdAt(header.getCreatedAt())
                .updatedAt(header.getUpdatedAt())
                .items(safeItems.stream().map(item -> SalesContractView.Item.builder()
                        .salesContractItemId(item.getSalesContractItemId())
                        .canonicalSkuId(item.getCanonicalSkuId())
                        .itemName(item.getItemName())
                        .uomCode(item.getUomCode())
                        .quantity(quantityText(item.getQuantity()))
                        .unitPriceMinor(item.getUnitPriceMinor())
                        .lineAmountMinor(item.getLineAmountMinor())
                        .lineNo(item.getLineNo())
                        .build()).toList())
                .build();
    }

    private static void validateEnvelope(SalesContractCommand command, String actorPrincipalId, Long actorAdminUserId) {
        require(command != null, "sales contract command is required");
        require(command.getOperation() != null, "operation is required");
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        require(actorAdminUserId != null && actorAdminUserId > 0, "actorAdminUserId is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireRef(command.getRunId(), "runId", 128);
        requireRef(command.getCorrelationId(), "correlationId", 128);
        requireRef(command.getCausationId(), "causationId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static String requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.trim().length() <= maxLength,
                field + " is required");
        return value.trim();
    }

    private static String requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
        return value.trim();
    }

    private static String requireCode(String value, String field) {
        require(value != null && SAFE_CODE.matcher(value).matches(),
                field + " must be an uppercase or normalized code");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireReasonCode(String value) {
        require(value != null && DOMAIN_CODE.matcher(value).matches(),
                "reasonCode must be an uppercase code");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireCurrency(String value, String field) {
        require(value != null && CURRENCY_CODE.matcher(value).matches(), field + " must be ISO-4217");
        return value.trim();
    }

    private static BigDecimal requireQuantity(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        try {
            BigDecimal quantity = new BigDecimal(value.trim());
            require(quantity.signum() > 0 && quantity.scale() <= 6 && quantity.precision() <= 24,
                    field + " must be a positive DECIMAL(24,6)");
            return quantity.stripTrailingZeros();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a decimal quantity", exception);
        }
    }

    private static Long requirePositiveMinor(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
        return value;
    }

    private static Long requirePositiveVersion(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
        return value;
    }

    private static Long parsePositiveLong(String value, String field) {
        try {
            long parsed = Long.parseLong(value);
            require(parsed > 0, field + " must be positive");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be numeric in the current CRM customer store", exception);
        }
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static String quantityText(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record ValidatedDraft(
            String contractCode,
            String contractName,
            String customerId,
            String sellerMerchantId,
            String sellerShopId,
            String currencyCode,
            java.time.LocalDate effectiveDate,
            java.time.LocalDate expiresOn,
            String reasonCode,
            List<SalesContractItem> items
    ) {
    }
}
