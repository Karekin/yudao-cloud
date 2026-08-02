package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.InboundQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarehouseInboundControllerSecurityTest {

    @Test
    void partialReceiveRequiresWarehouseCommandPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseNetworkCommandController.class
                .getMethod("partialReceive", cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundCommand.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:command')", authorization.value());
    }

    @Test
    void receiptProgressRequiresWarehouseQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseNetworkCommandController.class
                .getMethod("getReceiptProgress", String.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:query')", authorization.value());
    }

    @Test
    void receiptPageRequiresWarehouseQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseQueryController.class
                .getMethod("getInboundReceiptPage", InboundQueryService.InboundReceiptPageReqVO.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:query')", authorization.value());
    }

    @Test
    void receiptDetailRequiresWarehouseQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseQueryController.class
                .getMethod("getInboundReceipt", String.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:query')", authorization.value());
    }

    @Test
    void putawayCommandRequiresWarehouseCommandPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseNetworkCommandController.class
                .getMethod("executePutaway", cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundCommand.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:command')", authorization.value());
    }

    @Test
    void putawayPageRequiresWarehouseQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseQueryController.class
                .getMethod("getInboundPutawayPage", InboundQueryService.InboundPutawayPageReqVO.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:query')", authorization.value());
    }

    @Test
    void putawayDetailRequiresWarehouseQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseQueryController.class
                .getMethod("getInboundPutaway", String.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:query')", authorization.value());
    }
}
