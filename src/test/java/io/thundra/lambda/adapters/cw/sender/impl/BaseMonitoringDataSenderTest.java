package io.thundra.lambda.adapters.cw.sender.impl;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.util.IOUtils;
import io.thundra.lambda.adapters.cw.Config;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;

import static org.mockito.Mockito.when;

/**
 * @author Salih Kardan
 * @version 17/07/2018.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Config.class})
@PowerMockIgnore({"javax.net.ssl.*","javax.security.*","javax.management.*"})
public abstract class BaseMonitoringDataSenderTest {

    protected static final String COLLECTOR_TEST_URL = "COLLECTOR_TEST_URL";
    protected static final String SAMPLE_MONITORING_DATA;

    static {
        try {
            SAMPLE_MONITORING_DATA = IOUtils.toString(
                    BaseMonitoringDataSenderTest.class.getResourceAsStream("/sample_monitoring_data.json"));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Mock
    protected Context lambdaContext;

    @Mock
    protected LambdaLogger lambdaLogger;

    @Before
    public void setUp() {
        when(lambdaContext.getLogger()).thenReturn(lambdaLogger);
    }

}
