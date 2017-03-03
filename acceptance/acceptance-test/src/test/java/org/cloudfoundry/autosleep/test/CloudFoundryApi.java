package org.cloudfoundry.autosleep.test;

import java.io.InputStream;
import java.util.Map;

import org.cloudfoundry.autosleep.config.CloudfoundryClientBuilder;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.applications.ApplicationStatisticsRequest;
import org.cloudfoundry.client.v2.applications.ApplicationStatisticsResponse;
import org.cloudfoundry.client.v2.applications.CreateApplicationRequest;
import org.cloudfoundry.client.v2.applications.CreateApplicationResponse;
import org.cloudfoundry.client.v2.applications.DeleteApplicationRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationServiceBindingsRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationServiceBindingsResponse;
import org.cloudfoundry.client.v2.applications.UpdateApplicationRequest;
import org.cloudfoundry.client.v2.applications.UploadApplicationRequest;
import org.cloudfoundry.client.v2.domains.ListDomainsRequest;
import org.cloudfoundry.client.v2.domains.ListDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.routes.AssociateRouteApplicationRequest;
import org.cloudfoundry.client.v2.routes.CreateRouteRequest;
import org.cloudfoundry.client.v2.routes.CreateRouteResponse;
import org.cloudfoundry.client.v2.routes.DeleteRouteRequest;
import org.cloudfoundry.client.v2.servicebindings.DeleteServiceBindingRequest;
import org.cloudfoundry.client.v2.servicebrokers.CreateServiceBrokerRequest;
import org.cloudfoundry.client.v2.servicebrokers.CreateServiceBrokerResponse;
import org.cloudfoundry.client.v2.servicebrokers.DeleteServiceBrokerRequest;
import org.cloudfoundry.client.v2.servicebrokers.ListServiceBrokersRequest;
import org.cloudfoundry.client.v2.servicebrokers.ListServiceBrokersResponse;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceResponse;
import org.cloudfoundry.client.v2.serviceinstances.DeleteServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstanceServiceBindingsRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstanceServiceBindingsResponse;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesResponse;
import org.cloudfoundry.client.v2.serviceplans.ListServicePlansRequest;
import org.cloudfoundry.client.v2.serviceplans.ListServicePlansResponse;
import org.cloudfoundry.client.v2.serviceplanvisibilities.CreateServicePlanVisibilityRequest;
import org.cloudfoundry.client.v2.serviceplanvisibilities.CreateServicePlanVisibilityResponse;
import org.cloudfoundry.client.v2.serviceplanvisibilities.DeleteServicePlanVisibilityRequest;
import org.cloudfoundry.client.v2.services.ListServicesRequest;
import org.cloudfoundry.client.v2.services.ListServicesResponse;
import org.cloudfoundry.client.v2.spaces.CreateSpaceRequest;
import org.cloudfoundry.client.v2.spaces.CreateSpaceResponse;
import org.cloudfoundry.client.v2.spaces.DeleteSpaceRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;

public class CloudFoundryApi {
    
    private CloudfoundryClientBuilder builder;
    private CloudFoundryClient cfclient;
    
    public CloudFoundryApi() {
        builder = new CloudfoundryClientBuilder();
        cfclient = builder.getClient();
    }

    public ListOrganizationsResponse listOrganizations() {
        return cfclient.organizations()
                .list(ListOrganizationsRequest.builder()
                      .build())
                .get();
    }
    
    public ListServicesResponse listServices(String serviceName) {
        return cfclient.services()
                .list(ListServicesRequest.builder()
                      .label(serviceName)
                      .build())
                .get();      
    }
    
    public ListServicePlansResponse listServicePlan(String serviceId) {
        return cfclient.servicePlans()
                .list(ListServicePlansRequest.builder()
                      .serviceId(serviceId)
                      .build())
                .get();
    }
    
    public ListServiceBrokersResponse listServiceBroker() {
        return cfclient.serviceBrokers()
                .list(ListServiceBrokersRequest.builder()
                      .build())
                .get();
    }
    
    public CreateServiceBrokerResponse createServiceBroker(String brokerUrl, String brokerName,
            String brokerUserName, String brokerPassword) {
        return cfclient.serviceBrokers()
                .create(CreateServiceBrokerRequest.builder()
                        .brokerUrl(brokerUrl)
                        .name(brokerName)
                        .authenticationUsername(brokerUserName)
                        .authenticationPassword(brokerPassword)
                        .build())
                .get();
    }
    
    public ListDomainsResponse listDomain(String domainName) {
        return cfclient.domains()
                .list(ListDomainsRequest.builder()
                        .name(domainName)
                        .build())
                .get();
    }
    
    public CreateServicePlanVisibilityResponse 
                createServicePlanVisibility(String servicePlanId, String organizationId) {
        return cfclient.servicePlanVisibilities()
                .create(CreateServicePlanVisibilityRequest.builder()
                        .servicePlanId(servicePlanId)
                        .organizationId(organizationId)
                        .build())
                .get();
    }
    
    public CreateSpaceResponse createSpace(String organizationId, String spaceName) {
        return cfclient.spaces()
                .create(CreateSpaceRequest.builder()
                        .organizationId(organizationId)
                        .name(spaceName)
                        .build())
                .get();
    }
    
    public CreateApplicationResponse createApplication(String applicationName, int memory,
            String spaceId, String buildpack) {
        return cfclient.applicationsV2()
                .create(CreateApplicationRequest.builder()
                        .name(applicationName)
                        .memory(memory)
                        .spaceId(spaceId)
                        .buildpack(buildpack)
                        .build())
                .get();
    }
    
    public void uploadApplication(InputStream application, String applicationId) {
        cfclient.applicationsV2()
        .upload(UploadApplicationRequest.builder()
                .application(application)
                .applicationId(applicationId)
                .build())
            .get();
    }
    
    public CreateRouteResponse createRoute(String domainId, String spaceId, String hostname) {
        return cfclient.routes()
                .create(CreateRouteRequest.builder()
                        .domainId(domainId)
                        .spaceId(spaceId)
                        .host(hostname)
                        .build())
                .get();
    }
    
    public void associateRouteApplication(String applicationId, String routeId) {
        cfclient.routes()
        .associateApplication(AssociateRouteApplicationRequest.builder()
                .applicationId(applicationId)
                .routeId(routeId)
                .build())
            .get();
    }
    
    public void updateApplicationState(String applicationId, String state) {
        cfclient.applicationsV2().update(UpdateApplicationRequest.builder()
                .applicationId(applicationId)
                .state(state)
                .build())
        .get();
    }
    
    public ApplicationStatisticsResponse applicationStatistics(String applicationId) {
        return cfclient.applicationsV2()
                .statistics(ApplicationStatisticsRequest.builder()
                            .applicationId(applicationId)
                            .build())
                .get();
    }
    
    public ListServiceInstancesResponse listServiceInstance(String organizationId, String spaceId) {
        return cfclient.serviceInstances()
                .list(ListServiceInstancesRequest.builder()
                        .organizationId(organizationId)
                        .spaceId(spaceId)
                        .build())
                .get();
    }
    
    public ListApplicationServiceBindingsResponse listApplicationServiceBindings(String applicationId) {
        return cfclient.applicationsV2()
                .listServiceBindings(ListApplicationServiceBindingsRequest.builder()
                                     .applicationId(applicationId)
                                     .build())
                .get();
    }
    
    public ListSpacesResponse listSpaces(String organizationId, String applicationId) {
        return cfclient.spaces()
                .list(ListSpacesRequest.builder()
                        .organizationId(organizationId)
                        .applicationId(applicationId)
                        .build())
                .get();
    }
    
    public ListServiceInstanceServiceBindingsResponse listServiceInstanceServiceBindings(String instanceId) {
        return cfclient.serviceInstances()
                .listServiceBindings(ListServiceInstanceServiceBindingsRequest.builder()
                        .serviceInstanceId(instanceId)
                        .build())
                .get();
    }
    
    public void deleteServicePlanVisibility(String planVisibilityGuid) {
        cfclient.servicePlanVisibilities()
        .delete(DeleteServicePlanVisibilityRequest.builder()
                .servicePlanVisibilityId(planVisibilityGuid)
                .build())
            .get();
    }
    
    public void deleteRoute(String routeId) {
        cfclient.routes().delete(DeleteRouteRequest.builder()
                .routeId(routeId)
                .build())
        .get();
    }
    
    public void deleteApplication(String applicationId) {
        cfclient.applicationsV2().delete(DeleteApplicationRequest.builder()
                .applicationId(applicationId)
                .build())
        .get();
    }
    
    public void deleteSpace(String spaceId) {
        cfclient.spaces().delete(DeleteSpaceRequest.builder()
                .spaceId(spaceId)
                .build())
        .get();
    }
    
    public void deleteServiceBroker(String serviceBrokerId) {
        cfclient.serviceBrokers().delete(DeleteServiceBrokerRequest.builder()
                .serviceBrokerId(serviceBrokerId)
                .build())
        .get();
    }
    
    public void deleteServiceBinding(String bindingId) {
        DeleteServiceBindingRequest deleteBinding = DeleteServiceBindingRequest.builder()
                .serviceBindingId(bindingId)
                .build();
        cfclient.serviceBindings().delete(deleteBinding).get();
    }
    
    public void deleteServiceInstance(String instanceId) {
        cfclient.serviceInstances().delete(DeleteServiceInstanceRequest.builder()
                .serviceInstanceId(instanceId)
                .build())
        .get();
    }
    
    public CreateServiceInstanceResponse createServiceInstance(String serviceName, String spaceId,
            String planId, Map<String, Object> requestParameters) {
        return cfclient.serviceInstances()
                .create(CreateServiceInstanceRequest.builder()
                        .name(serviceName)
                        .spaceId(spaceId)
                        .servicePlanId(planId)
                        .parameters(requestParameters)
                        .build())
                .get();
    }
}
