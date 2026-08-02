package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementSourcingMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.procurement.service.reference.ProcurementReferenceValidationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ProcurementSourcingService implements SourcingCommandApi {
    private static final Pattern REF=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern CODE=Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");
    private static final Pattern SHA=Pattern.compile("[0-9a-f]{64}");
    private final ProcurementMapper procurementMapper;
    private final ProcurementSourcingMapper mapper;
    private final OutboxAppender outboxAppender;
    private final ProcurementActorPrincipalPort actorPort;
    private final ProcurementReferenceValidationPort referencePort;

    @Override @Transactional(rollbackFor=Exception.class)
    public SourcingResult execute(SourcingCommand c,String actor) {
        req(c!=null&&c.getOperation()!=null,"sourcing operation is required"); ref(c.getIdempotencyKey(),"idempotencyKey",128);
        ref(c.getRunId(),"runId",128); req(c.getOccurredAt()!=null,"occurredAt is required"); ref(actor,"actorPrincipalId",128);
        actorPort.requireActive(actor); Long tenant=TenantContextHolder.getRequiredTenantId();
        LocalDateTime now=LocalDateTime.ofInstant(c.getOccurredAt(),ZoneOffset.UTC);
        String hash=DigestUtil.sha256Hex(actor+"\n"+JsonUtils.toJsonString(c)), token=UUID.randomUUID().toString();
        procurementMapper.insertOrResolveOperation(tenant,c.getIdempotencyKey(),c.getOperation().name(),hash,token,now);
        Long op=procurementMapper.selectLastInsertId(); Operation stored=procurementMapper.selectOperationForUpdate(op,tenant);
        if(!token.equals(stored.getAttemptToken())) {
            req(Objects.equals(hash,stored.getRequestHash()),"idempotency payload conflict"); req(stored.getStatus()==10,"existing sourcing operation is incomplete");
            SourcingResult replay=JsonUtils.parseObject(stored.getResultJson(),SourcingResult.class); replay.setDuplicate(true); return replay;
        }
        Outcome out=switch(c.getOperation()) {
            case CREATE_SOURCING_EVENT -> createEvent(tenant,op,c.getEvent(),actor,now);
            case PUBLISH_SOURCING_EVENT -> transition(tenant,op,c.getEventTransition(),actor,now,"DRAFT","PUBLISHED","PUBLISHED");
            case INVITE_SUPPLIER -> invite(tenant,op,c.getInvitation(),actor,now);
            case OPEN_QUOTING -> transition(tenant,op,c.getEventTransition(),actor,now,"PUBLISHED","QUOTING","QUOTING_OPENED");
            case SUBMIT_QUOTATION_REVISION -> submitRevision(tenant,op,c.getQuotationRevision(),actor,now);
            case WITHDRAW_QUOTATION_REVISION -> withdrawRevision(tenant,op,c.getQuotationWithdrawal(),actor,now);
            case CLOSE_QUOTING -> closeQuoting(tenant,op,c.getEventTransition(),actor,now);
            case CREATE_EVALUATION_POLICY -> createPolicy(tenant,op,c.getEvaluationPolicy(),actor,now);
            case RECORD_EVALUATION_SCORE -> score(tenant,op,c.getEvaluationScore(),actor,now);
            case CREATE_AWARD_DRAFT -> createAward(tenant,op,c.getAward(),actor,now);
            case SUBMIT_AWARD -> submitAward(tenant,op,c.getAwardTransition(),actor,now);
            case APPROVE_AWARD -> approveAward(tenant,op,c.getAwardTransition(),actor,now);
            case REJECT_AWARD -> rejectAward(tenant,op,c.getAwardTransition(),actor,now);
            case CLOSE_SOURCING_EVENT -> transition(tenant,op,c.getEventTransition(),actor,now,"AWARDED","CLOSED","CLOSED");
            case CANCEL_SOURCING_EVENT -> cancelEvent(tenant,op,c.getEventTransition(),actor,now);
        };
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(UUID.randomUUID().toString()).eventType(out.eventType)
                .schemaVersion(2).sourceSystem("cloudmold-procurement").tenantId(tenant).aggregateType(out.type).aggregateId(out.id)
                .aggregateVersion(out.version).eventSequence((short)1).occurredAt(c.getOccurredAt()).traceId(c.getRunId())
                .correlationId(c.getCorrelationId()).causationId(c.getCausationId()).idempotencyKey(c.getIdempotencyKey()+":"+out.version)
                .payload(out.payload).headers(Map.of("run_id",c.getRunId(),"status",out.status)).destination("lakehouse").build());
        SourcingResult result=SourcingResult.builder().operationId(op).aggregateType(out.type).aggregateId(out.id)
                .aggregateCode(out.code).aggregateVersion(out.version).status(out.status).build();
        req(procurementMapper.markOperationSucceeded(op,tenant,out.type,out.id,JsonUtils.toJsonString(result),now)==1,"sourcing operation completion conflict");
        return result;
    }

    private Outcome createEvent(Long t,Long op,SourcingCommand.EventDefinition d,String actor,LocalDateTime now) {
        nn(d,"event is required"); ref(d.getEventId(),"eventId",128); code(d.getEventCode(),"eventCode"); ref(d.getRequisitionId(),"requisitionId",128);
        req(d.getTitle()!=null&&!d.getTitle().isBlank()&&d.getTitle().length()<=255,"title is required");
        req(d.getQuotationDeadline()!=null&&d.getQuotationDeadline().isAfter(now),"quotationDeadline must be in the future");
        PurchaseRequisition pr=nn(procurementMapper.selectPurchaseRequisitionById(t,d.getRequisitionId()),"purchase requisition not found");
        req("APPROVED".equals(pr.getStatus()),"sourcing requires an approved purchase requisition");
        Map<String,PurchaseRequisitionLine> prLines=index(procurementMapper.selectPurchaseRequisitionLines(t,pr.getRequisitionId()),PurchaseRequisitionLine::getLineId);
        Map<String,PurchaseRequisitionDeliverySchedule> prSchedules=index(procurementMapper.selectPurchaseRequisitionSchedules(t,pr.getRequisitionId()),PurchaseRequisitionDeliverySchedule::getScheduleId);
        req(d.getLines()!=null&&!d.getLines().isEmpty(),"sourcing lines are required");
        List<SourcingLine> lines=new ArrayList<>(); List<SourcingSchedule> schedules=new ArrayList<>();
        Set<Integer> lineNos=new HashSet<>(); Set<String> sourceLineIds=new HashSet<>(), sourceScheduleIds=new HashSet<>();
        for(var x:d.getLines()) {
            ref(x.getSourcingLineId(),"sourcingLineId",128); req(x.getLineNumber()!=null&&x.getLineNumber()>0&&lineNos.add(x.getLineNumber()),"lineNumber must be unique");
            PurchaseRequisitionLine pl=nn(prLines.get(x.getRequisitionLineId()),"requisition line is not part of approved PR");
            req(sourceLineIds.add(pl.getLineId()),"requisition line may be sourced only once per event");
            req(x.getSchedules()!=null&&!x.getSchedules().isEmpty(),"sourcing line schedules are required");
            BigDecimal lineQty=BigDecimal.ZERO; Set<Integer> scheduleNos=new HashSet<>();
            for(var y:x.getSchedules()) {
                ref(y.getSourcingScheduleId(),"sourcingScheduleId",128); req(y.getScheduleNumber()!=null&&y.getScheduleNumber()>0&&scheduleNos.add(y.getScheduleNumber()),"scheduleNumber must be unique per line");
                PurchaseRequisitionDeliverySchedule ps=nn(prSchedules.get(y.getRequisitionScheduleId()),"requisition schedule is not part of approved PR");
                req(ps.getLineId().equals(pl.getLineId()),"requisition schedule does not belong to referenced line");
                req(sourceScheduleIds.add(ps.getScheduleId()),"requisition schedule may be sourced only once per event"); lineQty=lineQty.add(ps.getScheduledQuantity());
                schedules.add(new SourcingSchedule().setSourcingScheduleId(y.getSourcingScheduleId()).setTenantId(t).setEventId(d.getEventId())
                        .setSourcingLineId(x.getSourcingLineId()).setScheduleNumber(y.getScheduleNumber()).setRequisitionScheduleId(ps.getScheduleId())
                        .setCanonicalWarehouseId(ps.getCanonicalWarehouseId()).setRequestedQuantity(ps.getScheduledQuantity())
                        .setRequiredDeliveryDate(ps.getRequiredDeliveryDate()).setCreatedAt(now));
            }
            req(lineQty.compareTo(pl.getRequestedQuantity())==0,"sourcing schedules must exactly cover requisition line quantity");
            lines.add(new SourcingLine().setSourcingLineId(x.getSourcingLineId()).setTenantId(t).setEventId(d.getEventId()).setLineNumber(x.getLineNumber())
                    .setRequisitionLineId(pl.getLineId()).setCanonicalSkuId(pl.getCanonicalSkuId()).setRequestedQuantity(lineQty).setUomCode(pl.getUomCode()).setCreatedAt(now));
        }
        SourcingEvent e=new SourcingEvent().setEventId(d.getEventId()).setTenantId(t).setEventCode(d.getEventCode()).setRequisitionId(pr.getRequisitionId())
                .setRequisitionVersion(pr.getVersion()).setTitle(d.getTitle()).setStatus("DRAFT").setQuotationDeadline(d.getQuotationDeadline()).setVersion(1L)
                .setCreatedByPrincipalId(actor).setCreatedAt(now).setUpdatedAt(now);
        req(mapper.insertEvent(e)==1&&mapper.insertLines(lines)==lines.size()&&mapper.insertSchedules(schedules)==schedules.size(),"failed to persist sourcing event");
        history(t,op,e,actor,"CREATED",now); return outcome("procurement.sourcing_event.created","sourcing_event",e.getEventId(),e.getEventCode(),1,"DRAFT",Map.of("line_count",lines.size(),"schedule_count",schedules.size()));
    }

    private Outcome transition(Long t,Long op,SourcingCommand.EventTransitionDefinition d,String actor,LocalDateTime now,String from,String to,String action) {
        nn(d,"event transition is required"); ref(d.getEventId(),"eventId",128); req(d.getExpectedVersion()!=null,"expectedVersion is required");
        if ("CLOSED".equals(to)) code(d.getReasonCode(),"reasonCode");
        SourcingEvent e=nn(mapper.selectEventForUpdate(t,d.getEventId()),"sourcing event not found"); req(from.equals(e.getStatus()),"invalid sourcing event transition");
        req(d.getExpectedVersion().equals(e.getVersion()),"sourcing event version conflict");
        req(mapper.transitionEvent(t,e.getEventId(),from,to,e.getVersion(),actor,d.getReasonCode(),now)==1,"sourcing event transition conflict");
        e.setStatus(to).setVersion(e.getVersion()+1); history(t,op,e,actor,action,now);
        return outcome("procurement.sourcing_event."+to.toLowerCase(Locale.ROOT),"sourcing_event",e.getEventId(),e.getEventCode(),e.getVersion(),to,Map.of("previous_status",from));
    }

    private Outcome invite(Long t,Long op,SourcingCommand.InvitationDefinition d,String actor,LocalDateTime now) {
        nn(d,"invitation is required"); ref(d.getInvitationId(),"invitationId",128); ref(d.getSupplierId(),"supplierId",128);
        referencePort.requireActiveSupplier(d.getSupplierId()); SourcingEvent e=lock(t,d.getEventId(),d.getExpectedEventVersion(),Set.of("PUBLISHED"));
        req(mapper.insertInvitation(new SupplierInvitation().setInvitationId(d.getInvitationId()).setTenantId(t).setEventId(e.getEventId()).setSupplierId(d.getSupplierId())
                .setStatus("INVITED").setInvitedByPrincipalId(actor).setInvitedAt(now))==1,"failed to persist invitation");
        bump(t,e,now); history(t,op,e,actor,"SUPPLIER_INVITED",now);
        return outcome("procurement.supplier.invited","sourcing_event",e.getEventId(),e.getEventCode(),e.getVersion(),e.getStatus(),Map.of("supplier_id",d.getSupplierId()));
    }

    private Outcome submitRevision(Long t,Long op,SourcingCommand.QuotationRevisionDefinition d,String actor,LocalDateTime now) {
        nn(d,"quotationRevision is required"); ref(d.getQuotationId(),"quotationId",128); code(d.getQuotationCode(),"quotationCode"); ref(d.getRevisionId(),"revisionId",128);
        ref(d.getSupplierId(),"supplierId",128); req(d.getRevisionNumber()!=null&&d.getRevisionNumber()>0,"revisionNumber must be positive");
        req(d.getCurrencyCode()!=null&&d.getCurrencyCode().matches("[A-Z]{3}"),"currencyCode must be ISO-4217");
        SourcingEvent e=lock(t,d.getEventId(),d.getExpectedEventVersion(),Set.of("QUOTING")); req(now.isBefore(e.getQuotationDeadline()),"quotation deadline has passed");
        req(mapper.countActiveInvitation(t,e.getEventId(),d.getSupplierId())==1,"supplier is not invited"); referencePort.requireActiveSupplier(d.getSupplierId());
        Quotation q=mapper.selectQuotationForUpdate(t,e.getEventId(),d.getSupplierId());
        boolean firstRevision=q==null;
        if(firstRevision) {
            req(d.getRevisionNumber()==1,"first quotation revisionNumber must be 1");
            q=new Quotation().setQuotationId(d.getQuotationId()).setTenantId(t).setQuotationCode(d.getQuotationCode()).setEventId(e.getEventId())
                    .setSupplierId(d.getSupplierId()).setCurrencyCode(d.getCurrencyCode()).setLatestRevisionNumber(1).setActiveRevisionId(null).setCreatedAt(now);
            req(mapper.insertQuotation(q)==1,"failed to persist quotation header");
        } else {
            req(q.getQuotationId().equals(d.getQuotationId())&&q.getQuotationCode().equals(d.getQuotationCode()),"quotation identity cannot change across revisions");
            req(q.getCurrencyCode().equals(d.getCurrencyCode()),"quotation currency cannot change across revisions");
            req(d.getRevisionNumber()==q.getLatestRevisionNumber()+1,"quotation revisionNumber must be sequential");
        }
        Map<String,SourcingLine> sourceLines=index(mapper.selectLines(t,e.getEventId()),SourcingLine::getSourcingLineId);
        Map<String,SourcingSchedule> sourceSchedules=index(mapper.selectSchedules(t,e.getEventId()),SourcingSchedule::getSourcingScheduleId);
        req(d.getLines()!=null&&!d.getLines().isEmpty(),"quotation revision lines are required");
        List<QuotationRevisionLine> lines=new ArrayList<>(); List<QuotationRevisionSchedule> schedules=new ArrayList<>();
        Set<Integer> lineNos=new HashSet<>(); Set<String> quotedLines=new HashSet<>(), quotedSchedules=new HashSet<>();
        for(var x:d.getLines()) {
            ref(x.getRevisionLineId(),"revisionLineId",128); req(x.getLineNumber()!=null&&x.getLineNumber()>0&&lineNos.add(x.getLineNumber()),"quotation lineNumber must be unique");
            SourcingLine source=nn(sourceLines.get(x.getSourcingLineId()),"quotation line references another event"); req(quotedLines.add(source.getSourcingLineId()),"duplicate sourcingLineId in revision");
            qty(x.getOfferedQuantity(),"offeredQuantity"); req(Objects.equals(x.getUomCode(),source.getUomCode()),"quotation UOM must equal sourcing UOM"); money(x.getUnitNetPriceMinor());
            code(x.getTaxCode(),"taxCode"); req(x.getTaxRateBps()!=null&&x.getTaxRateBps()>=0&&x.getTaxRateBps()<=10000,"taxRateBps out of range");
            req(x.getSchedules()!=null&&!x.getSchedules().isEmpty(),"quotation revision line schedules are required"); BigDecimal scheduled=BigDecimal.ZERO; Set<Integer> scheduleNos=new HashSet<>();
            for(var y:x.getSchedules()) {
                ref(y.getRevisionScheduleId(),"revisionScheduleId",128); req(y.getScheduleNumber()!=null&&y.getScheduleNumber()>0&&scheduleNos.add(y.getScheduleNumber()),"quotation scheduleNumber must be unique per line");
                SourcingSchedule sourceSchedule=nn(sourceSchedules.get(y.getSourcingScheduleId()),"quotation schedule references another event");
                req(sourceSchedule.getSourcingLineId().equals(source.getSourcingLineId()),"quotation schedule does not belong to quotation line"); req(quotedSchedules.add(sourceSchedule.getSourcingScheduleId()),"duplicate sourcingScheduleId in revision");
                qty(y.getOfferedQuantity(),"schedule offeredQuantity"); req(y.getOfferedQuantity().compareTo(sourceSchedule.getRequestedQuantity())<=0,"schedule offer exceeds requested quantity");
                req(y.getPromisedDeliveryDate()!=null,"promisedDeliveryDate is required"); scheduled=scheduled.add(y.getOfferedQuantity());
                schedules.add(new QuotationRevisionSchedule().setRevisionScheduleId(y.getRevisionScheduleId()).setTenantId(t).setRevisionId(d.getRevisionId())
                        .setRevisionLineId(x.getRevisionLineId()).setScheduleNumber(y.getScheduleNumber()).setSourcingScheduleId(sourceSchedule.getSourcingScheduleId())
                        .setOfferedQuantity(y.getOfferedQuantity()).setPromisedDeliveryDate(y.getPromisedDeliveryDate()));
            }
            req(scheduled.compareTo(x.getOfferedQuantity())==0,"quotation schedules must exactly sum to line offeredQuantity");
            long net=minor(x.getOfferedQuantity().multiply(x.getUnitNetPriceMinor())), tax=tax(net,x.getTaxRateBps());
            lines.add(new QuotationRevisionLine().setRevisionLineId(x.getRevisionLineId()).setTenantId(t).setRevisionId(d.getRevisionId()).setEventId(e.getEventId())
                    .setLineNumber(x.getLineNumber()).setSourcingLineId(source.getSourcingLineId()).setOfferedQuantity(x.getOfferedQuantity()).setUomCode(x.getUomCode())
                    .setUnitNetPriceMinor(x.getUnitNetPriceMinor()).setTaxCode(x.getTaxCode()).setTaxRateBps(x.getTaxRateBps()).setLineNetAmountMinor(net)
                    .setLineTaxAmountMinor(tax).setLineGrossAmountMinor(Math.addExact(net,tax)));
        }
        QuotationRevision revision=new QuotationRevision().setRevisionId(d.getRevisionId()).setTenantId(t).setQuotationId(d.getQuotationId()).setEventId(e.getEventId())
                .setRevisionNumber(d.getRevisionNumber()).setStatus("SUBMITTED").setSubmittedByPrincipalId(actor).setSubmittedAt(now)
                .setPayloadSha256(DigestUtil.sha256Hex(JsonUtils.toJsonString(d)));
        req(mapper.insertRevision(revision)==1&&mapper.insertRevisionLines(lines)==lines.size()&&mapper.insertRevisionSchedules(schedules)==schedules.size(),"failed to persist immutable quotation revision");
        if(firstRevision) {
            req(mapper.activateInitialRevision(t,q.getQuotationId(),revision.getRevisionId())==1,"quotation initial revision activation conflict");
        } else {
            if(q.getActiveRevisionId()!=null) req(mapper.supersedeRevision(t,q.getActiveRevisionId(),actor,now)==1,"active quotation revision conflict");
            req(mapper.advanceQuotation(t,q.getQuotationId(),q.getLatestRevisionNumber(),d.getRevisionNumber(),d.getRevisionId())==1,"quotation header version conflict");
        }
        bump(t,e,now); history(t,op,e,actor,"QUOTATION_REVISION_SUBMITTED",now);
        return outcome("procurement.quotation_revision.submitted","sourcing_event",e.getEventId(),e.getEventCode(),e.getVersion(),e.getStatus(),Map.of("quotation_id",d.getQuotationId(),"revision_id",d.getRevisionId(),"revision_number",d.getRevisionNumber()));
    }

    private Outcome withdrawRevision(Long t,Long op,SourcingCommand.QuotationWithdrawalDefinition d,String actor,LocalDateTime now) {
        nn(d,"quotationWithdrawal is required"); ref(d.getRevisionId(),"revisionId",128); code(d.getReasonCode(),"reasonCode");
        SourcingEvent e=lock(t,d.getEventId(),d.getExpectedEventVersion(),Set.of("QUOTING")); QuotationRevision r=nn(mapper.selectRevisionForUpdate(t,d.getRevisionId()),"quotation revision not found");
        req(r.getEventId().equals(e.getEventId()),"quotation revision belongs to another event"); req(mapper.withdrawRevision(t,r.getRevisionId(),d.getReasonCode(),actor,now)==1,"only active submitted revision may be withdrawn");
        mapper.clearActiveRevision(t,r.getQuotationId(),r.getRevisionId()); bump(t,e,now); history(t,op,e,actor,"QUOTATION_REVISION_WITHDRAWN",now);
        return outcome("procurement.quotation_revision.withdrawn","sourcing_event",e.getEventId(),e.getEventCode(),e.getVersion(),e.getStatus(),Map.of("revision_id",r.getRevisionId()));
    }

    private Outcome closeQuoting(Long t,Long op,SourcingCommand.EventTransitionDefinition d,String actor,LocalDateTime now) {
        nn(d,"event transition is required"); req(!mapper.selectActiveRevisions(t,d.getEventId()).isEmpty(),"evaluation requires at least one active quotation revision");
        return transition(t,op,d,actor,now,"QUOTING","EVALUATING","QUOTING_CLOSED");
    }

    private Outcome createPolicy(Long t,Long op,SourcingCommand.EvaluationPolicyDefinition d,String actor,LocalDateTime now) {
        nn(d,"evaluationPolicy is required"); ref(d.getPolicyId(),"policyId",128); code(d.getPolicyCode(),"policyCode");
        req(d.getPolicyVersion()!=null&&d.getPolicyVersion()>0,"policyVersion must be positive"); SourcingEvent e=lock(t,d.getEventId(),d.getExpectedEventVersion(),Set.of("EVALUATING"));
        EvaluationPolicy existing=mapper.selectPolicyForUpdate(t,e.getEventId(),d.getPolicyId());
        if(existing==null) req(d.getPolicyVersion()==1,"initial policyVersion must be 1");
        else { req(existing.getPolicyCode().equals(d.getPolicyCode()),"policyCode cannot change across versions"); req(d.getPolicyVersion()==existing.getActiveVersion()+1,"policyVersion must be sequential"); }
        req(d.getDimensions()!=null&&!d.getDimensions().isEmpty(),"evaluation dimensions are required"); List<EvaluationDimension> dimensions=new ArrayList<>();
        int totalWeight=0; Set<String> ids=new HashSet<>(), codes=new HashSet<>();
        for(var x:d.getDimensions()) { ref(x.getDimensionId(),"dimensionId",128); code(x.getDimensionCode(),"dimensionCode");
            req(ids.add(x.getDimensionId())&&codes.add(x.getDimensionCode()),"evaluation dimensions must be unique"); req(x.getDimensionName()!=null&&!x.getDimensionName().isBlank()&&x.getDimensionName().length()<=128,"dimensionName is required");
            req(x.getWeightBps()!=null&&x.getWeightBps()>0&&x.getWeightBps()<=10000,"weightBps out of range"); req(x.getMaximumScore()!=null&&x.getMaximumScore()>0,"maximumScore must be positive"); totalWeight+=x.getWeightBps();
            dimensions.add(new EvaluationDimension().setDimensionId(x.getDimensionId()).setTenantId(t).setPolicyId(d.getPolicyId()).setPolicyVersion(d.getPolicyVersion())
                    .setDimensionCode(x.getDimensionCode()).setDimensionName(x.getDimensionName()).setWeightBps(x.getWeightBps()).setMaximumScore(x.getMaximumScore())); }
        req(totalWeight==10000,"evaluation dimension weights must total 10000 bps"); String policyHash=DigestUtil.sha256Hex(JsonUtils.toJsonString(d));
        if(existing==null) req(mapper.insertPolicy(new EvaluationPolicy().setPolicyId(d.getPolicyId()).setTenantId(t).setPolicyCode(d.getPolicyCode()).setEventId(e.getEventId()).setActiveVersion(d.getPolicyVersion()).setCreatedAt(now))==1,"failed to persist evaluation policy");
        req(mapper.insertPolicyVersion(new EvaluationPolicyVersion().setPolicyId(d.getPolicyId()).setTenantId(t).setPolicyVersion(d.getPolicyVersion()).setStatus("ACTIVE").setCreatedByPrincipalId(actor).setCreatedAt(now).setPolicySha256(policyHash))==1
                &&mapper.insertDimensions(dimensions)==dimensions.size(),"failed to persist immutable policy version");
        if(existing!=null) req(mapper.advancePolicyVersion(t,d.getPolicyId(),existing.getActiveVersion(),d.getPolicyVersion())==1,"evaluation policy version conflict");
        bump(t,e,now); history(t,op,e,actor,"EVALUATION_POLICY_CREATED",now);
        return outcome("procurement.evaluation_policy.created","sourcing_event",e.getEventId(),e.getEventCode(),e.getVersion(),e.getStatus(),Map.of("policy_id",d.getPolicyId(),"policy_version",d.getPolicyVersion()));
    }

    private Outcome score(Long t,Long op,SourcingCommand.EvaluationScoreDefinition d,String actor,LocalDateTime now) {
        nn(d,"evaluationScore is required"); ref(d.getScoreId(),"scoreId",128); ref(d.getQuotationRevisionId(),"quotationRevisionId",128); sha(d.getReviewerEvidenceSha256(),"reviewerEvidenceSha256");
        SourcingEvent e=lock(t,d.getEventId(),d.getExpectedEventVersion(),Set.of("EVALUATING")); EvaluationPolicy p=nn(mapper.selectPolicy(t,e.getEventId(),d.getPolicyId()),"evaluation policy not found");
        req(Objects.equals(p.getActiveVersion(),d.getPolicyVersion()),"evaluation policy version is not active"); QuotationRevision r=nn(mapper.selectRevisionForUpdate(t,d.getQuotationRevisionId()),"quotation revision not found");
        req(r.getEventId().equals(e.getEventId())&&"SUBMITTED".equals(r.getStatus()),"evaluation requires active quotation revision");
        Map<String,EvaluationDimension> dimensions=index(mapper.selectDimensions(t,p.getPolicyId(),p.getActiveVersion()),EvaluationDimension::getDimensionId);
        req(d.getDimensions()!=null&&d.getDimensions().size()==dimensions.size(),"one score per policy dimension is required"); List<EvaluationDimensionScore> scores=new ArrayList<>(); long weighted=0; Set<String> seen=new HashSet<>();
        for(var x:d.getDimensions()) { EvaluationDimension dim=nn(dimensions.get(x.getDimensionId()),"score references another policy dimension"); req(seen.add(dim.getDimensionId()),"duplicate dimension score");
            req(x.getScore()!=null&&x.getScore()>=0&&x.getScore()<=dim.getMaximumScore(),"dimension score out of range"); req(x.getEvidenceReference()!=null&&!x.getEvidenceReference().isBlank()&&x.getEvidenceReference().length()<=512,"dimension evidenceReference is required");
            weighted+=Math.round((double)x.getScore()*dim.getWeightBps()/dim.getMaximumScore()); scores.add(new EvaluationDimensionScore().setScoreId(d.getScoreId()).setTenantId(t).setDimensionId(dim.getDimensionId()).setScore(x.getScore()).setEvidenceReference(x.getEvidenceReference())); }
        int weightedBps=Math.toIntExact(weighted); String summaryHash=DigestUtil.sha256Hex(JsonUtils.toJsonString(d.getDimensions()));
        EvaluationScore score=new EvaluationScore().setScoreId(d.getScoreId()).setTenantId(t).setEventId(e.getEventId()).setPolicyId(p.getPolicyId()).setPolicyVersion(p.getActiveVersion())
                .setQuotationRevisionId(r.getRevisionId()).setReviewerPrincipalId(actor).setWeightedScoreBps(weightedBps).setReviewerEvidenceSha256(d.getReviewerEvidenceSha256()).setEvaluationSummarySha256(summaryHash).setCreatedAt(now);
        req(mapper.insertScore(score)==1&&mapper.insertDimensionScores(scores)==scores.size(),"failed to persist evaluation evidence"); bump(t,e,now); history(t,op,e,actor,"EVALUATION_SCORE_RECORDED",now);
        return outcome("procurement.evaluation_score.recorded","sourcing_event",e.getEventId(),e.getEventCode(),e.getVersion(),e.getStatus(),Map.of("score_id",score.getScoreId(),"weighted_score_bps",weightedBps));
    }

    private Outcome createAward(Long t,Long op,SourcingCommand.AwardDefinition d,String actor,LocalDateTime now) {
        nn(d,"award is required"); ref(d.getAwardId(),"awardId",128); code(d.getAwardCode(),"awardCode"); code(d.getDecisionReasonCode(),"decisionReasonCode");
        SourcingEvent e=lock(t,d.getEventId(),d.getExpectedEventVersion(),Set.of("EVALUATING")); EvaluationPolicy p=nn(mapper.selectPolicy(t,e.getEventId(),d.getPolicyId()),"evaluation policy not found");
        req(Objects.equals(p.getActiveVersion(),d.getPolicyVersion()),"award must freeze active evaluation policy version");
        Map<String,SourcingLine> sourceLines=index(mapper.selectLines(t,e.getEventId()),SourcingLine::getSourcingLineId); Map<String,SourcingSchedule> sourceSchedules=index(mapper.selectSchedules(t,e.getEventId()),SourcingSchedule::getSourcingScheduleId);
        Map<String,QuotationRevisionLine> revisionLines=index(mapper.selectActiveRevisionLines(t,e.getEventId()),QuotationRevisionLine::getRevisionLineId); Map<String,QuotationRevisionSchedule> revisionSchedules=index(mapper.selectActiveRevisionSchedules(t,e.getEventId()),QuotationRevisionSchedule::getRevisionScheduleId);
        Map<String,Quotation> quotations=index(mapper.selectQuotations(t,e.getEventId()),Quotation::getQuotationId); Map<String,QuotationRevision> revisions=index(mapper.selectActiveRevisions(t,e.getEventId()),QuotationRevision::getRevisionId);
        req(d.getLines()!=null&&!d.getLines().isEmpty(),"award lines are required"); List<AwardLine> lines=new ArrayList<>(); Set<Integer> lineNos=new HashSet<>(); Map<String,BigDecimal> allocation=new HashMap<>(); Map<String,BigDecimal> quoteAllocation=new HashMap<>();
        for(var x:d.getLines()) { ref(x.getAwardLineId(),"awardLineId",128); req(x.getLineNumber()!=null&&x.getLineNumber()>0&&lineNos.add(x.getLineNumber()),"award lineNumber must be unique");
            SourcingLine sl=nn(sourceLines.get(x.getSourcingLineId()),"award line references another event"); SourcingSchedule ss=nn(sourceSchedules.get(x.getSourcingScheduleId()),"award schedule references another event"); req(ss.getSourcingLineId().equals(sl.getSourcingLineId()),"award schedule does not belong to sourcing line");
            QuotationRevisionLine ql=nn(revisionLines.get(x.getQuotationRevisionLineId()),"award requires active quotation revision line"); QuotationRevisionSchedule qs=nn(revisionSchedules.get(x.getQuotationRevisionScheduleId()),"award requires active quotation revision schedule");
            req(qs.getRevisionLineId().equals(ql.getRevisionLineId())&&ql.getSourcingLineId().equals(sl.getSourcingLineId())&&qs.getSourcingScheduleId().equals(ss.getSourcingScheduleId()),"award references must identify one exact quote schedule");
            QuotationRevision revision=nn(revisions.get(ql.getRevisionId()),"quotation revision not active"); Quotation quotation=nn(quotations.get(revision.getQuotationId()),"quotation header not found"); req(quotation.getSupplierId().equals(x.getSupplierId()),"award supplier must equal quotation supplier");
            qty(x.getAwardedQuantity(),"awardedQuantity"); req(x.getAwardedQuantity().compareTo(qs.getOfferedQuantity())<=0,"award exceeds quoted schedule quantity"); allocation.merge(ss.getSourcingScheduleId(),x.getAwardedQuantity(),BigDecimal::add); quoteAllocation.merge(qs.getRevisionScheduleId(),x.getAwardedQuantity(),BigDecimal::add); req(quoteAllocation.get(qs.getRevisionScheduleId()).compareTo(qs.getOfferedQuantity())<=0,"cumulative award exceeds quoted schedule quantity");
            List<EvaluationScore> evaluations=mapper.selectScores(t,e.getEventId(),p.getPolicyId(),p.getActiveVersion(),revision.getRevisionId()); req(!evaluations.isEmpty(),"every awarded quotation revision requires reviewer evaluation evidence");
            int scoreBps=(int)Math.round(evaluations.stream().mapToInt(EvaluationScore::getWeightedScoreBps).average().orElseThrow());
            String summaryHash=DigestUtil.sha256Hex(evaluations.stream().map(EvaluationScore::getEvaluationSummarySha256).sorted().collect(Collectors.joining("\n")));
            String evidenceHash=DigestUtil.sha256Hex(evaluations.stream().map(EvaluationScore::getReviewerEvidenceSha256).sorted().collect(Collectors.joining("\n")));
            long net=minor(x.getAwardedQuantity().multiply(ql.getUnitNetPriceMinor())), tax=tax(net,ql.getTaxRateBps());
            lines.add(new AwardLine().setAwardLineId(x.getAwardLineId()).setTenantId(t).setAwardId(d.getAwardId()).setLineNumber(x.getLineNumber()).setSourcingLineId(sl.getSourcingLineId()).setSourcingScheduleId(ss.getSourcingScheduleId())
                    .setQuotationRevisionLineId(ql.getRevisionLineId()).setQuotationRevisionScheduleId(qs.getRevisionScheduleId()).setSupplierId(quotation.getSupplierId()).setCanonicalSkuId(sl.getCanonicalSkuId()).setCanonicalWarehouseId(ss.getCanonicalWarehouseId())
                    .setAwardedQuantity(x.getAwardedQuantity()).setUomCode(sl.getUomCode()).setCurrencyCode(quotation.getCurrencyCode()).setUnitNetPriceMinor(ql.getUnitNetPriceMinor()).setTaxCode(ql.getTaxCode()).setTaxRateBps(ql.getTaxRateBps()).setPromisedDeliveryDate(qs.getPromisedDeliveryDate())
                    .setLineNetAmountMinor(net).setLineTaxAmountMinor(tax).setLineGrossAmountMinor(Math.addExact(net,tax)).setPolicyId(p.getPolicyId()).setPolicyVersion(p.getActiveVersion()).setEvaluationWeightedScoreBps(scoreBps).setEvaluationSummarySha256(summaryHash).setReviewerEvidenceSha256(evidenceHash)); }
        for(SourcingSchedule s:sourceSchedules.values()) req(s.getRequestedQuantity().compareTo(allocation.getOrDefault(s.getSourcingScheduleId(),BigDecimal.ZERO))==0,"award must exactly allocate every sourcing schedule");
        Award a=new Award().setAwardId(d.getAwardId()).setTenantId(t).setAwardCode(d.getAwardCode()).setEventId(e.getEventId()).setPolicyId(p.getPolicyId()).setPolicyVersion(p.getActiveVersion()).setStatus("DRAFT")
                .setDecisionReasonCode(d.getDecisionReasonCode()).setVersion(1L).setCreatedByPrincipalId(actor).setCreatedAt(now).setUpdatedAt(now);
        req(mapper.insertAward(a)==1&&mapper.insertAwardLines(lines)==lines.size(),"failed to persist award draft"); awardHistory(t,op,a,actor,"CREATED",null,now); bump(t,e,now); history(t,op,e,actor,"AWARD_DRAFT_CREATED",now);
        return outcome("procurement.award.draft_created","procurement_award",a.getAwardId(),a.getAwardCode(),1,"DRAFT",Map.of("event_id",e.getEventId(),"line_count",lines.size()));
    }

    private Outcome submitAward(Long t,Long op,SourcingCommand.AwardTransitionDefinition d,String actor,LocalDateTime now) {
        nn(d,"awardTransition is required"); Award a=lockAward(t,d,"DRAFT"); SourcingEvent e=lock(t,a.getEventId(),d.getExpectedEventVersion(),Set.of("EVALUATING"));
        req(mapper.transitionAward(t,a.getAwardId(),"DRAFT","SUBMITTED",a.getVersion(),actor,now)==1,"award submit conflict"); a.setStatus("SUBMITTED").setVersion(a.getVersion()+1).setSubmittedByPrincipalId(actor).setSubmittedAt(now);
        req(mapper.transitionEvent(t,e.getEventId(),"EVALUATING","AWARD_SUBMITTED",e.getVersion(),actor,null,now)==1,"event award submit conflict"); e.setStatus("AWARD_SUBMITTED").setVersion(e.getVersion()+1);
        awardHistory(t,op,a,actor,"SUBMITTED",null,now); history(t,op,e,actor,"AWARD_SUBMITTED",now);
        return outcome("procurement.award.submitted","procurement_award",a.getAwardId(),a.getAwardCode(),a.getVersion(),a.getStatus(),Map.of("event_id",e.getEventId()));
    }

    private Outcome approveAward(Long t,Long op,SourcingCommand.AwardTransitionDefinition d,String actor,LocalDateTime now) {
        nn(d,"awardTransition is required"); Award a=lockAward(t,d,"SUBMITTED");
        req(!actor.equals(a.getCreatedByPrincipalId())&&!actor.equals(a.getSubmittedByPrincipalId()),"maker-checker violation: award creator or submitter cannot approve award");
        req(!mapper.selectAwardReviewerPrincipalIds(t,a.getAwardId()).contains(actor),"maker-checker violation: evaluation reviewer cannot approve award");
        SourcingEvent e=lock(t,a.getEventId(),d.getExpectedEventVersion(),Set.of("AWARD_SUBMITTED")); req(mapper.transitionAward(t,a.getAwardId(),"SUBMITTED","APPROVED",a.getVersion(),actor,now)==1,"award approval conflict");
        a.setStatus("APPROVED").setVersion(a.getVersion()+1).setApprovedByPrincipalId(actor).setApprovedAt(now); long eventVersion=e.getVersion()+1;
        List<AwardLine> lines=mapper.selectAwardLines(t,a.getAwardId()); String snapshotId=a.getAwardId()+":v"+a.getVersion();
        AwardSnapshot snapshot=new AwardSnapshot().setSnapshotId(snapshotId).setTenantId(t).setAwardId(a.getAwardId()).setAwardVersion(a.getVersion()).setEventId(e.getEventId()).setEventVersion(eventVersion)
                .setStatus("APPROVED").setDecisionReasonCode(a.getDecisionReasonCode()).setApprovedByPrincipalId(actor).setApprovedAt(now).setCreatedAt(now);
        List<AwardSnapshotLine> snapshots=new ArrayList<>(); for(AwardLine line:lines){ AwardSnapshotLine s=new AwardSnapshotLine(); copy(line,s); s.setSnapshotLineId(snapshotId+":"+line.getLineNumber()); s.setSnapshotId(snapshotId); snapshots.add(s); }
        req(mapper.insertAwardSnapshot(snapshot)==1&&mapper.insertAwardSnapshotLines(snapshots)==snapshots.size(),"failed to persist immutable award snapshot");
        req(mapper.transitionEvent(t,e.getEventId(),"AWARD_SUBMITTED","AWARDED",e.getVersion(),actor,null,now)==1,"event award approval conflict"); e.setStatus("AWARDED").setVersion(eventVersion);
        awardHistory(t,op,a,actor,"APPROVED",d.getReasonCode(),now); history(t,op,e,actor,"AWARD_APPROVED",now);
        return outcome("procurement.award.approved","procurement_award",a.getAwardId(),a.getAwardCode(),a.getVersion(),a.getStatus(),Map.of("event_id",e.getEventId(),"snapshot_id",snapshotId));
    }

    private Outcome rejectAward(Long t,Long op,SourcingCommand.AwardTransitionDefinition d,String actor,LocalDateTime now) {
        nn(d,"awardTransition is required"); code(d.getReasonCode(),"reasonCode"); Award a=lockAward(t,d,"SUBMITTED");
        req(!actor.equals(a.getCreatedByPrincipalId())&&!actor.equals(a.getSubmittedByPrincipalId()),"maker-checker violation: award creator or submitter cannot reject award");
        req(!mapper.selectAwardReviewerPrincipalIds(t,a.getAwardId()).contains(actor),"maker-checker violation: evaluation reviewer cannot reject award");
        SourcingEvent e=lock(t,a.getEventId(),d.getExpectedEventVersion(),Set.of("AWARD_SUBMITTED"));
        req(mapper.transitionAward(t,a.getAwardId(),"SUBMITTED","REJECTED",a.getVersion(),actor,now)==1,"award rejection conflict"); a.setStatus("REJECTED").setVersion(a.getVersion()+1);
        req(mapper.transitionEvent(t,e.getEventId(),"AWARD_SUBMITTED","EVALUATING",e.getVersion(),actor,d.getReasonCode(),now)==1,"event award rejection conflict"); e.setStatus("EVALUATING").setVersion(e.getVersion()+1);
        awardHistory(t,op,a,actor,"REJECTED",d.getReasonCode(),now); history(t,op,e,actor,"AWARD_REJECTED",now);
        return outcome("procurement.award.rejected","procurement_award",a.getAwardId(),a.getAwardCode(),a.getVersion(),"REJECTED",Map.of("event_id",e.getEventId(),"reason_code",d.getReasonCode()));
    }

    private Outcome cancelEvent(Long t,Long op,SourcingCommand.EventTransitionDefinition d,String actor,LocalDateTime now) {
        nn(d,"event transition is required"); code(d.getReasonCode(),"reasonCode"); ref(d.getEventId(),"eventId",128); req(d.getExpectedVersion()!=null,"expectedVersion is required");
        SourcingEvent e=nn(mapper.selectEventForUpdate(t,d.getEventId()),"sourcing event not found"); req(Set.of("DRAFT","PUBLISHED","QUOTING","EVALUATING").contains(e.getStatus()),"event cannot be cancelled from current status");
        req(e.getVersion().equals(d.getExpectedVersion()),"sourcing event version conflict"); String from=e.getStatus(); req(mapper.transitionEvent(t,e.getEventId(),from,"CANCELLED",e.getVersion(),actor,d.getReasonCode(),now)==1,"event cancellation conflict");
        e.setStatus("CANCELLED").setVersion(e.getVersion()+1); history(t,op,e,actor,"CANCELLED",now); return outcome("procurement.sourcing_event.cancelled","sourcing_event",e.getEventId(),e.getEventCode(),e.getVersion(),"CANCELLED",Map.of("previous_status",from,"reason_code",d.getReasonCode()));
    }

    private SourcingEvent lock(Long t,String id,Long version,Set<String> statuses){ ref(id,"eventId",128); req(version!=null,"expectedEventVersion is required"); SourcingEvent e=nn(mapper.selectEventForUpdate(t,id),"sourcing event not found"); req(statuses.contains(e.getStatus()),"sourcing event status does not allow operation"); req(version.equals(e.getVersion()),"sourcing event version conflict"); return e; }
    private Award lockAward(Long t,SourcingCommand.AwardTransitionDefinition d,String status){ ref(d.getAwardId(),"awardId",128); req(d.getExpectedAwardVersion()!=null,"expectedAwardVersion is required"); Award a=nn(mapper.selectAwardForUpdate(t,d.getAwardId()),"award not found"); req(status.equals(a.getStatus()),"award status does not allow operation"); req(d.getExpectedAwardVersion().equals(a.getVersion()),"award version conflict"); return a; }
    private void bump(Long t,SourcingEvent e,LocalDateTime now){ req(mapper.bumpEvent(t,e.getEventId(),e.getStatus(),e.getVersion(),now)==1,"sourcing event version conflict"); e.setVersion(e.getVersion()+1); }
    private void history(Long t,Long op,SourcingEvent e,String actor,String action,LocalDateTime now){ req(mapper.insertHistory(new SourcingHistory().setTenantId(t).setEventId(e.getEventId()).setOperationId(op).setAggregateVersion(e.getVersion()).setStatus(e.getStatus()).setActionCode(action).setActorPrincipalId(actor).setOccurredAt(now).setCreatedAt(now))==1,"failed to persist sourcing history"); }
    private void awardHistory(Long t,Long op,Award a,String actor,String action,String reason,LocalDateTime now){ req(mapper.insertAwardHistory(new AwardHistory().setTenantId(t).setAwardId(a.getAwardId()).setOperationId(op).setAggregateVersion(a.getVersion()).setStatus(a.getStatus()).setActionCode(action).setReasonCode(reason).setActorPrincipalId(actor).setOccurredAt(now).setCreatedAt(now))==1,"failed to persist award history"); }
    private static void copy(AwardLine x,AwardSnapshotLine s){ s.setAwardLineId(x.getAwardLineId());s.setTenantId(x.getTenantId());s.setAwardId(x.getAwardId());s.setLineNumber(x.getLineNumber());s.setSourcingLineId(x.getSourcingLineId());s.setSourcingScheduleId(x.getSourcingScheduleId());s.setQuotationRevisionLineId(x.getQuotationRevisionLineId());s.setQuotationRevisionScheduleId(x.getQuotationRevisionScheduleId());s.setSupplierId(x.getSupplierId());s.setCanonicalSkuId(x.getCanonicalSkuId());s.setCanonicalWarehouseId(x.getCanonicalWarehouseId());s.setAwardedQuantity(x.getAwardedQuantity());s.setUomCode(x.getUomCode());s.setCurrencyCode(x.getCurrencyCode());s.setUnitNetPriceMinor(x.getUnitNetPriceMinor());s.setTaxCode(x.getTaxCode());s.setTaxRateBps(x.getTaxRateBps());s.setPromisedDeliveryDate(x.getPromisedDeliveryDate());s.setLineNetAmountMinor(x.getLineNetAmountMinor());s.setLineTaxAmountMinor(x.getLineTaxAmountMinor());s.setLineGrossAmountMinor(x.getLineGrossAmountMinor());s.setPolicyId(x.getPolicyId());s.setPolicyVersion(x.getPolicyVersion());s.setEvaluationWeightedScoreBps(x.getEvaluationWeightedScoreBps());s.setEvaluationSummarySha256(x.getEvaluationSummarySha256());s.setReviewerEvidenceSha256(x.getReviewerEvidenceSha256()); }
    private static <T> Map<String,T> index(List<T> xs,Function<T,String> key){ return xs.stream().collect(Collectors.toMap(key,Function.identity())); }
    private static Outcome outcome(String event,String type,String id,String code,long version,String status,Map<String,Object> payload){return new Outcome(event,type,id,code,version,status,payload);}
    private static long minor(BigDecimal v){return v.setScale(0,RoundingMode.HALF_UP).longValueExact();} private static long tax(long n,int b){return BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(b)).divide(BigDecimal.valueOf(10000),0,RoundingMode.HALF_UP).longValueExact();}
    private static void money(BigDecimal v){req(v!=null&&v.signum()>=0&&v.scale()<=6&&v.precision()<=24,"unitNetPriceMinor must be DECIMAL(24,6)");} private static void qty(BigDecimal v,String f){req(v!=null&&v.signum()>0&&v.scale()<=6&&v.precision()<=24,f+" must be positive DECIMAL(24,6)");}
    private static void ref(String v,String f,int max){req(v!=null&&v.length()<=max&&REF.matcher(v).matches(),f+" must be a safe reference");} private static void code(String v,String f){req(v!=null&&CODE.matcher(v).matches(),f+" must be an uppercase code");} private static void sha(String v,String f){req(v!=null&&SHA.matcher(v).matches(),f+" must be lowercase SHA-256");}
    private static void req(boolean ok,String m){if(!ok)throw new IllegalArgumentException(m);} private static <T>T nn(T v,String m){if(v==null)throw new IllegalArgumentException(m);return v;}
    private record Outcome(String eventType,String type,String id,String code,long version,String status,Map<String,Object> payload){}
}
