package cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.cloudmold.datacontract.service.query.DataReadinessOutboxOverviewRow;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DataReadinessQueryMapperTest extends BaseDbUnitTest {

    @Resource
    private DataReadinessQueryMapper mapper;

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DELETE FROM cloudmold_event_outbox");
    }

    @Test
    void shouldAggregateTenantScopedLiveOutboxCountsAndTimestamps() {
        insertEvent("pending-1", 1L, 0, "2026-07-19T08:00:00", null);
        insertEvent("pending-2", 1L, 0, "2026-07-19T09:00:00", null);
        insertEvent("claimed-1", 1L, 10, "2026-07-19T10:00:00", null);
        insertEvent("published-1", 1L, 20, "2026-07-19T11:00:00", "2026-07-19T11:10:00");
        insertEvent("published-2", 1L, 20, "2026-07-19T12:00:00", "2026-07-19T12:20:00");
        insertEvent("dead-1", 1L, 30, "2026-07-19T13:00:00", null);
        insertEvent("other-tenant", 2L, 20, "2026-07-19T15:00:00", "2026-07-19T15:30:00");

        DataReadinessOutboxOverviewRow row = mapper.selectOutboxOverview(1L);

        assertThat(row.getPendingCount()).isEqualTo(2L);
        assertThat(row.getClaimedCount()).isEqualTo(1L);
        assertThat(row.getPublishedCount()).isEqualTo(2L);
        assertThat(row.getDeadCount()).isEqualTo(1L);
        assertThat(row.getOldestPendingRecordedAt()).isEqualTo(time("2026-07-19T08:00:00"));
        assertThat(row.getLatestRecordedAt()).isEqualTo(time("2026-07-19T13:00:00"));
        assertThat(row.getLatestPublishedAt()).isEqualTo(time("2026-07-19T12:20:00"));
    }

    @Test
    void shouldReturnZeroCountsWithoutInferringHealthWhenTenantHasNoRows() {
        DataReadinessOutboxOverviewRow row = mapper.selectOutboxOverview(9L);

        assertThat(row.getPendingCount()).isZero();
        assertThat(row.getClaimedCount()).isZero();
        assertThat(row.getPublishedCount()).isZero();
        assertThat(row.getDeadCount()).isZero();
        assertThat(row.getOldestPendingRecordedAt()).isNull();
        assertThat(row.getLatestRecordedAt()).isNull();
        assertThat(row.getLatestPublishedAt()).isNull();
    }

    private void insertEvent(String eventId, Long tenantId, int status, String recordedAt, String publishedAt) {
        LocalDateTime recorded = time(recordedAt);
        jdbcTemplate.update("""
                        INSERT INTO cloudmold_event_outbox (
                            event_id, event_type, schema_version, source_system, tenant_id, aggregate_type,
                            aggregate_id, aggregate_version, event_sequence, occurred_at, recorded_at, correlation_id,
                            idempotency_key, payload, headers, payload_hash, destination, status, available_at,
                            attempt_count, max_attempts, published_at
                        ) VALUES (?, 'catalog.sku.upserted', 1, 'cloudmold-catalog', ?, 'SKU',
                                  ?, 1, 1, ?, ?, ?, ?, '{}', NULL, ?, 'catalog-events', ?, ?, 0, 20, ?)
                        """,
                eventId, tenantId, "aggregate-" + eventId, Timestamp.valueOf(recorded.minusMinutes(1)),
                Timestamp.valueOf(recorded), "corr-" + eventId, "idem-" + eventId,
                "a".repeat(64), status, Timestamp.valueOf(recorded), nullableTimestamp(publishedAt));
    }

    private static LocalDateTime time(String value) {
        return LocalDateTime.parse(value);
    }

    private static Timestamp nullableTimestamp(String value) {
        return value == null ? null : Timestamp.valueOf(time(value));
    }
}
