package cn.iocoder.yudao.module.cloudmold.quality.controller.admin;

import cn.iocoder.yudao.module.cloudmold.quality.api.ProcurementReceiptInspectionCommand;
import cn.iocoder.yudao.module.cloudmold.quality.controller.admin.vo.ProcurementReceiptInspectionPageReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementReceiptInspectionAdminControllerSecurityTest {
    private static final String COMMAND_PERMISSION =
            "@ss.hasPermission('cloudmold:quality:procurement-receipt-inspection:command')";
    private static final String QUERY_PERMISSION =
            "@ss.hasPermission('cloudmold:quality:procurement-receipt-inspection:query')";

    @Test
    void exposesOnlyCanonicalQualityRoutesWithExplicitCommandAndQueryPermissions() throws Exception {
        RequestMapping root = ProcurementReceiptInspectionAdminController.class
                .getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/cloudmold/quality/procurement-receipt-inspections");
        assertThat(root.value()[0]).doesNotContain("/wms", "legacy");

        Method command = ProcurementReceiptInspectionAdminController.class
                .getMethod("execute", ProcurementReceiptInspectionCommand.class);
        assertThat(command.getAnnotation(PostMapping.class).value()).containsExactly("/command");
        assertThat(command.getAnnotation(PreAuthorize.class).value()).isEqualTo(COMMAND_PERMISSION);

        Method page = ProcurementReceiptInspectionAdminController.class
                .getMethod("getPage", ProcurementReceiptInspectionPageReqVO.class);
        assertThat(page.getAnnotation(GetMapping.class).value()).containsExactly("/page");
        assertThat(page.getAnnotation(PreAuthorize.class).value()).isEqualTo(QUERY_PERMISSION);

        Method detail = ProcurementReceiptInspectionAdminController.class.getMethod("get", String.class);
        assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/{inspectionId}");
        assertThat(detail.getAnnotation(PreAuthorize.class).value()).isEqualTo(QUERY_PERMISSION);
    }
}
