package cn.iocoder.yudao.module.cloudmold.executor;

import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CloudMoldExecutorCli implements ApplicationRunner {

    private final CloudMoldCapabilityCatalog catalog;
    private final CloudMoldCapabilityExecutor executor;
    private final CapabilityContractDescriber contractDescriber;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext applicationContext;

    public CloudMoldExecutorCli(CloudMoldCapabilityCatalog catalog, CloudMoldCapabilityExecutor executor,
                                CapabilityContractDescriber contractDescriber, ObjectMapper objectMapper,
                                ConfigurableApplicationContext applicationContext) {
        this.catalog = catalog;
        this.executor = executor;
        this.contractDescriber = contractDescriber;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            if (args.containsOption("list-capabilities")) {
                System.out.println(objectMapper.writeValueAsString(catalog.all().stream()
                        .map(CapabilityOutput::from).toList()));
                return;
            }
            if (args.containsOption("describe-capability")) {
                System.out.println(objectMapper.writeValueAsString(
                        contractDescriber.describe(required(args, "describe-capability"))));
                return;
            }
            String capabilityId = required(args, "capability-id");
            JsonNode arguments = objectMapper.readTree(optional(args, "arguments-json", "[]"));
            CloudMoldRpcCallContext context = new CloudMoldRpcCallContext(
                    positiveLong(args, "tenant-id"),
                    positiveLong(args, "operator-id"),
                    Math.toIntExact(positiveLong(args, "operator-type")),
                    required(args, "skill-id"),
                    required(args, "run-id"));
            boolean writeApproved = Boolean.parseBoolean(optional(args, "write-approved", "false"));
            JsonNode result = executor.execute(capabilityId, arguments, context, writeApproved);
            System.out.println(objectMapper.writeValueAsString(new InvocationOutput(capabilityId, context.runId(),
                    "SUCCEEDED", result)));
        } catch (Exception ex) {
            System.err.println(objectMapper.writeValueAsString(new ErrorOutput("FAILED", ex.getClass().getName(),
                    ex.getMessage())));
            throw ex;
        } finally {
            SpringApplication.exit(applicationContext, () -> 0);
        }
    }

    private static String required(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.get(0).isBlank()) {
            throw new IllegalArgumentException("--" + name + " is required exactly once");
        }
        return values.get(0).trim();
    }

    private static String optional(ApplicationArguments args, String name, String defaultValue) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? defaultValue : values.get(0);
    }

    private static long positiveLong(ApplicationArguments args, String name) {
        long value = Long.parseLong(required(args, name));
        if (value <= 0) {
            throw new IllegalArgumentException("--" + name + " must be positive");
        }
        return value;
    }

    private record CapabilityOutput(String capabilityId, String interfaceName, String methodName,
                                    List<String> parameterTypes, String returnType,
                                    CapabilityOperationType operationType, String group, String version,
                                    int timeoutMillis) {
        static CapabilityOutput from(CapabilityDescriptor value) {
            return new CapabilityOutput(value.capabilityId(), value.interfaceName(), value.methodName(),
                    value.parameterTypes(), value.returnType(), value.operationType(), value.group(),
                    value.version(), value.timeoutMillis());
        }
    }

    private record InvocationOutput(String capabilityId, String runId, String status, JsonNode result) {
    }

    private record ErrorOutput(String status, String errorType, String message) {
    }
}
