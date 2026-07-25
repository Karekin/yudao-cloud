package cn.iocoder.yudao.module.trade.framework.delivery.config;

import cn.iocoder.yudao.module.trade.framework.delivery.core.enums.ExpressClientEnum;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeExpressPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAllowSecretFreeDefaultMode() {
        TradeExpressProperties properties = new TradeExpressProperties();

        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void shouldRejectSelectedProviderWithoutCredentials() {
        TradeExpressProperties properties = new TradeExpressProperties();
        properties.setClient(ExpressClientEnum.KD_100);
        properties.setKd100(new TradeExpressProperties.Kd100Config());

        assertFalse(validator.validate(properties).isEmpty());
    }

    @Test
    void shouldAcceptSelectedProviderWithCompleteCredentials() {
        TradeExpressProperties properties = new TradeExpressProperties();
        properties.setClient(ExpressClientEnum.KD_NIAO);
        TradeExpressProperties.KdNiaoConfig config = new TradeExpressProperties.KdNiaoConfig();
        config.setBusinessId("business-id");
        config.setApiKey("api-key");
        config.setRequestType("1002");
        properties.setKdNiao(config);

        assertTrue(validator.validate(properties).isEmpty());
    }
}
