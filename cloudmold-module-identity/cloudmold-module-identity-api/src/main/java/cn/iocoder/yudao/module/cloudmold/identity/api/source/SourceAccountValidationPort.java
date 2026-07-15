package cn.iocoder.yudao.module.cloudmold.identity.api.source;

/**
 * Anti-corruption port for validating a credential/profile authority without copying its profile or credentials.
 *
 * <p>The caller supplies the tenant explicitly so an adapter cannot accidentally validate an account under a
 * different tenant context.</p>
 */
public interface SourceAccountValidationPort {

    boolean supports(String sourceSystem, String sourceType);

    void requireActive(Long tenantId, String sourceSystem, String sourceType, String sourceId);
}
