package io.thundra.lambda.adapters.cw;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.util.StringUtils;
import io.thundra.lambda.adapters.cw.sender.MonitoringDataSender;
import io.thundra.lambda.adapters.cw.sender.impl.HttpMonitoringDataSender;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * @author serkan
 */
public class MonitoringDataCloudWatchHandler implements RequestHandler<Map<String, Object>, Void> {

    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private MonitoringDataSender monitoringDataSender;

    public MonitoringDataCloudWatchHandler() {
        this.monitoringDataSender = new HttpMonitoringDataSender();
    }

    private MonitoringDataInfo processMonitoring(JSONObject monitoringJsonObj) {
        String apiKey = null;
        if (monitoringJsonObj.has("apiKey")) {
            apiKey = monitoringJsonObj.getString("apiKey");
        }
        if (apiKey == null) {
            if (StringUtils.isNullOrEmpty(Config.THUNDRA_API_KEY) || "N/A".equals(Config.THUNDRA_API_KEY)) {
                throw new IllegalStateException(
                        "There is no specified Thundra API key " +
                        "neither in monitoring data nor in monitor lambda environment variables");
            } else {
                apiKey = Config.THUNDRA_API_KEY;
            }
        }
        monitoringJsonObj.put("apiKey", apiKey);
        return new MonitoringDataInfo(monitoringJsonObj.toString(), apiKey);
    }

    private static String decompress(String data) throws IOException {
        byte[] decodedAwsLogsData = DECODER.decode(data);
        //CHECKSTYLE:OFF
        StringBuilder sb = new StringBuilder(data.length() * 16);
        //CHECKSTYLE:ON
        GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(decodedAwsLogsData));
        for (int i = gzip.read(); i >= 0; i = gzip.read()) {
            sb.append((char) i);
        }
        return sb.toString();
    }

    @Override
    public Void handleRequest(final Map<String, Object> logEventMap,
                              final Context context) {
        List<MonitoringDataInfo> monitoringDataInfos = new ArrayList<>();
        try {
            Map<String, Object> awsLogs = (Map<String, Object>) logEventMap.get("awslogs");
            if (awsLogs != null) {
                String awsLogsData = (String) awsLogs.get("data");
                if (awsLogsData == null || awsLogsData.length() == 0) {
                    return null;
                }
                String eventDataJson = decompress(awsLogsData);
                JSONArray logEvents = new JSONObject(eventDataJson).getJSONArray("logEvents");
                for (int i = 0; i < logEvents.length(); i++) {
                    JSONObject logEvent = logEvents.getJSONObject(i);
                    String monitoringData = logEvent.getString("message");
                    JSONObject monitoringDataJsonObj = new JSONObject(monitoringData);
                    processMonitoringData(context, monitoringDataInfos, monitoringDataJsonObj);
                }
            } else {
                List<Map<String, Object>> monitoringDataLogs =
                        (List<Map<String, Object>>) logEventMap.get("monitoringDataLogs");
                if (monitoringDataLogs != null) {
                    for (Map<String, Object> monitoringDataLog : monitoringDataLogs) {
                        JSONObject monitoringDataJsonObj = new JSONObject(monitoringDataLog);
                        processMonitoringData(context, monitoringDataInfos, monitoringDataJsonObj);
                    }
                }
            }

            try {
                monitoringDataSender.send(context, monitoringDataInfos);
            } catch (Throwable error) {
                if (!Config.THUNDRA_DATA_SENDER_IGNORE_ERRORS) {
                    //ExceptionUtil.sneakyThrow(error);
                }
            }
        } catch (Throwable error) {
            if (!Config.THUNDRA_IGNORE_ERRORS) {
                //ExceptionUtil.sneakyThrow(error);
            }
        }

        return null;
    }

    private void processMonitoringData(Context context,
                                       List<MonitoringDataInfo> monitoringDataInfos,
                                       JSONObject monitoringDataJsonObj) throws IOException {
        boolean compressed = false;
        if (monitoringDataJsonObj.has("compressed")) {
            compressed = monitoringDataJsonObj.getBoolean("compressed");
        }
        if (compressed) {
            String data = monitoringDataJsonObj.getString("data");
            String decompressedData = decompress(data);
            monitoringDataJsonObj.put("data", new JSONObject(decompressedData));
        }
        if (Config.THUNDRA_ENABLE_DEBUG_LOGS) {
            context.getLogger().log("[INFO] Received monitoring data: " + monitoringDataJsonObj.toString());
        }
        MonitoringDataInfo monitoringDataInfo = processMonitoring(monitoringDataJsonObj);
        monitoringDataInfos.add(monitoringDataInfo);
    }

}
