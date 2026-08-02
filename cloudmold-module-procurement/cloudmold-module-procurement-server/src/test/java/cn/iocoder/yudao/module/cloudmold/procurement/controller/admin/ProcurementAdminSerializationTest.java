package cn.iocoder.yudao.module.cloudmold.procurement.controller.admin;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.procurement.controller.admin.vo.ProcurementAdminVo.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementAdminSerializationTest {
    @Test void serializesInt64AmountsAndOperationIdAsStringsButVersionAsNumber(){
        String command=JsonUtils.toJsonString(new CommandResult().setOperationId("9007199254740993").setAggregateId("award-1").setAggregateType("PURCHASE_AWARD").setAggregateVersion(3L).setDuplicate(false).setStatus("APPROVED"));
        assertThat(command).contains("\"operationId\":\"9007199254740993\"").contains("\"aggregateVersion\":3").doesNotContain("\"aggregateVersion\":\"3\"");
        String award=JsonUtils.toJsonString(new AwardLine().setAwardLineId("al-1").setAwardedGrossAmountMinor("9007199254740993").setUnitNetPriceMinor("1299.000000").setAwardedQuantity("12.500000"));
        assertThat(award).contains("\"awardedGrossAmountMinor\":\"9007199254740993\"").contains("\"unitNetPriceMinor\":\"1299.000000\"").contains("\"awardedQuantity\":\"12.500000\"");
        String order=JsonUtils.toJsonString(new PurchaseOrderDetail().setHeaderNetAmountMinor("9007199254740993").setHeaderTaxAmountMinor("117"));
        assertThat(order).contains("\"headerNetAmountMinor\":\"9007199254740993\"").contains("\"headerTaxAmountMinor\":\"117\"");
    }

    @Test void controllerPublishesOnlyFrozenProcurementPaths(){
        assertThat(ProcurementAdminController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/cloudmold/procurement");
        assertPath("requisitions",GetMapping.class,"/purchase-requisitions/page"); assertPath("events",GetMapping.class,"/sourcing-events/page");
        assertPath("quotations",GetMapping.class,"/quotations/page"); assertPath("awards",GetMapping.class,"/awards/page"); assertPath("orders",GetMapping.class,"/orders/page");
        assertPath("award",GetMapping.class,"/awards/{awardId}"); assertPath("order",GetMapping.class,"/order/{orderId}");
        assertPath("command",PostMapping.class,"/command"); assertPath("sourcing",PostMapping.class,"/sourcing/command");
    }

    private static void assertPath(String methodName,Class<? extends java.lang.annotation.Annotation> type,String expected){Method method=Arrays.stream(ProcurementAdminController.class.getDeclaredMethods()).filter(x->x.getName().equals(methodName)).findFirst().orElseThrow();String[] value=type==GetMapping.class?method.getAnnotation(GetMapping.class).value():method.getAnnotation(PostMapping.class).value();assertThat(value).containsExactly(expected);}
}
