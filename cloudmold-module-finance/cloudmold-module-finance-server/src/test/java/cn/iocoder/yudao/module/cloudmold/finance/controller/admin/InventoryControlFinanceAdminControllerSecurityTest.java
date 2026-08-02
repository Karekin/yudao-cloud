package cn.iocoder.yudao.module.cloudmold.finance.controller.admin;

import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.InventoryControlCommandRequest;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.InventoryControlJournalReverseRequest;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingPageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryControlFinanceAdminControllerSecurityTest {

    @Test
    void pageRequiresInventoryControlFinanceQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = InventoryControlFinanceAdminController.class
                .getMethod("page", PostingPageRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:finance:financial-impact:query')", authorization.value());
    }

    @Test
    void detailRequiresInventoryControlFinanceQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = InventoryControlFinanceAdminController.class
                .getMethod("detail", String.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:finance:financial-impact:query')", authorization.value());
    }

    @Test
    void commandRequiresInventoryControlFinanceCommandPermission() throws NoSuchMethodException {
        PreAuthorize authorization = InventoryControlFinanceAdminController.class
                .getMethod("execute", InventoryControlCommandRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:finance:inventory-control:command')", authorization.value());
    }

    @Test
    void reverseRequiresInventoryControlFinanceCommandPermission() throws NoSuchMethodException {
        PreAuthorize authorization = InventoryControlFinanceAdminController.class
                .getMethod("reverse", InventoryControlJournalReverseRequest.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermission('cloudmold:finance:inventory-control:command')", authorization.value());
    }
}
