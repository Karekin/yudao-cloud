package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryProcurementReceiptServiceImplTest {
    private static final String RECEIPT="10000000-0000-4000-8000-000000000001";
    private static final String LINE="10000000-0000-4000-8000-000000000002";
    private static final String PO="po:award-release:canonical-test";
    private static final String ITEM="poi:canonical-test";
    private static final String SCHEDULE="pos:canonical-test";
    private static final String SUPPLIER="10000000-0000-4000-8000-000000000006";
    private static final String OWNER="10000000-0000-4000-8000-000000000007";
    private static final String SKU="10000000-0000-4000-8000-000000000008";
    private static final String WAREHOUSE="10000000-0000-4000-8000-000000000009";
    private static final String LOCATION="10000000-0000-4000-8000-000000000010";
    private static final String CORRELATION="10000000-0000-4000-8000-000000000011";
    private static final String DECISION="10000000-0000-4000-8000-000000000012";
    private static final String PENDING="20000000-0000-4000-8000-000000000001";
    private static final String ACCEPTED="20000000-0000-4000-8000-000000000002";
    private static final String REJECTED="20000000-0000-4000-8000-000000000003";
    private static final String QUARANTINED="20000000-0000-4000-8000-000000000004";

    private final InventoryProcurementReceiptOperationMapper opMapper=mock(InventoryProcurementReceiptOperationMapper.class);
    private final InventoryProcurementReceiptMapper receiptMapper=mock(InventoryProcurementReceiptMapper.class);
    private final InventoryV3BalanceMapper balanceMapper=mock(InventoryV3BalanceMapper.class);
    private final InventoryLotMapper lotMapper=mock(InventoryLotMapper.class);
    private final InventoryV3LedgerTransactionMapper txMapper=mock(InventoryV3LedgerTransactionMapper.class);
    private final InventoryV3LedgerEntryMapper entryMapper=mock(InventoryV3LedgerEntryMapper.class);
    private final OutboxAppender outbox=mock(OutboxAppender.class);
    private final CatalogSkuValidationApi catalog=mock(CatalogSkuValidationApi.class);
    private final WarehouseReferenceValidationApi warehouse=mock(WarehouseReferenceValidationApi.class);
    private final MerchantOwnerValidationApi merchant=mock(MerchantOwnerValidationApi.class);
    private final InventoryProcurementReceiptServiceImpl service=new InventoryProcurementReceiptServiceImpl(
            opMapper,receiptMapper,balanceMapper,lotMapper,txMapper,entryMapper,outbox,catalog,warehouse,merchant);

    @BeforeEach void setUp(){
        TenantContextHolder.setTenantId(1L);
        when(receiptMapper.updateQuantitiesCas(anyLong(),anyString(),anyLong(),any(),any(),any(),any(),any(),any(),anyLong(),any())).thenReturn(1);
        when(balanceMapper.updateBalanceCas(anyLong(),anyString(),anyLong(),any(),any(),any(),any())).thenReturn(1);
        when(opMapper.markSucceeded(anyLong(),anyLong(),any(),any())).thenReturn(1);
        doAnswer(i->{((InventoryV3LedgerTransactionDO)i.getArgument(0)).setLedgerTransactionId(501L);return 1;})
                .when(txMapper).insert(any(InventoryV3LedgerTransactionDO.class));
    }
    @AfterEach void tearDown(){TenantContextHolder.clear();}

    @Test void receiveCreatesQaHoldWithMandatoryValuation(){
        newOperation(); receipt("0","0","0","0","0","0",0);
        InventoryV3BalanceDO pending=balance(PENDING,"QA_HOLD","PENDING_QC","0",0);
        target(pending,"QA_HOLD","PENDING_QC");
        InventoryProcurementReceiptResult r=service.execute(command(InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY,
                InventoryProcurementReceiptDisposition.PENDING,"10",0));
        assertThat(r.getReceivedQuantity()).isEqualByComparingTo("10.000000");
        assertThat(r.getPendingQuantity()).isEqualByComparingTo("10.000000");
        assertThat(r.getUnitCostAmountMinor()).isEqualTo(100L);
        assertThat(r.getMovementCostAmountMinor()).isEqualTo(1_000L);
        assertThat(r.getCurrencyCode()).isEqualTo("CNY");
        assertThat(r.getValuationPolicyId()).isEqualTo("b9645ea2-11b6-4ba0-87a2-f1416042f60a");
        assertThat(r.getValuationPolicyVersion()).isEqualTo("V1.0.0");
        assertThat(r.getValuationPolicyHash()).isEqualTo("a".repeat(64));
        verify(balanceMapper).updateBalanceCas(eq(1L),eq(PENDING),eq(0L),eq(new BigDecimal("10.000000")),
                eq(BigDecimal.ZERO.setScale(6)),eq(BigDecimal.ZERO.setScale(6)),any());
        verify(outbox).append(argThat(e->e.getSchemaVersion()==7
                && e.getPayload().get("movement_type").equals("RECEIVE_PENDING_QUALITY")
                && e.getPayload().get("valuation_policy").equals("b9645ea2-11b6-4ba0-87a2-f1416042f60a")));
    }

    @Test void acceptedQualityReclassifiesWithoutChangingTotalOnHand(){
        newOperation(); receipt("10","10","0","0","0","0",1);
        InventoryV3BalanceDO source=balance(PENDING,"QA_HOLD","PENDING_QC","10",1);
        InventoryV3BalanceDO target=balance(ACCEPTED,"SELLABLE","QUALIFIED","0",0);
        sourceAndTarget(source,target,"SELLABLE","QUALIFIED");
        InventoryProcurementReceiptResult r=service.execute(command(InventoryProcurementReceiptOperation.ACCEPT_QUALITY,
                InventoryProcurementReceiptDisposition.ACCEPTED,"4",1));
        assertThat(r.getPendingQuantity()).isEqualByComparingTo("6");
        assertThat(r.getAcceptedQuantity()).isEqualByComparingTo("4");
        ArgumentCaptor<InventoryV3LedgerEntryDO> entries=ArgumentCaptor.forClass(InventoryV3LedgerEntryDO.class);
        verify(entryMapper,times(2)).insert(entries.capture());
        assertThat(entries.getAllValues().stream().map(InventoryV3LedgerEntryDO::getDeltaOnHandQuantity)
                .reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0");
    }

    @Test void rejectedAndQuarantinedUseDistinctNonSellableBalances(){
        executeDisposition(InventoryProcurementReceiptOperation.REJECT_QUALITY,
                InventoryProcurementReceiptDisposition.REJECTED,REJECTED,"REJECTED");
        resetForSecondEffect();
        executeDisposition(InventoryProcurementReceiptOperation.QUARANTINE_QUALITY,
                InventoryProcurementReceiptDisposition.QUARANTINED,QUARANTINED,"QUARANTINED");
    }

    @Test void supplierReturnDebitsChosenDispositionAndWritesReverseEntry(){
        newOperation();
        when(receiptMapper.selectForUpdate(1L,LINE)).thenReturn(
                receiptAggregate("MERCHANT","10","0","4","3","3","0",4)
                        .setValuationPolicy("B9645EA2-11B6-4BA0-87A2-F1416042F60A"));
        InventoryV3BalanceDO accepted=balance(ACCEPTED,"SELLABLE","QUALIFIED","4",2);
        when(balanceMapper.selectDimension(1L,"MERCHANT",OWNER,SKU,WAREHOUSE,LOCATION,null,"SELLABLE","QUALIFIED"))
                .thenReturn(accepted);
        when(balanceMapper.selectByIdForUpdate(1L,ACCEPTED)).thenReturn(accepted);
        InventoryProcurementReceiptResult r=service.execute(command(InventoryProcurementReceiptOperation.RETURN_TO_SUPPLIER,
                InventoryProcurementReceiptDisposition.ACCEPTED,"2",2));
        assertThat(r.getAcceptedQuantity()).isEqualByComparingTo("2");
        assertThat(r.getReturnedQuantity()).isEqualByComparingTo("2");
        ArgumentCaptor<InventoryV3LedgerEntryDO> reverse=ArgumentCaptor.forClass(InventoryV3LedgerEntryDO.class);
        verify(entryMapper).insert(reverse.capture());
        assertThat(reverse.getValue().getDeltaOnHandQuantity()).isEqualByComparingTo("-2.000000");
    }

    @Test void missingOrInconsistentCostFailsBeforeOperationCreation(){
        InventoryProcurementReceiptCommand missing=command(InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY,
                InventoryProcurementReceiptDisposition.PENDING,"1",0).setUnitCostAmountMinor(null);
        assertThatThrownBy(()->service.execute(missing)).hasMessage("unitCostAmountMinor is required");
        InventoryProcurementReceiptCommand bad=command(InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY,
                InventoryProcurementReceiptDisposition.PENDING,"2",0).setMovementCostAmountMinor(199L);
        assertThatThrownBy(()->service.execute(bad)).hasMessage("movement cost must equal quantity multiplied by unit cost");
        InventoryProcurementReceiptCommand invalidPolicy=command(InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY,
                InventoryProcurementReceiptDisposition.PENDING,"1",0).setValuationPolicyHash("mutable-policy");
        assertThatThrownBy(()->service.execute(invalidPolicy)).hasMessage("valuationPolicyHash must be SHA-256");
        verifyNoInteractions(opMapper,receiptMapper,balanceMapper,txMapper,entryMapper,outbox);
    }

    @Test void platformOwnerUsesCanonicalOwnerTypeWithoutMerchantFallback(){
        newOperation();
        when(receiptMapper.selectForUpdate(1L,LINE)).thenReturn(receiptAggregate("PLATFORM","0","0","0","0","0","0",0));
        InventoryV3BalanceDO pending=balance("PLATFORM",PENDING,"QA_HOLD","PENDING_QC","0",0);
        when(balanceMapper.selectDimension(1L,"PLATFORM",OWNER,SKU,WAREHOUSE,LOCATION,null,"QA_HOLD","PENDING_QC"))
                .thenReturn(pending);
        when(balanceMapper.selectByIdForUpdate(1L,PENDING)).thenReturn(pending);

        InventoryProcurementReceiptResult result=service.execute(command(InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY,
                InventoryProcurementReceiptDisposition.PENDING,"3",0).setOwnerType("PLATFORM"));

        assertThat(result.getReceivedQuantity()).isEqualByComparingTo("3.000000");
        verifyNoInteractions(merchant);
    }

    @Test void qualityQuantityCannotExceedPending(){
        newOperation(); receipt("5","1","4","0","0","0",2);
        assertThatThrownBy(()->service.execute(command(InventoryProcurementReceiptOperation.REJECT_QUALITY,
                InventoryProcurementReceiptDisposition.REJECTED,"2",3)))
                .hasMessage("quality decision exceeds pending quantity");
        verifyNoInteractions(balanceMapper,txMapper,entryMapper,outbox);
    }

    @Test void idempotencyConflictFailsBeforeMasterValidation(){
        when(opMapper.selectLastInsertId()).thenReturn(101L);
        when(opMapper.selectForUpdate(1L,101L)).thenReturn(new InventoryProcurementReceiptOperationDO()
                .setOperationId(101L).setAttemptToken("old").setRequestHash("different").setStatus(10));
        assertThatThrownBy(()->service.execute(command(InventoryProcurementReceiptOperation.ACCEPT_QUALITY,
                InventoryProcurementReceiptDisposition.ACCEPTED,"1",1)))
                .hasMessage("idempotency key or receipt effect conflicts with different payload");
        verifyNoInteractions(merchant,catalog,warehouse,receiptMapper,balanceMapper,txMapper,entryMapper,outbox);
    }

    private void executeDisposition(InventoryProcurementReceiptOperation op,InventoryProcurementReceiptDisposition d,
                                    String targetId,String quality){
        newOperation(); receipt("10","10","0","0","0","0",1);
        InventoryV3BalanceDO source=balance(PENDING,"QA_HOLD","PENDING_QC","10",1);
        InventoryV3BalanceDO target=balance(targetId,"NON_SELLABLE",quality,"0",0);
        sourceAndTarget(source,target,"NON_SELLABLE",quality);
        InventoryProcurementReceiptResult r=service.execute(command(op,d,"2",1));
        if(d==InventoryProcurementReceiptDisposition.REJECTED) assertThat(r.getRejectedQuantity()).isEqualByComparingTo("2");
        else assertThat(r.getQuarantinedQuantity()).isEqualByComparingTo("2");
    }
    private void resetForSecondEffect(){reset(opMapper,receiptMapper,balanceMapper,txMapper,entryMapper,outbox,merchant,catalog,warehouse);setUp();}
    private void newOperation(){
        AtomicReference<String> attempt=new AtomicReference<>();
        when(opMapper.insertOrResolve(eq(1L),anyString(),isNull(),eq(LINE),eq(RECEIPT),anyLong(),anyString(),anyString(),anyString(),anyString(),any()))
                .thenAnswer(i->{attempt.set(i.getArgument(9));return 1;});
        when(opMapper.selectLastInsertId()).thenReturn(101L);
        when(opMapper.selectForUpdate(1L,101L)).thenAnswer(i->new InventoryProcurementReceiptOperationDO()
                .setOperationId(101L).setAttemptToken(attempt.get()).setStatus(0));
    }
    private void receipt(String received,String pending,String accepted,String rejected,String quarantined,String returned,long version){
        when(receiptMapper.selectForUpdate(1L,LINE)).thenReturn(
                receiptAggregate("MERCHANT",received,pending,accepted,rejected,quarantined,returned,version));
    }
    private InventoryProcurementReceiptDO receiptAggregate(String ownerType,String received,String pending,String accepted,
            String rejected,String quarantined,String returned,long version){return new InventoryProcurementReceiptDO()
                .setTenantId(1L).setReceiptId(RECEIPT).setReceiptLineId(LINE).setPurchaseOrderId(PO)
                .setPurchaseOrderItemId(ITEM).setPurchaseOrderScheduleId(SCHEDULE).setSupplierId(SUPPLIER)
                .setOwnerType(ownerType).setOwnerId(OWNER).setCanonicalSkuId(SKU).setWarehouseId(WAREHOUSE)
                .setLocationId(LOCATION).setBaseUomCode("PIECE").setValuationPolicy("b9645ea2-11b6-4ba0-87a2-f1416042f60a")
                .setValuationPolicyVersion("V1.0.0").setValuationPolicyHash("a".repeat(64))
                .setUnitCostAmountMinor(100L).setCurrencyCode("CNY").setReceivedQuantity(q(received))
                .setPendingQuantity(q(pending)).setAcceptedQuantity(q(accepted)).setRejectedQuantity(q(rejected))
                .setQuarantinedQuantity(q(quarantined)).setReturnedQuantity(q(returned)).setVersion(version);}
    private void target(InventoryV3BalanceDO target,String stock,String quality){
        when(balanceMapper.selectDimension(1L,"MERCHANT",OWNER,SKU,WAREHOUSE,LOCATION,null,stock,quality)).thenReturn(target);
        when(balanceMapper.selectByIdForUpdate(1L,target.getBalanceId())).thenReturn(target);
    }
    private void sourceAndTarget(InventoryV3BalanceDO source,InventoryV3BalanceDO target,String targetStock,String targetQuality){
        when(balanceMapper.selectDimension(1L,"MERCHANT",OWNER,SKU,WAREHOUSE,LOCATION,null,"QA_HOLD","PENDING_QC")).thenReturn(source);
        when(balanceMapper.selectDimension(1L,"MERCHANT",OWNER,SKU,WAREHOUSE,LOCATION,null,targetStock,targetQuality)).thenReturn(target);
        when(balanceMapper.selectByIdForUpdate(1L,source.getBalanceId())).thenReturn(source);
        when(balanceMapper.selectByIdForUpdate(1L,target.getBalanceId())).thenReturn(target);
    }
    private InventoryV3BalanceDO balance(String id,String stock,String quality,String onHand,long version){
        return balance("MERCHANT",id,stock,quality,onHand,version);
    }
    private InventoryV3BalanceDO balance(String ownerType,String id,String stock,String quality,String onHand,long version){return new InventoryV3BalanceDO()
            .setBalanceId(id).setTenantId(1L).setOwnerType(ownerType).setOwnerId(OWNER).setCanonicalSkuId(SKU)
            .setWarehouseId(WAREHOUSE).setLocationId(LOCATION).setStockStatus(stock).setQualityStatus(quality)
            .setBaseUomCode("PIECE").setOnHandQuantity(q(onHand)).setReservedQuantity(q("0"))
            .setInTransitQuantity(q("0")).setVersion(version);}
    private InventoryProcurementReceiptCommand command(InventoryProcurementReceiptOperation op,
            InventoryProcurementReceiptDisposition d,String quantity,long version){return InventoryProcurementReceiptCommand.builder()
            .operation(op).disposition(d).idempotencyKey("effect-"+op+"-"+d).receiptId(RECEIPT).receiptLineId(LINE)
            .purchaseOrderId(PO).purchaseOrderItemId(ITEM).purchaseOrderScheduleId(SCHEDULE).supplierId(SUPPLIER)
            .ownerType("MERCHANT").ownerId(OWNER).canonicalSkuId(SKU).warehouseId(WAREHOUSE).locationId(LOCATION)
            .baseUomCode("PIECE").quantity(q(quantity)).qualityDecisionId(version==0?null:DECISION)
            .decisionVersion(version==0?null:version).qualityEvidenceRef(version==0?null:"inspection-report:v"+version)
            .valuationPolicy("b9645ea2-11b6-4ba0-87a2-f1416042f60a").unitCostAmountMinor(100L)
            .valuationPolicyVersion("V1.0.0").valuationPolicyHash("a".repeat(64))
            .movementCostAmountMinor(q(quantity).multiply(BigDecimal.valueOf(100)).longValueExact()).currencyCode("CNY")
            .businessNo("RCV-001").correlationId(CORRELATION).occurredAt(Instant.parse("2026-08-02T01:00:00Z")).build();}
    private BigDecimal q(String v){return new BigDecimal(v).setScale(6);}
}
