package cn.iocoder.yudao.module.cloudmold.dreamplant.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.dreamplant.api.*;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.*;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.mysql.DreamPlantStoreMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DreamPlantServiceImplTest {
    private final DreamPlantStoreMapper mapper = mock(DreamPlantStoreMapper.class);
    private final DreamPlantServiceImpl service = new DreamPlantServiceImpl(mapper);
    private final AtomicLong operationSequence = new AtomicLong();
    private final AtomicReference<Long> lastOperationId = new AtomicReference<>();
    private final Map<Long, Operation> operations = new HashMap<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        doAnswer(invocation -> {
            long id = operationSequence.incrementAndGet();
            lastOperationId.set(id);
            operations.put(id, new Operation().setOperationId(id).setTenantId(invocation.getArgument(0))
                    .setIdempotencyKey(invocation.getArgument(1)).setCommandType(invocation.getArgument(2))
                    .setRequestHash(invocation.getArgument(3)).setAttemptToken(invocation.getArgument(4))
                    .setStatus(0));
            return 1;
        }).when(mapper).insertOrResolveOperation(anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class));
        when(mapper.selectLastInsertId()).thenAnswer(ignored -> lastOperationId.get());
        when(mapper.selectOperationForUpdate(eq(1L), anyLong()))
                .thenAnswer(invocation -> operations.get(invocation.getArgument(1)));
        when(mapper.markOperationSucceeded(eq(1L), anyLong(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    Operation operation = operations.get(invocation.getArgument(1));
                    operation.setStatus(10).setAggregateId(invocation.getArgument(2))
                            .setResultJson(invocation.getArgument(3));
                    return 1;
                });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPublishImmutableWorldMapWithHashAndVersionGuards() {
        String payload = "{\"capabilities\":[],\"products\":[],\"operationAgents\":[],\"solutions\":[],"
                + "\"agentRoles\":[],\"phaseRoadmap\":[]}";
        AtomicReference<WorldMap> worldMap = new AtomicReference<>();
        AtomicReference<Snapshot> snapshot = new AtomicReference<>();
        when(mapper.selectWorldMapForUpdate(1L, "dreamplant")).thenAnswer(ignored -> worldMap.get());
        when(mapper.insertWorldMap(any(WorldMap.class))).thenAnswer(invocation -> {
            worldMap.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.insertSnapshot(any(Snapshot.class))).thenAnswer(invocation -> {
            snapshot.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.advanceWorldMap(eq(1L), eq("dreamplant"), eq(0L), eq(true), any(LocalDateTime.class)))
                .thenAnswer(ignored -> {
                    worldMap.get().setCurrentVersion(1L).setPubliclyReadable(true);
                    return 1;
                });

        DreamPlantCommand command = publishCommand(payload, DigestUtil.sha256Hex(payload));
        DreamPlantCommandResult result = service.execute(command);

        assertThat(result).extracting(DreamPlantCommandResult::getMapKey,
                        DreamPlantCommandResult::getMapVersion, DreamPlantCommandResult::getStatus)
                .containsExactly("dreamplant", 1L, "PUBLISHED");
        assertThat(snapshot.get()).extracting(Snapshot::getVersion, Snapshot::getSchemaVersion,
                        Snapshot::getPayloadSha256, Snapshot::getOperationId)
                .containsExactly(1L, "dreamplant.bootstrap.v1",
                        DreamPlantCanonicalJson.canonicalize(payload).sha256(), 1L);

        assertThatThrownBy(() -> service.execute(publishCommand(payload, "0".repeat(64))
                .setIdempotencyKey("publish-bad-hash")))
                .hasMessage("payloadSha256 does not match payloadJson");
    }

    @Test
    void shouldQueueExplorationAndRequireVersionedEvidenceForOutcome() {
        WorldMap map = new WorldMap().setTenantId(1L).setMapKey("dreamplant").setCurrentVersion(3L)
                .setPubliclyReadable(true);
        when(mapper.selectWorldMap(1L, "dreamplant")).thenReturn(map);
        AtomicReference<ExplorationRun> run = new AtomicReference<>();
        when(mapper.insertExploration(any(ExplorationRun.class))).thenAnswer(invocation -> {
            run.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectExplorationForUpdate(1L, "explore-001")).thenAnswer(ignored -> run.get());
        when(mapper.updateExplorationOutcome(eq(1L), eq("explore-001"), eq(1L), eq("SUCCEEDED"),
                anyString(), anyString(), eq(true), any(LocalDateTime.class))).thenReturn(1);

        DreamPlantCommandResult queued = service.execute(DreamPlantCommand.builder()
                .operation(DreamPlantOperation.SUBMIT_EXPLORATION).idempotencyKey("explore-submit-001")
                .runTraceId("trace-explore-001").mapKey("dreamplant").explorationRunId("explore-001")
                .intent("探索订单履约异常的根因并形成可复用方案").contextJson("{}")
                .requestedByPrincipalId("principal-agent").build());
        assertThat(queued).extracting(DreamPlantCommandResult::getExplorationRunId,
                        DreamPlantCommandResult::getExplorationVersion, DreamPlantCommandResult::getStatus)
                .containsExactly("explore-001", 1L, "QUEUED");
        when(mapper.selectExplorations(1L, "QUEUED", 10)).thenReturn(List.of(run.get()));
        assertThat(service.listExplorations("QUEUED", 10))
                .extracting(DreamPlantExplorationView::getExplorationRunId)
                .containsExactly("explore-001");
        assertThatThrownBy(() -> service.listExplorations("QUEUED", 101))
                .hasMessage("limit must be between 1 and 100");

        DreamPlantCommand missingEvidence = outcomeCommand(null);
        assertThatThrownBy(() -> service.execute(missingEvidence)).hasMessageContaining("evidenceRef");

        String outcomeHash = DigestUtil.sha256Hex("{\"finding\":\"reusable\"}");
        DreamPlantCommandResult completed = service.execute(outcomeCommand("sha256:" + outcomeHash)
                .setIdempotencyKey("explore-outcome-002"));
        assertThat(completed).extracting(DreamPlantCommandResult::getExplorationVersion,
                        DreamPlantCommandResult::getStatus)
                .containsExactly(2L, "SUCCEEDED");
    }

    @Test
    void shouldExposeOnlyExplicitlyPublicProjectionWithoutTenantContext() {
        TenantContextHolder.clear();
        when(mapper.selectPublicSnapshot("dreamplant")).thenReturn(new Snapshot().setTenantId(1L)
                .setMapKey("dreamplant").setVersion(2L).setSchemaVersion("dreamplant.bootstrap.v1")
                .setPayloadJson("{}").setPayloadSha256("a".repeat(64)).setSourceRef("sha256:" + "a".repeat(64))
                .setPublishedAt(LocalDateTime.of(2026, 7, 18, 0, 0)));

        assertThat(service.getPublicWorldMap("dreamplant")).extracting(
                        DreamPlantWorldMapSnapshot::getVersion, DreamPlantWorldMapSnapshot::getPubliclyReadable)
                .containsExactly(2L, true);
        when(mapper.selectPublicSnapshot("private-map")).thenReturn(null);
        assertThatThrownBy(() -> service.getPublicWorldMap("private-map"))
                .hasMessage("public DreamPlant world map does not exist");
    }

    private static DreamPlantCommand publishCommand(String payload, String hash) {
        return DreamPlantCommand.builder().operation(DreamPlantOperation.PUBLISH_WORLD_MAP)
                .idempotencyKey("publish-world-map-001").runTraceId("trace-publish-001")
                .mapKey("dreamplant").expectedVersion(0L).schemaVersion("dreamplant.bootstrap.v1")
                .payloadJson(payload).payloadSha256(hash).sourceRef("sha256:" + hash)
                .publiclyReadable(true).build();
    }

    private static DreamPlantCommand outcomeCommand(String evidenceRef) {
        return DreamPlantCommand.builder().operation(DreamPlantOperation.RECORD_EXPLORATION_OUTCOME)
                .idempotencyKey("explore-outcome-001").runTraceId("trace-outcome-001")
                .explorationRunId("explore-001").expectedVersion(1L).outcomeStatus("SUCCEEDED")
                .outcomeJson("{\"finding\":\"reusable\"}").evidenceRef(evidenceRef).build();
    }
}
