package cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementAdminRecords.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProcurementSourcingMapper {
    @Insert("""
      INSERT INTO cloudmold_procurement_sourcing_event(event_id,tenant_id,event_code,requisition_id,requisition_version,title,status,quotation_deadline,version,created_by_principal_id,created_at,updated_at)
      VALUES(#{eventId},#{tenantId},#{eventCode},#{requisitionId},#{requisitionVersion},#{title},#{status},#{quotationDeadline},#{version},#{createdByPrincipalId},#{createdAt},#{updatedAt})
      """) int insertEvent(SourcingEvent value);

    @Insert("""
      <script>INSERT INTO cloudmold_procurement_sourcing_line(sourcing_line_id,tenant_id,event_id,line_number,requisition_line_id,canonical_sku_id,requested_quantity,uom_code,created_at) VALUES
      <foreach collection="values" item="v" separator=",">(#{v.sourcingLineId},#{v.tenantId},#{v.eventId},#{v.lineNumber},#{v.requisitionLineId},#{v.canonicalSkuId},#{v.requestedQuantity},#{v.uomCode},#{v.createdAt})</foreach></script>
      """)
    int insertLines(@Param("values") List<SourcingLine> values);

    @Insert("""
      <script>INSERT INTO cloudmold_procurement_sourcing_schedule(sourcing_schedule_id,tenant_id,event_id,sourcing_line_id,schedule_number,requisition_schedule_id,canonical_warehouse_id,requested_quantity,required_delivery_date,created_at) VALUES
      <foreach collection="values" item="v" separator=",">(#{v.sourcingScheduleId},#{v.tenantId},#{v.eventId},#{v.sourcingLineId},#{v.scheduleNumber},#{v.requisitionScheduleId},#{v.canonicalWarehouseId},#{v.requestedQuantity},#{v.requiredDeliveryDate},#{v.createdAt})</foreach></script>
      """)
    int insertSchedules(@Param("values") List<SourcingSchedule> values);

    @Select("SELECT * FROM cloudmold_procurement_sourcing_event WHERE tenant_id=#{tenantId} AND event_id=#{eventId} FOR UPDATE")
    SourcingEvent selectEventForUpdate(@Param("tenantId") Long tenantId,@Param("eventId") String eventId);
    @Select("SELECT * FROM cloudmold_procurement_sourcing_event WHERE tenant_id=#{tenantId} AND event_id=#{eventId}")
    SourcingEvent selectEvent(@Param("tenantId") Long tenantId,@Param("eventId") String eventId);
    @Select("SELECT * FROM cloudmold_procurement_sourcing_line WHERE tenant_id=#{tenantId} AND event_id=#{eventId} ORDER BY line_number")
    List<SourcingLine> selectLines(@Param("tenantId") Long tenantId,@Param("eventId") String eventId);
    @Select("SELECT * FROM cloudmold_procurement_sourcing_schedule WHERE tenant_id=#{tenantId} AND event_id=#{eventId} ORDER BY sourcing_line_id,schedule_number")
    List<SourcingSchedule> selectSchedules(@Param("tenantId") Long tenantId,@Param("eventId") String eventId);

    @Update("""
      UPDATE cloudmold_procurement_sourcing_event SET status=#{targetStatus},version=version+1,updated_at=#{now},
      published_by_principal_id=CASE WHEN #{targetStatus}='PUBLISHED' THEN #{actor} ELSE published_by_principal_id END,
      published_at=CASE WHEN #{targetStatus}='PUBLISHED' THEN #{now} ELSE published_at END,
      quoting_opened_by_principal_id=CASE WHEN #{targetStatus}='QUOTING' THEN #{actor} ELSE quoting_opened_by_principal_id END,
      quoting_opened_at=CASE WHEN #{targetStatus}='QUOTING' THEN #{now} ELSE quoting_opened_at END,
      evaluating_by_principal_id=CASE WHEN #{targetStatus}='EVALUATING' THEN #{actor} ELSE evaluating_by_principal_id END,
      evaluating_at=CASE WHEN #{targetStatus}='EVALUATING' THEN #{now} ELSE evaluating_at END,
      award_submitted_by_principal_id=CASE WHEN #{targetStatus}='AWARD_SUBMITTED' THEN #{actor} ELSE award_submitted_by_principal_id END,
      award_submitted_at=CASE WHEN #{targetStatus}='AWARD_SUBMITTED' THEN #{now} ELSE award_submitted_at END,
      awarded_by_principal_id=CASE WHEN #{targetStatus}='AWARDED' THEN #{actor} ELSE awarded_by_principal_id END,
      awarded_at=CASE WHEN #{targetStatus}='AWARDED' THEN #{now} ELSE awarded_at END,
      closed_by_principal_id=CASE WHEN #{targetStatus}='CLOSED' THEN #{actor} ELSE closed_by_principal_id END,
      closed_at=CASE WHEN #{targetStatus}='CLOSED' THEN #{now} ELSE closed_at END,
      cancelled_by_principal_id=CASE WHEN #{targetStatus}='CANCELLED' THEN #{actor} ELSE cancelled_by_principal_id END,
      cancelled_at=CASE WHEN #{targetStatus}='CANCELLED' THEN #{now} ELSE cancelled_at END,
      terminal_reason_code=CASE WHEN #{targetStatus} IN ('CLOSED','CANCELLED') THEN #{reasonCode} ELSE terminal_reason_code END
      WHERE tenant_id=#{tenantId} AND event_id=#{eventId} AND status=#{sourceStatus} AND version=#{expectedVersion}
      """)
    int transitionEvent(@Param("tenantId") Long tenantId,@Param("eventId") String eventId,@Param("sourceStatus") String sourceStatus,
                        @Param("targetStatus") String targetStatus,@Param("expectedVersion") Long expectedVersion,
                        @Param("actor") String actor,@Param("reasonCode") String reasonCode,@Param("now") LocalDateTime now);
    @Update("UPDATE cloudmold_procurement_sourcing_event SET version=version+1,updated_at=#{now} WHERE tenant_id=#{tenantId} AND event_id=#{eventId} AND status=#{status} AND version=#{expectedVersion}")
    int bumpEvent(@Param("tenantId") Long tenantId,@Param("eventId") String eventId,@Param("status") String status,@Param("expectedVersion") Long expectedVersion,@Param("now") LocalDateTime now);

    @Insert("INSERT INTO cloudmold_procurement_supplier_invitation(invitation_id,tenant_id,event_id,supplier_id,status,invited_by_principal_id,invited_at) VALUES(#{invitationId},#{tenantId},#{eventId},#{supplierId},#{status},#{invitedByPrincipalId},#{invitedAt})")
    int insertInvitation(SupplierInvitation value);
    @Select("SELECT COUNT(*) FROM cloudmold_procurement_supplier_invitation WHERE tenant_id=#{tenantId} AND event_id=#{eventId} AND supplier_id=#{supplierId} AND status='INVITED'")
    int countActiveInvitation(@Param("tenantId") Long tenantId,@Param("eventId") String eventId,@Param("supplierId") String supplierId);

    @Select("SELECT * FROM cloudmold_procurement_quotation WHERE tenant_id=#{tenantId} AND event_id=#{eventId} AND supplier_id=#{supplierId} FOR UPDATE")
    Quotation selectQuotationForUpdate(@Param("tenantId") Long tenantId,@Param("eventId") String eventId,@Param("supplierId") String supplierId);
    @Insert("INSERT INTO cloudmold_procurement_quotation(quotation_id,tenant_id,quotation_code,event_id,supplier_id,currency_code,latest_revision_number,active_revision_id,created_at) VALUES(#{quotationId},#{tenantId},#{quotationCode},#{eventId},#{supplierId},#{currencyCode},#{latestRevisionNumber},#{activeRevisionId},#{createdAt})")
    int insertQuotation(Quotation value);
    @Update("UPDATE cloudmold_procurement_quotation SET latest_revision_number=#{revisionNumber},active_revision_id=#{revisionId} WHERE tenant_id=#{tenantId} AND quotation_id=#{quotationId} AND latest_revision_number=#{previousRevisionNumber}")
    int advanceQuotation(@Param("tenantId") Long tenantId,@Param("quotationId") String quotationId,@Param("previousRevisionNumber") Integer previousRevisionNumber,@Param("revisionNumber") Integer revisionNumber,@Param("revisionId") String revisionId);
    @Update("UPDATE cloudmold_procurement_quotation SET active_revision_id=#{revisionId} WHERE tenant_id=#{tenantId} AND quotation_id=#{quotationId} AND latest_revision_number=1 AND active_revision_id IS NULL")
    int activateInitialRevision(@Param("tenantId") Long tenantId,@Param("quotationId") String quotationId,@Param("revisionId") String revisionId);
    @Update("UPDATE cloudmold_procurement_quotation_revision SET status='SUPERSEDED',terminal_by_principal_id=#{actor},terminal_at=#{now} WHERE tenant_id=#{tenantId} AND revision_id=#{revisionId} AND status='SUBMITTED' AND terminal_by_principal_id IS NULL AND terminal_at IS NULL")
    int supersedeRevision(@Param("tenantId") Long tenantId,@Param("revisionId") String revisionId,@Param("actor") String actor,@Param("now") LocalDateTime now);
    @Insert("INSERT INTO cloudmold_procurement_quotation_revision(revision_id,tenant_id,quotation_id,event_id,revision_number,status,submitted_by_principal_id,submitted_at,payload_sha256) VALUES(#{revisionId},#{tenantId},#{quotationId},#{eventId},#{revisionNumber},#{status},#{submittedByPrincipalId},#{submittedAt},#{payloadSha256})")
    int insertRevision(QuotationRevision value);
    @Insert("""
      <script>INSERT INTO cloudmold_procurement_quotation_revision_line(revision_line_id,tenant_id,revision_id,event_id,line_number,sourcing_line_id,offered_quantity,uom_code,unit_net_price_minor,tax_code,tax_rate_bps,line_net_amount_minor,line_tax_amount_minor,line_gross_amount_minor) VALUES
      <foreach collection="values" item="v" separator=",">(#{v.revisionLineId},#{v.tenantId},#{v.revisionId},#{v.eventId},#{v.lineNumber},#{v.sourcingLineId},#{v.offeredQuantity},#{v.uomCode},#{v.unitNetPriceMinor},#{v.taxCode},#{v.taxRateBps},#{v.lineNetAmountMinor},#{v.lineTaxAmountMinor},#{v.lineGrossAmountMinor})</foreach></script>
      """) int insertRevisionLines(@Param("values") List<QuotationRevisionLine> values);
    @Insert("""
      <script>INSERT INTO cloudmold_procurement_quotation_revision_schedule(revision_schedule_id,tenant_id,revision_id,revision_line_id,schedule_number,sourcing_schedule_id,offered_quantity,promised_delivery_date) VALUES
      <foreach collection="values" item="v" separator=",">(#{v.revisionScheduleId},#{v.tenantId},#{v.revisionId},#{v.revisionLineId},#{v.scheduleNumber},#{v.sourcingScheduleId},#{v.offeredQuantity},#{v.promisedDeliveryDate})</foreach></script>
      """) int insertRevisionSchedules(@Param("values") List<QuotationRevisionSchedule> values);
    @Select("SELECT * FROM cloudmold_procurement_quotation_revision WHERE tenant_id=#{tenantId} AND revision_id=#{revisionId} FOR UPDATE")
    QuotationRevision selectRevisionForUpdate(@Param("tenantId") Long tenantId,@Param("revisionId") String revisionId);
    @Update("UPDATE cloudmold_procurement_quotation_revision SET status='WITHDRAWN',withdrawal_reason_code=#{reasonCode},terminal_by_principal_id=#{actor},terminal_at=#{now} WHERE tenant_id=#{tenantId} AND revision_id=#{revisionId} AND status='SUBMITTED' AND terminal_by_principal_id IS NULL AND terminal_at IS NULL")
    int withdrawRevision(@Param("tenantId") Long tenantId,@Param("revisionId") String revisionId,@Param("reasonCode") String reasonCode,@Param("actor") String actor,@Param("now") LocalDateTime now);
    @Update("UPDATE cloudmold_procurement_quotation SET active_revision_id=NULL WHERE tenant_id=#{tenantId} AND quotation_id=#{quotationId} AND active_revision_id=#{revisionId}")
    int clearActiveRevision(@Param("tenantId") Long tenantId,@Param("quotationId") String quotationId,@Param("revisionId") String revisionId);
    @Select("SELECT * FROM cloudmold_procurement_quotation_revision WHERE tenant_id=#{tenantId} AND event_id=#{eventId} AND status='SUBMITTED'")
    List<QuotationRevision> selectActiveRevisions(@Param("tenantId") Long tenantId,@Param("eventId") String eventId);
    @Select("SELECT * FROM cloudmold_procurement_quotation_revision_line WHERE tenant_id=#{tenantId} AND event_id=#{eventId} AND revision_id IN (SELECT revision_id FROM cloudmold_procurement_quotation_revision WHERE tenant_id=#{tenantId} AND event_id=#{eventId} AND status='SUBMITTED')")
    List<QuotationRevisionLine> selectActiveRevisionLines(@Param("tenantId") Long tenantId,@Param("eventId") String eventId);
    @Select("SELECT * FROM cloudmold_procurement_quotation_revision_schedule WHERE tenant_id=#{tenantId} AND revision_id IN (SELECT revision_id FROM cloudmold_procurement_quotation_revision WHERE tenant_id=#{tenantId} AND event_id=#{eventId} AND status='SUBMITTED')")
    List<QuotationRevisionSchedule> selectActiveRevisionSchedules(@Param("tenantId") Long tenantId,@Param("eventId") String eventId);
    @Select("SELECT * FROM cloudmold_procurement_quotation WHERE tenant_id=#{tenantId} AND event_id=#{eventId}")
    List<Quotation> selectQuotations(@Param("tenantId") Long tenantId,@Param("eventId") String eventId);

    @Insert("INSERT INTO cloudmold_procurement_evaluation_policy(policy_id,tenant_id,policy_code,event_id,active_version,created_at) VALUES(#{policyId},#{tenantId},#{policyCode},#{eventId},#{activeVersion},#{createdAt})") int insertPolicy(EvaluationPolicy value);
    @Insert("INSERT INTO cloudmold_procurement_evaluation_policy_version(policy_id,tenant_id,policy_version,status,created_by_principal_id,created_at,policy_sha256) VALUES(#{policyId},#{tenantId},#{policyVersion},#{status},#{createdByPrincipalId},#{createdAt},#{policySha256})") int insertPolicyVersion(EvaluationPolicyVersion value);
    @Insert("""
      <script>INSERT INTO cloudmold_procurement_evaluation_dimension(dimension_id,tenant_id,policy_id,policy_version,dimension_code,dimension_name,weight_bps,maximum_score) VALUES
      <foreach collection="values" item="v" separator=",">(#{v.dimensionId},#{v.tenantId},#{v.policyId},#{v.policyVersion},#{v.dimensionCode},#{v.dimensionName},#{v.weightBps},#{v.maximumScore})</foreach></script>
      """) int insertDimensions(@Param("values") List<EvaluationDimension> values);
    @Select("SELECT * FROM cloudmold_procurement_evaluation_policy WHERE tenant_id=#{tenantId} AND policy_id=#{policyId} AND event_id=#{eventId}") EvaluationPolicy selectPolicy(@Param("tenantId") Long tenantId,@Param("eventId") String eventId,@Param("policyId") String policyId);
    @Select("SELECT * FROM cloudmold_procurement_evaluation_policy WHERE tenant_id=#{tenantId} AND policy_id=#{policyId} AND event_id=#{eventId} FOR UPDATE") EvaluationPolicy selectPolicyForUpdate(@Param("tenantId") Long tenantId,@Param("eventId") String eventId,@Param("policyId") String policyId);
    @Update("UPDATE cloudmold_procurement_evaluation_policy SET active_version=#{newVersion} WHERE tenant_id=#{tenantId} AND policy_id=#{policyId} AND active_version=#{previousVersion}") int advancePolicyVersion(@Param("tenantId") Long tenantId,@Param("policyId") String policyId,@Param("previousVersion") Integer previousVersion,@Param("newVersion") Integer newVersion);
    @Select("SELECT * FROM cloudmold_procurement_evaluation_dimension WHERE tenant_id=#{tenantId} AND policy_id=#{policyId} AND policy_version=#{policyVersion}") List<EvaluationDimension> selectDimensions(@Param("tenantId") Long tenantId,@Param("policyId") String policyId,@Param("policyVersion") Integer policyVersion);
    @Insert("INSERT INTO cloudmold_procurement_evaluation_score(score_id,tenant_id,event_id,policy_id,policy_version,quotation_revision_id,reviewer_principal_id,weighted_score_bps,reviewer_evidence_sha256,evaluation_summary_sha256,created_at) VALUES(#{scoreId},#{tenantId},#{eventId},#{policyId},#{policyVersion},#{quotationRevisionId},#{reviewerPrincipalId},#{weightedScoreBps},#{reviewerEvidenceSha256},#{evaluationSummarySha256},#{createdAt})") int insertScore(EvaluationScore value);
    @Insert("""
      <script>INSERT INTO cloudmold_procurement_evaluation_dimension_score(score_id,tenant_id,dimension_id,score,evidence_reference) VALUES
      <foreach collection="values" item="v" separator=",">(#{v.scoreId},#{v.tenantId},#{v.dimensionId},#{v.score},#{v.evidenceReference})</foreach></script>
      """) int insertDimensionScores(@Param("values") List<EvaluationDimensionScore> values);
    @Select("SELECT * FROM cloudmold_procurement_evaluation_score WHERE tenant_id=#{tenantId} AND event_id=#{eventId} AND policy_id=#{policyId} AND policy_version=#{policyVersion} AND quotation_revision_id=#{revisionId} ORDER BY created_at,score_id") List<EvaluationScore> selectScores(@Param("tenantId") Long tenantId,@Param("eventId") String eventId,@Param("policyId") String policyId,@Param("policyVersion") Integer policyVersion,@Param("revisionId") String revisionId);

    @Insert("INSERT INTO cloudmold_procurement_award(award_id,tenant_id,award_code,event_id,policy_id,policy_version,status,decision_reason_code,version,created_by_principal_id,created_at,updated_at) VALUES(#{awardId},#{tenantId},#{awardCode},#{eventId},#{policyId},#{policyVersion},#{status},#{decisionReasonCode},#{version},#{createdByPrincipalId},#{createdAt},#{updatedAt})") int insertAward(Award value);
    @Insert("""
      <script>INSERT INTO cloudmold_procurement_award_line(award_line_id,tenant_id,award_id,line_number,sourcing_line_id,sourcing_schedule_id,quotation_revision_line_id,quotation_revision_schedule_id,supplier_id,canonical_sku_id,canonical_warehouse_id,awarded_quantity,uom_code,currency_code,unit_net_price_minor,tax_code,tax_rate_bps,promised_delivery_date,line_net_amount_minor,line_tax_amount_minor,line_gross_amount_minor,policy_id,policy_version,evaluation_weighted_score_bps,evaluation_summary_sha256,reviewer_evidence_sha256) VALUES
      <foreach collection="values" item="v" separator=",">(#{v.awardLineId},#{v.tenantId},#{v.awardId},#{v.lineNumber},#{v.sourcingLineId},#{v.sourcingScheduleId},#{v.quotationRevisionLineId},#{v.quotationRevisionScheduleId},#{v.supplierId},#{v.canonicalSkuId},#{v.canonicalWarehouseId},#{v.awardedQuantity},#{v.uomCode},#{v.currencyCode},#{v.unitNetPriceMinor},#{v.taxCode},#{v.taxRateBps},#{v.promisedDeliveryDate},#{v.lineNetAmountMinor},#{v.lineTaxAmountMinor},#{v.lineGrossAmountMinor},#{v.policyId},#{v.policyVersion},#{v.evaluationWeightedScoreBps},#{v.evaluationSummarySha256},#{v.reviewerEvidenceSha256})</foreach></script>
      """) int insertAwardLines(@Param("values") List<AwardLine> values);
    @Select("SELECT * FROM cloudmold_procurement_award WHERE tenant_id=#{tenantId} AND award_id=#{awardId} FOR UPDATE") Award selectAwardForUpdate(@Param("tenantId") Long tenantId,@Param("awardId") String awardId);
    @Select("SELECT * FROM cloudmold_procurement_award WHERE tenant_id=#{tenantId} AND award_id=#{awardId} AND status='APPROVED'") Award selectApprovedAward(@Param("tenantId") Long tenantId,@Param("awardId") String awardId);
    @Select("SELECT * FROM cloudmold_procurement_award_line WHERE tenant_id=#{tenantId} AND award_id=#{awardId} ORDER BY line_number") List<AwardLine> selectAwardLines(@Param("tenantId") Long tenantId,@Param("awardId") String awardId);
    @Select("SELECT DISTINCT s.reviewer_principal_id FROM cloudmold_procurement_award_line al JOIN cloudmold_procurement_quotation_revision_line ql ON ql.tenant_id=al.tenant_id AND ql.revision_line_id=al.quotation_revision_line_id JOIN cloudmold_procurement_evaluation_score s ON s.tenant_id=al.tenant_id AND s.quotation_revision_id=ql.revision_id AND s.policy_id=al.policy_id AND s.policy_version=al.policy_version WHERE al.tenant_id=#{tenantId} AND al.award_id=#{awardId}")
    List<String> selectAwardReviewerPrincipalIds(@Param("tenantId") Long tenantId,@Param("awardId") String awardId);
    @Update("""
      UPDATE cloudmold_procurement_award SET status=#{targetStatus},version=version+1,updated_at=#{now},
      submitted_by_principal_id=CASE WHEN #{targetStatus}='SUBMITTED' THEN #{actor} ELSE submitted_by_principal_id END,
      submitted_at=CASE WHEN #{targetStatus}='SUBMITTED' THEN #{now} ELSE submitted_at END,
      approved_by_principal_id=CASE WHEN #{targetStatus}='APPROVED' THEN #{actor} ELSE approved_by_principal_id END,
      approved_at=CASE WHEN #{targetStatus}='APPROVED' THEN #{now} ELSE approved_at END,
      rejected_by_principal_id=CASE WHEN #{targetStatus}='REJECTED' THEN #{actor} ELSE rejected_by_principal_id END,
      rejected_at=CASE WHEN #{targetStatus}='REJECTED' THEN #{now} ELSE rejected_at END
      WHERE tenant_id=#{tenantId} AND award_id=#{awardId} AND status=#{sourceStatus} AND version=#{expectedVersion}
      """) int transitionAward(@Param("tenantId") Long tenantId,@Param("awardId") String awardId,@Param("sourceStatus") String sourceStatus,@Param("targetStatus") String targetStatus,@Param("expectedVersion") Long expectedVersion,@Param("actor") String actor,@Param("now") LocalDateTime now);

    @Insert("INSERT INTO cloudmold_procurement_award_snapshot(snapshot_id,tenant_id,award_id,award_version,event_id,event_version,status,decision_reason_code,approved_by_principal_id,approved_at,created_at) VALUES(#{snapshotId},#{tenantId},#{awardId},#{awardVersion},#{eventId},#{eventVersion},#{status},#{decisionReasonCode},#{approvedByPrincipalId},#{approvedAt},#{createdAt})") int insertAwardSnapshot(AwardSnapshot value);
    @Insert("""
      <script>INSERT INTO cloudmold_procurement_award_snapshot_line(snapshot_line_id,tenant_id,snapshot_id,award_line_id,line_number,sourcing_line_id,sourcing_schedule_id,quotation_revision_line_id,quotation_revision_schedule_id,supplier_id,canonical_sku_id,canonical_warehouse_id,awarded_quantity,uom_code,currency_code,unit_net_price_minor,tax_code,tax_rate_bps,promised_delivery_date,line_net_amount_minor,line_tax_amount_minor,line_gross_amount_minor,policy_id,policy_version,evaluation_weighted_score_bps,evaluation_summary_sha256,reviewer_evidence_sha256) VALUES
      <foreach collection="values" item="v" separator=",">(#{v.snapshotLineId},#{v.tenantId},#{v.snapshotId},#{v.awardLineId},#{v.lineNumber},#{v.sourcingLineId},#{v.sourcingScheduleId},#{v.quotationRevisionLineId},#{v.quotationRevisionScheduleId},#{v.supplierId},#{v.canonicalSkuId},#{v.canonicalWarehouseId},#{v.awardedQuantity},#{v.uomCode},#{v.currencyCode},#{v.unitNetPriceMinor},#{v.taxCode},#{v.taxRateBps},#{v.promisedDeliveryDate},#{v.lineNetAmountMinor},#{v.lineTaxAmountMinor},#{v.lineGrossAmountMinor},#{v.policyId},#{v.policyVersion},#{v.evaluationWeightedScoreBps},#{v.evaluationSummarySha256},#{v.reviewerEvidenceSha256})</foreach></script>
      """) int insertAwardSnapshotLines(@Param("values") List<AwardSnapshotLine> values);
    @Insert("INSERT INTO cloudmold_procurement_sourcing_history(tenant_id,event_id,operation_id,aggregate_version,status,action_code,actor_principal_id,occurred_at,created_at) VALUES(#{tenantId},#{eventId},#{operationId},#{aggregateVersion},#{status},#{actionCode},#{actorPrincipalId},#{occurredAt},#{createdAt})") int insertHistory(SourcingHistory value);
    @Insert("INSERT INTO cloudmold_procurement_award_history(tenant_id,award_id,operation_id,aggregate_version,status,action_code,reason_code,actor_principal_id,occurred_at,created_at) VALUES(#{tenantId},#{awardId},#{operationId},#{aggregateVersion},#{status},#{actionCode},#{reasonCode},#{actorPrincipalId},#{occurredAt},#{createdAt})") int insertAwardHistory(AwardHistory value);

    @Select("""
      SELECT COUNT(*) FROM cloudmold_procurement_sourcing_event e
      WHERE e.tenant_id=#{tenantId} AND (#{status} IS NULL OR e.status=#{status})
       AND (#{keyword} IS NULL OR #{keyword}='' OR e.event_code LIKE CONCAT('%',#{keyword},'%')
        OR e.event_id LIKE CONCAT('%',#{keyword},'%') OR e.title LIKE CONCAT('%',#{keyword},'%')
        OR EXISTS (SELECT 1 FROM cloudmold_purchase_requisition pr WHERE pr.tenant_id=e.tenant_id
          AND pr.requisition_id=e.requisition_id AND pr.requisition_code LIKE CONCAT('%',#{keyword},'%'))
        OR EXISTS (SELECT 1 FROM cloudmold_procurement_sourcing_line l WHERE l.tenant_id=e.tenant_id
          AND l.event_id=e.event_id AND l.canonical_sku_id LIKE CONCAT('%',#{keyword},'%'))
        OR EXISTS (SELECT 1 FROM cloudmold_procurement_supplier_invitation i WHERE i.tenant_id=e.tenant_id
          AND i.event_id=e.event_id AND i.supplier_id LIKE CONCAT('%',#{keyword},'%')))
      """) long countEventPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword);
    @Select("SELECT * FROM cloudmold_procurement_sourcing_event WHERE tenant_id=#{tenantId} AND (#{status} IS NULL OR status=#{status}) ORDER BY created_at DESC,event_id LIMIT #{limit} OFFSET #{offset}") List<SourcingEvent> selectEventPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("offset") long offset,@Param("limit") int limit);
    @Select("""
      SELECT COUNT(*) FROM cloudmold_procurement_quotation q
      WHERE q.tenant_id=#{tenantId} AND (#{keyword} IS NULL OR #{keyword}=''
       OR q.quotation_code LIKE CONCAT('%',#{keyword},'%') OR q.quotation_id LIKE CONCAT('%',#{keyword},'%')
       OR q.supplier_id LIKE CONCAT('%',#{keyword},'%')
       OR EXISTS (SELECT 1 FROM cloudmold_procurement_sourcing_event e WHERE e.tenant_id=q.tenant_id
         AND e.event_id=q.event_id AND e.event_code LIKE CONCAT('%',#{keyword},'%'))
       OR EXISTS (SELECT 1 FROM cloudmold_procurement_quotation_revision r
         JOIN cloudmold_procurement_quotation_revision_line l ON l.tenant_id=r.tenant_id AND l.revision_id=r.revision_id
         JOIN cloudmold_procurement_sourcing_line sl ON sl.tenant_id=l.tenant_id AND sl.sourcing_line_id=l.sourcing_line_id
         WHERE r.tenant_id=q.tenant_id AND r.quotation_id=q.quotation_id
           AND sl.canonical_sku_id LIKE CONCAT('%',#{keyword},'%')))
      """) long countQuotationPage(@Param("tenantId") Long tenantId,@Param("keyword") String keyword);
    @Select("SELECT * FROM cloudmold_procurement_quotation WHERE tenant_id=#{tenantId} ORDER BY created_at DESC,quotation_id LIMIT #{limit} OFFSET #{offset}") List<Quotation> selectQuotationPage(@Param("tenantId") Long tenantId,@Param("offset") long offset,@Param("limit") int limit);
    @Select("SELECT * FROM cloudmold_procurement_quotation_revision WHERE tenant_id=#{tenantId} AND quotation_id=#{quotationId} ORDER BY revision_number DESC") List<QuotationRevision> selectQuotationRevisions(@Param("tenantId") Long tenantId,@Param("quotationId") String quotationId);
    @Select("""
      SELECT COUNT(*) FROM cloudmold_procurement_award a
      WHERE a.tenant_id=#{tenantId} AND (#{status} IS NULL OR a.status=#{status})
       AND (#{keyword} IS NULL OR #{keyword}='' OR a.award_code LIKE CONCAT('%',#{keyword},'%')
        OR a.award_id LIKE CONCAT('%',#{keyword},'%')
        OR EXISTS (SELECT 1 FROM cloudmold_procurement_sourcing_event e WHERE e.tenant_id=a.tenant_id
          AND e.event_id=a.event_id AND e.event_code LIKE CONCAT('%',#{keyword},'%'))
        OR EXISTS (SELECT 1 FROM cloudmold_procurement_award_line l WHERE l.tenant_id=a.tenant_id
          AND l.award_id=a.award_id AND (l.supplier_id LIKE CONCAT('%',#{keyword},'%')
            OR l.canonical_sku_id LIKE CONCAT('%',#{keyword},'%'))))
      """) long countAwardPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword);
    @Select("SELECT * FROM cloudmold_procurement_award WHERE tenant_id=#{tenantId} AND (#{status} IS NULL OR status=#{status}) ORDER BY created_at DESC,award_id LIMIT #{limit} OFFSET #{offset}") List<Award> selectAwardPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("offset") long offset,@Param("limit") int limit);
    @Select("SELECT * FROM cloudmold_procurement_award WHERE tenant_id=#{tenantId} AND award_id=#{awardId}") Award selectAward(@Param("tenantId") Long tenantId,@Param("awardId") String awardId);

    @Select("""
      SELECT e.version aggregate_version,e.event_code,e.event_id,COUNT(DISTINCT i.invitation_id) invitation_count,
       COUNT(DISTINCT l.sourcing_line_id) line_count,e.quotation_deadline,COUNT(DISTINCT r.revision_id) quotation_revision_count,
       pr.requisition_code,e.requisition_id,e.status,e.title,e.updated_at
      FROM cloudmold_procurement_sourcing_event e JOIN cloudmold_purchase_requisition pr ON pr.tenant_id=e.tenant_id AND pr.requisition_id=e.requisition_id
       LEFT JOIN cloudmold_procurement_sourcing_line l ON l.tenant_id=e.tenant_id AND l.event_id=e.event_id
       LEFT JOIN cloudmold_procurement_supplier_invitation i ON i.tenant_id=e.tenant_id AND i.event_id=e.event_id
       LEFT JOIN cloudmold_procurement_quotation_revision r ON r.tenant_id=e.tenant_id AND r.event_id=e.event_id
      WHERE e.tenant_id=#{tenantId} AND (#{status} IS NULL OR e.status=#{status})
       AND (#{keyword} IS NULL OR #{keyword}='' OR e.event_code LIKE CONCAT('%',#{keyword},'%')
        OR e.event_id LIKE CONCAT('%',#{keyword},'%') OR e.title LIKE CONCAT('%',#{keyword},'%')
        OR pr.requisition_code LIKE CONCAT('%',#{keyword},'%')
        OR l.canonical_sku_id LIKE CONCAT('%',#{keyword},'%') OR i.supplier_id LIKE CONCAT('%',#{keyword},'%'))
      GROUP BY e.event_id
      ORDER BY e.updated_at DESC,e.event_id LIMIT #{limit} OFFSET #{offset}
      """) List<RfqSummary> selectRfqAdminPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("limit") int limit);

    @Select("""
      SELECT q.latest_revision_number aggregate_version,q.currency_code,e.event_code,q.event_id,COALESCE(SUM(l.line_gross_amount_minor),0) gross_amount_minor,
       COUNT(l.revision_line_id) line_count,q.quotation_code,q.quotation_id,MAX(r.revision_number) revision_number,COALESCE(MAX(r.status),'WITHDRAWN') status,q.supplier_id,MAX(r.submitted_at) submitted_at,
       COALESCE(MAX(r.terminal_at),MAX(r.submitted_at),q.created_at) updated_at
      FROM cloudmold_procurement_quotation q JOIN cloudmold_procurement_sourcing_event e ON e.tenant_id=q.tenant_id AND e.event_id=q.event_id
       LEFT JOIN cloudmold_procurement_quotation_revision r ON r.tenant_id=q.tenant_id AND r.revision_id=COALESCE(q.active_revision_id,(SELECT rr.revision_id FROM cloudmold_procurement_quotation_revision rr WHERE rr.tenant_id=q.tenant_id AND rr.quotation_id=q.quotation_id ORDER BY rr.revision_number DESC LIMIT 1))
       LEFT JOIN cloudmold_procurement_quotation_revision_line l ON l.tenant_id=q.tenant_id AND l.revision_id=r.revision_id
       LEFT JOIN cloudmold_procurement_sourcing_line sl ON sl.tenant_id=l.tenant_id AND sl.sourcing_line_id=l.sourcing_line_id
      WHERE q.tenant_id=#{tenantId} AND (#{keyword} IS NULL OR #{keyword}=''
       OR q.quotation_code LIKE CONCAT('%',#{keyword},'%') OR q.quotation_id LIKE CONCAT('%',#{keyword},'%')
       OR q.supplier_id LIKE CONCAT('%',#{keyword},'%') OR e.event_code LIKE CONCAT('%',#{keyword},'%')
       OR sl.canonical_sku_id LIKE CONCAT('%',#{keyword},'%'))
      GROUP BY q.quotation_id ORDER BY updated_at DESC,q.quotation_id LIMIT #{limit} OFFSET #{offset}
      """) List<QuotationSummary> selectQuotationAdminPage(@Param("tenantId") Long tenantId,@Param("keyword") String keyword,@Param("offset") long offset,@Param("limit") int limit);

    @Select("""
      SELECT a.version aggregate_version,a.award_code,a.award_id,COALESCE(SUM(l.awarded_quantity),0) awarded_quantity,e.version event_aggregate_version,e.event_code,a.event_id,
       COUNT(l.award_line_id) line_count,(SELECT COUNT(*) FROM cloudmold_procurement_order po WHERE po.tenant_id=a.tenant_id AND po.award_id=a.award_id) purchase_order_count,
       a.status,COUNT(DISTINCT l.supplier_id) supplier_count,a.updated_at
      FROM cloudmold_procurement_award a JOIN cloudmold_procurement_sourcing_event e ON e.tenant_id=a.tenant_id AND e.event_id=a.event_id
       LEFT JOIN cloudmold_procurement_award_line l ON l.tenant_id=a.tenant_id AND l.award_id=a.award_id
      WHERE a.tenant_id=#{tenantId} AND (#{status} IS NULL OR a.status=#{status})
       AND (#{keyword} IS NULL OR #{keyword}='' OR a.award_code LIKE CONCAT('%',#{keyword},'%')
        OR a.award_id LIKE CONCAT('%',#{keyword},'%') OR e.event_code LIKE CONCAT('%',#{keyword},'%')
        OR l.supplier_id LIKE CONCAT('%',#{keyword},'%') OR l.canonical_sku_id LIKE CONCAT('%',#{keyword},'%'))
      GROUP BY a.award_id
      ORDER BY a.updated_at DESC,a.award_id LIMIT #{limit} OFFSET #{offset}
      """) List<AwardSummary> selectAwardAdminPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("limit") int limit);

    @Select("""
      SELECT al.award_line_id,al.line_gross_amount_minor awarded_gross_amount_minor,al.awarded_quantity,al.canonical_sku_id,al.line_number,
       al.promised_delivery_date,q.quotation_id,al.quotation_revision_line_id quotation_line_id,r.revision_number,e.requisition_id,
       sl.requisition_line_id,ss.requisition_schedule_id,al.evaluation_weighted_score_bps score,al.sourcing_line_id,al.sourcing_schedule_id,
       al.supplier_id,al.unit_net_price_minor,al.uom_code
      FROM cloudmold_procurement_award_line al JOIN cloudmold_procurement_quotation_revision_line ql ON ql.tenant_id=al.tenant_id AND ql.revision_line_id=al.quotation_revision_line_id
       JOIN cloudmold_procurement_quotation_revision r ON r.tenant_id=al.tenant_id AND r.revision_id=ql.revision_id
       JOIN cloudmold_procurement_quotation q ON q.tenant_id=al.tenant_id AND q.quotation_id=r.quotation_id
       JOIN cloudmold_procurement_sourcing_line sl ON sl.tenant_id=al.tenant_id AND sl.sourcing_line_id=al.sourcing_line_id
       JOIN cloudmold_procurement_sourcing_schedule ss ON ss.tenant_id=al.tenant_id AND ss.sourcing_schedule_id=al.sourcing_schedule_id
       JOIN cloudmold_procurement_sourcing_event e ON e.tenant_id=al.tenant_id AND e.event_id=sl.event_id
      WHERE al.tenant_id=#{tenantId} AND al.award_id=#{awardId} ORDER BY al.line_number
      """) List<AwardLineDetail> selectAwardAdminLines(@Param("tenantId") Long tenantId,@Param("awardId") String awardId);

    @Select("SELECT * FROM cloudmold_procurement_award_history WHERE tenant_id=#{tenantId} AND award_id=#{awardId} ORDER BY aggregate_version") List<AwardHistory> selectAwardHistory(@Param("tenantId") Long tenantId,@Param("awardId") String awardId);
}
