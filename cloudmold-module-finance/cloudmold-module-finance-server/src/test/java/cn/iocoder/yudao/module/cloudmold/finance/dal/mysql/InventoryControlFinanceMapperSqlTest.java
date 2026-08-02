package cn.iocoder.yudao.module.cloudmold.finance.dal.mysql;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryControlFinanceMapperSqlTest {

    private final Configuration configuration = configuration();

    @Test
    void inventoryPostingPageExpandsPresentFilters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", 162L);
        parameters.put("sourceType", "INVENTORY_SCRAP");
        parameters.put("status", "POSTED");
        parameters.put("keyword", "SCRAP-20260802");
        parameters.put("offset", 0L);
        parameters.put("size", 20);

        String sql = boundSql("selectPostingPage", parameters).getSql();

        assertThat(sql)
                .doesNotContain("<if")
                .contains("p.source_type=?", "p.status=?", "p.source_document_id LIKE", "LIMIT ?,?");
    }

    @Test
    void supplierReturnCountOmitsAbsentFilters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", 162L);
        parameters.put("status", null);
        parameters.put("keyword", null);

        String sql = boundSql("countSupplierReturnPage", parameters).getSql();

        assertThat(sql)
                .doesNotContain("<if", "status=", "LIKE CONCAT")
                .contains("tenant_id=?");
    }

    private BoundSql boundSql(String methodName, Map<String, Object> parameters) {
        return configuration
                .getMappedStatement(InventoryControlFinanceMapper.class.getName() + "." + methodName)
                .getBoundSql(parameters);
    }

    private static Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.addMapper(InventoryControlFinanceMapper.class);
        return configuration;
    }
}
