package cn.iocoder.yudao.module.cloudmold.crm.controller.admin;

import cn.iocoder.yudao.module.cloudmold.crm.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrmAdminControllerSecurityTest {

    @Test
    void commandRequiresCrmCommandPermission() throws NoSuchMethodException {
        PreAuthorize authorization = CrmAdminController.class
                .getMethod("execute", CrmCommand.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:crm:command')", authorization.value());
    }

    @Test
    void workbenchRequiresCrmQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = CrmAdminController.class
                .getMethod("getWorkbench", String.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:crm:query')", authorization.value());
    }

    @Test
    void customersRequiresCrmQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = CrmAdminController.class
                .getMethod("getCustomers", CrmCustomerPageQuery.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:crm:query')", authorization.value());
    }

    @Test
    void leadsRequiresCrmQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = CrmAdminController.class
                .getMethod("getLeads", CrmLeadPageQuery.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:crm:query')", authorization.value());
    }

    @Test
    void contactsRequiresCrmQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = CrmAdminController.class
                .getMethod("getContacts", CrmContactPageQuery.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:crm:query')", authorization.value());
    }

    @Test
    void opportunitiesRequiresCrmQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = CrmAdminController.class
                .getMethod("getOpportunities", CrmOpportunityPageQuery.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:crm:query')", authorization.value());
    }

    @Test
    void followUpsRequiresCrmQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = CrmAdminController.class
                .getMethod("getFollowUps", CrmFollowUpPageQuery.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:crm:query')", authorization.value());
    }

    @Test
    void poolRequiresCrmQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = CrmAdminController.class
                .getMethod("getPool", CrmPoolPageQuery.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:crm:query')", authorization.value());
    }
}
