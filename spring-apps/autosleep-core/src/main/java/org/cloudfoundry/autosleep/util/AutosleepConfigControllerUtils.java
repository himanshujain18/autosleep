package org.cloudfoundry.autosleep.util;

import java.time.Duration;
import java.util.List;

import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApiService;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.Binding;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.AutoServiceInstanceRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.BindingRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.EnrolledOrganizationConfigRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.ui.servicebroker.service.InvalidParameterException;
import org.cloudfoundry.autosleep.ui.servicebroker.service.parameters.ParameterReader;
import org.cloudfoundry.autosleep.ui.servicebroker.service.parameters.ParameterReaderFactory;
import org.cloudfoundry.autosleep.worker.OrganizationEnroller;
import org.cloudfoundry.autosleep.worker.WorkerManagerService;
import org.cloudfoundry.autosleep.worker.scheduling.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AutosleepConfigControllerUtils {

    @Autowired
    private AutoServiceInstanceRepository autoServiceInstanceRepository;

    @Autowired
    private BindingRepository bindingRepository;

    @Autowired 
    private CloudFoundryApiService cloudFoundryApi;

    @Autowired
    private Clock clock;

    @Autowired
    private EnrolledOrganizationConfigRepository orgRepository;

    @Autowired
    private SpaceEnrollerConfigRepository spaceEnrollerConfigRepository;

    @Autowired
    private WorkerManagerService workerManager;

    public AutosleepConfigControllerResponse validateRequestBody(AutosleepConfigControllerRequest request) {

        log.info("validating Request Body");
        AutosleepConfigControllerResponse validatedParameter = 
                new AutosleepConfigControllerResponse();
        ParameterReaderFactory factory = new ParameterReaderFactory();
        if (request.getIdleDuration() != null) {
            validatedParameter.setParameter("idle-duration"); 
            ParameterReader<Duration> durationReader = factory.buildIdleDurationReader();
            try {
                durationReader.readParameter(request.getIdleDuration(), false);
                validatedParameter.setValue(request.getIdleDuration());                        
            } catch (InvalidParameterException i) {
                validatedParameter.setError(Config.ServiceInstanceParameters.IDLE_DURATION
                        + " param badly formatted (ISO-8601). Example: \"PT15M\" for 15mn");
            }  
        }          
        return validatedParameter;
    }

    public void updateOrganization(EnrolledOrganizationConfig orgInfo) {

        try {             
            workerManager.getOrganizationObjects().get(orgInfo.getOrganizationId()).callReschedule(orgInfo);
        } catch (RuntimeException re) {
            log.error("Updating organization " + orgInfo.getOrganizationId() + " failed. Error: " + re.getMessage());
        }

    }

    public void stopOrgEnrollerOnDelete(String organizationId) {

        try {             
            workerManager.getOrganizationObjects().get(organizationId).killTask();
        } catch (RuntimeException re) {
            log.error("Orgnization poller for organizationId " + organizationId 
                    + "failed. Error: " + re.getMessage());
        }
    }


    public void registerOrganization(EnrolledOrganizationConfig orgInfo) {

        try {
            OrganizationEnroller orgEnroller = OrganizationEnroller.builder()
                    .autoServiceInstanceRepository(autoServiceInstanceRepository)
                    .clock(clock)
                    .cloudFoundryApi(cloudFoundryApi)
                    .enrolledOrganizationConfig(orgInfo)
                    .organizationId(orgInfo.getOrganizationId())
                    .orgRepository(orgRepository)
                    .period((orgInfo.getIdleDuration() != null )                                  
                            ? orgInfo.getIdleDuration() : Config.DEFAULT_INACTIVITY_PERIOD)               
                    .spaceEnrollerConfigRepository(spaceEnrollerConfigRepository)                
                    .utils(this)
                    .build();

            workerManager.setOrganizationObjects(orgInfo.getOrganizationId(), orgEnroller);  
            orgEnroller.startNow();   
        } catch (RuntimeException re) {
            log.error("Registering organization " + orgInfo.getOrganizationId() 
            + " failed. Error: " + re.getMessage());

        }
    }

    public void deleteServiceInstance(String instanceId) {

        try {
            deleteServiceInstanceBinding(instanceId);
            cloudFoundryApi.deleteServiceInstance(instanceId);
            autoServiceInstanceRepository.delete(instanceId);
        } catch (CloudFoundryException ce) {         
            log.error("Service Instance " + instanceId + " cannot be deleted. Error: " + ce.getMessage());
        }
    }

    public void deleteServiceInstanceBinding(String instanceId) {

        List<Binding> serviceInstanceBindings = 
                bindingRepository.findByServiceInstanceId(instanceId);
        serviceInstanceBindings.forEach(Binding-> 
        { 
            try
            {                        
                cloudFoundryApi.deleteServiceInstanceBinding(Binding.getServiceBindingId());
            } catch(CloudFoundryException ce){ 
                log.error("ServiceBinding " + Binding.getServiceBindingId() 
                + " cannot be deleted. Error: " + ce.getMessage());
            }
        });
    }
}