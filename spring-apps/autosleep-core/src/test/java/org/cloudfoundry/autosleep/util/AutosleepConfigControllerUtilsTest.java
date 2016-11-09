package org.cloudfoundry.autosleep.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;

import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.config.Config;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class AutosleepConfigControllerUtilsTest {  

    @Mock
    private AutosleepConfigControllerRequest fakeRequest;

    @Mock
    AutosleepConfigControllerUtils utils;

    @Mock
    private EnrolledOrganizationConfig orgInfo; 

    @Before
    public void setUp() {
        utils = mock(AutosleepConfigControllerUtils.class);       
    }

    @Test
    public void test_validateRequestBody_inValid() throws CloudFoundryException {    

        fakeRequest = new AutosleepConfigControllerRequest();        
        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();        

        fakeRequest.setIdleDuration("fake time");
        fakeResponseJson.setParameter("idle-duration");
        fakeResponseJson.setError(Config.ServiceInstanceParameters.IDLE_DURATION
                + " param badly formatted (ISO-8601). Example: \"PT15M\" for 15mn");
        ArrayList<AutosleepConfigControllerResponse> fakeValidatedResponse = 
                new ArrayList<AutosleepConfigControllerResponse>();
        fakeValidatedResponse.add(fakeResponseJson);    

        when(utils.validateRequestBody(fakeRequest)).thenReturn(fakeValidatedResponse);
        assertEquals("validateRequest",utils.validateRequestBody(fakeRequest), fakeValidatedResponse);

    }

    @Test
    public void test_validateRequestBody_valid() throws CloudFoundryException  {

        fakeRequest = new AutosleepConfigControllerRequest();
        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();        

        fakeRequest.setIdleDuration("PT1M");
        fakeResponseJson.setParameter("idle-duration");
        fakeResponseJson.setValue(fakeRequest.getIdleDuration());
        ArrayList<AutosleepConfigControllerResponse> fakeValidatedResponse = 
                new ArrayList<AutosleepConfigControllerResponse>();
        fakeValidatedResponse.add(fakeResponseJson);

        when(utils.validateRequestBody(fakeRequest)).thenReturn(fakeValidatedResponse);
        assertEquals("validateRequest",utils.validateRequestBody(fakeRequest), fakeValidatedResponse);

    }

}
