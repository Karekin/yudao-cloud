package cn.iocoder.yudao.module.cloudmold.crm.api;

import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface CrmQueryApi {
    CrmWorkbenchView getWorkbench(String ownerPrincipalId);

    PageResult<CrmCustomerView> getCustomers(CrmCustomerPageQuery query);

    PageResult<CrmLeadView> getLeads(CrmLeadPageQuery query);

    PageResult<CrmContactView> getContacts(CrmContactPageQuery query);

    PageResult<CrmOpportunityView> getOpportunities(CrmOpportunityPageQuery query);

    PageResult<CrmFollowUpView> getFollowUps(CrmFollowUpPageQuery query);

    PageResult<CrmCustomerView> getPool(CrmPoolPageQuery query);
}
