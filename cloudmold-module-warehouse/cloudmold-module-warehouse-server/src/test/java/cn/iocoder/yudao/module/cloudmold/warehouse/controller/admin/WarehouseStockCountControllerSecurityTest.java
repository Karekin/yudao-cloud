package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.StockCountPageReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarehouseStockCountControllerSecurityTest {

    @Test
    void stockCountCommandRequiresWarehouseCommandPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseStockCountController.class
                .getMethod("execute", cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountCommand.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:command')", authorization.value());
    }

    @Test
    void stockCountPageRequiresWarehouseQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseStockCountController.class
                .getMethod("getPage", StockCountPageReqVO.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:query')", authorization.value());
    }

    @Test
    void stockCountDetailRequiresWarehouseQueryPermission() throws NoSuchMethodException {
        PreAuthorize authorization = WarehouseStockCountController.class
                .getMethod("get", String.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('cloudmold:warehouse:query')", authorization.value());
    }
}
