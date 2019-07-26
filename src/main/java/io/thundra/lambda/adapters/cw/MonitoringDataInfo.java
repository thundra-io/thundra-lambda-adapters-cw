package io.thundra.lambda.adapters.cw;

/**
 * @author serkan
 */
public class MonitoringDataInfo {

    private final String monitoringData;
    private final String apiKey;

    public MonitoringDataInfo(String monitoringData, String apiKey) {
        this.monitoringData = monitoringData;
        this.apiKey = apiKey;
    }

    public String getMonitoringData() {
        return monitoringData;
    }

    public String getApiKey() {
        return apiKey;
    }

}
