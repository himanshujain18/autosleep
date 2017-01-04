package org.cloudfoundry.autosleep.worker;


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
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledSpaceConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.AutoServiceInstanceRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.EnrolledOrganizationConfigRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerUtils;
import org.cloudfoundry.autosleep.worker.scheduling.AbstractPeriodicTask;
import org.cloudfoundry.autosleep.worker.scheduling.Clock;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpacesResponse;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public
class OrganizationEnroller extends AbstractPeriodicTask {   

    private final String organizationId;

    private final CloudFoundryApiService cloudFoundryApi;    

    private AutosleepConfigControllerUtils utils;

    private final SpaceEnrollerConfigRepository spaceEnrollerConfigRepository;

    private AutoServiceInstanceRepository autoServiceInstanceRepository;

    private EnrolledOrganizationConfigRepository orgRepository;

    private EnrolledOrganizationConfig enrolledOrganizationConfig;

    private Duration rescheduleTime;

    @Builder
    OrganizationEnroller(Clock clock,
            Duration period,
            String organizationId,
            CloudFoundryApiService cloudFoundryApi,
            AutosleepConfigControllerUtils utils,
            SpaceEnrollerConfigRepository spaceEnrollerConfigRepository,
            EnrolledOrganizationConfigRepository orgRepository,
            AutoServiceInstanceRepository autoServiceInstanceRepository,
            EnrolledOrganizationConfig enrolledOrganizationConfig) {
        super(clock, period);
        this.organizationId = organizationId;
        this.cloudFoundryApi = cloudFoundryApi;
        this.enrolledOrganizationConfig = enrolledOrganizationConfig;
        this.rescheduleTime = period;
        this.spaceEnrollerConfigRepository = spaceEnrollerConfigRepository;
        this.orgRepository = orgRepository;
        this.autoServiceInstanceRepository = autoServiceInstanceRepository;
        this.utils = utils;
    }

    @Override
    protected String getTaskId() {
        return organizationId;
    }

    @Override
    public void run() {

        try {            
            GetOrganizationResponse getOrgResponse = 
                    cloudFoundryApi.getOrganizationDetails(organizationId);            
            if (getOrgResponse != null) {
                enrollOrganizationSpaces();            
                System.out.println("reschedule time:: " + this.rescheduleTime); 
                reschedule(this.rescheduleTime);
            }
        } catch (org.cloudfoundry.client.v2.CloudFoundryException ce) {
            log.error("Error is: " + ce.getMessage()); 
            autoServiceInstanceRepository.deleteByOrgId(this.organizationId);
            orgRepository.delete(organizationId); //TO CHECK: this should delete the junk orgID
            killTask();            
        } 
    }

    public void enrollOrganizationSpaces() {
        try {
            List<String> cfSpaces = cfSpacesList();

            List<AutoServiceInstance> autoServiceInstances = autoServiceInstanceRepository.findByOrgId(this.organizationId);

            if (autoServiceInstances.size() != 0) { //some service Instances is already registered

                List<String> autoServiceInstanceIDs = new ArrayList<String>();

                for(AutoServiceInstance item : autoServiceInstances) {
                    autoServiceInstanceIDs.add(item.getServiceInstanceId());
                }

                Map<String,SpaceEnrollerConfig> existingServiceInstances = alreadyEnrolledSpaces(autoServiceInstanceIDs);

                Collection<String> newSpaces = new HashSet<String>();
                Collection<String> deletedSpaces = new HashSet<String>();
                newSpaces.addAll(cfSpaces);
                newSpaces.removeAll(existingServiceInstances.keySet());     
                if (newSpaces.size() != 0) {
                    createNewServiceInstances(newSpaces,enrolledOrganizationConfig);
                }

                deletedSpaces.addAll(existingServiceInstances.keySet()); //TODO: check a valid case
                deletedSpaces.removeAll(cfSpaces);
                if (deletedSpaces.size() != 0) {
                    deleteServiceInstances(deletedSpaces);
                }

                cfSpaces.retainAll(existingServiceInstances.keySet());
                if (cfSpaces.size() != 0) {
                    updateServiceInstances(existingServiceInstances, cfSpaces, enrolledOrganizationConfig);
                }
            } else {
                //create for all new spaces...first registration of Org
                createNewServiceInstances(cfSpaces,enrolledOrganizationConfig);
            }
        } catch (CloudFoundryException ce) {
            log.error("Error is: " + ce.getMessage());
        }
    }

    public  Map<String,SpaceEnrollerConfig> alreadyEnrolledSpaces(List<String> existingServiceIntanstanceIDs) throws CloudFoundryException {

        Map<String,SpaceEnrollerConfig> existingServiceInstances = 
                new HashMap<String, SpaceEnrollerConfig>();
        try {
            List<SpaceEnrollerConfig> enrolledSpaces = 
                    spaceEnrollerConfigRepository.listByIds(existingServiceIntanstanceIDs);

            //create a hashMap of spaces enrolled
            for (SpaceEnrollerConfig item : enrolledSpaces) {
                existingServiceInstances.put(item.getSpaceId(), item);              
            }
        } catch (RuntimeException re) {
            log.error("Error in retrieving already enrolled serviceInstances. Error: " + re.getMessage());
            throw new CloudFoundryException(re);
        }
        return existingServiceInstances;

    }

    public List<String> cfSpacesList() throws CloudFoundryException {

        List<String> cfSpaces = new ArrayList<String>();
        try {
            ListOrganizationSpacesResponse orgSpaceResponse = cloudFoundryApi
                    .listOrganizationSpaces(this.organizationId); //all spaces in that org from cf
            List<SpaceResource> orgSpace = orgSpaceResponse.getResources();

            for (int i = 0; i < orgSpace.size(); i++) {
                cfSpaces.add(orgSpace.get(i).getMetadata().getId());
            }
        } catch (CloudFoundryException ce) {
            log.error("SpaceIds from cloudfoundry cannot be retrieved. Error: " + ce.getMessage());
            throw ce;
        }

        return cfSpaces;
    }

    void createNewServiceInstances(Collection<String> spaceIds, EnrolledOrganizationConfig enrolledOrganizationConfig) {

        EnrolledSpaceConfig enrolledSpaceConfig;
        try {
            for(String item : spaceIds) {
                enrolledSpaceConfig = EnrolledSpaceConfig.builder()
                        .spaceId(item)
                        .organizationId(organizationId)
                        .idleDuration(enrolledOrganizationConfig.getIdleDuration()) //Check the value
                        .build();
                createNewServiceInstance(enrolledSpaceConfig); 
            }
        } catch (CloudFoundryException ce) {
            log.error("Service Instances for new spaces cannot be created. Error: ", ce.getMessage());
        }
    }

    void deleteServiceInstances(Collection<String> spaceIds) throws CloudFoundryException {

        try {
            //spaceIds.forEach(spaceId-> utils.deleteServiceInstance(spaceId));
            for (String item : spaceIds) {
                utils.deleteServiceInstance(item);
            }
        } catch (CloudFoundryException ce) {
            log.error("cloudfoundry error", ce);
            throw ce;
        }

    }

    void updateServiceInstances(Map<String,SpaceEnrollerConfig> existingServiceInstances, List<String> spaceIds, 
            EnrolledOrganizationConfig enrolledOrganizationConfig) throws CloudFoundryException {

        SpaceEnrollerConfig exisitingInstance;// = new ArrayList<SpaceEnrollerConfig>();
        try {
            for(String item : spaceIds) {
                if(existingServiceInstances.containsKey(item)) {
                    exisitingInstance = existingServiceInstances.get(item);
                    if (!(checkParameters(exisitingInstance,enrolledOrganizationConfig))) {

                        utils.deleteServiceInstance(exisitingInstance.getId());                       
                        EnrolledSpaceConfig enrolledSpaceConfig = EnrolledSpaceConfig.builder()
                                .spaceId(item)
                                .organizationId(enrolledOrganizationConfig.getOrganizationId())
                                .idleDuration(enrolledOrganizationConfig.getIdleDuration())
                                .build();
                        createNewServiceInstance(enrolledSpaceConfig);
                    }
                }
            }
        } catch (CloudFoundryException ce) {
            log.error(" Service Instances cannot be updated. Error: " + ce.getMessage());
            throw ce;
        }        
    }

    public void createNewServiceInstance(EnrolledSpaceConfig enrolledSpaceConfig) throws CloudFoundryException {

        try {
            CreateServiceInstanceResponse createServiceInstanceResponse = 
                    cloudFoundryApi.createServiceInstance(enrolledSpaceConfig);
            AutoServiceInstance autoServiceInstance = AutoServiceInstance.builder()
                    .organizationId(this.organizationId)
                    .serviceInstanceId(createServiceInstanceResponse.getMetadata().getId())
                    .spaceId(enrolledSpaceConfig.getSpaceId())
                    .build();
            autoServiceInstanceRepository.save(autoServiceInstance); 
        } catch (CloudFoundryException ce) {
            log.error(" Service Instance cannot be created. Error: " + ce.getMessage());
            throw ce;
        }
    }

    boolean checkParameters(SpaceEnrollerConfig oldInstance, EnrolledOrganizationConfig enrolledOrganizationConfig ) {

        boolean flag = false;
        if  (oldInstance.getIdleDuration().compareTo(enrolledOrganizationConfig.getIdleDuration()) == 0) {
            flag = true;
        }

        return flag;
    }

    public void callReschedule(EnrolledOrganizationConfig ec) {

        this.enrolledOrganizationConfig = ec;
        this.rescheduleTime = ec.getIdleDuration();
        start(Duration.ofSeconds(0));
    }

    public OrganizationEnroller getObj() {
        return this;
    }

}

