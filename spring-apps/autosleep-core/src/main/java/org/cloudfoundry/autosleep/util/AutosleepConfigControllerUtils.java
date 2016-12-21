package org.cloudfoundry.autosleep.util;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApiService;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.Binding;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.BindingRepository;
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
    private CloudFoundryApiService cloudFoundryApi;

    @Autowired
    private BindingRepository bindingRepository;

    @Autowired
    private Clock clock;

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

    /*
    public void deleteServiceInstances(List<String> serviceInstanceList) {
        try {

            List<String> deRegisteredOrganizationBindingIDs = 
                    bindingRepository.findByServiceInstanceIds(serviceInstanceList);

            //Delete Binding, if not null
            if (deRegisteredOrganizationBindingIDs.size() != 0) {
                for (String item : deRegisteredOrganizationBindingIDs) {
                    cloudFoundryApi.deleteServiceInstanceBinding(item);
                }
            }
            //Delete service Instance if not null
            if (serviceInstanceList.size() != 0 ) {
                for (String item : serviceInstanceList) {
                    cloudFoundryApi.deleteServiceInstance(item);
                }
            }
        } catch (CloudFoundryException ce) {
            log.error(" Error is : ", ce.getMessage());
        }

    }
*/
    public void updateOrganization(EnrolledOrganizationConfig orgInfo) {
        System.out.println("*************Utils :: updateOrganization *****");
        OrganizationEnroller orgEnroller ;
        Map<String,OrganizationEnroller> organizationObjects = workerManager.getOrganizationObjects();

        try {             
            orgEnroller =  organizationObjects.get(orgInfo.getOrganizationId());                
            orgEnroller.callReschedule(orgInfo);
        } catch (RuntimeException re) {
            log.error("Error is : " + re.getMessage());
        }

    }


    public void registerOrganization(EnrolledOrganizationConfig orgInfo) {
        OrganizationEnroller orgEnroller ;  
        System.out.println("*************Utils :: registerOrganization *****" + orgInfo.getOrganizationId());
        try {
            orgEnroller = OrganizationEnroller.builder()
                    .clock(clock)
                    .period((orgInfo.getIdleDuration() != null )                                  
                            ? orgInfo.getIdleDuration() : Config.DEFAULT_INACTIVITY_PERIOD)
                    .organizationId(orgInfo.getOrganizationId())
                    .cloudFoundryApi(cloudFoundryApi)
                    .enrolledOrganizationConfig(orgInfo)                
                    .build();
            
            workerManager.setOrganizationObjects(orgInfo.getOrganizationId(), orgEnroller);
  
            orgEnroller.startNow();   
            System.out.println("*************Utils :: after start ");

        } catch (RuntimeException re) {
            log.error("Error is : " + re.getMessage());
            re.printStackTrace();       
            }

    }
    
    public void deleteServiceInstances(List<SpaceEnrollerConfig> serviceInstances) {
        System.out.println("*************Utils :: deleteServiceInstance *****");
        for (SpaceEnrollerConfig serviceInstance : serviceInstances) {                     
            try {
                deleteServiceInstance(serviceInstance.getId());
                System.out.println("*************Utils :: after deleteServiceInstance *****");
            } catch (CloudFoundryException ce) {
                log.error(" Service Instance " + serviceInstance + " cannot be deleted " + ce.getMessage());
            }
        }


    }

    public void deleteServiceInstance(String instanceId) throws CloudFoundryException {
        System.out.println("*************Utils :: deleteServiceInstance *****");
        try {
            List<Binding> serviceInstanceBindings = 
                    bindingRepository.findByServiceInstanceId(instanceId);

            //Delete Binding, if not null
            if (serviceInstanceBindings.size() != 0) {
                for (Binding item : serviceInstanceBindings) {
                    cloudFoundryApi.deleteServiceInstanceBinding(item.getServiceBindingId());
                }
            }
            //delete serviceInstance
            cloudFoundryApi.deleteServiceInstance(instanceId);
        } catch (CloudFoundryException ce) {
            System.out.println("delete BINDING OR SERVICE INSTANCE FAILED");
            throw new CloudFoundryException(ce);
        }
    }
}
