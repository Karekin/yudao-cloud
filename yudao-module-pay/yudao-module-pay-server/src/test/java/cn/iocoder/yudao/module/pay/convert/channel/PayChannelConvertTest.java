package cn.iocoder.yudao.module.pay.convert.channel;

import cn.iocoder.yudao.module.pay.controller.admin.channel.vo.PayChannelRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.NonePayClientConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PayChannelConvertTest {

    @Test
    void detailPreservesConfigurationRequiredByTheEditContract() {
        PayChannelDO channel = new PayChannelDO();
        channel.setId(1L);
        channel.setConfig(new NonePayClientConfig());

        PayChannelRespVO response = PayChannelConvert.INSTANCE.convert(channel);

        assertNotNull(response.getConfig());
    }

    @Test
    void listAndPageRedactPersistedChannelConfiguration() {
        PayChannelDO channel = new PayChannelDO();
        channel.setId(1L);
        channel.setConfig(new NonePayClientConfig());

        List<PayChannelRespVO> list = PayChannelConvert.INSTANCE.convertList(List.of(channel));
        PageResult<PayChannelRespVO> page = PayChannelConvert.INSTANCE.convertPage(
                new PageResult<>(List.of(channel), 1L));

        assertNull(list.get(0).getConfig());
        assertNull(page.getList().get(0).getConfig());
    }
}
