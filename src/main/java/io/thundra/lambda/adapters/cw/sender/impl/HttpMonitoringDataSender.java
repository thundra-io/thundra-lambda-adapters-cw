package io.thundra.lambda.adapters.cw.sender.impl;

import com.amazonaws.services.lambda.runtime.Context;
import io.thundra.lambda.adapters.cw.Config;
import io.thundra.lambda.adapters.cw.MonitoringDataInfo;
import io.thundra.lambda.adapters.cw.sender.MonitoringDataSender;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author serkan
 */
public class HttpMonitoringDataSender implements MonitoringDataSender {

    private static final int MAX_MONITORING_DATA_BATCH_SIZE = 100;

    private final HttpClient httpClient;

    public HttpMonitoringDataSender() {
        httpClient = createHttpClient();
    }

    public HttpMonitoringDataSender(HttpClientBuilder httpClientBuilder) {
        httpClient = httpClientBuilder.build();
    }

    @Override
    public String getType() {
        return "http";
    }

    private static HttpClient createHttpClient() {
        HttpClientBuilder httpClientBuilder =
                HttpClientBuilder.create().
                        setDefaultSocketConfig(
                                SocketConfig.
                                        custom().
                                        setSoKeepAlive(true).
                                        setTcpNoDelay(true).
                                        build());
        if (Config.THUNDRA_TRUST_ALL_CERTIFICATES) {
            try {
                httpClientBuilder.
                        setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).
                        setSslcontext(new SSLContextBuilder()
                                .loadTrustMaterial(null, (chain, authType) -> true).build());
            } catch (Throwable t) {
                throw new IllegalStateException(
                        "[ERROR] " +
                                "Error occurred while enabling trusting all certificates behaviour. " +
                                "So this behaviour will not be active.",
                        t);
            }
        }
        return httpClientBuilder.build();
    }

    @Override
    public void send(Context context, List<MonitoringDataInfo> monitoringDataInfos) throws IOException {
        Map<String, List<JSONObject>> monitoringDataJsonObjectMap = new HashMap<>();
        List<JSONObject> compositeMonitoringDataJsonObjList = new ArrayList<>();

        for (MonitoringDataInfo monitoringDataInfo : monitoringDataInfos) {
            String monitoringData = monitoringDataInfo.getMonitoringData();
            String apiKey = monitoringDataInfo.getApiKey();
            JSONObject monitoringDataJsonObj = new JSONObject(monitoringData);
            if (!monitoringDataJsonObj.has("type")) {
                context.getLogger().log("[ERROR] There is no 'type' in the monitoring data: " + monitoringData);
                continue;
            }
            if (!monitoringDataJsonObj.has("data")) {
                context.getLogger().log("[ERROR] There is no 'data' in the monitoring data: " + monitoringData);
                continue;
            }
            monitoringDataJsonObj.put("apiKey", apiKey);
            String type = monitoringDataJsonObj.getString("type");
            if ("Invocation".equals(type) ||
                    "Span".equals(type) ||
                    "Metric".equals(type) ||
                    "Log".equals(type)) {
                List<JSONObject> monitoringDataJsonObjs = monitoringDataJsonObjectMap.get(apiKey);
                if (monitoringDataJsonObjs == null) {
                    monitoringDataJsonObjs = new ArrayList<>();
                    monitoringDataJsonObjectMap.put(apiKey, monitoringDataJsonObjs);
                }
                monitoringDataJsonObjs.add(monitoringDataJsonObj);
            } else if ("Composite".equals(type)) {
                compositeMonitoringDataJsonObjList.add(monitoringDataJsonObj);
            } else {
                context.getLogger().log("[ERROR] Unknown monitoring data type: " + type);
            }
        }

        List<Throwable> errors = new ArrayList<>();

        for (Map.Entry<String, List<JSONObject>> e : monitoringDataJsonObjectMap.entrySet()) {
            String apiKey = e.getKey();
            List<JSONObject> monitoringDataJsonObjs = e.getValue();
            if (monitoringDataJsonObjs != null && !monitoringDataJsonObjs.isEmpty()) {
                for (int i = 0; i < monitoringDataJsonObjs.size(); i += MAX_MONITORING_DATA_BATCH_SIZE) {
                    List<JSONObject> subMonitoringDataJsonObjs =
                            monitoringDataJsonObjs.subList(
                                    i,
                                    Math.min(i + MAX_MONITORING_DATA_BATCH_SIZE, monitoringDataJsonObjs.size()));
                    String batchedMonitoringData = buildBatchedMonitoringData(subMonitoringDataJsonObjs);
                    try {
                        if (Config.THUNDRA_ENABLE_DEBUG_LOGS) {
                            context.getLogger()
                                    .log("[DEBUG] Sending batched monitoring data: " + batchedMonitoringData);
                        }
                        sendMonitoringData(context, httpClient, "monitoring-data", batchedMonitoringData, apiKey);
                    } catch (Throwable t) {
                        context.getLogger().log("[ERROR] " + t.toString());
                        errors.add(t);
                    }
                }
            }
        }

        for (JSONObject compositeMonitoringDataJsonObj : compositeMonitoringDataJsonObjList) {
            try {
                String apiKey = compositeMonitoringDataJsonObj.getString("apiKey");
                String compositeMonitoringData = compositeMonitoringDataJsonObj.toString();
                if (Config.THUNDRA_ENABLE_DEBUG_LOGS) {
                    context.getLogger().log("[DEBUG] Sending composite monitoring data: " + compositeMonitoringData);
                }
                sendMonitoringData(context, httpClient, "composite-monitoring-data", compositeMonitoringData, apiKey);
            } catch (Throwable t) {
                context.getLogger().log("[ERROR] " + t.toString());
                errors.add(t);
            }
        }

        if (!errors.isEmpty()) {
            IOException exception = new IOException("Sending monitoring data failed!");
            for (Throwable error : errors) {
                error.addSuppressed(exception);
            }
            throw exception;
        }
    }
    //CHECKSTYLE:OFF
    public String buildBatchedMonitoringData(List<JSONObject> monitoringDataJsonObjs) {
        StringBuilder batchedMonitoringDataBuilder = new StringBuilder(4096);
        batchedMonitoringDataBuilder.append("[");
        for (int i = 0; i < monitoringDataJsonObjs.size(); i++) {
            JSONObject subMonitoringDataJsonObj = monitoringDataJsonObjs.get(i);
            if (i > 0) {
                batchedMonitoringDataBuilder.append(", ");
            }
            batchedMonitoringDataBuilder.append(subMonitoringDataJsonObj.toString());
        }
        batchedMonitoringDataBuilder.append("]");
        return batchedMonitoringDataBuilder.toString();
    }

    void sendMonitoringData(Context context, HttpClient httpClient, String path, String monitoringData, String apiKey)
            throws IOException {
        String url = Config.THUNDRA_DATA_COLLECTOR_API_URL + "/" + path;
        if (Config.THUNDRA_ENABLE_DEBUG_LOGS) {
            context.getLogger().log("[DEBUG] Sending  monitoring data to URL " + url);
        }
        HttpPost httpPost = prepareRequest(url, apiKey, monitoringData);
        HttpResponse httpResponse = httpClient.execute(httpPost);
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        int statusCodeGroup = statusCode / 100;
        if (statusCodeGroup != 2) {
            String responseString = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
            throw new IOException(
                    String.format(
                            "Sending monitoring data to Thundra over URL '%s' has failed " +
                            "with status code %d and response message '%s'",
                            url, statusCode, responseString));
        }
    }
    //CHECKSTYLE:ON

    HttpPost prepareRequest(String url, String apiKey, String monitoringData) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        httpPost.setHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey);
        StringEntity entity = new StringEntity(monitoringData, "UTF-8");
        entity.setChunked(false);
        httpPost.setEntity(entity);
        return httpPost;
    }

}
