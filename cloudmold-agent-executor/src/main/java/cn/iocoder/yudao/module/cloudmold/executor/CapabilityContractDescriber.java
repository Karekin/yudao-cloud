package cn.iocoder.yudao.module.cloudmold.executor;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class CapabilityContractDescriber {

    private static final int MAX_SCHEMA_DEPTH = 4;

    private final CloudMoldCapabilityCatalog catalog;
    private final ObjectMapper objectMapper;

    public CapabilityContractDescriber(CloudMoldCapabilityCatalog catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    public CapabilityContract describe(String capabilityId) {
        CapabilityDescriptor descriptor = catalog.require(capabilityId);
        return new CapabilityContract(
                descriptor.capabilityId(),
                descriptor.interfaceName(),
                descriptor.methodName(),
                descriptor.operationType(),
                descriptor.operationType() == CapabilityOperationType.WRITE,
                descriptor.group(),
                descriptor.version(),
                descriptor.timeoutMillis(),
                describeArguments(descriptor),
                describeType(descriptor.method().getGenericReturnType(), 0, new IdentityHashMap<>())
        );
    }

    private JsonNode describeArguments(CapabilityDescriptor descriptor) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("x-schema-kind", "cloudmold-capability-arguments");
        ArrayNode required = schema.putArray("required");
        ObjectNode properties = schema.putObject("properties");

        Parameter[] parameters = descriptor.method().getParameters();
        Type[] genericTypes = descriptor.method().getGenericParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            String parameterName = parameterName(parameters[index], index);
            required.add(parameterName);
            JsonNode propertySchema = describeType(genericTypes[index], 0, new IdentityHashMap<>());
            if (propertySchema instanceof ObjectNode objectNode) {
                objectNode.put("x-java-parameter-index", index);
                objectNode.put("x-java-parameter-type", descriptor.parameterTypes().get(index));
            }
            properties.set(parameterName, propertySchema);
        }
        return schema;
    }

    private String parameterName(Parameter parameter, int index) {
        if (parameter.isNamePresent() && !parameter.getName().startsWith("arg")) {
            return parameter.getName();
        }
        String simple = parameter.getType().getSimpleName();
        if (simple.isEmpty()) {
            return "arg" + index;
        }
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    private JsonNode describeType(Type type, int depth, IdentityHashMap<Type, Boolean> seen) {
        if (type == null) {
            return anySchema();
        }
        if (type instanceof JavaType javaType) {
            return describeJavaType(javaType, depth, seen);
        }
        if (depth >= MAX_SCHEMA_DEPTH || seen.containsKey(type)) {
            return refSchema(type);
        }
        seen.put(type, Boolean.TRUE);
        try {
            if (type instanceof Class<?> rawClass) {
                return describeClass(rawClass, depth, seen);
            }
            if (type instanceof ParameterizedType parameterizedType) {
                return describeParameterizedType(parameterizedType, depth, seen);
            }
            return refSchema(type);
        } finally {
            seen.remove(type);
        }
    }

    private JsonNode describeJavaType(JavaType type, int depth, IdentityHashMap<Type, Boolean> seen) {
        if (type == null) {
            return anySchema();
        }
        if (depth >= MAX_SCHEMA_DEPTH || seen.containsKey(type)) {
            return refSchema(type);
        }
        seen.put(type, Boolean.TRUE);
        try {
            Class<?> rawClass = type.getRawClass();
            if (Optional.class.isAssignableFrom(rawClass) && type.containedTypeCount() == 1) {
                return describeJavaType(type.containedType(0), depth + 1, seen);
            }
            if (Collection.class.isAssignableFrom(rawClass)) {
                ObjectNode schema = typedSchema("array");
                JsonNode itemSchema = type.containedTypeCount() == 1
                        ? describeJavaType(type.containedType(0), depth + 1, seen)
                        : anySchema();
                schema.set("items", itemSchema);
                schema.put("x-java-type", type.toCanonical());
                return schema;
            }
            if (Map.class.isAssignableFrom(rawClass)) {
                ObjectNode schema = typedSchema("object");
                JsonNode valueSchema = type.containedTypeCount() >= 2
                        ? describeJavaType(type.containedType(1), depth + 1, seen)
                        : anySchema();
                schema.set("additionalProperties", valueSchema);
                schema.put("x-java-type", type.toCanonical());
                return schema;
            }
            return describeClass(rawClass, depth, seen);
        } finally {
            seen.remove(type);
        }
    }

    private JsonNode describeParameterizedType(ParameterizedType type, int depth, IdentityHashMap<Type, Boolean> seen) {
        Type raw = type.getRawType();
        if (!(raw instanceof Class<?> rawClass)) {
            return refSchema(type);
        }
        if (Optional.class.isAssignableFrom(rawClass)) {
            return type.getActualTypeArguments().length == 1
                    ? describeType(type.getActualTypeArguments()[0], depth + 1, seen)
                    : anySchema();
        }
        if (Collection.class.isAssignableFrom(rawClass)) {
            ObjectNode schema = typedSchema("array");
            Type itemType = type.getActualTypeArguments().length == 1 ? type.getActualTypeArguments()[0] : Object.class;
            schema.set("items", describeType(itemType, depth + 1, seen));
            schema.put("x-java-type", type.getTypeName());
            return schema;
        }
        if (Map.class.isAssignableFrom(rawClass)) {
            ObjectNode schema = typedSchema("object");
            Type valueType = type.getActualTypeArguments().length >= 2 ? type.getActualTypeArguments()[1] : Object.class;
            schema.set("additionalProperties", describeType(valueType, depth + 1, seen));
            schema.put("x-java-type", type.getTypeName());
            return schema;
        }
        return describeClass(rawClass, depth, seen);
    }

    private JsonNode describeClass(Class<?> type, int depth, IdentityHashMap<Type, Boolean> seen) {
        if (type == Void.TYPE || type == Void.class) {
            ObjectNode schema = typedSchema("null");
            schema.put("x-java-type", type.getName());
            return schema;
        }
        if (type == String.class || CharSequence.class.isAssignableFrom(type)
                || type == Character.class || type == char.class) {
            return scalarSchema("string", type);
        }
        if (type == Boolean.class || type == boolean.class) {
            return scalarSchema("boolean", type);
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class
                || type == Short.class || type == short.class || type == Byte.class || type == byte.class
                || type == BigInteger.class) {
            return scalarSchema("integer", type);
        }
        if (type == Float.class || type == float.class || type == Double.class || type == double.class
                || type == BigDecimal.class) {
            return scalarSchema("number", type);
        }
        if (type.isEnum()) {
            ObjectNode schema = scalarSchema("string", type);
            ArrayNode values = schema.putArray("enum");
            for (Object constant : type.getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
            return schema;
        }
        if (Temporal.class.isAssignableFrom(type)) {
            ObjectNode schema = scalarSchema("string", type);
            schema.put("format", "date-time");
            return schema;
        }
        if (type.isArray()) {
            ObjectNode schema = typedSchema("array");
            schema.set("items", describeType(type.getComponentType(), depth + 1, seen));
            schema.put("x-java-type", type.getName());
            return schema;
        }
        if (Collection.class.isAssignableFrom(type)) {
            ObjectNode schema = typedSchema("array");
            schema.set("items", anySchema());
            schema.put("x-java-type", type.getName());
            return schema;
        }
        if (Map.class.isAssignableFrom(type)) {
            ObjectNode schema = typedSchema("object");
            schema.put("additionalProperties", true);
            schema.put("x-java-type", type.getName());
            return schema;
        }
        if (type.isPrimitive() || type.getName().startsWith("java.")) {
            return refSchema(type);
        }
        return objectSchema(type, depth, seen);
    }

    private JsonNode objectSchema(Class<?> type, int depth, IdentityHashMap<Type, Boolean> seen) {
        ObjectNode schema = typedSchema("object");
        schema.put("x-java-type", type.getName());
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        Set<String> propertyNames = new LinkedHashSet<>();
        objectMapper.getSerializationConfig()
                .introspect(objectMapper.constructType(type))
                .findProperties()
                .forEach(property -> {
                    String name = property.getName();
                    if (!propertyNames.add(name)) {
                        return;
                    }
                    Type propertyType = property.getPrimaryType() != null
                            ? property.getPrimaryType()
                            : property.getPrimaryMember() != null ? property.getPrimaryMember().getType() : Object.class;
                    properties.set(name, describeType(propertyType, depth + 1, seen));
                    required.add(name);
                });
        return schema;
    }

    private ObjectNode scalarSchema(String type, Class<?> javaType) {
        ObjectNode schema = typedSchema(type);
        schema.put("x-java-type", javaType.getName());
        return schema;
    }

    private ObjectNode typedSchema(String type) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", type);
        return schema;
    }

    private JsonNode anySchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("x-schema-kind", "any");
        return schema;
    }

    private JsonNode refSchema(Type type) {
        ObjectNode schema = anySchema().deepCopy();
        schema.put("x-java-type", type.getTypeName());
        return schema;
    }
}
