package org.cloudfoundry.autosleep.worker;

import java.time.Duration; 
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApiService;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.AutoServiceInstance;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.AutoServiceInstanceRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.EnrolledOrganizationConfigRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerUtils;
import org.cloudfoundry.autosleep.worker.scheduling.AbstractPeriodicTask;
import org.cloudfoundry.autosleep.worker.scheduling.Clock;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class OrganizationEnroller extends AbstractPeriodicTask {   

    private final String organizationId;

    private final CloudFoundryApiService cloudFoundryApi;    

    private AutosleepConfigControllerUtils utils;

    private AutoServiceInstanceRepository autoServiceInstanceRepository;

    private EnrolledOrganizationConfigRepository orgRepository;

    private EnrolledOrganizationConfig enrolledOrganizationConfig;

    private Duration rescheduleTime;

    private final SpaceEnrollerConfigRepository spaceEnrollerConfigRepository;

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

        if (cloudFoundryApi.isValidOrganization(organizationId)) {
            enrollOrganizationSpaces();
            reschedule(this.rescheduleTime);
        } else {
            autoServiceInstanceRepository.deleteByOrgId(this.organizationId);
            orgRepository.delete(organizationId); //TO CHECK: this should delete the junk orgID
            killTask();
        }
    }

    public void callReschedule(EnrolledOrganizationConfig ec) {

        this.enrolledOrganizationConfig = ec;
        this.rescheduleTime = ec.getIdleDuration();
        start(Duration.ofSeconds(0));
    }

    public OrganizationEnroller getObj() {
        return this;
    }

    public void enrollOrganizationSpaces() {
        try {
            List<String> cfSpaces = utils.cfSpacesList(this.organizationId);
            List<AutoServiceInstance> autoServiceInstances = 
                    autoServiceInstanceRepository.findByOrganizationId(this.organizationId);

            if (autoServiceInstances.size() != 0) {	
                List<String> autoServiceInstanceIDs = new ArrayList<String>();
                autoServiceInstances.forEach(serviceInstance->
                        autoServiceInstanceIDs.add(serviceInstance.getServiceInstanceId()));

                Map<String,SpaceEnrollerConfig> existingServiceInstances = 
                        utils.alreadyEnrolledSpaces(autoServiceInstanceIDs);

                Collection<String> newSpaces = new HashSet<String>();                
                newSpaces.addAll(cfSpaces);
                newSpaces.removeAll(existingServiceInstances.keySet());
                if (newSpaces.size() != 0) {
                    utils.createNewServiceInstances(newSpaces,enrolledOrganizationConfig);
                }
                Collection<String> deletedSpaces = new HashSet<String>();
                deletedSpaces.addAll(existingServiceInstances.keySet()); 
                deletedSpaces.removeAll(cfSpaces);
                if (deletedSpaces.size() != 0) {  
                    deletedSpaces.forEach(spaceId-> {
                        try {
                            utils.deleteServiceInstance(spaceId);
                        } catch (CloudFoundryException ce) {
                            log.error("ServiceInstance cannot be deleted. Error: " + ce);
                            throw new RuntimeException(ce);
                        }
                    });
                }
                cfSpaces.retainAll(existingServiceInstances.keySet());
                if (cfSpaces.size() != 0) {
                    utils.updateServiceInstances(existingServiceInstances, cfSpaces, enrolledOrganizationConfig);
                }
            } else {
                utils.createNewServiceInstances(cfSpaces,enrolledOrganizationConfig);
            }
        } catch (CloudFoundryException ce) {
            log.error("Organization: " + this.organizationId + " cannot be enrolled/updated. Error :" + ce);
        } catch (RuntimeException re) {
            log.error("ServiceInstance cannot be deleted. Error: " + re);
        }
    }
}

