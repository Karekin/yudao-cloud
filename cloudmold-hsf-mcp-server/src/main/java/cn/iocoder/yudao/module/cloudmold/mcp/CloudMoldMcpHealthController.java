package cn.iocoder.yudao.module.cloudmold.mcp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
final class CloudMoldMcpHealthController {

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "UP", "service", "cloudmold-hsf-mcp-server");
    }
}
