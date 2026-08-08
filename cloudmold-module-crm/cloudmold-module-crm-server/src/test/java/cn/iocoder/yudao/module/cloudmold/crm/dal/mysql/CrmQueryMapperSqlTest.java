package cn.iocoder.yudao.module.cloudmold.crm.dal.mysql;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CrmQueryMapperSqlTest {

    @Test
    void workbenchFollowUpCountsUseJdbcComparisonOperators() throws NoSuchMethodException {
        String dueSql = selectSql("countDueFollowUps", Long.class, String.class,
                LocalDateTime.class, LocalDateTime.class);
        String overdueSql = selectSql("countOverdueFollowUps", Long.class, String.class,
                LocalDateTime.class);

        assertThat(dueSql)
                .contains("next_follow_up_at >= #{now}")
                .contains("next_follow_up_at <= #{end}")
                .doesNotContain("&gt;", "&lt;");
        assertThat(overdueSql)
                .contains("next_follow_up_at < #{now}")
                .doesNotContain("&gt;", "&lt;");
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Select select = CrmQueryMapper.class.getMethod(methodName, parameterTypes).getAnnotation(Select.class);
        return String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
    }
}
