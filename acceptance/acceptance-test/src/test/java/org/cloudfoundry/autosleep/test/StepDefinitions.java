package org.cloudfoundry.autosleep.test;

import static org.junit.Assert.assertEquals;        
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.xhtmlrenderer.pdf.BookmarkElement;
import org.cloudfoundry.client.v2.CloudFoundryException;
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
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
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
import org.cloudfoundry.client.v2.servicebrokers.ServiceBrokerEntity;
import org.cloudfoundry.client.v2.servicebrokers.ServiceBrokerResource;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceResponse;
import org.cloudfoundry.client.v2.serviceinstances.DeleteServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstanceServiceBindingsRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstanceServiceBindingsResponse;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesResponse;
import org.cloudfoundry.client.v2.serviceplans.ListServicePlansRequest;
import org.cloudfoundry.client.v2.serviceplans.ListServicePlansResponse;
import org.cloudfoundry.client.v2.serviceplans.ServicePlanResource;
import org.cloudfoundry.client.v2.serviceplanvisibilities.CreateServicePlanVisibilityRequest;
import org.cloudfoundry.client.v2.serviceplanvisibilities.CreateServicePlanVisibilityResponse;
import org.cloudfoundry.client.v2.serviceplanvisibilities.DeleteServicePlanVisibilityRequest;
import org.cloudfoundry.client.v2.services.ListServicesRequest;
import org.cloudfoundry.client.v2.services.ListServicesResponse;
import org.cloudfoundry.client.v2.services.ServiceResource;
import org.cloudfoundry.client.v2.spaces.CreateSpaceRequest;
import org.cloudfoundry.client.v2.spaces.CreateSpaceResponse;
import org.cloudfoundry.client.v2.spaces.DeleteSpaceRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.cloudfoundry.autosleep.config.CloudfoundryClientBuilder;
import org.cloudfoundry.client.CloudFoundryClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class StepDefinitions {

    private static final Logger log = LoggerFactory.getLogger(StepDefinitions.class);

    private static String autosleepUrl;
    private static String domainName;
    private static String securityUsername;
    private static String securityUserPassword;
    private static String serviceBrokerName;
    private static String cfUsername;
    private static String cfUserPassword;
    private static String[] organizationNames;
    private static String[] organizationIds;
    private static String[][] spaceIds;
    private static String[][] testAppIds;
    private static String[][] routeIds;

    private static int[] status;
    private static String[] result;
    private static int instanceCount;
    private static String[] planVisibilityGuids;
    private static String serviceBrokerId;
    private static String servicePlanId;
    private static String[] serviceInstanceIds;

    private static InputStream inputStream;

    private static CloudfoundryClientBuilder builder;
    private static CloudFoundryClient cfclient;

    @Before({"@start"})
    public static void before() {

        log.info("Reading parameters from Properties file");

        builder = new CloudfoundryClientBuilder();
        cfclient = builder.getClient();

        try {
            Properties prop = new Properties();
            String fileName = "config.properties";

            inputStream = StepDefinitions.class.getClassLoader().getResourceAsStream(fileName);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file " + fileName + " not found");
            }

            autosleepUrl  = prop.getProperty("autosleepAppUrl");
            domainName = prop.getProperty("domain");
            securityUsername  = prop.getProperty("securityUsername");
            securityUserPassword  = prop.getProperty("securityUserPassword");
            cfUsername = prop.getProperty("email");
            cfUserPassword = prop.getProperty("password");
            serviceBrokerName = prop.getProperty("serviceBrokerName");
            organizationNames  = prop.getProperty("organizationName").split(",");

            if (organizationIds == null) {
                organizationIds  = new String[organizationNames.length];
            }

            if (spaceIds == null) {
                spaceIds = new String[organizationIds.length][2];
            }

            if (testAppIds == null) {
                testAppIds = new String[organizationIds.length][2];
            }

            if (routeIds == null) {
                routeIds = new String[organizationIds.length][2];
            }

            if (planVisibilityGuids == null) {
                planVisibilityGuids = new String[organizationIds.length];
            }

            if (status == null) {
                status = new int[organizationNames.length];
            }

            if (result == null) {
                result = new String[organizationNames.length];
            }
            if (serviceInstanceIds == null) {
                serviceInstanceIds = new String[organizationNames.length];
            }

            instanceCount = 0;

            ListOrganizationsRequest request = ListOrganizationsRequest.builder().build();
            ListOrganizationsResponse response = cfclient.organizations().list(request).get();

            for (int index = 0; index < organizationNames.length; index++) {
                for (OrganizationResource resource : response.getResources()) {
                    if ( organizationNames[index].compareTo(resource.getEntity().getName()) == 0) {
                        organizationIds[index] = resource.getMetadata().getId(); 
                    }
                }
            }
        } catch (RuntimeException e) {
            log.error("Runtime Exception : " + e);
        } catch (Exception e) {
            log.error("Error while reading file : " + e);
        }
    }

    public static String getServiceId() throws CloudFoundryException {
        ListServicesResponse response;
        String serviceId = null;

        try {
            ListServicesRequest request = ListServicesRequest.builder().label(getServiceName()).build();    
            response = cfclient.services().list(request).get();        
            List<ServiceResource> serviceResources = response.getResources();

            if (serviceResources.size() != 0) {
                serviceId = serviceResources.get(0).getMetadata().getId();
            } 
        } catch (RuntimeException re) {
            log.error("Invalid service name : " + re);
        }
        return serviceId;
    }

    public static String getServicePlanId(String serviceId) throws CloudFoundryException {
        String servicePlanId = null;   
        try {
            if (serviceId != null) {
                ListServicePlansRequest request = ListServicePlansRequest.builder().serviceId(serviceId).build();
                ListServicePlansResponse response = cfclient.servicePlans().list(request).get();
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
            log.error("Invalid service plan : " + re);
        }
        return servicePlanId;
    }

    public static String getServiceName() {
        log.info("Fetching catalog");

        String auth = securityUsername + ":" + securityUserPassword;

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        String serviceName = "";

        try {
            RestTemplate rest = new RestTemplate();
            HttpEntity<String> requestEntity = new HttpEntity<String>(header);
            ResponseEntity<String> response = rest.exchange(autosleepUrl + "/v2/catalog",
                    HttpMethod.GET, requestEntity, String.class);

            JSONObject jsonResponse = new JSONObject(response.getBody());
            JSONArray jsonArray = jsonResponse.getJSONArray("services");

            JSONObject element = (JSONObject) jsonArray.get(0);

            serviceName = element.getString("name");
        } catch (HttpClientErrorException e) {
            log.error("Autosleep service not available");
        } catch (RuntimeException e) {
            log.error("Error Fetching Catalog : " + e);
        }
        return serviceName;
    }
        
    @Given("^a cloud foundry landscape with autosleep application deployed on it$")
    public void given_state() {
        log.info("Autosleep application is deployed on Cloud Foundry instance");
    }

    @Before({"@registerNewOrganization"})
    public static void before_scenario_register_new_organization() {
        log.info("Before scenario execution");

        ListServiceBrokersRequest brokerRequest = ListServiceBrokersRequest.builder()
                .build();

        ListServiceBrokersResponse brokerResponse = cfclient.serviceBrokers()
                .list(brokerRequest)
                .get();

        String serviceId = "";
        if (brokerResponse.getTotalResults() == 0) {
            log.error("Service broker with name : " + serviceBrokerName + " does not exists");
            log.info("creating service broker");

            CreateServiceBrokerRequest request = CreateServiceBrokerRequest.builder()
                    .brokerUrl(autosleepUrl)
                    .name(serviceBrokerName)
                    .authenticationUsername(cfUsername)
                    .authenticationPassword(cfUserPassword)
                    .build();

            CreateServiceBrokerResponse response = cfclient.serviceBrokers()
                    .create(request)
                    .get();
            serviceBrokerId = response.getMetadata().getId();
        } else {
            List<ServiceBrokerResource> brokerResource = brokerResponse.getResources();
            for (ServiceBrokerResource serviceBrokerResource : brokerResource) {
                if (serviceBrokerResource.getEntity().getBrokerUrl().compareTo(autosleepUrl) == 0) {
                    if (serviceBrokerResource.getEntity().getName().compareTo(serviceBrokerName) != 0) {
                        throw new CloudFoundryException(400, "The service broker url is taken", "270003");
                    }
                    break;
                }
            }
        }

        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                ListDomainsResponse dom = cfclient.domains()
                        .list(ListDomainsRequest.builder()
                                .name(domainName)
                                .build())
                        .get();

                String domainId = dom.getResources().get(0).getMetadata().getId();

                serviceId = getServiceId();

                if (serviceId != null) {
                    servicePlanId = getServicePlanId(serviceId);
                    CreateServicePlanVisibilityResponse visibilityResponse = cfclient.servicePlanVisibilities()
                            .create(CreateServicePlanVisibilityRequest.builder()
                                    .servicePlanId(servicePlanId)
                                    .organizationId(organizationIds[index])
                                    .build())
                            .get();

                    planVisibilityGuids[index] = visibilityResponse.getMetadata().getId();

                    try {
                        CreateSpaceResponse spaceResponse = cfclient.spaces()
                                .create(CreateSpaceRequest.builder()
                                        .organizationId(organizationIds[index])
                                        .name("space" + System.nanoTime())
                                        .build())
                                .get();

                        spaceIds[index][0] = spaceResponse.getMetadata().getId();

                        CreateApplicationResponse appResponse = cfclient.applicationsV2()
                                .create(CreateApplicationRequest.builder()
                                        .name("test-app")
                                        .memory(256)
                                        .spaceId(spaceIds[index][0])
                                        .buildpack("nodejs_buildpack")
                                        .build())
                                .get();

                        testAppIds[index][0] = appResponse.getMetadata().getId();

                        cfclient.applicationsV2()
                        .upload(UploadApplicationRequest.builder()
                                .application(StepDefinitions.class.getClassLoader().getResourceAsStream("test-app.zip"))
                                .applicationId(testAppIds[index][0])
                                .build())
                            .get();

                        CreateRouteResponse route = cfclient.routes()
                                .create(CreateRouteRequest.builder()
                                        .domainId(domainId)
                                        .spaceId(spaceIds[index][0])
                                        .host("test-app" + System.nanoTime())
                                        .build())
                                .get();

                        routeIds[index][0] = route.getMetadata().getId();

                        cfclient.routes()
                        .associateApplication(AssociateRouteApplicationRequest.builder()
                                .applicationId(testAppIds[index][0])
                                .routeId(routeIds[index][0])
                                .build())
                            .get();

                        cfclient.applicationsV2().update(UpdateApplicationRequest.builder()
                                .applicationId(testAppIds[index][0])
                                .state("STARTED")
                                .build())
                        .get();

                        ApplicationStatisticsRequest stats = ApplicationStatisticsRequest.builder()
                                .applicationId(testAppIds[index][0])
                                .build();
                        String state = "";

                        do {
                            ApplicationStatisticsResponse statsResponse = cfclient.applicationsV2()
                                    .statistics(stats)
                                    .get();
                            state = statsResponse.get("0").getState();
                        } while (state.compareTo("DOWN") == 0);

                    } catch (CloudFoundryException ce) {
                        log.error("Error in pre scenario execution : " + ce);
                    }
                } else {
                    log.error("autosleep service is not available");
                }
            } else {
                throw new CloudFoundryException(HttpStatus.NOT_FOUND.value(),
                        "Organization : \"" + organizationNames[index] + "\" could not be found", "30003");
            }
        }
    }

    @When("^an organization is enrolled with autosleep$")
    public void an_organization_is_enrolled_with_autosleep() throws Throwable {

        log.info("Creating PUT request and making entry for an organization");

        String auth = securityUsername + ":" + securityUserPassword;

        String jsonParam = "{\"idle-duration\":\"PT6M\"}";

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        for (int index = 0; index < organizationIds.length; index++) {
            try {
                RestTemplate rest = new RestTemplate();
                HttpEntity<String> requestEntity = new HttpEntity<String>(jsonParam, header);
                rest.exchange(autosleepUrl + "/v1/enrolled-orgs/" + organizationIds[index],
                        HttpMethod.PUT, requestEntity, String.class);

            } catch (HttpClientErrorException e) {
                status[index] = 400; 
                result[index] = "Organization : \"" 
                        + organizationNames[index] + "\" could not be found";
            } catch (RuntimeException e) {
                log.error("Error completing Pre Scenario Execution : " + e);
            }
        }
    }

    @Then("^service instances are created in all spaces of the organization$")
    public static void service_instances_are_created_in_all_spaces_of_the_organization() throws Throwable {

        for (int index = 0; index < organizationIds.length; index++) {
            Thread.currentThread();
            Thread.sleep(30000);
            if (organizationIds[index] != null) {
                ListServiceInstancesResponse response = cfclient.serviceInstances()
                        .list(ListServiceInstancesRequest.builder()
                                .organizationId(organizationIds[index])
                                .spaceId(spaceIds[index][0])
                                .build())
                        .get();
                instanceCount = response.getTotalResults();

                assertEquals(1, instanceCount);
            } else {
                throw new CloudFoundryException(HttpStatus.NOT_FOUND.value(),
                        "Organization : \"" + organizationNames[index] + "\" could not be found", "30003");
            }
        }        
    }

    @Then("^applications in the spaces are bounded with the service instance$")
    public void applications_in_the_spaces_are_bounded_with_the_service_instance() throws Throwable {

        for (int index = 0; index < organizationIds.length; index++) {
            ListApplicationServiceBindingsRequest appBindRequest = ListApplicationServiceBindingsRequest.builder()
                    .applicationId(testAppIds[index][0])
                    .build();

            ListApplicationServiceBindingsResponse appBindRes = cfclient.applicationsV2()
                    .listServiceBindings(appBindRequest)
                    .get();

            String serviceBindingIdForApp = appBindRes.getResources().get(0).getMetadata().getId();

            ListSpacesResponse spaceRes = cfclient.spaces()
                    .list(ListSpacesRequest.builder()
                            .organizationId(organizationIds[index])
                            .applicationId(testAppIds[index][0])
                            .build())
                    .get();

            spaceIds[index][0] = spaceRes.getResources().get(0).getMetadata().getId();

            ListServiceInstancesResponse instanceRes = cfclient.serviceInstances()
                    .list(ListServiceInstancesRequest.builder()
                            .organizationId(organizationIds[index])
                            .spaceId(spaceIds[index][0])
                            .build())
                    .get();

            String instanceId = instanceRes.getResources().get(0).getMetadata().getId();

            ListServiceInstanceServiceBindingsResponse bindRes = cfclient.serviceInstances()
                    .listServiceBindings(ListServiceInstanceServiceBindingsRequest.builder()
                            .serviceInstanceId(instanceId)
                            .build())
                    .get();

            String serviceBindingIdForInstance = bindRes.getResources().get(0).getMetadata().getId();

            assertEquals(serviceBindingIdForApp, serviceBindingIdForInstance);
        }
    }
    
    @When("^fetching the organization enrollment details$")
    public void fetching_organization_enrollment_details() {
        Arrays.fill(status, 404);

        log.info("Executing Scenario");

        int index = 0;

        String auth = securityUsername + ":" + securityUserPassword;

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        for (index = 0; index < organizationIds.length; index++) {
            try {
                RestTemplate rest = new RestTemplate();
                HttpEntity<String> requestEntity = new HttpEntity<String>(header);
                ResponseEntity<String> response = rest.exchange(autosleepUrl + "/v1/enrolled-orgs/" 
                        + organizationIds[index], HttpMethod.GET, requestEntity, String.class);

                result[index] = response.getBody();
                status[index] = response.getStatusCode().value();

            } catch (HttpClientErrorException e) {
                status[index] = 404;
            } catch (RuntimeException e) {
                log.error("Error Fetching Organization details : " + e);
            }
        }
    }
    
    @Then("^should return the organization details as \"(.*)\" \"(.*)\" \"(.*)\"$")
    public void the_response_body_is(String arg1, String arg2, String arg3) {
        log.info("Asserting result for response body");

        for (int index = 0; index < organizationIds.length; index++) {
            String expectedResult = arg1 + "\"" + organizationIds[index] + "\"" + arg3;
            if (organizationIds[index] == null) {
                throw new CloudFoundryException(HttpStatus.NOT_FOUND.value(),
                        "Organization : \"" + organizationNames[index] + "\" could not be found", "30003");
            } else {
                assertEquals(expectedResult, result[index] );
            }
        }
    }

    @Then("^the response status code is (\\d+)$")
    public void the_status_is(int expectedStatus) {
        log.info("Asserting result for status code");

        for (int index = 0; index < organizationIds.length; index++) {
            assertEquals(expectedStatus, status[index]);
        }
    }

    @Before({"@updateAlreadyEnrolledOrganization"})
    public void before_scenario_update_enrolled_organization() {
        log.info("Before scenario register new organization");

        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                ListDomainsResponse dom = cfclient.domains()
                        .list(ListDomainsRequest.builder()
                                .name(domainName)
                                .build())
                        .get();

                String domainId = dom.getResources().get(0).getMetadata().getId();

                try {
                    CreateSpaceResponse spaceResponse = cfclient.spaces()
                            .create(CreateSpaceRequest.builder()
                                    .organizationId(organizationIds[index])
                                    .name("space" + System.nanoTime())
                                    .build())
                            .get();

                    spaceIds[index][1] = spaceResponse.getMetadata().getId();

                    CreateApplicationResponse appResponse = cfclient.applicationsV2()
                            .create(CreateApplicationRequest.builder()
                                    .name("test-app")
                                    .memory(256)
                                    .spaceId(spaceIds[index][1])
                                    .buildpack("nodejs_buildpack")
                                    .build())
                            .get();

                    testAppIds[index][1] = appResponse.getMetadata().getId();

                    cfclient.applicationsV2()
                    .upload(UploadApplicationRequest.builder()
                            .application(getClass().getClassLoader().getResourceAsStream("test-app.zip"))
                            .applicationId(testAppIds[index][1])
                            .build())
                        .get();

                    CreateRouteResponse route = cfclient.routes()
                            .create(CreateRouteRequest.builder()
                                    .domainId(domainId)
                                    .spaceId(spaceIds[index][1])
                                    .host("test-app" + System.nanoTime())
                                    .build())
                            .get();

                    routeIds[index][1] = route.getMetadata().getId();

                    cfclient.routes()
                    .associateApplication(AssociateRouteApplicationRequest.builder()
                            .applicationId(testAppIds[index][1])
                            .routeId(routeIds[index][1])
                            .build())
                        .get();

                    cfclient.applicationsV2().update(UpdateApplicationRequest.builder()
                            .applicationId(testAppIds[index][1])
                            .state("STARTED")
                            .build())
                    .get();

                    ApplicationStatisticsRequest stats = ApplicationStatisticsRequest.builder()
                            .applicationId(testAppIds[index][1])
                            .build();
                    String state = "";

                    do {
                        ApplicationStatisticsResponse statsResponse = cfclient.applicationsV2()
                                .statistics(stats)
                                .get();
                        state = statsResponse.get("0").getState();
                    } while (state.compareTo("DOWN") == 0);

                } catch (CloudFoundryException ce) {
                    log.error("Error in pre scenario execution : " + ce);
                }

            } else {
                throw new CloudFoundryException(HttpStatus.NOT_FOUND.value(),
                        "Organization : \"" + organizationNames[index] + "\" could not be found", "30003");
            }
        }
    }
    
    @Given("^an organization is already enrolled with autosleep and service instances are "
            + "running in its each space as per previous enrollment$")
    public void an_organization_is_enrolled_with_autosleep_with_service_instances_in_it() throws Throwable {
        log.info("Given an organization is already enrolled with autosleep and service instances "
                + "in transitive mode are running in its each space as per previous enrollment");
    }

    @When("^an organization enrollment is updated with autosleep$")
    public void an_organization_enrollment_is_updated_with_autosleep() throws Throwable {
        log.info("Creating PUT request and updating entry for an organization");

        String auth = securityUsername + ":" + securityUserPassword;

        String jsonParam = "{\"idle-duration\":\"PT4M\"}";

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        for (int index = 0; index < organizationIds.length; index++) {
            try {
                RestTemplate rest = new RestTemplate();
                HttpEntity<String> requestEntity = new HttpEntity<String>(jsonParam, header);
                rest.exchange(autosleepUrl + "/v1/enrolled-orgs/" + organizationIds[index],
                        HttpMethod.PUT, requestEntity, String.class);

            } catch (HttpClientErrorException e) {
                status[index] = 400; 
                result[index] = "Organization : \"" 
                        + organizationNames[index] + "\" could not be found";
            } catch (RuntimeException e) {
                log.error("Error completing Pre Scenario Execution : " + e);
            }
        }
    }
    
    @Then("^service instances are updated in all spaces of the organization as per latest enrollment$")
    public static void service_instances_are_updated_in_all_spaces_of_the_organization() throws Throwable {

        Thread.currentThread();
        Thread.sleep(30000);

        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                ListServiceInstancesResponse responseForFirstSpace = cfclient.serviceInstances()
                        .list(ListServiceInstancesRequest.builder()
                                .organizationId(organizationIds[index])
                                .spaceId(spaceIds[index][0])
                                .build())
                        .get();
                instanceCount = responseForFirstSpace.getTotalResults();

                assertEquals(1, instanceCount);

                ListServiceInstancesResponse responseForSecondSpace = cfclient.serviceInstances()
                        .list(ListServiceInstancesRequest.builder()
                                .organizationId(organizationIds[index])
                                .spaceId(spaceIds[index][1])
                                .build())
                        .get();
                instanceCount = responseForSecondSpace.getTotalResults();

                assertEquals(1, instanceCount);
            } else {
                throw new CloudFoundryException(HttpStatus.NOT_FOUND.value(),
                        "Organization : \"" + organizationNames[index] + "\" could not be found", "30003");
            }
        }
    }

    @When("^unenrolling an organization from autosleep$")
    public void delete_organization_enrollment_details() throws Throwable {
        log.info("Executing Scenario Unenrolling organization");

        int index = 0;

        String auth = securityUsername + ":" + securityUserPassword;

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        for (index = 0; index < organizationNames.length; index++) {
            try {    
                RestTemplate rest = new RestTemplate();
                HttpEntity<String> requestEntity = new HttpEntity<String>("", header);
                ResponseEntity<String> response = rest.exchange(autosleepUrl + "/v1/enrolled-orgs/" 
                        + organizationIds[index], HttpMethod.DELETE, requestEntity, String.class);

                status[index] = response.getStatusCode().value();

                Thread.currentThread();
                Thread.sleep(20000);
            } catch (HttpClientErrorException e) {
                status[index] = 404;
            } catch (RuntimeException e) {
                log.error("Error deleting organization details : " + e);
            }
        }
    }
    
    @Then("^service instances are deleted from all of its spaces$")
    public static void service_instances_are_deleted() throws Throwable {

        log.info("Check for service instances after unenrolling");

        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                ListServiceInstancesResponse responseForFirstSpace = cfclient.serviceInstances()
                        .list(ListServiceInstancesRequest.builder()
                                .organizationId(organizationIds[index])
                                .spaceId(spaceIds[index][0])
                                .build())
                        .get();
                instanceCount = responseForFirstSpace.getTotalResults();

                assertEquals(0, instanceCount);

                ListServiceInstancesResponse responseForSecondSpace = cfclient.serviceInstances()
                        .list(ListServiceInstancesRequest.builder()
                                .organizationId(organizationIds[index])
                                .spaceId(spaceIds[index][1])
                                .build())
                        .get();
                instanceCount = responseForSecondSpace.getTotalResults();

                assertEquals(0, instanceCount);
            } else {
                throw new CloudFoundryException(HttpStatus.NOT_FOUND.value(),
                        "Organization : \"" + organizationNames[index] + "\" could not be found", "30003");
            }
        }
    }
    
    @After({"@OrganizationNotFoundForUnenroll"})
    public void after_scenario_register_new_organization() {
        log.info("Executing post scenario");

        for (int index = 0; index < organizationIds.length; index++) {

            cfclient.servicePlanVisibilities()
            .delete(DeleteServicePlanVisibilityRequest.builder()
                    .servicePlanVisibilityId(planVisibilityGuids[index])
                    .build())
                .get();

            for (int i = 0; i < 2; i++) {
                cfclient.routes().delete(DeleteRouteRequest.builder()
                        .routeId(routeIds[index][i])
                        .build())
                .get();

                cfclient.applicationsV2().delete(DeleteApplicationRequest.builder()
                        .applicationId(testAppIds[index][i])
                        .build())
                .get();

                cfclient.spaces().delete(DeleteSpaceRequest.builder()
                        .spaceId(spaceIds[index][i])
                        .build())
                .get();
            }
        }

        if (serviceBrokerId != null) {
            cfclient.serviceBrokers().delete(DeleteServiceBrokerRequest.builder()
                    .serviceBrokerId(serviceBrokerId)
                    .build())
            .get();
        }
    }

    @Before({"@transientOptOut"})
    public void before_scenario_transientOtpOut() {
        before_scenario_register_new_organization();
    }

    @Given("^an autosleep service instance in transitive mode is present in a space of an organization$")
    public void an_autosleep_service_instance_in_transitive_mode_is_present_in_a_space_of_an_organization() 
            throws Throwable {

        Map<String, Object> requestParameters = new HashMap<String, Object>();
        requestParameters.put("idle-duration", "PT1M");
        requestParameters.put("auto-enrollment", "transitive");

        for (int index = 0; index < organizationIds.length; index++) {

            try {
                CreateServiceInstanceRequest request = CreateServiceInstanceRequest.builder()
                        .name("autosleep" + System.nanoTime())
                        .spaceId(spaceIds[index][0])
                        .servicePlanId(servicePlanId)
                        .parameters(requestParameters)
                        .build();
                CreateServiceInstanceResponse response = cfclient.serviceInstances()
                        .create(request)
                        .get();

                serviceInstanceIds[index] = response.getMetadata().getId();
                // Delay for binding to be done with the test application
                Thread.sleep(20000);

            } catch (CloudFoundryException ce) {
                log.error("Service instance can not be created. Error : " + ce);
                throw ce;
            }
        }
    }

    @Given("^an application is bounded with the service instance$")
    public void an_application_is_bounded_with_the_service_instance() throws Throwable {
        applications_in_the_spaces_are_bounded_with_the_service_instance();
    }

    @When("^the application is unbinded from the service instance$")
    public void the_application_is_unbinded_from_service_instance() throws Throwable {
        for (int index = 0; index < organizationIds.length; index++) {
            ListServiceInstanceServiceBindingsRequest request = ListServiceInstanceServiceBindingsRequest.builder()
                    .serviceInstanceId(serviceInstanceIds[index])
                    .build();
            ListServiceInstanceServiceBindingsResponse response = cfclient.serviceInstances()
                    .listServiceBindings(request)
                    .get();

            String binding = response.getResources().get(0).getMetadata().getId();

            DeleteServiceBindingRequest deleteBinding = DeleteServiceBindingRequest.builder()
                    .serviceBindingId(binding)
                    .build();
            cfclient.serviceBindings().delete(deleteBinding).get();

        }
    }

    @Then("^the application gets bounded with the service instance in next scan$")
    public void application_gets_bounded_with_the_service_instance_in_next_scan() throws Throwable {

        for (int index = 0; index < organizationIds.length; index++) {

            cfclient.applicationsV2().update(UpdateApplicationRequest.builder()
                    .applicationId(testAppIds[index][0])
                    .state("STARTED")
                    .build())
            .get();

            ApplicationStatisticsRequest stats = ApplicationStatisticsRequest.builder()
                    .applicationId(testAppIds[index][0])
                    .build();
            String state = "";

            do {
                ApplicationStatisticsResponse statsResponse = cfclient.applicationsV2()
                        .statistics(stats)
                        .get();
                state = statsResponse.get("0").getState();
            } while (state.compareTo("DOWN") == 0);
        }

        // wait for idle duration time
        Thread.sleep(60000);
        applications_in_the_spaces_are_bounded_with_the_service_instance();
    }

    @After({"@transientOptOut"})
    public void after_scenario_transientOptOut() {
        log.info("Executing post scenario");

        for (int index = 0; index < organizationIds.length; index++) {

            ListApplicationServiceBindingsRequest request = ListApplicationServiceBindingsRequest.builder()
                    .applicationId(testAppIds[index][0])
                    .build();
            ListApplicationServiceBindingsResponse response = cfclient.applicationsV2()
                    .listServiceBindings(request)
                    .get();
            String bindingId = response.getResources().get(0).getMetadata().getId();

            cfclient.serviceBindings().delete(DeleteServiceBindingRequest.builder()
                    .serviceBindingId(bindingId)
                    .build())
            .get();

            cfclient.serviceInstances().delete(DeleteServiceInstanceRequest.builder()
                    .serviceInstanceId(serviceInstanceIds[index])
                    .build())
            .get();

            cfclient.servicePlanVisibilities()
            .delete(DeleteServicePlanVisibilityRequest.builder()
                    .servicePlanVisibilityId(planVisibilityGuids[index])
                    .build())
                .get();

            cfclient.routes().delete(DeleteRouteRequest.builder()
                    .routeId(routeIds[index][0])
                    .build())
            .get();



            cfclient.applicationsV2().delete(DeleteApplicationRequest.builder()
                    .applicationId(testAppIds[index][0])
                    .build())
            .get();

            cfclient.spaces().delete(DeleteSpaceRequest.builder()
                    .spaceId(spaceIds[index][0])
                    .build())
            .get();
        }

        if (serviceBrokerId != null) {
            cfclient.serviceBrokers().delete(DeleteServiceBrokerRequest.builder()
                    .serviceBrokerId(serviceBrokerId)
                    .build())
            .get();
        }
    }

    @Before({"@transientmodeInstanceDeletion"})
    public static void before_transientmodeInstanceDeletion() {
        ListServiceBrokersRequest brokerRequest = ListServiceBrokersRequest.builder()
                .name(serviceBrokerName)
                .build();

        ListServiceBrokersResponse brokerResponse = cfclient.serviceBrokers()
                .list(brokerRequest)
                .get();

        String serviceId = "";
        if (brokerResponse.getTotalResults() == 0) {
            log.error("Service broker with name : " + serviceBrokerName + " does not exists");
            log.info("creating service broker");

            CreateServiceBrokerRequest request = CreateServiceBrokerRequest.builder()
                    .brokerUrl(autosleepUrl)
                    .name(serviceBrokerName)
                    .authenticationUsername(cfUsername)
                    .authenticationPassword(cfUserPassword)
                    .build();

            CreateServiceBrokerResponse response = cfclient.serviceBrokers()
                    .create(request)
                    .get();
            serviceBrokerId = response.getMetadata().getId();
        } else {
            List<ServiceBrokerResource> brokerResource = brokerResponse.getResources();
            for (ServiceBrokerResource serviceBrokerResource : brokerResource) {
                if (serviceBrokerResource.getEntity().getBrokerUrl().compareTo(autosleepUrl) == 0) {
                    if (serviceBrokerResource.getEntity().getName().compareTo(serviceBrokerName) != 0) {
                        throw new CloudFoundryException(400, "The service broker url is taken", "270003");
                    }
                    break;
                }
            }
        }

        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                serviceId = getServiceId();

                if (serviceId != null) {
                    servicePlanId = getServicePlanId(serviceId);
                    CreateServicePlanVisibilityResponse visibilityResponse = cfclient.servicePlanVisibilities()
                            .create(CreateServicePlanVisibilityRequest.builder()
                                    .servicePlanId(servicePlanId)
                                    .organizationId(organizationIds[index])
                                    .build())
                            .get();

                    planVisibilityGuids[index] = visibilityResponse.getMetadata().getId();

                    try {
                        CreateSpaceResponse spaceResponse = cfclient.spaces()
                                .create(CreateSpaceRequest.builder()
                                        .organizationId(organizationIds[index])
                                        .name("space" + System.nanoTime())
                                        .build())
                                .get();

                        spaceIds[index][0] = spaceResponse.getMetadata().getId();

                    } catch (CloudFoundryException ce) {
                        log.error("Error in pre scenario execution : " + ce);
                    }
                } else {
                    log.error("autosleep service is not available");
                }
            } else {
                throw new CloudFoundryException(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "Organization : \"" + organizationNames[index] + "\" doesn't exist in Cloud Foundry");
            }
        }
    }

    @When("^we delete the service instance$")
    public void we_delete_the_service_instance() throws Throwable {

        for (int index = 0; index < organizationIds.length; index++) {
            DeleteServiceInstanceRequest request = DeleteServiceInstanceRequest.builder()
                    .serviceInstanceId(serviceInstanceIds[index])
                    .build();
            cfclient.serviceInstances().delete(request).get();

            // Delay for delete job to complete
            Thread.sleep(5000);
        }
    }

    @Then("^the service instance gets deleted from the space within the organization$")
    public static void the_service_instance_gets_deleted_from_the_space_within_the_organization() {

        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                ListServiceInstancesResponse responseForFirstSpace = cfclient.serviceInstances()
                        .list(ListServiceInstancesRequest.builder()
                                .organizationId(organizationIds[index])
                                .spaceId(spaceIds[index][0])
                                .build())
                        .get();
                instanceCount = responseForFirstSpace.getTotalResults();

                assertEquals(0, instanceCount);

            } else {
                throw new CloudFoundryException(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "Organization : \"" + organizationNames[index] + "\" doesn't exist in Cloud Foundry");
            }
        }
    }

    @After({"@transientmodeInstanceDeletion"})
    public void after_scenario_transientmodeInstanceDeletion() {
        log.info("Executing post scenario");

        for (int index = 0; index < organizationIds.length; index++) {

            cfclient.servicePlanVisibilities()
            .delete(DeleteServicePlanVisibilityRequest.builder()
                    .servicePlanVisibilityId(planVisibilityGuids[index])
                    .build())
                .get();

            cfclient.spaces().delete(DeleteSpaceRequest.builder()
                    .spaceId(spaceIds[index][0])
                    .build())
            .get();
        }

        if (serviceBrokerId != null) {
            cfclient.serviceBrokers().delete(DeleteServiceBrokerRequest.builder()
                    .serviceBrokerId(serviceBrokerId)
                    .build())
            .get();
        }
    }
}