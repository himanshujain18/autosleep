package org.cloudfoundry.autosleep.worker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.EnrolledOrganizationConfigRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerUtils;
import org.cloudfoundry.autosleep.worker.scheduling.AbstractPeriodicTask;
import org.cloudfoundry.autosleep.worker.scheduling.Clock;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrganizationDeRegister extends AbstractPeriodicTask {

    private final SpaceEnrollerConfigRepository spaceEnrollerConfigRepository;

    //private final List<String> enrolledOrgIDs;
    
    private EnrolledOrganizationConfigRepository orgRepository;

    private AutosleepConfigControllerUtils utils;

    @Builder
    OrganizationDeRegister(Clock clock,
            Duration period,           
            SpaceEnrollerConfigRepository spaceEnrollerConfigRepository,         
            EnrolledOrganizationConfigRepository orgRepository,
            AutosleepConfigControllerUtils utils) {
        super(clock, period);
        this.spaceEnrollerConfigRepository = spaceEnrollerConfigRepository;
        this.orgRepository = orgRepository;
        this.utils = utils;
    }

    @Override
    protected String getTaskId() {
        return getClass().getName();
    }

    public void run() {
        System.out.println("************Inside OrganizationDeRegister.java:deRegisterOrganization*********"+Thread.currentThread().getName());

        try {
            List<EnrolledOrganizationConfig> enrolledOrgs = orgRepository.findAll(); 
            List<String> enrolledOrgIDs = new ArrayList<String>();
            
            if (enrolledOrgs != null) {
                for (EnrolledOrganizationConfig item:enrolledOrgs) {
                    enrolledOrgIDs.add(item.getOrganizationId());                                       
                }            
            }
            
          //  List<String> deRegisteredOrganizationServiceInstanceIDs;
            
            List<SpaceEnrollerConfig> deRegisteredOrganizationServiceInstance;
            
            if (enrolledOrgIDs.size() == 0) {
                enrolledOrgIDs.add("");
            }
            System.out.println("************Inside OrganizationDeRegister.java:SERVICEINSTANCE QUERY");
            
            deRegisteredOrganizationServiceInstance = 
                    spaceEnrollerConfigRepository.deRegisteredOrganizationServiceInstances(enrolledOrgIDs);
            
            System.out.println("************Inside OrganizationDeRegister.java :Size:: "+deRegisteredOrganizationServiceInstance.size());
            
            if (deRegisteredOrganizationServiceInstance.size() != 0) {                 
                utils.deleteServiceInstances(deRegisteredOrganizationServiceInstance);
            }
            
            //reschedule
            System.out.println("**********Inside OrganizationDeRegister.java:RESCHEDULE***************");
            rescheduleWithDefaultPeriod();
        } catch (RuntimeException re) {
            log.error("Error is:  ", re.getMessage());

        }
    }
}
