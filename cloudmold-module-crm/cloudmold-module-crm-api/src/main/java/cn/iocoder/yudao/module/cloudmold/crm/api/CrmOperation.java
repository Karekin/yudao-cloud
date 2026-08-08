package cn.iocoder.yudao.module.cloudmold.crm.api;

public enum CrmOperation {
    CREATE_CUSTOMER,
    UPDATE_CUSTOMER,
    CLAIM_CUSTOMER,
    RETURN_CUSTOMER_TO_POOL,

    CREATE_LEAD,
    UPDATE_LEAD,
    ASSIGN_LEAD,

    CREATE_CONTACT,
    UPDATE_CONTACT,

    CREATE_OPPORTUNITY,
    UPDATE_OPPORTUNITY,

    RECORD_FOLLOW_UP
}
