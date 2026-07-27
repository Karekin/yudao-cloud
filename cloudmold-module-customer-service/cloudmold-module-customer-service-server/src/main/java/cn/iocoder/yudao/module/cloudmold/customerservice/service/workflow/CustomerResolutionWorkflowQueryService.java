package cn.iocoder.yudao.module.cloudmold.customerservice.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow.CustomerResolutionWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow.CustomerResolutionWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow.CustomerResolutionWorkflowResult.Artifact;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow.CustomerResolutionWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.dal.mysql.CustomerServiceStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomerResolutionWorkflowQueryService implements CustomerResolutionWorkflowQueryPort {

    private final CustomerServiceStoreMapper mapper;

    @Override
    public CustomerResolutionWorkflowResult inspect(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            throw new IllegalArgumentException("ticketId is required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String key = ticketId.trim();
        CustomerServiceTicketDO ticket = mapper.selectTicket(tenantId, key);
        if (ticket == null) {
            return result(key, Status.PREPARE, "工单准备", false, "尚未创建客服工单", 0L,
                    List.of("Customer Service SoR 中不存在工单"), List.of("创建并分类客服工单"), List.of());
        }
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("TICKET", ticket.getTicketId(), ticket.getStatus(), ticket.getVersion(), "客服工单 " + ticket.getTicketNo()));
        if (!StringUtils.hasText(ticket.getCategoryCode())) {
            return result(key, Status.WAITING, "问题分类", false, "客服问题尚未完成分类", ticket.getVersion(),
                    List.of("工单分类编码为空"), List.of("完成问题分类"), artifacts);
        }
        if ("OPEN".equals(ticket.getStatus())) {
            boolean assigned = StringUtils.hasText(ticket.getAssignedAgentPrincipalId());
            return result(key, Status.WAITING, assigned ? "开始处理" : "工单分派", false,
                    assigned ? "工单已分派，等待客服开始处理" : "工单尚未分派客服",
                    ticket.getVersion(), List.of(assigned ? "工单仍为 OPEN" : "缺少负责客服"),
                    List.of(assigned ? "开始处理工单" : "分派负责客服"), artifacts);
        }
        if ("IN_PROGRESS".equals(ticket.getStatus())) {
            return result(key, Status.RUNNING, "问题处理", false, "客服正在处理该工单", ticket.getVersion(),
                    List.of(), List.of("解决工单并记录处理结果"), artifacts);
        }
        if ("RESOLVED".equals(ticket.getStatus())) {
            return result(key, Status.WAITING, "结案确认", false, "问题已解决，等待关闭工单", ticket.getVersion(),
                    List.of("工单尚未关闭"), List.of("关闭工单"), artifacts);
        }
        if (!"CLOSED".equals(ticket.getStatus())) {
            return result(key, Status.WAITING, "状态核对", false, "客服工单处于未识别状态", ticket.getVersion(),
                    List.of("未知工单状态：" + ticket.getStatus()), List.of("人工核对工单状态"), artifacts);
        }

        List<CustomerServiceClaimDO> claims = safe(mapper.selectClaimsByTicket(tenantId, key));
        List<CustomerServiceCompensationEntryDO> entries = safe(mapper.selectCompensationEntriesByTicket(tenantId, key));
        List<CustomerServiceBuyerFeedbackDO> feedback = safe(mapper.selectBuyerFeedbackByTicket(tenantId, key));
        claims.forEach(claim -> artifacts.add(artifact("CLAIM", claim.getClaimId(), claim.getStatus(), claim.getVersion(), "客户诉求 " + claim.getClaimCode())));
        entries.forEach(entry -> artifacts.add(artifact("COMPENSATION", entry.getCompensationEntryId(), entry.getEntryType(), null, "赔付凭证")));
        feedback.forEach(item -> artifacts.add(artifact("BUYER_FEEDBACK", item.getFeedbackId(), item.getSentimentCode(), null, "客户回访")));
        List<String> blockers = new ArrayList<>();
        List<String> next = new ArrayList<>();
        if (claims.stream().anyMatch(claim -> "REQUESTED".equals(claim.getStatus()) || "APPROVED".equals(claim.getStatus()))) {
            blockers.add("仍有未终结的客户赔付诉求");
            next.add("完成赔付审批或支付");
        }
        Set<String> entryIds = new HashSet<>();
        entries.forEach(entry -> entryIds.add(entry.getCompensationEntryId()));
        if (claims.stream().anyMatch(claim -> "PAID".equals(claim.getStatus())
                && (!StringUtils.hasText(claim.getCompensationEntryId()) || !entryIds.contains(claim.getCompensationEntryId())))) {
            blockers.add("已支付诉求缺少真实赔付凭证");
            next.add("补齐赔付台账凭证");
        }
        if (feedback.isEmpty()) {
            blockers.add("尚未完成客户回访");
            next.add("回访客户并记录反馈");
        }
        if (!blockers.isEmpty()) {
            return result(key, Status.WAITING, "赔付与回访", false, "工单已关闭，但客服闭环证据尚不完整",
                    ticket.getVersion(), blockers, next, artifacts);
        }
        return result(key, Status.SUCCEEDED, "客服闭环完成", true,
                "工单 " + ticket.getTicketNo() + " 已完成分类、分派、处理、赔付核验和客户回访",
                ticket.getVersion(), List.of(), List.of(), artifacts);
    }

    private static CustomerResolutionWorkflowResult result(String key, Status status, String phase, boolean terminal,
                                                            String summary, Long version, List<String> blockers,
                                                            List<String> next, List<Artifact> artifacts) {
        return CustomerResolutionWorkflowResult.builder().workflowType("CustomerResolutionWorkflow")
                .workflowInstanceKey("customer-resolution:" + key).businessKey(key).status(status).phase(phase)
                .terminal(terminal).actionRequired(!next.isEmpty()).summary(summary).aggregateVersion(version)
                .blockers(blockers).nextActions(next).artifacts(artifacts).build();
    }

    private static Artifact artifact(String type, String id, String status, Long version, String label) {
        return Artifact.builder().type(type).id(id).status(status).version(version).label(label).build();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
