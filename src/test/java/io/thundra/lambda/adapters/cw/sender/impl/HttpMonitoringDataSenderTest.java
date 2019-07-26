package io.thundra.lambda.adapters.cw.sender.impl;

import io.thundra.lambda.adapters.cw.MonitoringDataInfo;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hamcrest.Matchers;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Salih Kardan
 * @version 16/07/2018.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpClientBuilder.class})
public class HttpMonitoringDataSenderTest extends BaseMonitoringDataSenderTest {

    @Spy
    private HttpClientBuilder httpClientBuilder = spy(HttpClientBuilder.class);

    private HttpMonitoringDataSender httpMonitoringDataSender = new HttpMonitoringDataSender(httpClientBuilder);

    @Mock
    private CloseableHttpClient httpClient;

    @Override
    @Before
    public void setUp() {
        super.setUp();

        PowerMockito.stub(PowerMockito.method(HttpClientBuilder.class, "create")).toReturn(httpClientBuilder);
        when(httpClientBuilder.build()).thenReturn(httpClient);
    }

    @Test(expected = IOException.class)
    public void monitoringDataShouldBeAbleToSentSuccessfully() throws IOException {
        List<MonitoringDataInfo> monitoringDataInfos = new ArrayList<>();
        String apiKey = "test-api-key";
        monitoringDataInfos.add(new MonitoringDataInfo(SAMPLE_MONITORING_DATA, apiKey));

        CloseableHttpResponse httpResponseMock = mock(CloseableHttpResponse.class);
        when(httpClient.execute(any())).thenReturn(httpResponseMock);

        StatusLine statusLineMock = mock(StatusLine.class);
        when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
        when(statusLineMock.getStatusCode()).thenReturn(400);
        when(httpResponseMock.getEntity()).thenReturn(new StringEntity("failed"));

        try {
            httpMonitoringDataSender.send(lambdaContext, monitoringDataInfos);
        } catch (Exception e) {
            List<JSONObject> monitoringDataJsonObjs = new ArrayList<>();
            monitoringDataJsonObjs.add(new JSONObject(SAMPLE_MONITORING_DATA.replace("\n", "")));
            String batchedMonitoringData = httpMonitoringDataSender.buildBatchedMonitoringData(monitoringDataJsonObjs);
            HttpPost httpPost = httpMonitoringDataSender.prepareRequest(COLLECTOR_TEST_URL, apiKey, batchedMonitoringData);
            verify(httpClient.execute(httpPost));
            Assert.assertThat("collector url is wrong", httpPost.getURI().toString(), Matchers.equalTo(COLLECTOR_TEST_URL));
            Assert.assertEquals(e.getMessage(), "Sending monitoring data failed!");
            throw e;
        }
    }

}