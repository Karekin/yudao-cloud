package cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CatalogQueryMapperSqlTest {

    @Test
    void skuDetailSqlShouldBeParsableByTenantInterceptor() throws NoSuchMethodException {
        Method method = CatalogQueryMapper.class.getMethod("selectSkuDetail", Long.class, String.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value())
                .replace("#{tenantId}", "?")
                .replace("#{skuId}", "?");

        assertThat(sql).doesNotContain("&lt;", "&gt;");
        assertThat(sql).contains(
                "s.status AS style_status",
                "p.status AS spu_status",
                "c.status AS color_status",
                "g.status AS size_group_status",
                "z.status AS size_status",
                "g.size_group_id");
        assertThatCode(() -> CCJSqlParserUtil.parse(sql)).doesNotThrowAnyException();
    }

    @Test
    void waveAggregateSqlShouldBeParsableByTenantInterceptor() throws NoSuchMethodException {
        Method method = CatalogQueryMapper.class.getMethod("selectWaveAggregate",
                Long.class, Integer.class, String.class, String.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value())
                .replace("#{tenantId}", "?")
                .replace("#{planningYear}", "?")
                .replace("#{seasonCode}", "?")
                .replace("#{waveCode}", "?");

        assertThat(sql).contains(
                "COUNT(DISTINCT s.style_id) AS style_count",
                "COUNT(DISTINCT CASE WHEN p.status = 30 THEN p.spu_id END) AS active_spu_count",
                "last_catalog_updated_at");
        assertThatCode(() -> CCJSqlParserUtil.parse(sql)).doesNotThrowAnyException();
    }
}
