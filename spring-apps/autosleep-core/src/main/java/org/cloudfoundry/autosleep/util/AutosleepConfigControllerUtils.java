package org.cloudfoundry.autosleep.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApiService;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.AutoServiceInstance;
import org.cloudfoundry.autosleep.access.dao.model.Binding;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledSpaceConfig;
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
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpacesResponse;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
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
            log.error("Updating organization failed. Error: " + re);
            throw re;
        }

    }

    public void stopOrgEnrollerOnDelete(String organizationId) {

        try {             
            workerManager.getOrganizationObjects().get(organizationId).killTask();
        } catch (RuntimeException re) {
            log.error("Orgnization poller for organizationId " + organizationId 
                    + "failed. Error: " + re);
            throw re;
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
                    + " failed. Error: " + re);
            throw re;
        }
    }

    public void deleteServiceInstance(String instanceId) throws CloudFoundryException {

        try {
            deleteServiceInstanceBinding(instanceId);
            cloudFoundryApi.deleteServiceInstance(instanceId);
            autoServiceInstanceRepository.delete(instanceId);
        } catch (RuntimeException re) {         
            log.error("Service Instance " + instanceId + " cannot be deleted. Error: " + re);
            throw new CloudFoundryException(re);
        }
    }

    public void deleteServiceInstanceBinding(String instanceId) throws CloudFoundryException {

        try {
            List<Binding> serviceInstanceBindings = 
                    bindingRepository.findByServiceInstanceId(instanceId);
            serviceInstanceBindings.forEach(Binding-> 
            { 
                try {                        
                    cloudFoundryApi.deleteServiceInstanceBinding(Binding.getServiceBindingId());
                } catch (CloudFoundryException ce) { 
                    log.error("ServiceBinding " + Binding.getServiceBindingId()
                            + " cannot be deleted. Error: " + ce.getMessage());
                    throw new RuntimeException(ce);
                }
            });
        } catch (RuntimeException re) {         
            log.error("Service Instance " + instanceId + " cannot be deleted. Error: " + re);
            throw new CloudFoundryException(re);
        }
    }

    public List<String> cfSpacesList(String organizationId) throws CloudFoundryException {

        List<String> cfSpaces = new ArrayList<String>();
        try {
            ListOrganizationSpacesResponse orgSpaceResponse = cloudFoundryApi
                    .listOrganizationSpaces(organizationId);
            List<SpaceResource> orgSpace = orgSpaceResponse.getResources();
            for (int i = 0; i < orgSpace.size(); i++) {
                cfSpaces.add(orgSpace.get(i).getMetadata().getId());
            }
        } catch (CloudFoundryException ce) {
            log.error("SpaceIds from cloudfoundry cannot be retrieved for organization " 
                    + organizationId + ". Error: " + ce);
            throw ce;
        }

        return cfSpaces;
    }

    public  Map<String,SpaceEnrollerConfig> alreadyEnrolledSpaces(List<String> autoServiceInstanceIDs) 
            throws CloudFoundryException {

        Map<String,SpaceEnrollerConfig> existingServiceInstances = 
                new HashMap<String, SpaceEnrollerConfig>();
        try {
            List<SpaceEnrollerConfig> enrolledSpaces = 
                    spaceEnrollerConfigRepository.listByIds(autoServiceInstanceIDs);
            enrolledSpaces.forEach(spaceConfig-> 
                    existingServiceInstances.put(spaceConfig.getSpaceId(), spaceConfig));
            deleteInvalidAutoServiceInstance(autoServiceInstanceIDs,enrolledSpaces);            
        } catch (RuntimeException ce) {
            log.error("Error in retrieving already enrolled serviceInstances. Error: " + ce);
            throw new CloudFoundryException(ce);
        }
        return existingServiceInstances;
    }

    public void createNewServiceInstances(Collection<String> spaceIds, 
            EnrolledOrganizationConfig enrolledOrganizationConfig) throws CloudFoundryException {

        try {
            spaceIds.forEach(space-> {
                EnrolledSpaceConfig enrolledSpaceConfig = EnrolledSpaceConfig.builder()
                        .spaceId(space)
                        .organizationId(enrolledOrganizationConfig.getOrganizationId())
                        .idleDuration(enrolledOrganizationConfig.getIdleDuration())
                        .build();

                try {
                    createNewServiceInstance(enrolledSpaceConfig);
                } catch (CloudFoundryException ce) {
                    log.error("Service Instances for new spaces cannot be created. Error: ", ce);
                    throw new RuntimeException(ce);
                }
            });
        } catch (RuntimeException ce) {
            log.error("Error in retrieving already enrolled serviceInstances. Error: " + ce);
            throw new CloudFoundryException(ce);
        }
    }

    public void updateServiceInstances(Map<String,SpaceEnrollerConfig> existingServiceInstances,
            List<String> spaceIds, EnrolledOrganizationConfig enrolledOrganizationConfig) 
                    throws CloudFoundryException {

        SpaceEnrollerConfig exisitingInstance;

        for (String item : spaceIds) {
            try {
                if (existingServiceInstances.containsKey(item)) {
                    exisitingInstance = existingServiceInstances.get(item);
                    if (!(checkParameters(exisitingInstance,enrolledOrganizationConfig))) {
                        deleteServiceInstance(exisitingInstance.getId());  
                        EnrolledSpaceConfig enrolledSpaceConfig = EnrolledSpaceConfig.builder()
                                .spaceId(item)
                                .organizationId(enrolledOrganizationConfig.getOrganizationId())
                                .idleDuration(enrolledOrganizationConfig.getIdleDuration())
                                .build();
                        createNewServiceInstance(enrolledSpaceConfig);
                    }
                }
            }  catch (RuntimeException re) {
                log.error("Error in updating serviceInsace for Space: " + item + ". Error: " + re);
                throw new CloudFoundryException(re);
            }      
        }  
    }

    public void deleteServiceInstances(String organizationId) throws CloudFoundryException {
        try {

            List<AutoServiceInstance> autoServiceInstances = 
                    autoServiceInstanceRepository.findByOrganizationId(organizationId);

            if (autoServiceInstances.size() != 0) { 
                List<String> autoServiceInstanceIDs = new ArrayList<String>();
                autoServiceInstances.forEach(serviceInstance->
                        autoServiceInstanceIDs.add(serviceInstance.getServiceInstanceId()));        

                List<SpaceEnrollerConfig> enrolledSpaces = 
                        spaceEnrollerConfigRepository.listByIds(autoServiceInstanceIDs);

                //        
                enrolledSpaces.forEach(serviceInstance->  { 
                    try {    
                        deleteServiceInstance(serviceInstance.getId()); 
                    } catch (CloudFoundryException ce) {
                        log.error("ServiceInstace: " + serviceInstance.getId() + "cannot be deleted. Error: " + ce);
                        throw new RuntimeException(ce);
                    }
                });
            }
        } catch (RuntimeException re) {
            log.error("ServiceInstances cannot be deleted for organizationId: "
                   + organizationId + ". Error: "+ re);
            throw new CloudFoundryException(re);
        }
    }

    public void deleteInvalidAutoServiceInstance(List<String> autoServiceInstances, 
            List<SpaceEnrollerConfig> spaceConfigServiceInstances) throws CloudFoundryException {
        try {
            List<String> spaceServiceInstances =  new ArrayList<String>();
            spaceConfigServiceInstances.forEach(spaceConfig->
                    spaceServiceInstances.add(spaceConfig.getId()));

            //delete invalid entries from autoServiceinstance Table
            Collection<String> invalidServiceInstance = new HashSet<String>();
            invalidServiceInstance.addAll(autoServiceInstances);
            invalidServiceInstance.removeAll(spaceServiceInstances);

            invalidServiceInstance.forEach(item -> autoServiceInstanceRepository.delete(item));
        } catch (RuntimeException re) {
            log.error("Error in cleaning database. Error: " + re);
            throw new CloudFoundryException(re);
        }

    }

    public void createNewServiceInstance(EnrolledSpaceConfig enrolledSpaceConfig) throws CloudFoundryException {

        try {
            CreateServiceInstanceResponse createServiceInstanceResponse = 
                    cloudFoundryApi.createServiceInstance(enrolledSpaceConfig);
            AutoServiceInstance autoServiceInstance = AutoServiceInstance.builder()
                    .organizationId(enrolledSpaceConfig.getOrganizationId())
                    .serviceInstanceId(createServiceInstanceResponse.getMetadata().getId())
                    .spaceId(enrolledSpaceConfig.getSpaceId())
                    .build();
            autoServiceInstanceRepository.save(autoServiceInstance); 

        } catch (CloudFoundryException ce) {
            log.error(" Service Instance cannot be created for space " + enrolledSpaceConfig.getSpaceId()
                   + ". Error: " + ce);
            throw ce;
        } catch (RuntimeException re) {
            log.error(" Service Instance cannot be created for space " + enrolledSpaceConfig.getSpaceId()
                   + ". Error: " + re);
            throw new CloudFoundryException(re);
        }
    }

    public boolean checkParameters(SpaceEnrollerConfig oldInstance, 
            EnrolledOrganizationConfig enrolledOrganizationConfig ) {
        boolean flag = false;   
        if (oldInstance.getIdleDuration().compareTo(enrolledOrganizationConfig.getIdleDuration()) == 0) {         
            flag = true;
        }
        return flag;
    }
}