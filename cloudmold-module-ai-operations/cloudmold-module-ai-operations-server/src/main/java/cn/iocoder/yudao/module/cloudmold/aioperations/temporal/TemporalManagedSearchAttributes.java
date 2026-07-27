package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TemporalManagedSearchAttributes {

    static final String TENANT_ID = "CustomIntField";
    static final String SCHEDULE_ID = "CustomKeywordField";

    private TemporalManagedSearchAttributes() {
    }

    public static Map<String, Object> from(TemporalManagedRunRequest request,
                                           TemporalManagedRunState state) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        put(attributes, TENANT_ID, request.getTenantId());
        put(attributes, SCHEDULE_ID, request.getScheduleId() + "|" + request.getSkillId() + "|" + state.getStatus());
        return attributes;
    }

    private static void put(Map<String, Object> attributes, String key, Object value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }
}
