package cn.iocoder.yudao.module.cloudmold.rpc;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CloudMoldDubboServiceAllowlistTest {

    @Test
    void shouldLoadEveryCloudMoldPublicApiContractName() {
        Set<String> services = CloudMoldDubboServiceAllowlist.load();

        assertThat(services).hasSize(85)
                .contains("cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantKnowledgeCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantKnowledgeQueryApi",
                        "cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi",
                        "cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi");
        assertThat(services).allSatisfy(service -> {
            assertThat(service).startsWith("cn.iocoder.yudao.module.cloudmold.");
            assertThat(service).endsWith("Api");
        });
    }
}
