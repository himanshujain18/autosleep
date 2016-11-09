package org.cloudfoundry.autosleep.util;

import java.time.Duration;
import java.util.ArrayList;

import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.ui.servicebroker.service.InvalidParameterException;
import org.cloudfoundry.autosleep.ui.servicebroker.service.parameters.ParameterReader;
import org.cloudfoundry.autosleep.ui.servicebroker.service.parameters.ParameterReaderFactory;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AutosleepConfigControllerUtils {

    public ArrayList<AutosleepConfigControllerResponse> validateRequestBody(AutosleepConfigControllerRequest request) {

        log.info("validating Request Body");
        AutosleepConfigControllerResponse responseJson = new AutosleepConfigControllerResponse();
        ArrayList<AutosleepConfigControllerResponse> validatedRequest = 
                new ArrayList<AutosleepConfigControllerResponse>();
        ParameterReaderFactory factory = new ParameterReaderFactory();

        if (request.getIdleDuration() != null) {
            responseJson.setParameter("idle-duration"); 
            ParameterReader<Duration> durationReader = factory.buildIdleDurationReader();
            try {
                durationReader.readParameter(request.getIdleDuration(), false);
                responseJson.setValue(request.getIdleDuration());                        
            } catch (InvalidParameterException i) {
                responseJson.setError(Config.ServiceInstanceParameters.IDLE_DURATION
                        + " param badly formatted (ISO-8601). Example: \"PT15M\" for 15mn");
            }
            validatedRequest.add(responseJson);  
        }  
        return validatedRequest;
    }

}
