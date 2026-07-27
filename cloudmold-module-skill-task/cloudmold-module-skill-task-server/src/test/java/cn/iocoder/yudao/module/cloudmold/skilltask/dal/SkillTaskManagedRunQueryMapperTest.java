package cn.iocoder.yudao.module.cloudmold.skilltask.dal;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillTaskManagedRunQueryMapperTest extends BaseDbUnitTest {

    @Resource
    private SkillTaskMapper mapper;

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cloudmold_skill_task_step (
                    tenant_id BIGINT NOT NULL,
                    task_id VARCHAR(64) NOT NULL,
                    step_code VARCHAR(128) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cloudmold_skill_task_instance (
                    tenant_id BIGINT NOT NULL,
                    task_id VARCHAR(64) PRIMARY KEY,
                    run_id VARCHAR(64) NOT NULL,
                    skill_id VARCHAR(191) NOT NULL,
                    skill_version VARCHAR(64) NOT NULL,
                    input_json CLOB,
                    input_sha256 VARCHAR(64),
                    definition_sha256 VARCHAR(64),
                    definition_closure_sha256 VARCHAR(64),
                    terminal_result_sha256 VARCHAR(64),
                    risk_level VARCHAR(8) NOT NULL,
                    parent_task_id VARCHAR(64),
                    parent_step_code VARCHAR(128),
                    status VARCHAR(32) NOT NULL,
                    current_step_code VARCHAR(128),
                    attempt_count INT NOT NULL,
                    max_attempts INT NOT NULL,
                    last_error_code VARCHAR(128),
                    version BIGINT NOT NULL,
                    started_at TIMESTAMP NULL,
                    completed_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("DELETE FROM cloudmold_skill_task_instance");
    }

    @Test
    void shouldFilterByTenantAndReturnNewestCreatedFirstOrder() {
        insertTask("task-old", 1L, "run-1", "skill-a", "1.0.0", "R2", "QUEUED",
                "step-a", 0, 3, 1L, "2026-07-26T09:00:00", "2026-07-26T09:05:00", null);
        insertTask("task-newer-a", 1L, "run-2", "skill-b", "1.1.0", "R3", "RUNNING",
                "step-b", 1, 5, 2L, "2026-07-26T10:00:00", "2026-07-26T10:10:00", null);
        insertTask("task-newer-b", 1L, "run-3", "skill-b", "1.1.0", "R3", "SUCCEEDED",
                "step-c", 1, 5, 3L, "2026-07-26T10:00:00", "2026-07-26T10:12:00", "2026-07-26T10:20:00");
        insertTask("task-other-tenant", 2L, "run-4", "skill-b", "1.1.0", "R3", "SUCCEEDED",
                "step-x", 1, 5, 9L, "2026-07-26T11:00:00", "2026-07-26T11:01:00", "2026-07-26T11:02:00");

        long total = mapper.countManagedRunPage(1L, null, null, null, null, null);
        List<Task> page = mapper.selectManagedRunPage(1L, null, null, null, null, null,
                0L, 10);

        assertThat(total).isEqualTo(3L);
        assertThat(page).extracting(Task::getTaskId)
                .containsExactly("task-newer-b", "task-newer-a", "task-old");
        assertThat(page.get(0).getCompletedAt()).isEqualTo(LocalDateTime.parse("2026-07-26T10:20:00"));
        assertThat(page.get(1).getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void shouldApplyExactFiltersForManagedRunPage() {
        insertTask("task-1", 1L, "run-match", "skill-a", "1.0.0", "R2", "RUNNING",
                "step-a", 1, 3, 5L, "2026-07-26T08:00:00", "2026-07-26T08:10:00", null);
        insertTask("task-2", 1L, "run-other", "skill-a", "1.0.0", "R1", "QUEUED",
                "step-b", 0, 3, 2L, "2026-07-26T09:00:00", "2026-07-26T09:10:00", null);

        long total = mapper.countManagedRunPage(1L, "task-1", "run-match", "skill-a", "RUNNING", "R2");
        List<Task> page = mapper.selectManagedRunPage(1L, "task-1", "run-match", "skill-a",
                "RUNNING", "R2", 0L, 10);

        assertThat(total).isOne();
        assertThat(page).singleElement().satisfies(item -> {
            assertThat(item.getTaskId()).isEqualTo("task-1");
            assertThat(item.getRunId()).isEqualTo("run-match");
            assertThat(item.getRiskLevel()).isEqualTo("R2");
        });
    }

    private void insertTask(String taskId, Long tenantId, String runId, String skillId, String skillVersion,
                            String riskLevel, String status, String currentStepCode, int attemptCount,
                            int maxAttempts, long version, String createdAt, String updatedAt,
                            String completedAt) {
        jdbcTemplate.update("""
                        INSERT INTO cloudmold_skill_task_instance (
                            tenant_id, task_id, run_id, skill_id, skill_version, input_json, input_sha256,
                            definition_sha256, definition_closure_sha256, terminal_result_sha256, risk_level,
                            parent_task_id, parent_step_code, status, current_step_code, attempt_count,
                            max_attempts, version, started_at, completed_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                tenantId, taskId, runId, skillId, skillVersion, "{}", "i".repeat(64), "d".repeat(64), "c".repeat(64),
                completedAt == null ? null : "t".repeat(64), riskLevel, null, null, status, currentStepCode,
                attemptCount, maxAttempts, version, Timestamp.valueOf(LocalDateTime.parse(createdAt).minusMinutes(1)),
                completedAt == null ? null : Timestamp.valueOf(LocalDateTime.parse(completedAt)),
                Timestamp.valueOf(LocalDateTime.parse(createdAt)), Timestamp.valueOf(LocalDateTime.parse(updatedAt)));
    }

}
