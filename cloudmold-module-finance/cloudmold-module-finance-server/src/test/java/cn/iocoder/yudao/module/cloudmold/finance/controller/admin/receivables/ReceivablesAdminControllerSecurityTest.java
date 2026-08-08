package cn.iocoder.yudao.module.cloudmold.finance.controller.admin.receivables;

import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesCommands;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceivablesAdminControllerSecurityTest {

    @Test
    void customerSummaryRequiresQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = ReceivablesAdminController.class
                .getMethod("summarizeByCustomer", String.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:finance:receivables:query')", authorization.value());
    }

    @Test
    void salesContractSummaryRequiresQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = ReceivablesAdminController.class
                .getMethod("summarizeBySalesContract", String.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:finance:receivables:query')", authorization.value());
    }

    @Test
    void registerPlanRequiresCommandPermission() throws NoSuchMethodException {
        PreAuthorize authorization = ReceivablesAdminController.class
                .getMethod("registerPlan", ReceivablesCommands.RegisterPlan.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:finance:receivables:command')", authorization.value());
    }

    @Test
    void recordReceiptRequiresCommandPermission() throws NoSuchMethodException {
        PreAuthorize authorization = ReceivablesAdminController.class
                .getMethod("recordReceipt", ReceivablesCommands.RecordReceipt.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:finance:receivables:command')", authorization.value());
    }

    @Test
    void allocateReceiptRequiresCommandPermission() throws NoSuchMethodException {
        PreAuthorize authorization = ReceivablesAdminController.class
                .getMethod("allocateReceipt", ReceivablesCommands.AllocateReceipt.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:finance:receivables:command')", authorization.value());
    }
}
