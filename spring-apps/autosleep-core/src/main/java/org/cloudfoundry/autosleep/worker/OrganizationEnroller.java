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
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledSpaceConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerUtils;
import org.cloudfoundry.autosleep.worker.scheduling.AbstractPeriodicTask;
import org.cloudfoundry.autosleep.worker.scheduling.Clock;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpacesResponse;
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
    
    private EnrolledOrganizationConfig enrolledOrganizationConfig;
    
    private Duration rescheduleTime;

    @Builder
    OrganizationEnroller(Clock clock,
            Duration period,
            String organizationId,
            CloudFoundryApiService cloudFoundryApi,
            AutosleepConfigControllerUtils utils,
            SpaceEnrollerConfigRepository spaceEnrollerConfigRepository,
            EnrolledOrganizationConfig enrolledOrganizationConfig) {
        super(clock, period);
        this.organizationId = organizationId;
        this.cloudFoundryApi = cloudFoundryApi;
        this.enrolledOrganizationConfig = enrolledOrganizationConfig;
        this.rescheduleTime = period;
        this.spaceEnrollerConfigRepository = spaceEnrollerConfigRepository;
        this.utils = utils;
    }

    @Override
    protected String getTaskId() {
        return organizationId;
    }

    @Override
    public void run() {   
        System.out.println("*********** Inside RUN");
        try {            
            GetOrganizationResponse getOrgResponse = 
                    cloudFoundryApi.getOrganizationDetails(organizationId);            
            if (getOrgResponse != null) {
               // System.out.println("************Inside OrganizationEnroller.java:: OrgNot NULL"); 
                System.out.println("reschedule time:: " + this.rescheduleTime);             

             //   List<SpaceEnrollerConfig> enrolledSpaces = spaceEnrollerConfigRepository.listByOrganizationId(this.organizationId);
                //System.out.println("************Inside OrganizationEnroller.java:: Table Spaces:: "+ enrolledSpaces.size());
               // if (enrolledSpaces.size() != 0) {
             //       utils.deleteServiceInstances(enrolledSpaces);            
               // }
                //All the existing serviceInstances deleted
                System.out.println("************Inside OrganizationEnroller.java:: all serviceInstances deleted ");

                ListOrganizationSpacesResponse orgSpaceResponse = cloudFoundryApi
                        .listOrganizationSpaces(this.organizationId); //all spaces in that org from cf

                List<SpaceResource> orgSpace = orgSpaceResponse.getResources();
                List<String> cfSpaces = new ArrayList<String>();
                if (orgSpace.size() != 0) {
                    for (int i = 0; i < orgSpace.size(); i++) {
                        System.out.println("cfSpace Name is :: "+ orgSpace.get(i).getEntity().getName());
                        cfSpaces.add(orgSpace.get(i).getMetadata().getId());
                    }
                }
                System.out.println("************Inside OrganizationEnroller.java:: CFOrgSpace:: "+ orgSpace.size());
                createNewServiceInstances(cfSpaces, this.enrolledOrganizationConfig);        

            }
            System.out.println("reschedule time:: " + this.rescheduleTime); 
            //rescheduleWithDefaultPeriod();
            // tempReschedule(this.rescheduleTime);
            reschedule(this.rescheduleTime);

        } catch (CloudFoundryException ce) {
            log.error("Error is: " + ce.getMessage());
            ce.printStackTrace();
        }
        System.out.println("*********** Inside RUN END");
    }

    public void callReschedule(EnrolledOrganizationConfig ec) {
        System.out.println("*********** Inside call REschedule");
        this.enrolledOrganizationConfig = ec;
        this.rescheduleTime = ec.getIdleDuration();

        System.out.println("********** callReschedule  :: "+ this.rescheduleTime);
        start(Duration.ofSeconds(0));
        System.out.println("*********** Inside call reschedule END");

    }

    /*  public void tempReschedule(Duration d) {
        reschedule(this.rescheduleTime);
    }
     */ 


    public OrganizationEnroller getObj() {
        System.out.println("*******WorkerManager :: OBJ  "+ this);
        return this;
    }

    void createNewServiceInstances(List<String> spaceIds, EnrolledOrganizationConfig enrolledOrganizationConfig) {
        System.out.println("************Inside OrganizationEnroller:: createNewServiceInstaces");
        EnrolledSpaceConfig enrolledSpaceConfig;
        try {
            for(String item : spaceIds) {
                enrolledSpaceConfig = EnrolledSpaceConfig.builder()
                        .spaceId(item)
                        .organizationId(organizationId)
                        .idleDuration(enrolledOrganizationConfig.getIdleDuration().toString()) //Check the value
                        .build();
                cloudFoundryApi.createServiceInstance(enrolledSpaceConfig); 
            }
        } catch (CloudFoundryException ce) {
            log.error("cloudfoundry error", ce);
        }
    }

    /*
    void deleteServiceInstances(Collection<String> spaceIds) {
        System.out.println("************Inside OrganizationEnroller:: deleteServiceInstaces");
        //  utils.deleteServiceInstances(new ArrayList<String>(spaceIds));

    }

    void updateServiceInstances(Map<String,Collection<SpaceEnrollerConfig>> existingServiceInstances, List<String> spaceIds, 
            EnrolledOrganizationConfig enrolledOrganizationConfig) {

        System.out.println("************Inside OrganizationEnroller:: updateServiceInstaces");

        List<SpaceEnrollerConfig> exisitingInstanceList = new ArrayList<SpaceEnrollerConfig>();

        System.out.println("************Inside OrganizationEnroller::exisitingInstanceList  " + exisitingInstanceList);
        try {
            for(String item : spaceIds) {
                //  if(existingServiceInstances.containsKey(item)) {
               // exisitingInstanceList = (List<SpaceEnrollerConfig>) existingServiceInstances.get(item);
                utils.deleteServiceInstances((List<SpaceEnrollerConfig>) existingServiceInstances.get(item)); 
                System.out.println("************** deleted all ");
                EnrolledSpaceConfig enrolledSpaceConfig = EnrolledSpaceConfig.builder()
                        .spaceId(item) 
                        .organizationId(enrolledOrganizationConfig.getOrganizationId())
                        .idleDuration(enrolledOrganizationConfig.getIdleDuration())
                        .build();
                cloudFoundryApi.createServiceInstance(enrolledSpaceConfig);
                System.out.println("************** service instance updated ");
                //  }
            }
        } catch (CloudFoundryException ce) {
            log.error(" Service Instance cannot be updated." + ce.getMessage());
        }        
    }
     */  

}