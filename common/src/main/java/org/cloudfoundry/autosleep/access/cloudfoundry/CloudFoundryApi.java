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

package org.cloudfoundry.autosleep.access.cloudfoundry;

import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.autosleep.access.cloudfoundry.model.ApplicationActivity;
import org.cloudfoundry.autosleep.access.cloudfoundry.model.ApplicationIdentity;
import org.cloudfoundry.autosleep.access.dao.model.ApplicationInfo;
import org.cloudfoundry.autosleep.access.dao.model.AutoServiceInstance;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.config.Config.CloudFoundryAppState;
import org.cloudfoundry.autosleep.config.Config.EnvKey;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledSpaceConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.AutoServiceInstanceRepository;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.applications.ApplicationInstanceInfo;
import org.cloudfoundry.client.v2.applications.ApplicationInstancesRequest;
import org.cloudfoundry.client.v2.applications.ApplicationInstancesResponse;
import org.cloudfoundry.client.v2.applications.GetApplicationRequest;
import org.cloudfoundry.client.v2.applications.GetApplicationResponse;
import org.cloudfoundry.client.v2.applications.ListApplicationRoutesRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationRoutesResponse;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.UpdateApplicationRequest;
import org.cloudfoundry.client.v2.domains.GetDomainRequest;
import org.cloudfoundry.client.v2.domains.GetDomainResponse;
import org.cloudfoundry.client.v2.events.EventEntity;
import org.cloudfoundry.client.v2.events.EventResource;
import org.cloudfoundry.client.v2.events.ListEventsRequest;
import org.cloudfoundry.client.v2.events.ListEventsResponse;
import org.cloudfoundry.client.v2.organizations.GetOrganizationRequest;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpacesRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpacesResponse;
import org.cloudfoundry.client.v2.routes.GetRouteRequest;
import org.cloudfoundry.client.v2.routes.GetRouteResponse;
import org.cloudfoundry.client.v2.routes.ListRouteApplicationsRequest;
import org.cloudfoundry.client.v2.routes.ListRouteApplicationsResponse;
import org.cloudfoundry.client.v2.routes.RouteEntity;
import org.cloudfoundry.client.v2.servicebindings.CreateServiceBindingRequest;
import org.cloudfoundry.client.v2.servicebindings.DeleteServiceBindingRequest;
import org.cloudfoundry.client.v2.serviceinstances.BindServiceInstanceToRouteRequest;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceResponse;
import org.cloudfoundry.client.v2.serviceinstances.DeleteServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceplans.ListServicePlansRequest;
import org.cloudfoundry.client.v2.serviceplans.ListServicePlansResponse;
import org.cloudfoundry.client.v2.serviceplans.ServicePlanResource;
import org.cloudfoundry.client.v2.services.ListServicesRequest;
import org.cloudfoundry.client.v2.services.ListServicesResponse;
import org.cloudfoundry.client.v2.services.ServiceResource;
import org.cloudfoundry.logging.LogMessage;
import org.cloudfoundry.logging.LoggingClient;
import org.cloudfoundry.logging.RecentLogsRequest;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CloudFoundryApi implements CloudFoundryApiService {

    private static class BaseSubscriber<T> implements Subscriber<T> {

        Consumer<Throwable> errorConsumer;

        CountDownLatch latch;

        Consumer<T> resultConsumer;

        public BaseSubscriber(CountDownLatch latch, Consumer<Throwable> errorConsumer, Consumer<T> resultConsumer) {
            this.latch = latch;
            this.resultConsumer = resultConsumer;
            this.errorConsumer = errorConsumer;
        }

        @Override
        public void onComplete() {
            System.out.println("******* onComplete");
            latch.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
            System.out.println("****** onError");
            if (errorConsumer != null) {
                errorConsumer.accept(throwable);
            }
            latch.countDown();
        }

        @Override
        public void onNext(T result) {
            System.out.println("***** onNext : " + result);
            if (resultConsumer != null) {
                resultConsumer.accept(result);
            }
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            System.out.println("*********** onSubscribe");
            subscription.request(Long.MAX_VALUE);
        }
    }

    static final int CF_INSTANCES_ERROR = 220_001;

    static final int CF_STAGING_NOT_FINISHED = 170_002;
    
    static final int CF_ORGANIZATION_NOT_FOUND = 30_003;

    @Autowired
    private CloudFoundryClient cfClient;

    @Autowired
    private LoggingClient logClient;
    
    @Autowired
    private Environment environment;
    
    /*@Autowired
    private AutoServiceInstanceRepository autoServiceInstanceRepository;*/

    private <T, U> void bind(List<T> objectsToBind, Function<T, Mono<U>> caller)
            throws CloudFoundryException {
        System.out.println("***** inside cfapi bind method");
        log.debug("bind - {} objects", objectsToBind.size());
        final CountDownLatch latch = new CountDownLatch(objectsToBind.size());
        final AtomicReference<Throwable> errorEncountered = new AtomicReference<>(null);
        final Subscriber<U> subscriber
                = new BaseSubscriber<>(latch, errorEncountered::set, null);

        objectsToBind.forEach(objectToBind -> caller.apply(objectToBind).subscribe(subscriber));
        waitForResult(latch, errorEncountered, null);
    }

    @Override
    public void bindApplications(String serviceInstanceId, List<ApplicationIdentity> applications) throws
            CloudFoundryException {
        System.out.println("********* inside cfapi bindApplications method");
        bind(applications,
                application -> cfClient.serviceBindings()
                        .create(
                                CreateServiceBindingRequest
                                        .builder()
                                        .applicationId(application.getGuid())
                                        .serviceInstanceId(serviceInstanceId)
                                        .build()));
    }

    public void bindRoutes(String serviceInstanceId, List<String> routeIds) throws CloudFoundryException {
        System.out.println("***** inside cfapi bindRoutes method");
        bind(routeIds,
                routeId -> cfClient.serviceInstances()
                        .bindToRoute(
                                BindServiceInstanceToRouteRequest.builder()
                                        .serviceInstanceId(serviceInstanceId)
                                        .routeId(routeId)
                                        .build()));
    }

    private ApplicationInfo.DiagnosticInfo.ApplicationEvent buildAppEvent(EventResource event) {
        if (event == null) {
            return null;
        } else {
            EventEntity cfEvent = event.getEntity();
            return ApplicationInfo.DiagnosticInfo.ApplicationEvent.builder()
                    .actee(cfEvent.getActee())
                    .actor(cfEvent.getActor())
                    .name(cfEvent.getType())
                    .timestamp(Instant.parse(cfEvent.getTimestamp()).toEpochMilli())
                    .type(cfEvent.getType())
                    .build();
        }
    }

    private ApplicationInfo.DiagnosticInfo.ApplicationLog buildAppLog(LogMessage cfLog) {
        return cfLog == null ? null : ApplicationInfo.DiagnosticInfo.ApplicationLog.builder()
                .message(cfLog.getMessage())
                .timestamp(cfLog.getTimestamp().getTime())
                .messageType(cfLog.getMessageType().toString())
                .sourceId(cfLog.getSourceId())
                .sourceName(cfLog.getSourceName())
                .build();
    }

    private boolean changeApplicationState(String applicationUuid, String targetState) throws CloudFoundryException {
        log.debug("changeApplicationState to {}", targetState);
        try {
            if (!targetState.equals(getApplicationState(applicationUuid))) {
                cfClient.applicationsV2()
                        .update(
                                UpdateApplicationRequest.builder()
                                        .applicationId(applicationUuid)
                                        .state(targetState)
                                        .build())
                        .get(Config.CF_API_TIMEOUT);
                return true;
            } else {
                log.warn("application {} already in state {}, nothing to do", applicationUuid, targetState);
                return false;
            }
        } catch (RuntimeException r) {
            throw new CloudFoundryException(r);
        }
    }

    @Override
    public ApplicationActivity getApplicationActivity(String appUid) throws CloudFoundryException {
        log.debug("getApplicationActivity -  {}", appUid);

        //We need to call for appState, lastlogs and lastEvents
        final CountDownLatch latch = new CountDownLatch(3);
        final AtomicReference<Throwable> errorEncountered = new AtomicReference<>(null);
        final AtomicReference<LogMessage> lastLogReference = new AtomicReference<>(null);
        final AtomicReference<ListEventsResponse> lastEventsReference = new AtomicReference<>(null);
        final AtomicReference<GetApplicationResponse> appReference = new AtomicReference<>(null);
        final AtomicReference<Instant> mostRecentLogInstant = new AtomicReference<>(null);

        cfClient.applicationsV2()
                .get(GetApplicationRequest.builder()
                        .applicationId(appUid)
                        .build())
                .subscribe(new BaseSubscriber<>(latch, errorEncountered::set, appReference::set));

        cfClient.events()
                .list(ListEventsRequest.builder()
                        .actee(appUid)
                        .build())
                .subscribe(new BaseSubscriber<>(latch, errorEncountered::set, lastEventsReference::set));

        logClient.recent(RecentLogsRequest.builder()
                .applicationId(appUid)
                .build())
                .subscribe(new BaseSubscriber<>(
                        latch,
                        errorEncountered::set,
                        logMessage -> {
                            //logs are not ordered, must find the most recent
                            Instant msgInstant = logMessage.getTimestamp().toInstant();
                            if (mostRecentLogInstant.get() == null || mostRecentLogInstant.get().isBefore(msgInstant)) {
                                mostRecentLogInstant.set(msgInstant);
                                lastLogReference.set(logMessage);
                            }
                        }
                ));

        return waitForResult(latch, errorEncountered,
                () -> ApplicationActivity.builder()
                        .application(ApplicationIdentity.builder()
                                .guid(appUid)
                                .name(appReference.get().getEntity().getName())
                                .build())
                        .lastEvent(
                                lastEventsReference.get().getResources().isEmpty() ? null
                                        : buildAppEvent(lastEventsReference.get().getResources().get(0)))
                        .lastLog(buildAppLog(lastLogReference.get()))
                        .state(appReference.get().getEntity().getState())
                        .build());
    }

    private Mono<ApplicationInstancesResponse> getApplicationInstances(String applicationUuid) {
        log.debug("listApplicationRoutes");
        return cfClient.applicationsV2()
                .instances(
                        ApplicationInstancesRequest.builder()
                                .applicationId(applicationUuid)
                                .build())
                .otherwise(throwable -> {
                    if (throwable instanceof org.cloudfoundry.client.v2.CloudFoundryException
                            && isNoInstanceFoundError((org.cloudfoundry.client.v2.CloudFoundryException) throwable)) {
                        return Mono.just(ApplicationInstancesResponse.builder().build());
                    } else {
                        return Mono.error(throwable);
                    }
                });
    }

    @Override
    public String getApplicationState(String applicationUuid) throws CloudFoundryException {
        log.debug("getApplicationState");
        try {
            return this.cfClient
                    .applicationsV2()
                    .get(GetApplicationRequest.builder()
                            .applicationId(applicationUuid)
                            .build())
                    .get(Config.CF_API_TIMEOUT)
                    .getEntity().getState();

        } catch (RuntimeException r) {
            throw new CloudFoundryException(r);
        }
    }

    @Override
    public String getHost(String routeId) throws CloudFoundryException {
        try {
            log.debug("getHost");
            GetRouteResponse response = cfClient.routes()
                    .get(GetRouteRequest.builder()
                            .routeId(routeId)
                            .build())
                    .get(Config.CF_API_TIMEOUT);
            RouteEntity routeEntity = response.getEntity();
            String route = routeEntity.getHost() + routeEntity.getPath();
            log.debug("route =  {}", route);

            GetDomainResponse domainResponse = cfClient.domains()
                    .get(GetDomainRequest.builder()
                            .domainId(routeEntity.getDomainId())
                            .build())
                    .get(Config.CF_API_TIMEOUT);
            log.debug("domain = {}", domainResponse.getEntity());
            return route + "." + domainResponse.getEntity().getName();
        } catch (RuntimeException r) {
            throw new CloudFoundryException(r);
        }
    }

    @Override
    public boolean isAppRunning(String appUid) throws CloudFoundryException {
        log.debug("isAppRunning");
        try {
            return !getApplicationInstances(appUid)
                    .flatMap(response -> Flux.fromIterable(response.values()))
                    .filter(instanceInfo -> "RUNNING".equals(instanceInfo.getState()))
                    .collect(ArrayList<ApplicationInstanceInfo>::new, ArrayList::add)
                    .get(Config.CF_API_TIMEOUT)
                    .isEmpty();

        } catch (RuntimeException r) {
            throw new CloudFoundryException(r);
        }
    }

    private boolean isNoInstanceFoundError(org.cloudfoundry.client.v2.CloudFoundryException cloudfoundryException) {
        return cloudfoundryException.getCode() == CF_INSTANCES_ERROR
                || cloudfoundryException.getCode() == CF_STAGING_NOT_FINISHED;
    }

    @Override
    public List<ApplicationIdentity> listAliveApplications(String spaceUuid, Pattern excludeNames) throws
            CloudFoundryException {
        log.debug("listAliveApplications from space_guid:" + spaceUuid);
        try {
            return Mono.just(spaceUuid)
                    .then(spaceId -> this.cfClient
                            .applicationsV2()
                            .list(ListApplicationsRequest.builder()
                                    .spaceId(spaceUuid)
                                    .build()))
                    .flatMap(listApplicationsResponse -> Flux.fromIterable(listApplicationsResponse.getResources()))
                    //remove all filtered applications
                    .filter(applicationResource -> excludeNames == null
                            || !excludeNames.matcher(applicationResource.getEntity().getName()).matches())
                    //get instances
                    .flatMap(applicationResource -> Mono.when(Mono.just(applicationResource),
                            getApplicationInstances(applicationResource.getMetadata().getId())))
                    //filter the one that has no instances (ie. STOPPED)
                    .filter(tuple -> !tuple.getT2().isEmpty())
                    .map(tuple -> ApplicationIdentity.builder()
                            .guid(tuple.getT1().getMetadata().getId())
                            .name(tuple.getT1().getEntity().getName())
                            .build())
                    .collect(ArrayList<ApplicationIdentity>::new, ArrayList::add)
                    .get(Config.CF_API_TIMEOUT);
        } catch (RuntimeException r) {
            throw new CloudFoundryException("failed listing applications from space_id: " + spaceUuid, r);
        }
    }

    @Override
    public List<String> listApplicationRoutes(String applicationUuid) throws CloudFoundryException {
        log.debug("listApplicationRoutes");
        try {
            ListApplicationRoutesResponse response = cfClient.applicationsV2()
                    .listRoutes(
                            ListApplicationRoutesRequest.builder()
                                    .applicationId(applicationUuid)
                                    .build())
                    .get(Config.CF_API_TIMEOUT);
            return response.getResources().stream()
                    .map(routeResource -> routeResource.getMetadata().getId())
                    .collect(Collectors.toList());
        } catch (RuntimeException r) {
            throw new CloudFoundryException(r);
        }
    }

    @Override
    public List<String> listRouteApplications(String routeUuid) throws CloudFoundryException {
        log.debug("listRouteApplications");
        try {
            ListRouteApplicationsResponse response = cfClient.routes()
                    .listApplications(
                            ListRouteApplicationsRequest.builder()
                                    .routeId(routeUuid)
                                    .build())
                    .get(Config.CF_API_TIMEOUT);
            return response.getResources().stream()
                    .map(appResource -> appResource.getMetadata().getId())
                    .collect(Collectors.toList());
        } catch (RuntimeException r) {
            throw new CloudFoundryException(r);
        }
    }

    @Override
    public boolean startApplication(String applicationUuid) throws CloudFoundryException {
        log.debug("startApplication");
        return changeApplicationState(applicationUuid, CloudFoundryAppState.STARTED);
    }

    @Override
    public boolean stopApplication(String applicationUuid) throws CloudFoundryException {
        log.debug("stopApplication");
        return changeApplicationState(applicationUuid, CloudFoundryAppState.STOPPED);
    }

    @Override
    public void unbind(String bindingId) throws CloudFoundryException {
        try {
            cfClient.serviceBindings()
                    .delete(DeleteServiceBindingRequest.builder()
                            .serviceBindingId(bindingId)
                            .build())
                    .get(Config.CF_API_TIMEOUT);
        } catch (RuntimeException r) {
            throw new CloudFoundryException(r);
        }
    }

    private <T> T waitForResult(CountDownLatch latch, AtomicReference<Throwable> errorEncountered,
                                Supplier<T> callback) throws CloudFoundryException {
        try {
            System.out.println("**** waiting for result");
            if (!latch.await(Config.CF_API_TIMEOUT.getSeconds(), TimeUnit.SECONDS)) {
                System.out.println("********** subscriber exception");
                throw new IllegalStateException("subscriber timed out");
            } else if (errorEncountered.get() != null) {
                throw new CloudFoundryException(errorEncountered.get());
            } else {
                if (callback != null) {
                    System.out.println("******** inside wait " + callback);
                    return callback.get();
                } else {
                    return null;
                }
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            return null;
        }
    }
    
    @Override
    public GetOrganizationResponse getOrganizationDetails(String organizationId) 
            throws org.cloudfoundry.client.v2.CloudFoundryException {
        GetOrganizationResponse response;
        try {
            GetOrganizationRequest request = GetOrganizationRequest.builder()
                    .organizationId(organizationId).build(); 
            response = cfClient.organizations().get(request).get();

            if (response != null) {
                log.info("OrganizationId : " + organizationId + " is valid organization");
            }
        } catch (org.cloudfoundry.client.v2.CloudFoundryException re) {
            throw re;
        }
        return response;
    }

    @Override
    public boolean isValidOrganization(String organizationGuid) 
            throws org.cloudfoundry.client.v2.CloudFoundryException {
        GetOrganizationResponse response = cfClient.organizations()
                .get(GetOrganizationRequest.builder().organizationId(organizationGuid).build())
                .otherwise(throwable -> {
                    if (throwable instanceof org.cloudfoundry.client.v2.CloudFoundryException
                            && isNoOrganizationFoundError(
                                    (org.cloudfoundry.client.v2.CloudFoundryException) throwable)) {
                        return Mono.just(GetOrganizationResponse.builder().build());
                    } else {
                        return Mono.error(throwable);
                    }
                }).get();
        return response.getEntity() != null;
    }

    private boolean isNoOrganizationFoundError(
            org.cloudfoundry.client.v2.CloudFoundryException cloudfoundryException) {
        return cloudfoundryException.getCode() == CF_ORGANIZATION_NOT_FOUND;
    }

    @Override
    public ListOrganizationSpacesResponse listOrganizationSpaces(String organizationId) throws CloudFoundryException {

        ListOrganizationSpacesResponse response;     
        try {
            ListOrganizationSpacesRequest request = 
                    ListOrganizationSpacesRequest.builder().organizationId(organizationId).build();
            response = cfClient.organizations().listSpaces(request).get();
        } catch (RuntimeException re) {            
            throw new CloudFoundryException(re);
        }
        return response;
    }

    @Override
    public CreateServiceInstanceResponse createServiceInstance(EnrolledSpaceConfig serviceInstanceInfo) 
            throws CloudFoundryException {
        CreateServiceInstanceResponse response = null;
        String instanceName = "autosleep" + System.nanoTime();
        Map<String, Object> requestParameters = new HashMap<String, Object>();
        if (serviceInstanceInfo.getIdleDuration() != null) {
            requestParameters.put("idle-duration", serviceInstanceInfo.getIdleDuration().toString());
        }
        requestParameters.put("auto-enrollment", "transitive");
        String serviceId = getServiceId();  
        if (serviceId != null ) {
            String servicePlanId = getServicePlanId(serviceId);
            try {
                CreateServiceInstanceRequest  request = CreateServiceInstanceRequest.builder()
                        .spaceId(serviceInstanceInfo.getSpaceId())
                        .name(instanceName)
                        .servicePlanId(servicePlanId)
                        .parameters(requestParameters)
                        .build();  
                response = cfClient.serviceInstances().create(request).get();           
            } catch (RuntimeException re) {
                log.error("ServiceInstance cannot be created. Error : " + re);
                throw new CloudFoundryException(re);
            } 
        } else {
            log.error("autosleep service is not available");
        }

        return response;
    }
    
    /*private <T, U> void createInstances(List<T> instancesToCreate, Function<T, Mono<U>> caller)
            throws CloudFoundryException {
        log.debug("bind - {} objects", instancesToCreate.size());
        final CountDownLatch latch = new CountDownLatch(instancesToCreate.size());
        final AtomicReference<Throwable> errorEncountered = new AtomicReference<>(null);
        final Subscriber<U> subscriber
                = new BaseSubscriber<>(latch, errorEncountered::set, null);

        instancesToCreate.forEach(instanceToCreate -> caller.apply(instanceToCreate).subscribe(subscriber));
        waitForResult(latch, errorEncountered, null);
    }*/
    
    @Override
    public CreateServiceInstanceResponse createServiceInstance(List<EnrolledSpaceConfig> enrolledSpaceConfigs) 
        throws CloudFoundryException {
        final CountDownLatch latch = new CountDownLatch(5);
        final AtomicReference<Throwable> errorEncountered = new AtomicReference<>(null);
        final AtomicReference<CreateServiceInstanceResponse> instanceResponse = new AtomicReference<>(null);
        final Subscriber<CreateServiceInstanceResponse> subscriber
                = new BaseSubscriber<>(latch, errorEncountered::set, instanceResponse::set);
        
        List<Map<String, Object>> requestParametersList = new ArrayList<>();
        
        enrolledSpaceConfigs.forEach(enrolledSpaceConfig -> {
            Map<String, Object> requestParameter = new HashMap<>();
            if (enrolledSpaceConfig.getIdleDuration() != null) {
                requestParameter.put("idle-duration", enrolledSpaceConfig.getIdleDuration().toString());
            }
            requestParameter.put("auto-enrollment", "transitive");
            requestParametersList.add(requestParameter);
        });
        
        String serviceId = getServiceId();
        
        if (serviceId != null) {
            String servicePlanId = getServicePlanId(serviceId);
            Iterator<Map<String, Object>> listIterator = requestParametersList.iterator();
            
            //enrolledSpaceConfigs.forEach(enrolledSpaceConfig -> {
                try {
                    return waitForResult(latch, errorEncountered, () -> {
                        Mono<CreateServiceInstanceResponse> response = cfClient.serviceInstances()
                            .create(CreateServiceInstanceRequest.builder()
                            .spaceId(enrolledSpaceConfigs.get(0).getSpaceId())
                            .name("autosleep" + System.nanoTime())
                            .servicePlanId(servicePlanId)
                            .parameters(listIterator.next())
                            .build());
                        response.subscribe(subscriber);
                        return response.get();});
                } catch (CloudFoundryException e) {
                    System.out.println("***** inside catch");
                    e.printStackTrace();
                }
            //});
            /*createInstances( enrolledSpaceConfigs,
                        enrolledSpaceconfig -> {
                            System.out.println("******* space is " + enrolledSpaceconfig.getSpaceId());
                            Mono<CreateServiceInstanceResponse> response = cfClient.serviceInstances()
                                .create(CreateServiceInstanceRequest.builder()
                                        .spaceId(enrolledSpaceconfig.getSpaceId())
                                        .name("autosleep" + System.nanoTime())
                                        .servicePlanId(servicePlanId)
                                        .parameters(listIterator.next())
                                        .build());
                            
                            CreateServiceInstanceResponse instResponse = response.get();
                            AutoServiceInstance autoInstance = AutoServiceInstance.builder()
                                    .organizationId(enrolledSpaceconfig.getOrganizationId())
                                    .serviceInstanceId(instResponse.getMetadata().getId())
                                    .spaceId(enrolledSpaceconfig.getSpaceId())
                                    .build();
                            autoServiceInstanceRepository.save(autoInstance);
                            return response;
                            });*/
            
        } else {
            log.error("autosleep service is not available");
            return null;
        }
        return null;
    }

    @Override
    public String getServiceId() throws CloudFoundryException {
        ListServicesResponse response;
        String serviceId = null;
        String serviceBrokerName = environment.getProperty(EnvKey.CF_SERVICE_BROKER_NAME,
                Config.ServiceCatalog.DEFAULT_SERVICE_BROKER_NAME);
        try {
            ListServicesRequest request = ListServicesRequest.builder().label(serviceBrokerName).build();         
            response = cfClient.services().list(request).get();           
            List<ServiceResource> serviceResources = response.getResources();

            if (serviceResources.size() != 0) {
                serviceId = serviceResources.get(0).getMetadata().getId();
            } 
        } catch (RuntimeException re) {     
            throw new CloudFoundryException(re);
        }
        return serviceId;
    }

    @Override
    public String getServicePlanId(String serviceId) throws CloudFoundryException {
        String servicePlanId = null;   
        try {
            if (serviceId != null) {
                ListServicePlansRequest request = ListServicePlansRequest.builder().serviceId(serviceId).build();
                ListServicePlansResponse response = cfClient.servicePlans().list(request).get();
                List<ServicePlanResource> servicePlanResource = response.getResources();           

                if (servicePlanResource.size() != 0) {                 
                    servicePlanId = servicePlanResource.get(0).getMetadata().getId();
                } else {
                    log.error("autosleep service plans are not available");
                }
            } else {
                log.error("autosleep service doesnot exists");
            }
        } catch (RuntimeException re) {           
            throw new CloudFoundryException(re);
        }
        return servicePlanId;
    }

    @Override
    public void deleteServiceInstanceBinding(String bindingId) throws CloudFoundryException {

        try {
            DeleteServiceBindingRequest request = 
                    DeleteServiceBindingRequest.builder().serviceBindingId(bindingId).build();
            cfClient.serviceBindings().delete(request).get();
        } catch (RuntimeException re) {            
            throw new CloudFoundryException(re);
        }

    }

    @Override
    public void deleteServiceInstance(String serviceInstanceId) throws CloudFoundryException {

        try {
            DeleteServiceInstanceRequest request = 
                    DeleteServiceInstanceRequest.builder().serviceInstanceId(serviceInstanceId).build();
            cfClient.serviceInstances().delete(request).get();
        } catch (RuntimeException re) {           
            throw new CloudFoundryException(re);
        }
    }

    public ListOrganizationsResponse listAllOrganizations() throws CloudFoundryException {
        ListOrganizationsResponse response;
        try {
            ListOrganizationsRequest request = ListOrganizationsRequest.builder().build();
            response = cfClient.organizations().list(request).get();
        } catch (RuntimeException re) {            
            throw new CloudFoundryException(re);
        }        
        return response;
    }    
}
