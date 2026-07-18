package cn.iocoder.yudao.module.cloudmold.dreamplant.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DreamPlantCanonicalJsonTest {

    @Test
    void canonicalizeShouldIgnoreObjectFieldOrderAndWhitespace() {
        var left = DreamPlantCanonicalJson.canonicalize("{\"z\":1,\"nested\":{\"b\":2,\"a\":1}}");
        var right = DreamPlantCanonicalJson.canonicalize(" { \"nested\" : { \"a\" : 1, \"b\" : 2 }, \"z\" : 1 } ");

        assertThat(left.json()).isEqualTo("{\"nested\":{\"a\":1,\"b\":2},\"z\":1}");
        assertThat(left).isEqualTo(right);
    }

    @Test
    void canonicalizeShouldRetainArrayOrder() {
        var first = DreamPlantCanonicalJson.canonicalize("{\"values\":[1,2]}");
        var second = DreamPlantCanonicalJson.canonicalize("{\"values\":[2,1]}");

        assertThat(first.sha256()).isNotEqualTo(second.sha256());
    }
}
