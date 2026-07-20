package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AgentControlSerializationContractTest {
    @Test
    void everyPublicDtoIncludingNestedDefinitionsIsSerializable() {
        Stream.concat(Stream.of(AgentControlCommand.class, AgentAuthorityCommand.class, AgentControlResult.class),
                        Stream.concat(Arrays.stream(AgentControlCommand.class.getDeclaredClasses()),
                                Arrays.stream(AgentAuthorityCommand.class.getDeclaredClasses()))
                                .filter(type -> !type.getSimpleName().endsWith("Builder")))
                .forEach(type -> assertThat(Serializable.class.isAssignableFrom(type))
                        .as(type.getName() + " must support RPC serialization")
                        .isTrue());
    }

    @Test
    void ordinaryAgentControlCommandsCannotGrantOrRevokeAuthority() {
        assertThat(Arrays.stream(AgentControlOperation.values()).map(Enum::name))
                .noneMatch(name -> name.contains("GRANT") || name.contains("REVOKE"));
        assertThat(AgentAuthorityOperation.values()).containsExactly(
                AgentAuthorityOperation.GRANT_ROLE, AgentAuthorityOperation.REVOKE_ROLE,
                AgentAuthorityOperation.GRANT_APPROVER, AgentAuthorityOperation.REVOKE_APPROVER);
    }
}
