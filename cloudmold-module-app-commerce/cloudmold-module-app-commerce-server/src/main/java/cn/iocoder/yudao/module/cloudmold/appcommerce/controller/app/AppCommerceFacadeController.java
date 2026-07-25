package cn.iocoder.yudao.module.cloudmold.appcommerce.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.AfterSaleView;
import cn.iocoder.yudao.module.cloudmold.appcommerce.service.*;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.CustomerServiceView;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.AppCustomerServicePageView;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentView;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.AppAfterSalePageView;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCommandResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - CloudMold 规范交易")
@RestController
@RequestMapping("/cloudmold/app")
@Validated
public class AppCommerceFacadeController {

    @Resource private AppMemberPrincipalResolver principalResolver;
    @Resource private AppProductReadService productReadService;
    @Resource private AppCheckoutService checkoutService;
    @Resource private AppOrderQueryApi orderQueryApi;
    @Resource private AppSupportService supportService;
    @Resource private AppFulfillmentQueryApi fulfillmentQueryApi;

    @GetMapping("/me")
    @Operation(summary = "校验当前登录会员并解析规范 Principal")
    public CommonResult<AppMemberPrincipalView> me() {
        return success(principalResolver.requireCurrent());
    }

    @GetMapping("/products")
    @PermitAll
    @Operation(summary = "查询当前有效的规范商品")
    public CommonResult<AppProductPageView> products(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(required = false) @Size(max = 64) String keyword) {
        return success(productReadService.page(keyword, pageNo, pageSize));
    }

    @GetMapping("/products/{listingId}")
    @PermitAll
    @Operation(summary = "查询规范商品详情")
    public CommonResult<AppProductView> product(@PathVariable String listingId) {
        return success(productReadService.detail(listingId));
    }

    @PostMapping("/checkout/preview")
    @Operation(summary = "服务端复核价格、库存并创建限时结算快照")
    public CommonResult<AppCheckoutView> preview(@Valid @RequestBody CheckoutPreviewReq request) {
        return success(checkoutService.preview(request.idempotencyKey, request.listingId,
                request.listingOfferId, request.canonicalSkuId, request.quantity));
    }

    @PostMapping("/orders")
    @Operation(summary = "从结算快照创建规范订单并预留库存")
    public CommonResult<AppOrderView> createOrder(@Valid @RequestBody CreateOrderReq request) {
        return success(checkoutService.createOrder(request.idempotencyKey, request.checkoutToken));
    }

    @GetMapping("/orders/{orderId}")
    public CommonResult<AppOrderView> order(@PathVariable String orderId) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        return success(orderQueryApi.requireOwned(principal.getPrincipalId(), orderId));
    }

    @GetMapping("/orders")
    public CommonResult<AppOrderPageView> orders(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(required = false) @Size(max = 32) String status) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        return success(orderQueryApi.listOwned(principal.getPrincipalId(), status, pageNo, pageSize));
    }

    @GetMapping("/fulfillments/by-order/{orderId}")
    public CommonResult<AppFulfillmentView> fulfillment(@PathVariable String orderId) {
        AppMemberPrincipalView principal = principalResolver.requireCurrent();
        orderQueryApi.requireOwned(principal.getPrincipalId(), orderId);
        return success(fulfillmentQueryApi.getByOrder(orderId));
    }

    @PostMapping("/payments/internal-test/capture")
    @Operation(summary = "仅限非生产环境的内部测试支付")
    public CommonResult<PaymentCommandResult> capture(@Valid @RequestBody CapturePaymentReq request) {
        return success(checkoutService.captureInternalTest(request.idempotencyKey,
                request.orderId, request.expectedOrderVersion));
    }

    @PostMapping("/after-sales")
    public CommonResult<AfterSaleView> requestAfterSale(@Valid @RequestBody AfterSaleReq request) {
        return success(supportService.requestAfterSale(request.idempotencyKey, request.orderId,
                request.orderItemId, request.quantity, request.afterSaleType, request.reasonCode));
    }

    @GetMapping("/after-sales/{afterSaleId}")
    public CommonResult<AfterSaleView> afterSale(@PathVariable String afterSaleId) {
        return success(supportService.getAfterSale(afterSaleId));
    }

    @GetMapping("/after-sales")
    public CommonResult<AppAfterSalePageView> afterSales(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return success(supportService.listAfterSales(pageNo, pageSize));
    }

    @PostMapping("/customer-service/tickets")
    public CommonResult<CustomerServiceView> createTicket(@Valid @RequestBody TicketReq request) {
        return success(supportService.createTicket(request.idempotencyKey, request.categoryCode,
                request.referenceType, request.referenceId));
    }

    @GetMapping("/customer-service/tickets/{ticketId}")
    public CommonResult<CustomerServiceView> ticket(@PathVariable String ticketId) {
        return success(supportService.getTicket(ticketId));
    }

    @GetMapping("/customer-service/tickets")
    public CommonResult<AppCustomerServicePageView> tickets(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return success(supportService.listTickets(pageNo, pageSize));
    }

    @Data
    public static class CheckoutPreviewReq {
        @NotBlank @Size(min = 8, max = 128) private String idempotencyKey;
        @NotBlank private String listingId;
        @NotBlank private String listingOfferId;
        @NotBlank private String canonicalSkuId;
        @Min(1) @Max(99) private int quantity;
    }

    @Data
    public static class CreateOrderReq {
        @NotBlank @Size(min = 8, max = 128) private String idempotencyKey;
        @NotBlank private String checkoutToken;
    }

    @Data
    public static class CapturePaymentReq {
        @NotBlank @Size(min = 8, max = 128) private String idempotencyKey;
        @NotBlank private String orderId;
        @NotNull @Positive private Long expectedOrderVersion;
    }

    @Data
    public static class AfterSaleReq {
        @NotBlank @Size(min = 8, max = 128) private String idempotencyKey;
        @NotBlank private String orderId;
        @NotBlank private String orderItemId;
        @NotNull @DecimalMin("0.000001") private BigDecimal quantity;
        @NotBlank @Pattern(regexp = "^RETURN_AND_REFUND$") private String afterSaleType;
        @NotBlank @Pattern(regexp = "^SIZE_NOT_FIT$") private String reasonCode;
    }

    @Data
    public static class TicketReq {
        @NotBlank @Size(min = 8, max = 128) private String idempotencyKey;
        @NotBlank @Pattern(regexp = "^(ORDER_SUPPORT|FULFILLMENT_SUPPORT|AFTER_SALE_SUPPORT|QUALITY_SUPPORT|GENERAL)$")
        private String categoryCode;
        @NotBlank @Pattern(regexp = "^(ORDER|AFTER_SALE|GENERAL)$") private String referenceType;
        private String referenceId;
    }
}
