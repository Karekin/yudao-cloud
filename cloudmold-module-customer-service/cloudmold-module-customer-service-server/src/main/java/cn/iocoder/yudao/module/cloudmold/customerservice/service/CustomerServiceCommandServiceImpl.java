package cn.iocoder.yudao.module.cloudmold.customerservice.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.mysql.CustomerServiceStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerServiceCommandServiceImpl implements CustomerServiceCommandApi, CustomerServiceQueryApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final Set<String> CHANNELS = Set.of("APP", "WEB", "PHONE", "INTERNAL");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> DIRECTIONS = Set.of("INBOUND", "OUTBOUND", "INTERNAL");
    private static final Set<String> SENDER_TYPES = Set.of("CUSTOMER", "AGENT", "SYSTEM");
    private static final Set<String> MESSAGE_TYPES = Set.of("TEXT", "IMAGE", "FILE", "SYSTEM_NOTE");
    private static final Set<String> SCAN_STATUSES = Set.of("PENDING", "CLEAN", "BLOCKED");
    private static final Set<String> FEEDBACK_TOUCHPOINTS = Set.of("TICKET_RESOLUTION", "CLAIM_COMPENSATION",
            "AFTER_SALE_HANDLING");
    private static final Set<String> FEEDBACK_SENTIMENTS = Set.of("SATISFIED", "NEUTRAL", "DISSATISFIED");
    private static final Set<String> CLAIM_TYPES = Set.of("SERVICE_COMPENSATION", "LOGISTICS_DAMAGE", "PRICE_PROTECTION");
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{1,63}");
    private static final Pattern DOMAIN_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern MEDIA_TYPE = Pattern.compile("[a-z0-9][a-z0-9.+-]{0,31}/[a-z0-9][a-z0-9.+-]{0,63}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_TOKEN = Pattern.compile("(?:sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})");

    private final CustomerServiceStoreMapper mapper;
    private final CustomerServiceEventService eventService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerServiceView execute(CustomerServiceCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Instant occurredAt = command.getOccurredAt() == null ? now.toInstant(ZoneOffset.UTC) : command.getOccurredAt();
        require(!occurredAt.isAfter(Instant.now().plusSeconds(300)), "occurredAt cannot be materially in the future");
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve customer-service operation");
        CustomerServiceOperationDO operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "customer-service operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different customer-service payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing customer-service operation is not complete");
            CustomerServiceView replay = JsonUtils.parseObject(operation.getResultJson(), CustomerServiceView.class);
            replay.setDuplicate(true);
            return replay;
        }

        CustomerServiceView result = switch (command.getOperation()) {
            case CREATE_TICKET -> createTicket(tenantId, operationId, command, occurredAt, now);
            case LINK_REFERENCE -> linkReference(tenantId, operationId, command, now);
            case ASSIGN_AGENT -> assignAgent(tenantId, operationId, command, occurredAt, now);
            case START_PROCESSING, RESOLVE_TICKET, CLOSE_TICKET, REOPEN_TICKET ->
                    transitionTicket(tenantId, operationId, command, occurredAt, now);
            case RECORD_MESSAGE -> recordMessage(tenantId, operationId, command, occurredAt, now);
            case RECORD_ATTACHMENT -> recordAttachment(tenantId, operationId, command, occurredAt, now);
            case RECORD_BUYER_FEEDBACK -> recordBuyerFeedback(tenantId, operationId, command, occurredAt, now);
            case RECORD_QUALITY_REVIEW -> recordQualityReview(tenantId, operationId, command, occurredAt, now);
            case REQUEST_CLAIM -> requestClaim(tenantId, operationId, command, occurredAt, now);
            case APPROVE_CLAIM, REJECT_CLAIM, PAY_COMPENSATION ->
                    transitionClaim(tenantId, operationId, command, occurredAt, now);
        };
        String aggregateId = firstNonNull(result.getClaimId(), result.getAttachmentId(), result.getMessageId(),
                result.getFeedbackId(), result.getReviewId(), result.getLinkId(), result.getTicketId());
        require(mapper.markOperationSucceeded(operationId, tenantId, aggregateId, JsonUtils.toJsonString(result), now) == 1,
                "customer-service operation completion conflict");
        return result;
    }

    @Override
    public CustomerServiceView getTicket(String ticketId) {
        requireId(ticketId, "ticketId");
        CustomerServiceTicketDO ticket = mapper.selectTicket(TenantContextHolder.getRequiredTenantId(), ticketId);
        require(ticket != null, "customer-service ticket does not exist");
        return ticketView(null, ticket, false);
    }

    @Override
    public CustomerServiceView getClaim(String claimId) {
        requireId(claimId, "claimId");
        CustomerServiceClaimDO claim = mapper.selectClaim(TenantContextHolder.getRequiredTenantId(), claimId);
        require(claim != null, "customer-service claim does not exist");
        return claimView(null, claim, false);
    }

    private CustomerServiceView createTicket(Long tenantId, Long operationId, CustomerServiceCommand command,
                                             Instant occurredAt, LocalDateTime now) {
        requireCode(command.getTicketNo(), "ticketNo");
        requireId(command.getCustomerPrincipalId(), "customerPrincipalId");
        require(CHANNELS.contains(command.getChannelCode()), "unsupported channelCode");
        require(PRIORITIES.contains(command.getPriority()), "unsupported priority");
        requireDomainCode(command.getCategoryCode(), "categoryCode");
        validateExperienceSnapshot(command, occurredAt);
        String ticketId = UUID.randomUUID().toString();
        CustomerServiceTicketDO ticket = new CustomerServiceTicketDO().setTicketId(ticketId).setTenantId(tenantId)
                .setTicketNo(command.getTicketNo()).setRunId(command.getRunId())
                .setCustomerPrincipalId(command.getCustomerPrincipalId()).setChannelCode(command.getChannelCode())
                .setPriority(command.getPriority()).setCategoryCode(command.getCategoryCode())
                .setSlaPolicyCode(command.getSlaPolicyCode()).setSlaPolicyVersion(command.getSlaPolicyVersion())
                .setResolutionDeadlineAt(asUtcDateTime(command.getResolutionDeadlineAt()))
                .setFcrWindowHours(command.getFcrWindowHours()).setStatus("OPEN").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertTicket(ticket) == 1, "failed to create customer-service ticket");
        eventService.appendTicket(operationId, ticket, null, command, occurredAt, now);
        return ticketView(operationId, ticket, false);
    }

    private CustomerServiceView linkReference(Long tenantId, Long operationId, CustomerServiceCommand command,
                                              LocalDateTime now) {
        CustomerServiceTicketDO ticket = requireMutableTicket(tenantId, command);
        require(!"CLOSED".equals(ticket.getStatus()), "closed ticket cannot accept references");
        requireQualifiedReference(command.getReferenceSourceSystem(), command.getReferenceType(), command.getReferenceId());
        TicketOrderLinkDO link = new TicketOrderLinkDO().setLinkId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setTicketId(ticket.getTicketId()).setReferenceSourceSystem(command.getReferenceSourceSystem())
                .setReferenceType(command.getReferenceType()).setReferenceId(command.getReferenceId()).setCreatedAt(now);
        require(mapper.insertLink(link) == 1, "failed to link qualified ticket reference");
        return ticketView(operationId, ticket, false).setLinkId(link.getLinkId());
    }

    private CustomerServiceView assignAgent(Long tenantId, Long operationId, CustomerServiceCommand command,
                                            Instant occurredAt, LocalDateTime now) {
        CustomerServiceTicketDO ticket = requireMutableTicket(tenantId, command);
        requireId(command.getAssignedAgentPrincipalId(), "assignedAgentPrincipalId");
        String previous = ticket.getStatus();
        require(mapper.assignAgent(tenantId, ticket.getTicketId(), ticket.getVersion(),
                command.getAssignedAgentPrincipalId(), now) == 1, "ticket assignment conflict");
        ticket.setAssignedAgentPrincipalId(command.getAssignedAgentPrincipalId())
                .setVersion(ticket.getVersion() + 1).setUpdatedAt(now);
        eventService.appendTicket(operationId, ticket, previous, command, occurredAt, now);
        return ticketView(operationId, ticket, false);
    }

    private CustomerServiceView transitionTicket(Long tenantId, Long operationId, CustomerServiceCommand command,
                                                 Instant occurredAt, LocalDateTime now) {
        CustomerServiceTicketDO ticket = requireMutableTicket(tenantId, command);
        String before = ticket.getStatus();
        String after = nextTicketStatus(command.getOperation(), before);
        require(mapper.transitionTicket(tenantId, ticket.getTicketId(), ticket.getVersion(), before, after, now) == 1,
                "ticket state transition conflict");
        ticket.setStatus(after).setVersion(ticket.getVersion() + 1).setUpdatedAt(now);
        eventService.appendTicket(operationId, ticket, before, command, occurredAt, now);
        return ticketView(operationId, ticket, false);
    }

    private CustomerServiceView recordMessage(Long tenantId, Long operationId, CustomerServiceCommand command,
                                              Instant occurredAt, LocalDateTime now) {
        CustomerServiceTicketDO ticket = requireMutableTicket(tenantId, command);
        require(!"CLOSED".equals(ticket.getStatus()), "closed ticket cannot accept messages");
        require(DIRECTIONS.contains(command.getDirection()), "unsupported message direction");
        require(SENDER_TYPES.contains(command.getSenderType()), "unsupported senderType");
        if (!"SYSTEM".equals(command.getSenderType())) requireId(command.getSenderPrincipalId(), "senderPrincipalId");
        require(MESSAGE_TYPES.contains(command.getMessageType()), "unsupported messageType");
        requireSafeToken(command.getContentToken(), "contentToken");
        CustomerServiceMessageDO message = new CustomerServiceMessageDO().setMessageId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setTicketId(ticket.getTicketId()).setRunId(command.getRunId())
                .setDirection(command.getDirection()).setSenderType(command.getSenderType())
                .setSenderPrincipalId(command.getSenderPrincipalId()).setMessageType(command.getMessageType())
                .setContentToken(command.getContentToken()).setAttachmentCount(0)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertMessage(message) == 1, "failed to record customer-service message");
        eventService.appendMessage(message, command, occurredAt);
        return ticketView(operationId, ticket, false).setMessageId(message.getMessageId());
    }

    private CustomerServiceView recordAttachment(Long tenantId, Long operationId, CustomerServiceCommand command,
                                                 Instant occurredAt, LocalDateTime now) {
        CustomerServiceTicketDO ticket = requireMutableTicket(tenantId, command);
        require(!"CLOSED".equals(ticket.getStatus()), "closed ticket cannot accept attachments");
        requireId(command.getMessageId(), "messageId");
        CustomerServiceMessageDO message = mapper.selectMessageForUpdate(tenantId, command.getMessageId());
        require(message != null && ticket.getTicketId().equals(message.getTicketId()),
                "message does not belong to the tenant-scoped ticket");
        require(command.getMediaType() != null && MEDIA_TYPE.matcher(command.getMediaType()).matches(),
                "mediaType must be a normalized MIME type");
        requireSafeToken(command.getObjectToken(), "objectToken");
        require(command.getContentSha256() != null && SHA256.matcher(command.getContentSha256()).matches(),
                "contentSha256 must be lowercase SHA-256");
        require(command.getSizeBytes() != null && command.getSizeBytes() >= 0 && command.getSizeBytes() <= 20_000_000,
                "sizeBytes must be between 0 and 20000000");
        require(SCAN_STATUSES.contains(command.getMalwareScanStatus()), "unsupported malwareScanStatus");
        CustomerServiceAttachmentDO attachment = new CustomerServiceAttachmentDO()
                .setAttachmentId(UUID.randomUUID().toString()).setTenantId(tenantId).setTicketId(ticket.getTicketId())
                .setMessageId(message.getMessageId()).setRunId(command.getRunId()).setMediaType(command.getMediaType())
                .setObjectToken(command.getObjectToken()).setContentSha256(command.getContentSha256())
                .setSizeBytes(command.getSizeBytes()).setMalwareScanStatus(command.getMalwareScanStatus())
                .setCreatedAt(now);
        require(mapper.insertAttachment(attachment) == 1, "failed to record attachment metadata");
        require(mapper.incrementAttachmentCount(tenantId, message.getMessageId()) == 1,
                "failed to update message attachment count");
        eventService.appendAttachment(attachment, command, occurredAt);
        return ticketView(operationId, ticket, false).setMessageId(message.getMessageId())
                .setAttachmentId(attachment.getAttachmentId());
    }

    private CustomerServiceView recordBuyerFeedback(Long tenantId, Long operationId, CustomerServiceCommand command,
                                                    Instant occurredAt, LocalDateTime now) {
        CustomerServiceTicketDO ticket = requireMutableTicket(tenantId, command);
        require(Set.of("RESOLVED", "CLOSED").contains(ticket.getStatus()),
                "buyer feedback requires a resolved or closed ticket");
        require(ticket.getCustomerPrincipalId().equals(command.getCustomerPrincipalId()),
                "buyer feedback must be authored by the ticket customer");
        require(FEEDBACK_TOUCHPOINTS.contains(command.getTouchpointCode()), "unsupported touchpointCode");
        require(FEEDBACK_SENTIMENTS.contains(command.getSentimentCode()), "unsupported sentimentCode");
        if (!"SATISFIED".equals(command.getSentimentCode())) {
            requireDomainCode(command.getReasonCode(), "reasonCode");
        }
        if (command.getCommentToken() != null) {
            requireSafeToken(command.getCommentToken(), "commentToken");
        }
        CustomerServiceBuyerFeedbackDO feedback = new CustomerServiceBuyerFeedbackDO()
                .setFeedbackId(UUID.randomUUID().toString()).setTenantId(tenantId).setTicketId(ticket.getTicketId())
                .setRunId(command.getRunId()).setCustomerPrincipalId(command.getCustomerPrincipalId())
                .setTouchpointCode(command.getTouchpointCode()).setSentimentCode(command.getSentimentCode())
                .setScoreBasisPoints(feedbackScore(command.getSentimentCode())).setReasonCode(command.getReasonCode())
                .setCommentToken(command.getCommentToken())
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertBuyerFeedback(feedback) == 1, "failed to record buyer feedback");
        eventService.appendBuyerFeedback(feedback, command, occurredAt);
        return ticketView(operationId, ticket, false).setFeedbackId(feedback.getFeedbackId());
    }

    private CustomerServiceView recordQualityReview(Long tenantId, Long operationId, CustomerServiceCommand command,
                                                    Instant occurredAt, LocalDateTime now) {
        CustomerServiceTicketDO ticket = requireMutableTicket(tenantId, command);
        require(Set.of("RESOLVED", "CLOSED").contains(ticket.getStatus()),
                "quality review requires a resolved or closed ticket");
        requireId(command.getReviewerPrincipalId(), "reviewerPrincipalId");
        require(command.getScoreBasisPoints() != null && command.getScoreBasisPoints() >= 0
                && command.getScoreBasisPoints() <= 10_000, "scoreBasisPoints must be between 0 and 10000");
        requireDomainCode(command.getOutcomeCode(), "outcomeCode");
        requireDomainCode(command.getReasonCode(), "reasonCode");
        CustomerServiceQualityReviewDO review = new CustomerServiceQualityReviewDO()
                .setReviewId(UUID.randomUUID().toString()).setTenantId(tenantId).setTicketId(ticket.getTicketId())
                .setRunId(command.getRunId()).setReviewerPrincipalId(command.getReviewerPrincipalId())
                .setScoreBasisPoints(command.getScoreBasisPoints()).setOutcomeCode(command.getOutcomeCode())
                .setReasonCode(command.getReasonCode()).setCreatedAt(now);
        require(mapper.insertQualityReview(review) == 1, "failed to record customer-service quality review");
        eventService.appendQualityReview(review, command, occurredAt);
        return ticketView(operationId, ticket, false).setReviewId(review.getReviewId());
    }

    private CustomerServiceView requestClaim(Long tenantId, Long operationId, CustomerServiceCommand command,
                                             Instant occurredAt, LocalDateTime now) {
        CustomerServiceTicketDO ticket = requireMutableTicket(tenantId, command);
        require(!"CLOSED".equals(ticket.getStatus()), "closed ticket cannot accept a claim request");
        requireCode(command.getClaimCode(), "claimCode");
        require(CLAIM_TYPES.contains(command.getClaimType()), "unsupported claimType");
        require(command.getRequestedAmountMinor() != null && command.getRequestedAmountMinor() > 0,
                "requestedAmountMinor must be positive");
        require(command.getCurrencyCode() != null && CURRENCY.matcher(command.getCurrencyCode()).matches(),
                "currencyCode must be ISO-4217 alpha-3");
        requireDomainCode(command.getReasonCode(), "reasonCode");
        List<TicketOrderLinkDO> links = mapper.selectLinks(tenantId, ticket.getTicketId());
        String orderRef = firstReference(links, "ORDER");
        String afterSaleRef = firstReference(links, "AFTER_SALE");
        require(orderRef != null || afterSaleRef != null,
                "claim requires a qualified canonical Order or AfterSale reference");
        CustomerServiceClaimDO claim = new CustomerServiceClaimDO().setClaimId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setClaimCode(command.getClaimCode()).setTicketId(ticket.getTicketId())
                .setRunId(command.getRunId()).setClaimType(command.getClaimType()).setOrderRef(orderRef)
                .setAfterSaleRef(afterSaleRef).setStatus("REQUESTED")
                .setRequestedAmountMinor(command.getRequestedAmountMinor()).setApprovedAmountMinor(0L)
                .setPaidAmountMinor(0L).setCurrencyCode(command.getCurrencyCode()).setReasonCode(command.getReasonCode())
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertClaim(claim) == 1, "failed to create customer-service claim");
        eventService.appendClaim(operationId, claim, null, command, occurredAt, now);
        return claimView(operationId, claim, false).setTicketId(ticket.getTicketId()).setTicketNo(ticket.getTicketNo())
                .setTicketStatus(ticket.getStatus()).setTicketVersion(ticket.getVersion());
    }

    private CustomerServiceView transitionClaim(Long tenantId, Long operationId, CustomerServiceCommand command,
                                                Instant occurredAt, LocalDateTime now) {
        requireId(command.getClaimId(), "claimId");
        requireExpectedVersion(command);
        CustomerServiceClaimDO claim = mapper.selectClaimForUpdate(tenantId, command.getClaimId());
        require(claim != null, "customer-service claim does not exist");
        require(Objects.equals(claim.getVersion(), command.getExpectedVersion()), "claim version conflict");
        String before = claim.getStatus();
        String after;
        Long approvedAmount = claim.getApprovedAmountMinor();
        Long paidAmount = claim.getPaidAmountMinor();
        String reasonCode = claim.getReasonCode();
        String compensationEntryId = claim.getCompensationEntryId();
        if (command.getOperation() == CustomerServiceOperation.APPROVE_CLAIM) {
            require("REQUESTED".equals(before), "only requested claim can be approved");
            require(command.getApprovedAmountMinor() != null && command.getApprovedAmountMinor() > 0
                    && command.getApprovedAmountMinor() <= claim.getRequestedAmountMinor(),
                    "approvedAmountMinor must be positive and no greater than requested amount");
            after = "APPROVED";
            approvedAmount = command.getApprovedAmountMinor();
        } else if (command.getOperation() == CustomerServiceOperation.REJECT_CLAIM) {
            require("REQUESTED".equals(before), "only requested claim can be rejected");
            requireDomainCode(command.getReasonCode(), "reasonCode");
            after = "REJECTED";
            reasonCode = command.getReasonCode();
        } else {
            require("APPROVED".equals(before), "only approved claim can be compensated");
            require(approvedAmount != null && approvedAmount > 0, "approved claim amount is invalid");
            after = "PAID";
            paidAmount = approvedAmount;
            compensationEntryId = UUID.randomUUID().toString();
            CustomerServiceCompensationEntryDO entry = new CustomerServiceCompensationEntryDO()
                    .setCompensationEntryId(compensationEntryId).setTenantId(tenantId).setClaimId(claim.getClaimId())
                    .setTicketId(claim.getTicketId()).setEntryType("SERVICE_COMPENSATION")
                    .setAmountMinor(paidAmount).setCurrencyCode(claim.getCurrencyCode())
                    .setOperationIdempotencyKey(command.getIdempotencyKey())
                    .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
            require(mapper.insertCompensationEntry(entry) == 1, "failed to append compensation ledger entry");
        }
        require(mapper.transitionClaim(tenantId, claim.getClaimId(), claim.getVersion(), before, after,
                approvedAmount, paidAmount, reasonCode, compensationEntryId, now) == 1,
                "claim state transition conflict");
        claim.setStatus(after).setApprovedAmountMinor(approvedAmount).setPaidAmountMinor(paidAmount)
                .setReasonCode(reasonCode).setCompensationEntryId(compensationEntryId)
                .setVersion(claim.getVersion() + 1).setUpdatedAt(now);
        eventService.appendClaim(operationId, claim, before, command, occurredAt, now);
        return claimView(operationId, claim, false);
    }

    private CustomerServiceTicketDO requireMutableTicket(Long tenantId, CustomerServiceCommand command) {
        requireId(command.getTicketId(), "ticketId");
        requireExpectedVersion(command);
        CustomerServiceTicketDO ticket = mapper.selectTicketForUpdate(tenantId, command.getTicketId());
        require(ticket != null, "customer-service ticket does not exist");
        require(Objects.equals(ticket.getVersion(), command.getExpectedVersion()), "ticket version conflict");
        return ticket;
    }

    private static String nextTicketStatus(CustomerServiceOperation operation, String before) {
        return switch (operation) {
            case START_PROCESSING -> requireTransition(before, "OPEN", "IN_PROGRESS");
            case RESOLVE_TICKET -> {
                require(Set.of("OPEN", "IN_PROGRESS").contains(before), "ticket cannot be resolved from " + before);
                yield "RESOLVED";
            }
            case CLOSE_TICKET -> requireTransition(before, "RESOLVED", "CLOSED");
            case REOPEN_TICKET -> {
                require(Set.of("RESOLVED", "CLOSED").contains(before), "ticket cannot be reopened from " + before);
                yield "OPEN";
            }
            default -> throw new IllegalStateException("operation does not transition ticket status");
        };
    }

    private static String requireTransition(String actual, String expected, String after) {
        require(expected.equals(actual), "ticket cannot transition from " + actual + " to " + after);
        return after;
    }

    private static void validateExperienceSnapshot(CustomerServiceCommand command, Instant occurredAt) {
        boolean anyConfigured = command.getSlaPolicyCode() != null || command.getSlaPolicyVersion() != null
                || command.getResolutionDeadlineAt() != null || command.getFcrWindowHours() != null;
        if (!anyConfigured) return;
        requireDomainCode(command.getSlaPolicyCode(), "slaPolicyCode");
        require(command.getSlaPolicyVersion() != null && command.getSlaPolicyVersion() > 0,
                "slaPolicyVersion must be positive");
        require(command.getResolutionDeadlineAt() != null
                        && !command.getResolutionDeadlineAt().isBefore(occurredAt),
                "resolutionDeadlineAt must be present and not before occurredAt");
        require(command.getFcrWindowHours() != null && command.getFcrWindowHours() >= 1
                        && command.getFcrWindowHours() <= 720,
                "fcrWindowHours must be between 1 and 720");
    }

    private static int feedbackScore(String sentimentCode) {
        return switch (sentimentCode) {
            case "SATISFIED" -> 10_000;
            case "NEUTRAL" -> 5_000;
            case "DISSATISFIED" -> 0;
            default -> throw new IllegalStateException("unsupported sentimentCode");
        };
    }

    private static LocalDateTime asUtcDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void requireQualifiedReference(String sourceSystem, String referenceType, String referenceId) {
        requireId(referenceId, "referenceId");
        if ("ORDER".equals(referenceType)) {
            require("cloudmold-order".equals(sourceSystem), "ORDER reference must be qualified by cloudmold-order");
        } else if ("AFTER_SALE".equals(referenceType)) {
            require("cloudmold-aftersales".equals(sourceSystem),
                    "AFTER_SALE reference must be qualified by cloudmold-aftersales");
        } else {
            throw new IllegalStateException("referenceType must be ORDER or AFTER_SALE");
        }
    }

    private static String firstReference(List<TicketOrderLinkDO> links, String type) {
        if (links == null) return null;
        return links.stream().filter(link -> type.equals(link.getReferenceType()))
                .map(TicketOrderLinkDO::getReferenceId).findFirst().orElse(null);
    }

    private static CustomerServiceView ticketView(Long operationId, CustomerServiceTicketDO ticket, boolean duplicate) {
        return new CustomerServiceView().setOperationId(operationId).setDuplicate(duplicate)
                .setTicketId(ticket.getTicketId()).setTicketNo(ticket.getTicketNo())
                .setTicketStatus(ticket.getStatus()).setTicketVersion(ticket.getVersion())
                .setAssignedAgentPrincipalId(ticket.getAssignedAgentPrincipalId());
    }

    private static CustomerServiceView claimView(Long operationId, CustomerServiceClaimDO claim, boolean duplicate) {
        return new CustomerServiceView().setOperationId(operationId).setDuplicate(duplicate)
                .setTicketId(claim.getTicketId()).setClaimId(claim.getClaimId()).setClaimCode(claim.getClaimCode())
                .setClaimStatus(claim.getStatus()).setClaimVersion(claim.getVersion())
                .setCompensationEntryId(claim.getCompensationEntryId()).setPaidAmountMinor(claim.getPaidAmountMinor())
                .setCurrencyCode(claim.getCurrencyCode());
    }

    private static void validateCommon(CustomerServiceCommand command) {
        require(command != null, "customer-service command is required");
        require(command.getOperation() != null, "customer-service operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireText(command.getRunId(), "runId", 128);
        requireUuid(command.getRunId(), "runId");
        requireText(command.getCorrelationId(), "correlationId", 128);
    }

    private static void requireExpectedVersion(CustomerServiceCommand command) {
        require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                "expectedVersion must be positive");
    }

    private static void requireSafeToken(String value, String field) {
        require(value != null && SAFE_TOKEN.matcher(value).matches(),
                field + " must be a SHA-256 digest or opaque restricted-store token");
    }

    private static void requireCode(String value, String field) {
        require(value != null && CODE.matcher(value).matches(), field + " has invalid business-key format");
    }

    private static void requireDomainCode(String value, String field) {
        require(value != null && DOMAIN_CODE.matcher(value).matches(), field + " must be an uppercase domain code");
    }

    private static void requireId(String value, String field) {
        requireText(value, field, 128);
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength,
                field + " is required and must be at most " + maxLength + " characters");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(field + " must be a UUID", error);
        }
    }

    private static String firstNonNull(String... values) {
        for (String value : values) if (value != null) return value;
        throw new IllegalStateException("customer-service result has no aggregate id");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
