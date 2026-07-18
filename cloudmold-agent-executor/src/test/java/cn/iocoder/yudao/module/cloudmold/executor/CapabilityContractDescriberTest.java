package cn.iocoder.yudao.module.cloudmold.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityContractDescriberTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldDescribeWriteCapabilityAsMcpReadyJsonSchema() {
        CloudMoldCapabilityCatalog catalog = new CloudMoldCapabilityCatalog(new cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcProperties());
        CapabilityContractDescriber describer = new CapabilityContractDescriber(catalog, objectMapper);

        CapabilityContract contract = describer.describe(
                "capability.cloudmold.integration.yudao-procurement-promise.save-purchase-promise.v1");

        assertThat(contract.operationType()).isEqualTo(CapabilityOperationType.WRITE);
        assertThat(contract.writeApprovalRequired()).isTrue();
        assertThat(contract.argumentsSchema().path("type").asText()).isEqualTo("object");
        ObjectNode argumentProperties = (ObjectNode) contract.argumentsSchema().path("properties");
        assertThat(argumentProperties).hasSize(1);
        Iterator<String> writeArgumentNames = argumentProperties.fieldNames();
        String writeArgumentName = writeArgumentNames.next();
        assertThat(argumentProperties.path(writeArgumentName).path("type").asText()).isEqualTo("object");
        assertThat(argumentProperties.path(writeArgumentName)
                .path("properties").path("purchaseOrderLineId").path("type").asText()).isEqualTo("integer");
        assertThat(contract.resultSchema().path("properties").path("promiseId").path("type").asText())
                .isEqualTo("string");
        assertThat(contract.resultSchema().path("properties").path("orderedQuantity").path("type").asText())
                .isEqualTo("number");
    }

    @Test
    void shouldDescribeReadCapabilityWithScalarArgument() {
        CloudMoldCapabilityCatalog catalog = new CloudMoldCapabilityCatalog(new cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcProperties());
        CapabilityContractDescriber describer = new CapabilityContractDescriber(catalog, objectMapper);

        CapabilityContract contract = describer.describe(
                "capability.cloudmold.integration.yudao-procurement-promise.get-purchase-promise.v1");

        assertThat(contract.operationType()).isEqualTo(CapabilityOperationType.READ);
        assertThat(contract.writeApprovalRequired()).isFalse();
        ObjectNode argumentProperties = (ObjectNode) contract.argumentsSchema().path("properties");
        assertThat(argumentProperties).hasSize(1);
        Iterator<String> readArgumentNames = argumentProperties.fieldNames();
        String readArgumentName = readArgumentNames.next();
        assertThat(argumentProperties.path(readArgumentName).path("type").asText()).isEqualTo("integer");
        assertThat(contract.resultSchema().path("properties").path("promiseKey").path("type").asText())
                .isEqualTo("string");
    }
}
