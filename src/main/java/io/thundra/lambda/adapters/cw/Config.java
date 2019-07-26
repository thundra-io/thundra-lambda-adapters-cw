package io.thundra.lambda.adapters.cw;

/**
 * @author serkan
 */
public final class Config {

    public static final String THUNDRA_API_KEY =
            getProperty("thundra.apiKey");
    public static final boolean THUNDRA_ENABLE_DEBUG_LOGS =
            Boolean.parseBoolean(getProperty("thundra.lambda.adapters.cw.enableDebugLogs"));
    public static final boolean THUNDRA_IGNORE_ERRORS =
            Boolean.parseBoolean(getProperty("thundra.lambda.adapters.cw.ignoreErrors"));
    public static final String THUNDRA_DATA_SENDER_TYPE =
            getProperty("thundra.lambda.adapters.cw.send.senderType", "http");
    public static final boolean THUNDRA_DATA_SENDER_IGNORE_ERRORS =
            Boolean.parseBoolean(getProperty("thundra.lambda.adapters.cw.send.ignoreErrors"));
    public static final String THUNDRA_DATA_COLLECTOR_API_URL =
            getProperty("thundra.lambda.adapters.cw.send.rest.baseUrl", "https://api.thundra.io/v1");
    public static final boolean THUNDRA_TRUST_ALL_CERTIFICATES =
            Boolean.getBoolean(getProperty("thundra.lambda.adapters.cw.send.rest.trustAllCertificates", "false"));

    static {
        print();
    }

    private Config() {
    }

    public static String getProperty(String propName) {
        return System.getenv(propName.replace(".", "_"));
    }

    public static String getProperty(String propName, String defaultValue) {
        String propValue = getProperty(propName);
        if (propValue == null) {
            return defaultValue;
        }
        return propValue;
    }

    public static void print() {
        System.out.println("===============================================================");
        System.out.println("thundra.lambda.adapters.cw.enableDebugLogs: " + THUNDRA_ENABLE_DEBUG_LOGS);
        System.out.println("thundra.lambda.adapters.cw.ignoreErrors: " + THUNDRA_IGNORE_ERRORS);
        System.out.println("thundra.lambda.adapters.cw.send.senderType: " + THUNDRA_DATA_SENDER_TYPE);
        System.out.println("thundra.lambda.adapters.cw.send.ignoreErrors: " + THUNDRA_DATA_SENDER_IGNORE_ERRORS);
        System.out.println("thundra.lambda.adapters.cw.send.rest.baseUrl: " + THUNDRA_DATA_COLLECTOR_API_URL);
        System.out.println("thundra.lambda.adapters.cw.send.rest.trustAllCertificates: " + THUNDRA_TRUST_ALL_CERTIFICATES);
        System.out.println("===============================================================");
    }

}
