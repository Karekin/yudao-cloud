package cn.iocoder.yudao.module.trade.framework.delivery.config;

import cn.iocoder.yudao.module.trade.framework.delivery.core.enums.ExpressClientEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

// TODO @芋艿：未来要不要放数据库中？考虑 saas 多租户时，不同租户使用不同的配置？
/**
 * 交易运费快递的配置项
 *
 * @author jason
 */
@Component
@ConfigurationProperties(prefix = "yudao.trade.express")
@Data
@Validated
public class TradeExpressProperties {

    /**
     * 快递客户端
     *
     * 默认不提供，需要提醒用户配置一个快递服务商。
     */
    private ExpressClientEnum client = ExpressClientEnum.NOT_PROVIDE;

    /**
     * 快递鸟配置
     */
    @Valid
    private KdNiaoConfig kdNiao;
    /**
     * 快递 100 配置
     */
    @Valid
    private Kd100Config kd100;

    /**
     * Only the selected provider must carry credentials. This keeps the default
     * {@link ExpressClientEnum#NOT_PROVIDE} mode usable without shipping placeholder
     * secrets, while still failing fast when an operator enables a provider
     * incompletely.
     */
    @AssertTrue(message = "启用的快递服务商配置不完整")
    public boolean isSelectedClientConfigured() {
        if (client == null || client == ExpressClientEnum.NOT_PROVIDE) {
            return true;
        }
        if (client == ExpressClientEnum.KD_NIAO) {
            return kdNiao != null
                    && StringUtils.hasText(kdNiao.getBusinessId())
                    && StringUtils.hasText(kdNiao.getApiKey())
                    && StringUtils.hasText(kdNiao.getRequestType());
        }
        if (client == ExpressClientEnum.KD_100) {
            return kd100 != null
                    && StringUtils.hasText(kd100.getCustomer())
                    && StringUtils.hasText(kd100.getKey());
        }
        return false;
    }

    /**
     * 快递鸟配置项目
     */
    @Data
    public static class KdNiaoConfig {

        /**
         * 快递鸟用户 ID
         */
        private String businessId;
        /**
         * 快递鸟 API Key
         */
        private String apiKey;

        /**
         * 接口指令
         *
         * 1. 1002：免费版（只能查询申通、圆通快递）
         * 2. 8001：付费版
         */
        private String requestType = "1002";

    }

    /**
     * 快递 100 配置项
     */
    @Data
    public static class Kd100Config {

        /**
         * 快递 100 授权码
         */
        private String customer;
        /**
         * 快递 100 授权 key
         */
        private String key;

    }

}
