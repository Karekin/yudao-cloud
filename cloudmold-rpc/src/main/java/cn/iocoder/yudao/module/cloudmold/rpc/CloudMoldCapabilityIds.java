package cn.iocoder.yudao.module.cloudmold.rpc;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CloudMoldCapabilityIds {

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    private static final String PACKAGE_MARKER = ".cloudmold.";

    private CloudMoldCapabilityIds() {
    }

    public static String forMethod(Method method) {
        return forMethod(method.getDeclaringClass(), method.getName(), method.getParameterTypes());
    }

    public static String forMethod(Class<?> serviceInterface, String methodName, Class<?>[] parameterTypes) {
        String packageName = serviceInterface.getPackageName();
        int marker = packageName.indexOf(PACKAGE_MARKER);
        if (marker < 0) {
            throw new IllegalArgumentException("Not a CloudMold interface: " + serviceInterface.getName());
        }
        String domainPath = packageName.substring(marker + PACKAGE_MARKER.length());
        int separator = domainPath.indexOf('.');
        String domain = separator < 0 ? domainPath : domainPath.substring(0, separator);
        String serviceName = serviceInterface.getSimpleName();
        if (serviceName.endsWith("Api")) {
            serviceName = serviceName.substring(0, serviceName.length() - 3);
        }
        String overloadSuffix = isOverloaded(serviceInterface, methodName)
                ? ".sig-" + signatureHash(parameterTypes) : "";
        return "capability.cloudmold." + kebab(domain) + "." + kebab(serviceName) + "."
                + kebab(methodName) + overloadSuffix + ".v1";
    }

    private static boolean isOverloaded(Class<?> serviceInterface, String methodName) {
        return Arrays.stream(serviceInterface.getMethods()).filter(method -> method.getName().equals(methodName))
                .limit(2).count() > 1;
    }

    private static String signatureHash(Class<?>[] parameterTypes) {
        String signature = Arrays.stream(parameterTypes).map(Class::getName).reduce((left, right) -> left + "," + right)
                .orElse("");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String kebab(String value) {
        return CAMEL_BOUNDARY.matcher(value).replaceAll("-").replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
