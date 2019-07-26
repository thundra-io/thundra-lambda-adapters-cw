package io.thundra.lambda.adapters.cw.sender;

import com.amazonaws.services.lambda.runtime.Context;
import io.thundra.lambda.adapters.cw.MonitoringDataInfo;

import java.io.IOException;
import java.util.List;

/**
 * @author serkan
 */
public interface MonitoringDataSender {

    String getType();

    default void init() {
    }

    void send(Context context, List<MonitoringDataInfo> monitoringDataInfos) throws IOException;

}
