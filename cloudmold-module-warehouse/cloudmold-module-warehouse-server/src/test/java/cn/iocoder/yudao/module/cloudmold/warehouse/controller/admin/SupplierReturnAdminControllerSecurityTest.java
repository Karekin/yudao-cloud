package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.SupplierReturnCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.SupplierReturnPageReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierReturnAdminControllerSecurityTest {

    private static final String COMMAND_PERMISSION =
            "@ss.hasPermission('cloudmold:warehouse:supplier-return:command')";
    private static final String QUERY_PERMISSION =
            "@ss.hasPermission('cloudmold:warehouse:supplier-return:query')";

    @Test
    void exposesCanonicalSupplierReturnRoutesWithExplicitPermissions() throws Exception {
        RequestMapping root = SupplierReturnAdminController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/cloudmold/warehouse/supplier-returns");

        Method command = SupplierReturnAdminController.class.getMethod("execute", SupplierReturnCommand.class);
        assertThat(command.getAnnotation(PostMapping.class).value()).containsExactly("/command");
        assertThat(command.getAnnotation(PreAuthorize.class).value()).isEqualTo(COMMAND_PERMISSION);

        Method page = SupplierReturnAdminController.class.getMethod("getPage", SupplierReturnPageReqVO.class);
        assertThat(page.getAnnotation(GetMapping.class).value()).containsExactly("/page");
        assertThat(page.getAnnotation(PreAuthorize.class).value()).isEqualTo(QUERY_PERMISSION);

        Method detail = SupplierReturnAdminController.class.getMethod("get", String.class);
        assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/get");
        assertThat(detail.getAnnotation(PreAuthorize.class).value()).isEqualTo(QUERY_PERMISSION);
    }
}
