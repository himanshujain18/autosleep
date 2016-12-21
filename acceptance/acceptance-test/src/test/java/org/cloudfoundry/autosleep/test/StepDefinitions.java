package org.cloudfoundry.autosleep.test;

import static org.junit.Assert.assertEquals; 
import static org.junit.Assert.assertNotEquals;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import org.cloudfoundry.client.v2.CloudFoundryException;
import org.cloudfoundry.client.v2.applications.ApplicationStatisticsRequest;
import org.cloudfoundry.client.v2.applications.ApplicationStatisticsResponse;
import org.cloudfoundry.client.v2.applications.CreateApplicationRequest;
import org.cloudfoundry.client.v2.applications.CreateApplicationResponse;
import org.cloudfoundry.client.v2.applications.DeleteApplicationRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsRequest;
import org.cloudfoundry.client.v2.applications.ListApplicationsResponse;
import org.cloudfoundry.client.v2.applications.UpdateApplicationRequest;
import org.cloudfoundry.client.v2.applications.UploadApplicationRequest;
import org.cloudfoundry.client.v2.domains.ListDomainsRequest;
import org.cloudfoundry.client.v2.domains.ListDomainsResponse;
import org.cloudfoundry.client.v2.organizations.CreateOrganizationRequest;
import org.cloudfoundry.client.v2.organizations.CreateOrganizationResponse;
import org.cloudfoundry.client.v2.organizations.DeleteOrganizationRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.routes.AssociateRouteApplicationRequest;
import org.cloudfoundry.client.v2.routes.CreateRouteRequest;
import org.cloudfoundry.client.v2.routes.CreateRouteResponse;
import org.cloudfoundry.client.v2.routes.DeleteRouteRequest;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceResponse;
import org.cloudfoundry.client.v2.serviceinstances.DeleteServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstanceServiceBindingsRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstanceServiceBindingsResponse;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesRequest;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesResponse;
import org.cloudfoundry.client.v2.serviceinstances.ServiceInstanceResource;
import org.cloudfoundry.client.v2.serviceinstances.UpdateServiceInstanceRequest;
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
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.cloudfoundry.autosleep.config.CloudfoundryClientBuilder;
import org.cloudfoundry.client.CloudFoundryClient;


import org.python.core.PyString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class StepDefinitions {

    private static final Logger log = LoggerFactory.getLogger(StepDefinitions.class);

    private String autosleepName;
    private String autosleepUrl;
    private String securityUsername;
    private String securityUserPassword;
    private String serviceName;
    private String[] organizationName;
    private String[] organizationId;
    private String tempOrgId;
    private String testAppId;
    private String routeId;

    private int[] status;
    private String[] result;
    private String jsonRequest;
    private int instanceCount;
    private String planVisibilityGuid;

    private InputStream inputStream;

    private CloudfoundryClientBuilder builder;
    private CloudFoundryClient cfclient;

    @Before({"@start"})
    public void before() {

        log.info("Reading parameters from Properties file");

        builder = new CloudfoundryClientBuilder();
        cfclient = builder.getClient();

        try {
            Properties prop = new Properties();
            String fileName = "config.properties";

            inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file " + fileName + " not found");
            }

            autosleepName = prop.getProperty("autosleepAppName");
            autosleepUrl  = prop.getProperty("autosleepAppUrl");
            securityUsername  = prop.getProperty("securityUsername");
            securityUserPassword  = prop.getProperty("securityUserPassword");
            serviceName = prop.getProperty("serviceName");
            organizationName  = prop.getProperty("organizationName").split(",");

            organizationId  = new String[organizationName.length];
            status = new int[organizationName.length];
            result = new String[organizationName.length];
            instanceCount = 0;

            ListOrganizationsRequest request = ListOrganizationsRequest.builder().build();
            ListOrganizationsResponse response = cfclient.organizations().list(request).get();

            for (int index = 0; index < organizationName.length; index++) {
                for (OrganizationResource resource : response.getResources()) {
                    if ( organizationName[index].compareTo(resource.getEntity().getName()) == 0) {
                        organizationId[index] = resource.getMetadata().getId(); 
                    }
                }
            }

        } catch (RuntimeException e) {
            log.error("Runtime Exception : " + e);
        } catch (Exception e) {
            log.error("Error while reading file : " + e);
        }
    }

    @Before({"@ScenarioOrganizationFound"})
    public void before_scenario_organization_found() {

        log.info("Starting Pre Scenario Execution");
        log.info("Creating PUT request and making entry for an org");

        String auth = securityUsername + ":" + securityUserPassword;

        String fileName = "RequestBodyParameters.json";
        inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        Scanner sc = new Scanner(inputStream, StandardCharsets.UTF_8.toString());

        String jsonParam = sc.next();
        sc.close();

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        for (int index = 0; index < organizationName.length; index++) {
            try {
                RestTemplate rest = new RestTemplate();
                HttpEntity<String> requestEntity = new HttpEntity<String>(jsonParam, header);
                rest.exchange(autosleepUrl + "/v1/enrolled-orgs/" + organizationId[index],
                        HttpMethod.PUT, requestEntity, String.class);

            } catch (HttpClientErrorException e) {
                this.status[index] = 400; 
                this.result[index] = "Organization : \"" 
                        + organizationName[index] + "\" doesn't exist in Cloud Foundry";
            } catch (Exception e) {
                log.error("Error completing Pre Scenario Execution : " + e);
            }
        }
    }

    @Before({"@ScenarioUpdateOrganizationWithEmptyBody"})
    public void before_scenario_update_organization_with_empty_body() {
        before_scenario_organization_found();
    }

    @Before({"@ScenarioUpdateOrganizationWithBody_IdleDuration"})
    public void before_scenario_update_organization_with_body_idle_duration() {
        before_scenario_organization_found();
    }

    @Before({"@ScenarioUpdateOrganizationWithBody_IdleDuration_Failure"})
    public void before_scenario_update_organization_with_body_idle_duration_failure() {
        before_scenario_organization_found();
    }

    @Before({"@ScenarioOrganizationFoundForUnenroll"})
    public void before_scenario_organization_found_for_unenroll() {
        before_scenario_organization_found();
    }

    @Given("^a cloud foundry instance with autosleep application deployed on it$")
    public void given_state() {
        log.info("Autosleep application is deployed on Cloud Foundry instance");
    }

    @Given("^request body as$")
    public void request_body_as(PyString arg) {
        this.jsonRequest = arg.asString();
    }

    @Given("^an organization with id \"([^\"]*)\"$")
    public void an_organization_with_id(String organizationId) throws Throwable {
        for (int index = 0; index < organizationName.length; index++) {
            this.organizationId[index] = organizationId;
        }
    }

    @When("^fetching the organizations enrollment details$")
    public void fetching_organizations_enrollment_details() {
        Arrays.fill(status, 404);

        log.info("Executing Scenario");

        int index = 0;

        String auth = securityUsername + ":" + securityUserPassword;

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        for (index = 0; index < organizationId.length; index++) {
            try {
                RestTemplate rest = new RestTemplate();
                HttpEntity<String> requestEntity = new HttpEntity<String>(header);
                ResponseEntity<String> response = rest.exchange(autosleepUrl + "/v1/enrolled-orgs/" 
                        + organizationId[index], HttpMethod.GET, requestEntity, String.class);

                this.result[index] = response.getBody();
                this.status[index] = response.getStatusCode().value();

            } catch (HttpClientErrorException e) {
                this.status[index] = 404;
            } catch (Exception e) {
                log.error("Error Fetching Organization details : " + e);
            }
        }
    }

    @When("^enrolling organization with autosleep$")
    public void enrolling_organization_with_autosleep() {

        log.info("Executing Scenario for Organization Enrollment");

        int index = 0;

        String auth = securityUsername + ":" + securityUserPassword;

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        for (index = 0; index < organizationName.length; index++) {

            try {
                RestTemplate rest = new RestTemplate();
                HttpEntity<String> requestEntity = new HttpEntity<String>(jsonRequest, header);
                ResponseEntity<String> response = rest.exchange(autosleepUrl + "/v1/enrolled-orgs/" 
                        + organizationId[index], HttpMethod.PUT, requestEntity, String.class);

                this.result[index] = response.getBody();
                this.status[index] = response.getStatusCode().value();

            } catch (HttpClientErrorException e) {
                if (organizationId[index] == null) {
                    this.result[index] = "[{\"parameter\":\"null\",\"value\":null,\"error\":\"CF-OrganizationNotFound"
                            + "(30003): The organization could not be found: null\"}]";
                } else if (organizationId[index].compareTo("fakeId") == 0) {
                    this.result[index] = "[{\"parameter\":\"fakeId\",\"value\":null,\"error\":\"CF-OrganizationNotFound"
                            + "(30003): The organization could not be found: fakeId\"}]";
                } else {
                    this.result[index] = "[{\"parameter\":\"idle-duration\",\"value\":null,\"error\":\"idle-duration "
                            + "param badly formatted (ISO-8601). Example: \\\"PT15M\\\" for 15mn\"}]";
                }
                this.status[index] = 400;
            } catch (Exception e) {
                log.error("Error enrolling organization : " + e);
            }
        }
    }

    @When("^deleting the organizations enrollment details$")
    public void delete_organizations_enrollment_details() {
        log.info("Executing Scenario Delete org");

        int index = 0;

        String auth = securityUsername + ":" + securityUserPassword;

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        for (index = 0; index < organizationName.length; index++) {
            try {    
                RestTemplate rest = new RestTemplate();
                HttpEntity<String> requestEntity = new HttpEntity<String>("", header);
                ResponseEntity<String> response = rest.exchange(autosleepUrl + "/v1/enrolled-orgs/" 
                        + organizationId[index], HttpMethod.DELETE, requestEntity, String.class);

                this.status[index] = response.getStatusCode().value();

            } catch (HttpClientErrorException e) {
                this.status[index] = 404;
            } catch (Exception e) {
                log.error("Error deleting organization details : " + e);
            }
        }
    }

    @Then("^should return the organizations details as \"(.*)\" \"(.*)\" \"(.*)\"$")
    public void the_response_body_is(String arg1, String arg2, String arg3) {
        log.info("Asserting result for response body");

        for (int index = 0; index < organizationId.length; index++) {
            String expectedResult = arg1 + "\"" + this.organizationId[index] + "\"" + arg3;
            if (organizationId[index] == null) {
                throw new CloudFoundryException(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "Organization : \"" + organizationName[index] + "\" doesn't exist in Cloud Foundry");
            } else {
                assertEquals(expectedResult, this.result[index] );
            }
        }
    }

    @Then("^should return the organizations details as$")
    public void response_body_is(PyString result) {
        log.info("Asserting result for response body");

        for (int index = 0; index < organizationName.length; index++) {
            if (organizationId[index] == null) {
                throw new CloudFoundryException(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "Organization : \"" + organizationName[index] + "\" doesn't exist in Cloud Foundry");
            } else {
                assertEquals(result.asString(), this.result[index] );
            }
        }
    }

    @Then("^the response status code is (\\d+)$")
    public void the_status_is(int status) {
        log.info("Asserting result for status code");

        for (int index = 0; index < organizationId.length; index++) {
            assertEquals(status, this.status[index]);
        }
    }

    @After({"@ScenarioOrganizationFound"})
    public void after_scenario_organization_found() {

        log.info("Executing post scenario");

        String auth = securityUsername + ":" + securityUserPassword;

        HttpHeaders header = new HttpHeaders();
        header.add("Authorization", "Basic " + java.util.Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        header.add("Content-Type", "application/json");

        for (int index = 0; index < organizationName.length; index++) {
            try {
                RestTemplate rest = new RestTemplate();
                HttpEntity<String> requestEntity = new HttpEntity<String>(header);
                rest.exchange(autosleepUrl + "/v1/enrolled-orgs/" 
                        + organizationId[index], HttpMethod.DELETE, requestEntity, String.class);

            } catch (HttpClientErrorException e) {
                log.error("Post scenario execution completed");
            } catch (Exception e) {
                log.error("Error completing Post Scenario execution : " + e);
            }
        }
    }

    @After({"@ScenarioUpdateOrganizationWithEmptyBody"})
    public void after_scenario_update_organization_with_empty_body() {
        after_scenario_organization_found();
    }

    @After({"@ScenarioEnrollOrganizationWithEmptyBody"})
    public void after_scenario_enroll_organization_with_empty_body() {
        after_scenario_organization_found();
    }

    @After({"@ScenarioUpdateOrganizationWithBody_IdleDuration"})
    public void after_scenario_update_organization_with_body_idle_duration() {
        after_scenario_organization_found();
    }

    @After({"@ScenarioUpdateOrganizationWithBody_IdleDuration_Failure"})
    public void after_scenario_update_organization_with_body_idle_duration_failure() {
        after_scenario_organization_found();
    }

    @After({"@ScenarioEnrollOrganizationWithBody_IdleDuration"})
    public void after_scenario_enroll_organization_with_body_idle_duration() {
        after_scenario_organization_found();
    }

    @After({"@ScenarioOrganizationFoundForUnenroll"})
    public void after_scenario_organization_found_for_unenroll() {
        after_scenario_organization_found();
    }

    public String getServiceId() throws CloudFoundryException {
        ListServicesResponse response;
        String serviceId = null;

        try {
            ListServicesRequest request = ListServicesRequest.builder().label(serviceName).build();         
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

    public String getServicePlanId(String serviceId) throws CloudFoundryException {
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

    @Before({"@deleteInstancesStandardMode"})
    public void before_scenario_delete_instances_in_standard_mode() {
        log.info("Before scenario deleteInstancesStandardMode ");
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .name("org" + System.nanoTime()).build();
        CreateOrganizationResponse response = cfclient.organizations().create(request).get();

        tempOrgId = response.getMetadata().getId();

        ListDomainsResponse dom = cfclient.domains()
                .list(ListDomainsRequest.builder()
                        .name("bosh-lite.com")
                        .build())
                .get();

        String domainId = dom.getResources().get(0).getMetadata().getId();

        String serviceId = getServiceId();
        if (serviceId != null) {
            String servicePlanId = getServicePlanId(serviceId);

            CreateServicePlanVisibilityResponse visibilityResponse = cfclient.servicePlanVisibilities()
                    .create(CreateServicePlanVisibilityRequest.builder()
                            .servicePlanId(servicePlanId)
                            .organizationId(tempOrgId)
                            .build())
                    .get();

            planVisibilityGuid = visibilityResponse.getMetadata().getId();


            try {
                for (int i = 0; i < 1; i++) {

                    CreateSpaceResponse spaceResponse = cfclient.spaces()
                            .create(CreateSpaceRequest.builder()
                                    .organizationId(tempOrgId)
                                    .name("space" + i)
                                    .build())
                            .get();

                    String spaceId = spaceResponse.getMetadata().getId();

                    CreateApplicationResponse appResponse = cfclient.applicationsV2()
                            .create(CreateApplicationRequest.builder()
                                    .name("test-app")
                                    .memory(256)
                                    .spaceId(spaceId)
                                    .buildpack("java_buildpack")
                                    .build())
                            .get();

                    testAppId = appResponse.getMetadata().getId();


                    cfclient.applicationsV2()
                    .upload(UploadApplicationRequest.builder()
                            .application(getClass().getClassLoader().getResourceAsStream("Test-App.war"))
                            .applicationId(testAppId)
                            .build())
                        .get();

                    CreateRouteResponse route = cfclient.routes()
                            .create(CreateRouteRequest.builder()
                                    .domainId(domainId)
                                    .spaceId(spaceId)
                                    .host("test-app" + System.nanoTime())
                                    .build())
                            .get();

                    routeId = route.getMetadata().getId();

                    cfclient.routes()
                    .associateApplication(AssociateRouteApplicationRequest.builder()
                            .applicationId(testAppId)
                            .routeId(routeId)
                            .build())
                        .get();

                    cfclient.applicationsV2().update(UpdateApplicationRequest.builder()
                            .applicationId(testAppId)
                            .state("STARTED")
                            .build())
                    .get();

                    ApplicationStatisticsRequest stats = ApplicationStatisticsRequest.builder()
                            .applicationId(testAppId)
                            .build();
                    String state = "";

                    do {
                        ApplicationStatisticsResponse statsResponse = cfclient.applicationsV2()
                                .statistics(stats)
                                .get();
                        state = statsResponse.get("0").getState();
                    } while (state.compareTo("DOWN") == 0);

                    Map<String, Object> requestParameters = new HashMap<String, Object>();
                    requestParameters.put("idle-duration", "PT20S");
                    requestParameters.put("auto-enrollment", "standard");

                    CreateServiceInstanceResponse instanceResponse = cfclient.serviceInstances()
                            .create(CreateServiceInstanceRequest.builder()
                                    .name("autosleep" + System.nanoTime())
                                    .spaceId(spaceId)
                                    .servicePlanId(servicePlanId)
                                    .acceptsIncomplete(false)
                                    .parameters(requestParameters)
                                    .build())
                            .get();

                    String instanceId = instanceResponse.getMetadata().getId();

                    ListServiceInstanceServiceBindingsRequest bindReq = 
                            ListServiceInstanceServiceBindingsRequest.builder()
                            .serviceInstanceId(instanceId)
                            .build();


                    int bindingCount = 0;

                    do {
                        ListServiceInstanceServiceBindingsResponse bindRes = cfclient.serviceInstances()
                                .listServiceBindings(bindReq)
                                .get();
                        bindingCount = bindRes.getTotalResults();
                    } while (bindingCount == 0);
                }
            } catch (CloudFoundryException ce) {
                log.error("Error in pre scenario execution : " + ce);
            }
        } else {
            log.error("autosleep service is not available");
        }

    }

    @Before({"@NoDeletionInForcedMode"})
    public void before_scenario_no_deletion_of_instances() {
        log.info("Before scenario NoDeletionInForcedMode");
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .name("org" + System.nanoTime()).build();
        CreateOrganizationResponse response = cfclient.organizations().create(request).get();

        tempOrgId = response.getMetadata().getId();

        ListDomainsResponse dom = cfclient.domains()
                .list(ListDomainsRequest.builder()
                        .name("bosh-lite.com")
                        .build())
                .get();

        String domainId = dom.getResources().get(0).getMetadata().getId();

        String serviceId = getServiceId();
        if (serviceId != null) {
            String servicePlanId = getServicePlanId(serviceId);

            CreateServicePlanVisibilityResponse visibilityResponse = cfclient.servicePlanVisibilities()
                    .create(CreateServicePlanVisibilityRequest.builder()
                            .servicePlanId(servicePlanId)
                            .organizationId(tempOrgId)
                            .build())
                    .get();

            planVisibilityGuid = visibilityResponse.getMetadata().getId();


            try {
                for (int i = 0; i < 1; i++) {

                    CreateSpaceResponse spaceResponse = cfclient.spaces()
                            .create(CreateSpaceRequest.builder()
                                    .organizationId(tempOrgId)
                                    .name("space" + i)
                                    .build())
                            .get();

                    String spaceId = spaceResponse.getMetadata().getId();

                    CreateApplicationResponse appResponse = cfclient.applicationsV2()
                            .create(CreateApplicationRequest.builder()
                                    .name("test-app")
                                    .memory(256)
                                    .spaceId(spaceId)
                                    .buildpack("java_buildpack")
                                    .build())
                            .get();

                    testAppId = appResponse.getMetadata().getId();


                    cfclient.applicationsV2()
                    .upload(UploadApplicationRequest.builder()

                            .application(getClass().getClassLoader().getResourceAsStream("Test-App.war"))
                            .applicationId(testAppId)
                            .build())
                        .get();

                    CreateRouteResponse route = cfclient.routes()
                            .create(CreateRouteRequest.builder()
                                    .domainId(domainId)
                                    .spaceId(spaceId)
                                    .host("test-app" + System.nanoTime())
                                    .build())
                            .get();

                    routeId = route.getMetadata().getId();

                    cfclient.routes()
                    .associateApplication(AssociateRouteApplicationRequest.builder()
                            .applicationId(testAppId)
                            .routeId(routeId)
                            .build())
                        .get();

                    cfclient.applicationsV2().update(UpdateApplicationRequest.builder()
                            .applicationId(testAppId)
                            .state("STARTED")
                            .build())
                    .get();

                    ApplicationStatisticsRequest stats = ApplicationStatisticsRequest.builder()
                            .applicationId(testAppId)
                            .build();
                    String state = "";

                    do {
                        ApplicationStatisticsResponse statsResponse = cfclient.applicationsV2()
                                .statistics(stats)
                                .get();
                        state = statsResponse.get("0").getState();
                    } while (state.compareTo("DOWN") == 0);

                    Map<String, Object> requestParameters = new HashMap<String, Object>();
                    requestParameters.put("idle-duration", "PT20S");
                    requestParameters.put("auto-enrollment", "forced");
                    requestParameters.put("secret", "forced");

                    CreateServiceInstanceResponse instanceResponse = cfclient.serviceInstances()
                            .create(CreateServiceInstanceRequest.builder()
                                    .name("autosleep" + System.nanoTime())
                                    .spaceId(spaceId)
                                    .servicePlanId(servicePlanId)
                                    .acceptsIncomplete(false)
                                    .parameters(requestParameters)
                                    .build())
                            .get();

                    String instanceId = instanceResponse.getMetadata().getId();

                    ListServiceInstanceServiceBindingsRequest bindReq = 
                            ListServiceInstanceServiceBindingsRequest.builder()
                            .serviceInstanceId(instanceId)
                            .build();


                    int bindingCount = 0;

                    do {
                        ListServiceInstanceServiceBindingsResponse bindRes = cfclient.serviceInstances()
                                .listServiceBindings(bindReq)
                                .get();
                        bindingCount = bindRes.getTotalResults();
                    } while (bindingCount == 0);
                }
            } catch (CloudFoundryException ce) {
                log.error("Error in pre scenario execution : " + ce);
            }
        } else {
            log.error("autosleep service is not available");
        }

    }

    @Before({"@deleteInstancesTransitiveMode"})
    public void before_scenario_delete_instances_in_transitive_mode() {
        log.info("Before scenario deleteInstancesTransitiveMode");
        CreateOrganizationRequest request = CreateOrganizationRequest.builder()
                .name("org" + System.nanoTime()).build();
        CreateOrganizationResponse response = cfclient.organizations().create(request).get();

        tempOrgId = response.getMetadata().getId();

        ListDomainsResponse dom = cfclient.domains()
                .list(ListDomainsRequest.builder()
                        .name("bosh-lite.com")
                        .build())
                .get();

        String domainId = dom.getResources().get(0).getMetadata().getId();

        String serviceId = getServiceId();
        if (serviceId != null) {
            String servicePlanId = getServicePlanId(serviceId);

            CreateServicePlanVisibilityResponse visibilityResponse = cfclient.servicePlanVisibilities()
                    .create(CreateServicePlanVisibilityRequest.builder()
                            .servicePlanId(servicePlanId)
                            .organizationId(tempOrgId)
                            .build())
                    .get();

            planVisibilityGuid = visibilityResponse.getMetadata().getId();


            try {
                for (int i = 0; i < 1; i++) {

                    CreateSpaceResponse spaceResponse = cfclient.spaces()
                            .create(CreateSpaceRequest.builder()
                                    .organizationId(tempOrgId)
                                    .name("space" + i)
                                    .build())
                            .get();

                    String spaceId = spaceResponse.getMetadata().getId();

                    CreateApplicationResponse appResponse = cfclient.applicationsV2()
                            .create(CreateApplicationRequest.builder()
                                    .name("test-app")
                                    .memory(256)
                                    .spaceId(spaceId)
                                    .buildpack("java_buildpack")
                                    .build())
                            .get();

                    testAppId = appResponse.getMetadata().getId();


                    cfclient.applicationsV2()
                    .upload(UploadApplicationRequest.builder()

                            .application(getClass().getClassLoader().getResourceAsStream("Test-App.war"))
                            .applicationId(testAppId)
                            .build())
                        .get();

                    CreateRouteResponse route = cfclient.routes()
                            .create(CreateRouteRequest.builder()
                                    .domainId(domainId)
                                    .spaceId(spaceId)
                                    .host("test-app" + System.nanoTime())
                                    .build())
                            .get();

                    routeId = route.getMetadata().getId();

                    cfclient.routes()
                    .associateApplication(AssociateRouteApplicationRequest.builder()
                            .applicationId(testAppId)
                            .routeId(routeId)
                            .build())
                        .get();

                    cfclient.applicationsV2().update(UpdateApplicationRequest.builder()
                            .applicationId(testAppId)
                            .state("STARTED")
                            .build())
                    .get();

                    ApplicationStatisticsRequest stats = ApplicationStatisticsRequest.builder()
                            .applicationId(testAppId)
                            .build();
                    String state = "";

                    do {
                        ApplicationStatisticsResponse statsResponse = cfclient.applicationsV2()
                                .statistics(stats)
                                .get();
                        state = statsResponse.get("0").getState();
                    } while (state.compareTo("DOWN") == 0);

                    Map<String, Object> requestParameters = new HashMap<String, Object>();
                    requestParameters.put("idle-duration", "PT20S");
                    requestParameters.put("auto-enrollment", "transitive");

                    CreateServiceInstanceResponse instanceResponse = cfclient.serviceInstances()
                            .create(CreateServiceInstanceRequest.builder()
                                    .name("autosleep" + System.nanoTime())
                                    .spaceId(spaceId)
                                    .servicePlanId(servicePlanId)
                                    .acceptsIncomplete(false)
                                    .parameters(requestParameters)
                                    .build())
                            .get();

                    String instanceId = instanceResponse.getMetadata().getId();

                    ListServiceInstanceServiceBindingsRequest bindReq = 
                            ListServiceInstanceServiceBindingsRequest.builder()
                            .serviceInstanceId(instanceId)
                            .build();


                    int bindingCount = 0;

                    do {
                        ListServiceInstanceServiceBindingsResponse bindRes = cfclient.serviceInstances()
                                .listServiceBindings(bindReq)
                                .get();
                        bindingCount = bindRes.getTotalResults();
                    } while (bindingCount == 0);
                }
            } catch (CloudFoundryException ce) {
                log.error("Error in pre scenario execution : " + ce);
            }
        } else {
            log.error("autosleep service is not available");
        }

    }

    @Given("^a cloud foundry instance with an unenrolled organization which have service instance in standard mode$")
    public void a_cloud_foundry_instance_with_an_unenrolled_organization_with_service_instances_in_standard_mode() 
            throws Throwable {
        log.info("a cloud foundry instance with autosleep service in standard mode is available");
    }

    @Given("^a cloud foundry instance with an unenrolled organization which have service instance in transitive mode$")
    public void a_cloud_foundry_instance_with_an_unenrolled_organization_with_service_instances_in_transitive_mode() 
            throws Throwable {
        log.info("a cloud foundry instance with autosleep service in transitive mode is available");
    }

    @Given("^a cloud foundry instance with an unenrolled organization which have service instance in forced mode$")
    public void a_cloud_foundry_instance_with_an_unenrolled_organization_with_service_instances_in_forced_mode() 
            throws Throwable {
        log.info("a cloud foundry instance with autosleep service in forced mode is available");
    }

    @When("^organization deregister runs$")
    public void organization_deregister_runs() throws Throwable {

        ListApplicationsResponse response = cfclient.applicationsV2()
                .list(ListApplicationsRequest.builder()
                        .name(autosleepName)
                        .build())
                .get();

        String appId = response.getResources().get(0).getMetadata().getId();

        cfclient.applicationsV2().update(UpdateApplicationRequest.builder()
                .applicationId(appId)
                .state("STOPPED")
                .build())
        .get();

        cfclient.applicationsV2().update(UpdateApplicationRequest.builder()
                .applicationId(appId)
                .state("STARTED")
                .build())
        .get();
    }

    @Then("^service instances should be deleted$")
    public void service_instances_should_be_deleted() throws Throwable {

        try {
            ListApplicationsResponse appResponse = cfclient.applicationsV2()
                    .list(ListApplicationsRequest.builder()
                            .name(autosleepName)
                            .build())
                    .get();

            String appId = appResponse.getResources().get(0).getMetadata().getId();

            ApplicationStatisticsRequest stats = ApplicationStatisticsRequest.builder()
                    .applicationId(appId)
                    .build();
            String state = "";

            do {
                ApplicationStatisticsResponse statsResponse = cfclient.applicationsV2()
                        .statistics(stats)
                        .get();
                state = statsResponse.get("0").getState();
            } while (state.compareTo("DOWN") == 0);

            if (state.compareTo("RUNNING") == 0) {
                Thread.currentThread();
                Thread.sleep(30000);
            } else {
                throw new CloudFoundryException(524, "Error restarting application: Start app timeout", "290006");
            }

            ListServiceInstancesResponse response = cfclient.serviceInstances()
                    .list(ListServiceInstancesRequest.builder()
                            .organizationId(tempOrgId)
                            .build())
                    .get();
            instanceCount = response.getTotalResults();

            assertEquals(0, instanceCount);
        } catch (CloudFoundryException re) {
            log.error("Application failed to start : " + re);
        }

    }

    @Then("^service instances are not deleted$")
    public void service_instances_are_not_deleted() throws Throwable {
        try {
            ListApplicationsResponse appResponse = cfclient.applicationsV2()
                    .list(ListApplicationsRequest.builder()
                            .name(autosleepName)
                            .build())
                    .get();

            String appId = appResponse.getResources().get(0).getMetadata().getId();

            ApplicationStatisticsRequest stats = ApplicationStatisticsRequest.builder()
                    .applicationId(appId)
                    .build();
            String state = "";

            do {
                ApplicationStatisticsResponse statsResponse = cfclient.applicationsV2()
                        .statistics(stats)
                        .get();
                state = statsResponse.get("0").getState();
            } while (state.compareTo("DOWN") == 0);

            if (state.compareTo("RUNNING") == 0) {
                Thread.currentThread();
                Thread.sleep(30000);
            } else {
                throw new CloudFoundryException(524, "Error restarting application: Start app timeout", "290006");
            }

            ListServiceInstancesResponse response = cfclient.serviceInstances()
                    .list(ListServiceInstancesRequest.builder()
                            .organizationId(tempOrgId)
                            .build())
                    .get();
            instanceCount = response.getTotalResults();

            assertNotEquals(0, instanceCount);
        } catch (CloudFoundryException re) {
            log.error("Application failed to start : " + re);
        }
    }

    @After({"@deleteInstancesStandardMode"})
    public void after_scenario_delete_instances() {

        cfclient.servicePlanVisibilities()
        .delete(DeleteServicePlanVisibilityRequest.builder()
                .servicePlanVisibilityId(planVisibilityGuid)
                .build())
            .get();

        ListSpacesResponse listResponse = cfclient.spaces()
                .list(ListSpacesRequest.builder()
                        .organizationId(tempOrgId)
                        .build())
                .get();

        for (SpaceResource resource : listResponse.getResources()) {
            String spaceId = resource.getMetadata().getId();

            cfclient.routes().delete(DeleteRouteRequest.builder()
                    .routeId(routeId)
                    .build())
            .get();

            cfclient.applicationsV2().delete(DeleteApplicationRequest.builder()
                    .applicationId(testAppId)
                    .build())
            .get();

            cfclient.spaces().delete(DeleteSpaceRequest.builder()
                    .spaceId(spaceId)
                    .build())
            .get();
        }

        DeleteOrganizationRequest request = DeleteOrganizationRequest.builder()
                .organizationId(tempOrgId)
                .build();
        cfclient.organizations().delete(request).get();
    }

    @After({"@NoDeletionInForcedMode"})
    public void after_scenario_no_deletion_in_forced_mode() {

        log.info("Post scenario execution");

        cfclient.servicePlanVisibilities()
        .delete(DeleteServicePlanVisibilityRequest.builder()
                .servicePlanVisibilityId(planVisibilityGuid)
                .build())
            .get();

        ListServiceInstancesRequest instanceRequest = ListServiceInstancesRequest.builder()
                .organizationId(tempOrgId)
                .build();

        ListServiceInstancesResponse instanceResponse = cfclient.serviceInstances()
                .list(instanceRequest)
                .get();

        for (ServiceInstanceResource item : instanceResponse.getResources()) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("auto-enrollment", "standard");
            params.put("secret", "forced");

            cfclient.serviceInstances().update(UpdateServiceInstanceRequest.builder()
                    .serviceInstanceId(item.getMetadata().getId())
                    .parameters(params)
                    .build())
            .get();

            cfclient.serviceInstances().delete(DeleteServiceInstanceRequest.builder()
                    .serviceInstanceId(item.getMetadata().getId())
                    .build())
            .get();
        }

        ListSpacesResponse listResponse = cfclient.spaces()
                .list(ListSpacesRequest.builder()
                        .organizationId(tempOrgId)
                        .build())
                .get();

        for (SpaceResource resource : listResponse.getResources()) {
            String spaceId = resource.getMetadata().getId();

            cfclient.routes().delete(DeleteRouteRequest.builder()
                    .routeId(routeId)
                    .build())
            .get();

            cfclient.applicationsV2().delete(DeleteApplicationRequest.builder()
                    .applicationId(testAppId)
                    .build())
            .get();

            cfclient.spaces().delete(DeleteSpaceRequest.builder()
                    .spaceId(spaceId)
                    .build())
            .get();
        }

        DeleteOrganizationRequest request = DeleteOrganizationRequest.builder()
                .organizationId(tempOrgId)
                .build();
        cfclient.organizations().delete(request).get();
    }

    @After({"@deleteInstancesTransitiveMode"})
    public void after_scenario_delete_instances_in_transitive_mode() {
        after_scenario_delete_instances();
    }
}