package cn.iocoder.yudao.module.cloudmold.finance.controller.admin;

import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.SupplierReturnReversalCommands;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierReturnReversalAdminControllerSecurityTest {

    @Test
    void exposesCanonicalSupplierReturnFinanceRouteWithFinanceCommandPermission() throws Exception {
        RequestMapping root = SupplierReturnReversalAdminController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/cloudmold/finance/procure-to-pay/supplier-return-reversals");

        Method command = SupplierReturnReversalAdminController.class.getMethod(
                "post", SupplierReturnReversalCommands.Post.class);
        assertThat(command.getAnnotation(PostMapping.class).value()).containsExactly("/command");
        assertThat(command.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@ss.hasPermission('cloudmold:finance:procure-to-pay:command')");
    }
}
