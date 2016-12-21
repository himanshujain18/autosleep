/*
 * Autosleep
 * Copyright (C) 2016 Orange
 * Authors: Benjamin Einaudi   benjamin.einaudi@orange.com
 *          Arnaud Ruffin      arnaud.ruffin@orange.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.autosleep.worker;

import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.autosleep.access.dao.repositories.ProxyMapEntryRepository;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.config.DeployedApplicationConfig;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.ApplicationRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.BindingRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.EnrolledOrganizationConfigRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.util.ApplicationLocker;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerUtils;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApiService;
import org.cloudfoundry.autosleep.worker.scheduling.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.cloudfoundry.autosleep.access.dao.model.Binding.ResourceType.Application;

@Slf4j
@Service
public class WorkerManager implements WorkerManagerService {

    @Autowired
    private ApplicationLocker applicationLocker;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private BindingRepository bindingRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private CloudFoundryApiService cloudFoundryApi;

    @Autowired
    private DeployedApplicationConfig.Deployment deployment;

    @Autowired
    private EnrolledOrganizationConfigRepository orgRepository;

    @Autowired
    private SpaceEnrollerConfigRepository spaceEnrollerConfigRepository;

    @Autowired
    private ProxyMapEntryRepository proxyMapEntryRepository;

    @Autowired
    private AutosleepConfigControllerUtils utils;

    private Map<String,OrganizationEnroller> organizationObjects;

    @PostConstruct
    public void init() {
        System.out.println("************Inside WorkerManager.java:init*********"+Thread.currentThread().getName());
        
       // List<SpaceEnrollerConfig> enrolledSpaces = spaceEnrollerConfigRepository.listByOrganizationId("7c16c246-bef6-448e-af0d-a2cffc9c2a2a");
       // System.out.println("************Inside OrganizationEnroller.java:: Table Spaces:: "+ enrolledSpaces.size());

        log.debug("Initializer watchers for every organization already enrolled (except if handle by another" 
                + "instance of autosleep)");
        this.organizationObjects = new HashMap<String,OrganizationEnroller>();

        List<EnrolledOrganizationConfig> enrolledOrgs = orgRepository.findAll();        
        // List<String> enrolledOrgIDs = new ArrayList<String>();

        if (enrolledOrgs != null) {
            for (EnrolledOrganizationConfig item:enrolledOrgs) {
                //      enrolledOrgIDs.add(item.getOrganizationId());
                registerOrganizationEnroller(item);                   
            }            
        }


        log.debug("Initializer watchers for every app already enrolled (except if handle by another instance of "
                + "autosleep)");

        bindingRepository.findAllByResourceType(Application).forEach(applicationBinding -> {
            SpaceEnrollerConfig spaceEnrollerConfig =
                    spaceEnrollerConfigRepository.findOne(applicationBinding.getServiceInstanceId());
            if (spaceEnrollerConfig != null) {
                registerApplicationStopper(spaceEnrollerConfig,
                        applicationBinding.getResourceId(),
                        applicationBinding.getServiceBindingId());
            }
        });       

        spaceEnrollerConfigRepository.findAll().forEach(this::registerSpaceEnroller);

        organizationDeRegister(orgRepository);  //check the sequence of call
        System.out.println("********** FIND BY ORG");

    }

    @Override
    public void registerOrganizationEnroller(EnrolledOrganizationConfig orgInfo) {
        OrganizationEnroller orgEnroller = OrganizationEnroller.builder()
                .clock(clock)
                .period((orgInfo.getIdleDuration() != null )                                  
                        ? orgInfo.getIdleDuration() : Config.DEFAULT_INACTIVITY_PERIOD)
                .organizationId(orgInfo.getOrganizationId())
                .cloudFoundryApi(cloudFoundryApi)
                .enrolledOrganizationConfig(orgInfo)
                .spaceEnrollerConfigRepository(spaceEnrollerConfigRepository)
                .utils(utils)
                .build();
        setOrganizationObjects(orgInfo.getOrganizationId(), orgEnroller);
        orgEnroller.start(Config.DELAY_BEFORE_FIRST_SERVICE_CHECK); 

    }

    @Override
    public Map<String,OrganizationEnroller> getOrganizationObjects() {
        return this.organizationObjects;
    }

    @Override
    public void setOrganizationObjects(String id, OrganizationEnroller orgEnroller) { 
        this.organizationObjects.put(id, orgEnroller);
    }

    @Override
    public void registerApplicationStopper(SpaceEnrollerConfig config, String applicationId, String appBindingId) {
        Duration interval = spaceEnrollerConfigRepository.findOne(config.getId()).getIdleDuration();
        log.debug("Initializing a watch on app {}, for an idleDuration of {} ", applicationId,
                interval.toString());
        ApplicationStopper checker = ApplicationStopper.builder()
                .applicationLocker(applicationLocker)
                .applicationRepository(applicationRepository)
                .appUid(applicationId)
                .bindingId(appBindingId)
                .clock(clock)
                .cloudFoundryApi(cloudFoundryApi)
                .ignoreRouteBindingError(config.isIgnoreRouteServiceError())
                .period(interval)
                .spaceEnrollerConfigId(config.getId())
                .proxyMap(proxyMapEntryRepository)
                .build();
        checker.startNow();
    }

    @Override
    public void registerSpaceEnroller(SpaceEnrollerConfig service) {
        System.out.println("Register thread for new service Instance");

        SpaceEnroller spaceEnroller = SpaceEnroller.builder()
                .clock(clock)
                .period(service.getIdleDuration())
                .spaceEnrollerConfigId(service.getId())
                .spaceEnrollerConfigRepository(spaceEnrollerConfigRepository)
                .cloudFoundryApi(cloudFoundryApi)
                .applicationRepository(applicationRepository)
                .deployment(deployment)
                .build();
        spaceEnroller.start(Config.DELAY_BEFORE_FIRST_SERVICE_CHECK);
    }

    @Override
    public void organizationDeRegister(EnrolledOrganizationConfigRepository orgRepository) {
        OrganizationDeRegister orgDeregister = OrganizationDeRegister.builder()
                .clock(clock)
                .period(Config.DEFAULT_INACTIVITY_PERIOD)
                .orgRepository(orgRepository)                
                .spaceEnrollerConfigRepository(spaceEnrollerConfigRepository)
                .utils(utils)
                .build();

        orgDeregister.start(Config.DELAY_BEFORE_FIRST_SERVICE_CHECK);   //TODO: Decide delay time to start this thread 
    }

}
