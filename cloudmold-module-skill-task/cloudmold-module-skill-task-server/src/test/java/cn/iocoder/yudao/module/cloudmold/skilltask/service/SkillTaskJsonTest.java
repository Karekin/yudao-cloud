package cn.iocoder.yudao.module.cloudmold.skilltask.service;

import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillTaskJsonTest {

    @Test
    void canonicalizesObjectKeysForStableReplayHashes() throws Exception {
        SkillTaskProperties properties = new SkillTaskProperties();
        SkillTaskJson json = new SkillTaskJson(new ObjectMapper(), properties);

        String first = json.canonical(json.parse("{\"b\":2,\"a\":{\"d\":4,\"c\":3}}", "input"));
        String second = json.canonical(json.parse("{\"a\":{\"c\":3,\"d\":4},\"b\":2}", "input"));

        assertThat(first).isEqualTo("{\"a\":{\"c\":3,\"d\":4},\"b\":2}");
        assertThat(json.sha256(first)).isEqualTo(json.sha256(second)).hasSize(64);
    }

    @Test
    void rejectsOversizedLedgerPayloads() throws Exception {
        SkillTaskProperties properties = new SkillTaskProperties();
        properties.setMaxPayloadBytes(8);
        SkillTaskJson json = new SkillTaskJson(new ObjectMapper(), properties);

        assertThatThrownBy(() -> json.canonical(new ObjectMapper().readTree("{\"value\":123}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }
}
