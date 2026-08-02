package cn.iocoder.yudao.module.cloudmold.procurement.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.procurement.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.controller.admin.vo.ProcurementAdminVo.*;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.procurement.service.query.ProcurementAdminQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name="CloudMold - Procurement") @RestController @RequestMapping("/cloudmold/procurement")
public class ProcurementAdminController {
    @Resource private ProcurementCommandApi orderCommands;
    @Resource private AwardReleaseCommandApi awardReleaseCommands;
    @Resource private SourcingCommandApi sourcingCommands;
    @Resource private ProcurementAdminQueryService query;
    @Resource private ProcurementActorPrincipalPort actors;

    @GetMapping("/purchase-requisitions/page") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:requisition:query')")
    public CommonResult<Page<RequisitionPageItem>> requisitions(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status,@RequestParam(required=false) String keyword){return success(query.requisitions(pageNo,pageSize,status,keyword));}
    @GetMapping("/sourcing-events/page") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:sourcing:query')")
    public CommonResult<Page<RfqPageItem>> events(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status,@RequestParam(required=false) String keyword){return success(query.events(pageNo,pageSize,status,keyword));}
    @GetMapping("/quotations/page") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:quotation:query')")
    public CommonResult<Page<QuotationPageItem>> quotations(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword){return success(query.quotations(pageNo,pageSize,keyword));}
    @GetMapping("/awards/page") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:award:query')")
    public CommonResult<Page<AwardPageItem>> awards(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status,@RequestParam(required=false) String keyword){return success(query.awards(pageNo,pageSize,status,keyword));}
    @GetMapping("/orders/page") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:order:query')")
    public CommonResult<Page<PurchaseOrderPageItem>> orders(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String status,@RequestParam(required=false) String keyword){return success(query.orders(pageNo,pageSize,status,keyword));}
    @GetMapping("/awards/{awardId}") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:award:query')")
    public CommonResult<AwardDetail> award(@PathVariable String awardId){return success(query.award(awardId));}
    @GetMapping("/order/{orderId}") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:order:query')")
    public CommonResult<PurchaseOrderDetail> order(@PathVariable String orderId){return success(query.order(orderId));}

    @PostMapping("/command") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:order:write') or @ss.hasPermission('cloudmold:procurement:order:release')")
    public CommonResult<CommandResult> command(@RequestBody ProcurementCommand c){return success(admin(orderCommands.execute(c,actor())));}
    @PostMapping("/awards/{awardId}/release-purchase-orders") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:award:release')")
    public CommonResult<AwardReleaseResultBody> releaseAward(@PathVariable String awardId,@RequestBody AwardReleaseBody body){
        AwardReleaseCommand command=AwardReleaseCommand.builder().awardId(awardId).expectedAwardVersion(body.getExpectedAwardVersion())
                .idempotencyKey(body.getIdempotencyKey()).runId(body.getRunId()).correlationId(body.getCorrelationId()).causationId(body.getCausationId())
                .occurredAt(Instant.parse(body.getOccurredAt())).build();
        return success(admin(awardReleaseCommands.releaseApprovedAward(command,actor())));
    }
    @PostMapping("/sourcing/command") @PreAuthorize("@ss.hasPermission('cloudmold:procurement:sourcing:write') or @ss.hasPermission('cloudmold:procurement:quotation:write') or @ss.hasPermission('cloudmold:procurement:evaluation:write') or @ss.hasPermission('cloudmold:procurement:award:write') or @ss.hasPermission('cloudmold:procurement:award:submit') or @ss.hasPermission('cloudmold:procurement:award:approve')")
    public CommonResult<CommandResult> sourcing(@RequestBody SourcingCommand c){return success(admin(sourcingCommands.execute(c,actor())));}

    private String actor(){return actors.resolveSystemAdmin(getLoginUserId());}
    private static CommandResult admin(ProcurementResult r){return new CommandResult().setOperationId(str(r.getOperationId())).setDuplicate(r.isDuplicate()).setAggregateType("PURCHASE_ORDER").setAggregateId(r.getAggregateId()).setPurchaseOrderId(r.getAggregateId()).setAggregateVersion(r.getAggregateVersion()).setStatus(r.getStatus());}
    private static AwardReleaseResultBody admin(AwardReleaseResult r){List<PurchaseOrderReleaseItem> orders=new ArrayList<>();if(r.getPurchaseOrders()!=null){for(ProcurementOrderView order:r.getPurchaseOrders()){orders.add(new PurchaseOrderReleaseItem().setOrderId(order.getOrderId()).setOrderCode(order.getOrderCode()).setSupplierId(order.getSupplierId()).setCurrencyCode(order.getCurrencyCode()).setStatus(order.getStatus()).setAggregateVersion(order.getVersion()).setLineCount(order.getItems()==null?0:order.getItems().size()).setGrossAmountMinor(str(order.getHeaderGrossAmountMinor())));}}return new AwardReleaseResultBody().setAwardId(r.getAwardId()).setAwardVersion(r.getAwardVersion()).setDuplicate(r.isDuplicate()).setOperationId(str(r.getOperationId())).setStatus("PURCHASE_ORDERS_CREATED").setPurchaseOrders(orders);}
    private static CommandResult admin(SourcingResult r){return new CommandResult().setOperationId(str(r.getOperationId())).setDuplicate(r.isDuplicate()).setAggregateType(switch(r.getAggregateType()){case "procurement_award"->"PURCHASE_AWARD";case "sourcing_event"->"PURCHASE_SOURCING_EVENT";default->r.getAggregateType();}).setAggregateId(r.getAggregateId()).setAggregateVersion(r.getAggregateVersion()).setStatus(r.getStatus());}
    private static String str(Long x){return x==null?null:x.toString();}
}
