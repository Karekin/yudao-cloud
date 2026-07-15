package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarehouseNetworkCommandServiceImplTest {

    private final WarehouseOperationMapper operationMapper = mock(WarehouseOperationMapper.class);
    private final CanonicalWarehouseMapper warehouseMapper = mock(CanonicalWarehouseMapper.class);
    private final WarehouseZoneMapper zoneMapper = mock(WarehouseZoneMapper.class);
    private final WarehouseLocationMapper locationMapper = mock(WarehouseLocationMapper.class);
    private final WarehouseSourceMappingMapper sourceMappingMapper = mock(WarehouseSourceMappingMapper.class);
    private final WarehouseOperatorAssignmentMapper assignmentMapper = mock(WarehouseOperatorAssignmentMapper.class);
    private final PrincipalValidationApi principalValidationApi = mock(PrincipalValidationApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final WarehouseNetworkCommandServiceImpl service = new WarehouseNetworkCommandServiceImpl(
            operationMapper, warehouseMapper, zoneMapper, locationMapper, sourceMappingMapper,
            assignmentMapper, principalValidationApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult(
                "11111111-1111-4111-8111-111111111111", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void definesWarehouseAndAppendsLegalOutboxEventAtomically() {
        prepareNewOperation(WarehouseNetworkOperation.DEFINE_WAREHOUSE);

        WarehouseNetworkCommandResult result = service.execute(defineWarehouseCommand());

        assertThat(result.getWarehouseId()).isNotBlank();
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(warehouseMapper).insert(argThat((WarehouseDO row) -> row.getTenantId().equals(1L)
                && row.getWarehouseCode().equals("WH-SH-01") && row.getVersion().equals(1L)));
        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("warehouse.entity.status_changed");
        assertThat(event.getValue().getEventType()).doesNotContain("-");
        assertThat(event.getValue().getAggregateVersion()).isEqualTo(1L);
        assertThat(event.getValue().getPayload()).containsEntry("entity_type", "WAREHOUSE")
                .containsEntry("entity_id", result.getWarehouseId())
                .containsKey("previous_status")
                .containsEntry("current_status", "DRAFT")
                .doesNotContainKey("status")
                .doesNotContainKey("tenant_id");
    }

    @Test
    void returnsImmutableFirstResultForIdempotentReplay() {
        WarehouseNetworkCommand command = defineWarehouseCommand();
        WarehouseNetworkCommandResult first = WarehouseNetworkCommandResult.builder()
                .warehouseId("warehouse-existing").aggregateVersion(1L).status("DRAFT").duplicate(false).build();
        when(operationMapper.selectLastInsertId()).thenReturn(42L);
        when(operationMapper.selectForUpdate(42L, 1L)).thenReturn(new WarehouseOperationDO()
                .setOperationId(42L).setAttemptToken("first-attempt").setRequestHash(
                        WarehouseNetworkCommandServiceImpl.fingerprint(1L, command))
                .setStatus(10).setResultJson(JsonUtils.toJsonString(first)));

        WarehouseNetworkCommandResult replay = service.execute(command);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getWarehouseId()).isEqualTo("warehouse-existing");
        verifyNoInteractions(warehouseMapper, zoneMapper, locationMapper, sourceMappingMapper,
                assignmentMapper, principalValidationApi, outboxAppender);
    }

    @Test
    void rejectsLocationWhoseZoneBelongsToAnotherWarehouse() {
        prepareNewOperation(WarehouseNetworkOperation.DEFINE_LOCATION);
        when(warehouseMapper.selectForUpdate(1L, "warehouse-1")).thenReturn(activeWarehouse("warehouse-1"));
        when(zoneMapper.selectForUpdate(1L, "zone-1")).thenReturn(new WarehouseZoneDO()
                .setTenantId(1L).setZoneId("zone-1").setWarehouseId("warehouse-2").setStatus("ACTIVE"));

        assertThatThrownBy(() -> service.execute(WarehouseNetworkCommand.builder()
                .operation(WarehouseNetworkOperation.DEFINE_LOCATION)
                .idempotencyKey("warehouse-location-test-0001")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-15T10:00:00Z"))
                .location(WarehouseLocationDefinition.builder().warehouseId("warehouse-1").zoneId("zone-1")
                        .locationCode("A-01-01").name("A-01-01").locationType("STORAGE").build()).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("zone does not belong to warehouse");

        verify(locationMapper, never()).insert(any(WarehouseLocationDO.class));
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void assignsOnlyAnActiveCanonicalPrincipal() {
        prepareNewOperation(WarehouseNetworkOperation.ASSIGN_OPERATOR);
        when(warehouseMapper.selectForUpdate(1L, "warehouse-1")).thenReturn(activeWarehouse("warehouse-1"));

        service.execute(WarehouseNetworkCommand.builder()
                .operation(WarehouseNetworkOperation.ASSIGN_OPERATOR)
                .idempotencyKey("warehouse-operator-test-0001")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-15T10:00:00Z"))
                .operatorAssignment(WarehouseOperatorAssignmentDefinition.builder()
                        .warehouseId("warehouse-1").principalId("principal-1").roleCode("PICKER")
                        .validFrom(Instant.parse("2026-07-15T10:00:00Z")).build()).build());

        verify(principalValidationApi).requireActivePrincipal("principal-1");
        verify(assignmentMapper).insert(argThat((WarehouseOperatorAssignmentDO row) -> row.getPrincipalId().equals("principal-1")
                && row.getStatus().equals("ACTIVE")));
        verify(outboxAppender).append(argThat(event -> event.getEventType()
                .equals("warehouse.operator_assignment.changed")
                && "ACTIVE".equals(event.getPayload().get("current_status"))
                && !event.getPayload().containsKey("status")
                && !event.getPayload().containsKey("tenant_id")));
    }

    @Test
    void publishesCanonicalSourceIdentityForWarehouseMapping() {
        prepareNewOperation(WarehouseNetworkOperation.LINK_SOURCE);
        when(warehouseMapper.selectForUpdate(1L, "warehouse-1")).thenReturn(activeWarehouse("warehouse-1"));

        service.execute(WarehouseNetworkCommand.builder()
                .operation(WarehouseNetworkOperation.LINK_SOURCE)
                .idempotencyKey("warehouse-source-test-0002")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-15T10:00:00Z"))
                .sourceMapping(WarehouseSourceMappingDefinition.builder().sourceSystem("WMS")
                        .sourceType("WAREHOUSE").sourceId("10").targetType("WAREHOUSE")
                        .warehouseId("warehouse-1").verificationRef("migration-run-1")
                        .validFrom(Instant.parse("2026-07-15T10:00:00Z")).build()).build());

        verify(sourceMappingMapper).insert(argThat((WarehouseSourceMappingDO row) ->
                "WAREHOUSE".equals(row.getCanonicalType()) && "warehouse-1".equals(row.getCanonicalId())));
        verify(outboxAppender).append(argThat(event -> event.getEventType()
                .equals("warehouse.source_mapping.changed")
                && "WAREHOUSE".equals(event.getPayload().get("canonical_type"))
                && "warehouse-1".equals(event.getPayload().get("canonical_id"))
                && "ACTIVE".equals(event.getPayload().get("current_status"))
                && !event.getPayload().containsKey("status")
                && !event.getPayload().containsKey("tenant_id")));
    }

    @Test
    void normalizesQualifiedSourceIdentityBeforePersistingAndResolvingDuplicates() {
        prepareNewOperation(WarehouseNetworkOperation.LINK_SOURCE);
        when(warehouseMapper.selectForUpdate(1L, "warehouse-1")).thenReturn(activeWarehouse("warehouse-1"));

        service.execute(WarehouseNetworkCommand.builder()
                .operation(WarehouseNetworkOperation.LINK_SOURCE)
                .idempotencyKey("warehouse-source-normalize-0001")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-15T10:00:00Z"))
                .sourceMapping(WarehouseSourceMappingDefinition.builder().sourceSystem(" wms ")
                        .sourceType(" warehouse ").sourceId(" 10 ").targetType(" warehouse ")
                        .warehouseId("warehouse-1").verificationRef("migration-run-1")
                        .validFrom(Instant.parse("2026-07-15T10:00:00Z")).build()).build());

        verify(sourceMappingMapper).selectEffectiveForUpdate(eq(1L), eq("WMS"), eq("WAREHOUSE"), eq("10"), any());
        verify(sourceMappingMapper).insert(argThat((WarehouseSourceMappingDO row) ->
                "WMS".equals(row.getSourceSystem()) && "WAREHOUSE".equals(row.getSourceType())
                        && "10".equals(row.getSourceId()) && "WAREHOUSE".equals(row.getCanonicalType())));
    }

    @Test
    void rejectsCanonicalIdentifierEqualToSourceIdentifier() {
        prepareNewOperation(WarehouseNetworkOperation.LINK_SOURCE);
        when(warehouseMapper.selectForUpdate(1L, "same-id")).thenReturn(activeWarehouse("same-id"));

        WarehouseNetworkCommand command = WarehouseNetworkCommand.builder()
                .operation(WarehouseNetworkOperation.LINK_SOURCE)
                .idempotencyKey("warehouse-source-distinct-0001")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-15T10:00:00Z"))
                .sourceMapping(WarehouseSourceMappingDefinition.builder().sourceSystem("ERP")
                        .sourceType("WAREHOUSE").sourceId("same-id").targetType("WAREHOUSE")
                        .warehouseId("same-id").verificationRef("migration-run-1")
                        .validFrom(Instant.parse("2026-07-15T10:00:00Z")).build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("canonicalId must not equal sourceId");
        verify(sourceMappingMapper, never()).insert(any(WarehouseSourceMappingDO.class));
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void rejectsSourceMappingWithoutQualifiedSourceIdentity() {
        WarehouseNetworkCommand command = WarehouseNetworkCommand.builder()
                .operation(WarehouseNetworkOperation.LINK_SOURCE)
                .idempotencyKey("warehouse-source-test-0001")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-15T10:00:00Z"))
                .sourceMapping(WarehouseSourceMappingDefinition.builder().sourceSystem("WMS")
                        .sourceType("WAREHOUSE").sourceId(" ").targetType("WAREHOUSE")
                        .warehouseId("warehouse-1").verificationRef("migration-run-1")
                        .validFrom(Instant.parse("2026-07-15T10:00:00Z")).build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceId");
        verifyNoInteractions(operationMapper, sourceMappingMapper, outboxAppender);
    }

    private void prepareNewOperation(WarehouseNetworkOperation operation) {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), isNull(), eq(operation.name()),
                anyString(), anyString(), any(LocalDateTime.class))).thenAnswer(invocation -> {
            attemptToken.set(invocation.getArgument(5));
            return 1;
        });
        when(operationMapper.selectLastInsertId()).thenReturn(42L);
        when(operationMapper.selectForUpdate(42L, 1L)).thenAnswer(ignored -> new WarehouseOperationDO()
                .setOperationId(42L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markSucceeded(eq(42L), eq(1L), anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);
    }

    private static WarehouseNetworkCommand defineWarehouseCommand() {
        return WarehouseNetworkCommand.builder().operation(WarehouseNetworkOperation.DEFINE_WAREHOUSE)
                .idempotencyKey("warehouse-define-test-0001")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-15T10:00:00Z"))
                .warehouse(WarehouseDefinition.builder().warehouseCode("WH-SH-01").name("上海一号仓")
                        .warehouseType("FULFILLMENT").timezone("Asia/Shanghai").build()).build();
    }

    private static WarehouseDO activeWarehouse(String id) {
        return new WarehouseDO().setTenantId(1L).setWarehouseId(id).setWarehouseCode("WH-01")
                .setName("Warehouse").setStatus("ACTIVE").setVersion(1L);
    }
}
