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
import org.cloudfoundry.client.v2.CloudFoundryException;
import org.cloudfoundry.client.v2.applications.ApplicationStatisticsResponse;
import org.cloudfoundry.client.v2.applications.CreateApplicationResponse;
import org.cloudfoundry.client.v2.applications.ListApplicationServiceBindingsResponse;
import org.cloudfoundry.client.v2.domains.ListDomainsResponse;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;
import org.cloudfoundry.client.v2.routes.CreateRouteResponse;
import org.cloudfoundry.client.v2.servicebrokers.CreateServiceBrokerResponse;
import org.cloudfoundry.client.v2.servicebrokers.ListServiceBrokersResponse;
import org.cloudfoundry.client.v2.servicebrokers.ServiceBrokerResource;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceResponse;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstanceServiceBindingsResponse;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesResponse;
import org.cloudfoundry.client.v2.serviceplans.ListServicePlansResponse;
import org.cloudfoundry.client.v2.serviceplans.ServicePlanResource;
import org.cloudfoundry.client.v2.serviceplanvisibilities.CreateServicePlanVisibilityResponse;
import org.cloudfoundry.client.v2.services.ListServicesResponse;
import org.cloudfoundry.client.v2.services.ServiceResource;
import org.cloudfoundry.client.v2.spaces.CreateSpaceResponse;
import org.cloudfoundry.client.v2.spaces.ListSpacesResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class StepDefinitions {

    private static final Logger log = LoggerFactory.getLogger(StepDefinitions.class);

    private String autosleepUrl;
    private String domainName;
    private String securityUsername;
    private String securityUserPassword;
    private String serviceBrokerName;
    private String cfUsername;
    private String cfUserPassword;
    private String[] organizationNames;
    private String[] organizationIds;
    private String[][] spaceIds;
    private String[][] testAppIds;
    private String[][] routeIds;

    private int[] status;
    private String[] result;
    private int instanceCount;
    private String[] planVisibilityGuids;
    private String serviceBrokerId;
    private String servicePlanId;
    private String[] serviceInstanceIds;
    private boolean brokerAlreadyCreated;

    private InputStream inputStream;

    private CloudFoundryApi cfapi;
    
    @Before({"@start"})
    public void before() {

        log.info("Reading parameters from Properties file");

        try {
            Properties prop = new Properties();
            String fileName = "config.properties";

            inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

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

            organizationIds  = new String[organizationNames.length];
            spaceIds = new String[organizationIds.length][2];
            testAppIds = new String[organizationIds.length][2];
            routeIds = new String[organizationIds.length][2];
            planVisibilityGuids = new String[organizationIds.length];
            status = new int[organizationNames.length];
            result = new String[organizationNames.length];
            serviceInstanceIds = new String[organizationNames.length];
            instanceCount = 0;
            brokerAlreadyCreated = true;

            cfapi = new CloudFoundryApi();
            ListOrganizationsResponse response = cfapi.listOrganizations();
            
            for (int index = 0; index < organizationNames.length; index++) {
                for (OrganizationResource resource : response.getResources()) {
                    if ( organizationNames[index].compareTo(resource.getEntity().getName()) == 0) {
                        organizationIds[index] = resource.getMetadata().getId(); 
                    }
                }
            }
        } catch (RuntimeException re) {
            log.error("Runtime Exception : " + re);
        } catch (Exception e) {
            log.error("Error while reading file : " + e);
        }
    }

    public String getServiceId() throws CloudFoundryException {
        ListServicesResponse response;
        String serviceId = null;

        try {
            response = cfapi.listServices(getServiceName());  
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
                ListServicePlansResponse response = cfapi.listServicePlan(serviceId);
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

    public String getServiceName() {
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
        } catch (HttpClientErrorException he) {
            log.error("Autosleep service not available");
        } catch (RuntimeException re) {
            log.error("Error Fetching Catalog : " + re);
        }
        return serviceName;
    }

    @Before({"@registerNewOrganization"})
    public void before_scenario_register_new_organization() throws Throwable {
        log.info("Before scenario execution");

        

        ListServiceBrokersResponse brokerResponse = cfapi.listServiceBroker();

        String serviceId = "";
        if (brokerResponse.getTotalResults() == 0) {
            log.error("Service broker with name : " + serviceBrokerName + " does not exists");
            log.info("creating service broker");

            CreateServiceBrokerResponse response = cfapi
                    .createServiceBroker(autosleepUrl, serviceBrokerName, cfUsername, cfUserPassword);
            
            serviceBrokerId = response.getMetadata().getId();
            brokerAlreadyCreated = false;
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
                ListDomainsResponse domainResponse = cfapi.listDomain(domainName);

                String domainId = domainResponse.getResources().get(0).getMetadata().getId();

                serviceId = getServiceId();

                if (serviceId != null) {
                    servicePlanId = getServicePlanId(serviceId);
                    CreateServicePlanVisibilityResponse visibilityResponse = cfapi
                            .createServicePlanVisibility(servicePlanId, organizationIds[index]);

                    planVisibilityGuids[index] = visibilityResponse.getMetadata().getId();

                    try {
                        CreateSpaceResponse spaceResponse = cfapi
                                .createSpace(organizationIds[index], "space" + System.nanoTime());
                        spaceIds[index][0] = spaceResponse.getMetadata().getId();

                        CreateApplicationResponse appResponse = cfapi
                                .createApplication("test-app", 256, spaceIds[index][0], "nodejs_buildpack");
                        testAppIds[index][0] = appResponse.getMetadata().getId();
                        cfapi.uploadApplication(getClass().getClassLoader().getResourceAsStream("test-app.zip"), 
                                testAppIds[index][0]);
                        
                        CreateRouteResponse routeResponse = cfapi
                                .createRoute(domainId, spaceIds[index][0], "test-app" + System.nanoTime());

                        routeIds[index][0] = routeResponse.getMetadata().getId();
                        cfapi.associateRouteApplication(testAppIds[index][0], routeIds[index][0]);
                        cfapi.updateApplicationState(testAppIds[index][0], "STARTED");
                        
                        // Wait for application to get to running state
                        Thread.sleep(30000);
                        
                        String state = "";

                        ApplicationStatisticsResponse statsResponse = cfapi.applicationStatistics(testAppIds[index][0]);
                        state = statsResponse.get("0").getState();
                        
                        if (state.compareTo("RUNNING") != 0) {
                            throw new CloudFoundryException(HttpStatus.BAD_REQUEST.value(),
                                    "test application has not finished staging", "170002");
                        }

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
    
    @Before({"@OrganizationFound"})
    public void before_scenario_get_enrolled_organization() throws Throwable {
        before_scenario_register_new_organization();
        an_organization_is_enrolled_with_autosleep();
    }
    
    @Before({"@updateAlreadyEnrolledOrganization"})
    public void before_scenario_update_enrolled_organization() throws Throwable {
        before_scenario_register_new_organization();
        an_organization_is_enrolled_with_autosleep();
        
        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                ListDomainsResponse domainResponse = cfapi.listDomain(domainName);

                String domainId = domainResponse.getResources().get(0).getMetadata().getId();

                try {
                    CreateSpaceResponse spaceResponse = cfapi
                            .createSpace(organizationIds[index], "space" + System.nanoTime());

                    spaceIds[index][1] = spaceResponse.getMetadata().getId();

                    CreateApplicationResponse appResponse = cfapi
                            .createApplication("test-app", 256, spaceIds[index][1], "nodejs_buildpack");

                    testAppIds[index][1] = appResponse.getMetadata().getId();

                    cfapi.uploadApplication(getClass().getClassLoader().getResourceAsStream("test-app.zip"),
                            testAppIds[index][1]);
                    
                    CreateRouteResponse routeResponse = cfapi
                            .createRoute(domainId, spaceIds[index][1], "test-app" + System.nanoTime());

                    routeIds[index][1] = routeResponse.getMetadata().getId();

                    cfapi.associateRouteApplication(testAppIds[index][1], routeIds[index][1]);
                    cfapi.updateApplicationState(testAppIds[index][1], "STARTED");
                    
                    // Wait for application to get to running state
                    Thread.sleep(30000);
                    
                    String state = "";

                    ApplicationStatisticsResponse statsResponse = cfapi.applicationStatistics(testAppIds[index][1]);
                    state = statsResponse.get("0").getState();
                    
                    if (state.compareTo("RUNNING") != 0) {
                        throw new CloudFoundryException(HttpStatus.BAD_REQUEST.value(),
                                "test application has not finished staging", "170002");
                    }
                    
                } catch (CloudFoundryException ce) {
                    log.error("Error in pre scenario execution : " + ce);
                }

            } else {
                throw new CloudFoundryException(HttpStatus.NOT_FOUND.value(),
                        "Organization : \"" + organizationNames[index] + "\" could not be found", "30003");
            }
        }
    }
    
    @Before({"@OrganizationFoundForUnenroll"})
    public void before_scenario_unenroll_an_organization() throws Throwable {
        before_scenario_register_new_organization();
        an_organization_is_enrolled_with_autosleep();
        Thread.sleep(20000);
    }
    
    @Before({"@OrganizationNotFound"})
    public void before_scenario_fail_to_retrieve_details_of_an_organization() throws Throwable {
        before_scenario_register_new_organization();
    }
    
    @Before({"@OrganizationNotFoundForUnenroll"})
    public void before_scenario_fail_to_unenroll_an_organization() throws Throwable {
        before_scenario_register_new_organization();
    }
    
    @Given("^a cloud foundry landscape with autosleep application deployed on it$")
    public void given_state() {
        log.info("Autosleep application is deployed on Cloud Foundry instance");
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

            } catch (HttpClientErrorException he) {
                status[index] = 400; 
                result[index] = "Organization : \"" 
                        + organizationNames[index] + "\" could not be found";
            } catch (RuntimeException re) {
                log.error("Error completing Pre Scenario Execution : " + re);
            }
        }
    }

    @Then("^service instances are created in all spaces of the organization$")
    public void service_instances_are_created_in_all_spaces_of_the_organization() throws Throwable {

        for (int index = 0; index < organizationIds.length; index++) {
            Thread.currentThread();
            Thread.sleep(30000);
            if (organizationIds[index] != null) {
                ListServiceInstancesResponse response = cfapi
                        .listServiceInstance(organizationIds[index], spaceIds[index][0]);
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

            ListApplicationServiceBindingsResponse appBindResponse = cfapi
                    .listApplicationServiceBindings(testAppIds[index][0]);

            String serviceBindingIdForApp = appBindResponse.getResources().get(0).getMetadata().getId();

            ListSpacesResponse spaceResponse = cfapi.listSpaces(organizationIds[index], testAppIds[index][0]);

            spaceIds[index][0] = spaceResponse.getResources().get(0).getMetadata().getId();

            ListServiceInstancesResponse instanceResponse = cfapi
                    .listServiceInstance(organizationIds[index], spaceIds[index][0]);

            String instanceId = instanceResponse.getResources().get(0).getMetadata().getId();

            ListServiceInstanceServiceBindingsResponse bindRes = cfapi
                    .listServiceInstanceServiceBindings(instanceId);

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

            } catch (HttpClientErrorException he) {
                status[index] = 404;
            } catch (RuntimeException re) {
                log.error("Error Fetching Organization details : " + re);
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

            } catch (HttpClientErrorException he) {
                status[index] = 400; 
                result[index] = "Organization : \"" 
                        + organizationNames[index] + "\" could not be found";
            } catch (RuntimeException re) {
                log.error("Error completing Pre Scenario Execution : " + re);
            }
        }
    }
    
    @Then("^service instances are updated in all spaces of the organization as per latest enrollment$")
    public void service_instances_are_updated_in_all_spaces_of_the_organization() throws Throwable {

        Thread.currentThread();
        Thread.sleep(30000);

        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                ListServiceInstancesResponse responseForFirstSpace = cfapi
                        .listServiceInstance(organizationIds[index], spaceIds[index][0]);
                instanceCount = responseForFirstSpace.getTotalResults();

                assertEquals(1, instanceCount);

                ListServiceInstancesResponse responseForSecondSpace = cfapi
                        .listServiceInstance(organizationIds[index], spaceIds[index][1]);
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
            } catch (HttpClientErrorException he) {
                status[index] = 404;
            } catch (RuntimeException re) {
                log.error("Error deleting organization details : " + re);
            }
        }
    }
    
    @Then("^service instances are deleted from all of its spaces$")
    public void service_instances_are_deleted() throws Throwable {

        log.info("Check for service instances after unenrolling");

        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                ListServiceInstancesResponse responseForFirstSpace = cfapi
                        .listServiceInstance(organizationIds[index], spaceIds[index][0]);
                instanceCount = responseForFirstSpace.getTotalResults();

                assertEquals(0, instanceCount);
            } else {
                throw new CloudFoundryException(HttpStatus.NOT_FOUND.value(),
                        "Organization : \"" + organizationNames[index] + "\" could not be found", "30003");
            }
        }
    }
    
    @After({"@registerNewOrganization"})
    public void after_scenario_register_new_organization() throws Throwable {
        log.info("Executing post scenario");

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

                log.info("Post scenario execution completed. Status : " + response.getStatusCode().value());
            } catch (HttpClientErrorException he) {
                log.error("Error completing post scenario execution : " + he);
            } catch (RuntimeException re) {
                log.error("Error completing post scenario execution : " + re);
            }
        }
        
        for (index = 0; index < organizationIds.length; index++) {
            cfapi.deleteServicePlanVisibility(planVisibilityGuids[index]);
            
            for (int i = 0; i < 1; i++) {
                cfapi.deleteRoute(routeIds[index][i]);
                cfapi.deleteApplication(testAppIds[index][i]);
                cfapi.deleteSpace(spaceIds[index][i]);
            }
        }

        deleteServiceBroker();
    }
    
    @After({"@OrganizationFound"})
    public void after_scenario_get_enrolled_organization() throws Throwable {
        after_scenario_register_new_organization();
    }

    @After({"@updateAlreadyEnrolledOrganization"})
    public void after_scenario_update_enrolled_organization() throws Throwable {
        log.info("Executing post scenario");

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

                log.info("Post scenario execution completed. Status : " + response.getStatusCode().value());
            } catch (HttpClientErrorException he) {
                log.error("Error completing post scenario execution : " + he);
            } catch (RuntimeException re) {
                log.error("Error completing post scenario execution : " + re);
            }
        }
        
        for (index = 0; index < organizationIds.length; index++) {

            cfapi.deleteServicePlanVisibility(planVisibilityGuids[index]);
            
            for (int i = 0; i < 2; i++) {
                cfapi.deleteRoute(routeIds[index][i]);
                cfapi.deleteApplication(testAppIds[index][i]);
                cfapi.deleteSpace(spaceIds[index][i]);
            }
        }

        deleteServiceBroker();
    }
    
    @After({"@OrganizationFoundForUnenroll"})
    public void after_scenario_unenroll_an_organization() throws Throwable {
        after_scenario_register_new_organization();
    }
    
    @After({"@OrganizationNotFound"})
    public void after_scenario_fail_to_retrieve_details_of_an_organization() throws Throwable {
        after_scenario_register_new_organization();
    }
    
    @After({"@OrganizationNotFoundForUnenroll"})
    public void after_scenario_fail_to_unenroll_an_organization() throws Throwable {
        after_scenario_register_new_organization();
    }
    
    @Before({"@transientOptOut"})
    public void before_scenario_transientOtpOut() throws Throwable {
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
                CreateServiceInstanceResponse response = cfapi
                        .createServiceInstance("autosleep" + System.nanoTime(), spaceIds[index][0],
                                servicePlanId, requestParameters);

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
            ListServiceInstanceServiceBindingsResponse response = cfapi
                    .listServiceInstanceServiceBindings(serviceInstanceIds[index]);

            String binding = response.getResources().get(0).getMetadata().getId();
            cfapi.deleteServiceBinding(binding);

        }
    }

    @Then("^the application gets bounded with the service instance in next scan$")
    public void application_gets_bounded_with_the_service_instance_in_next_scan() throws Throwable {

        for (int index = 0; index < organizationIds.length; index++) {
            cfapi.updateApplicationState(testAppIds[index][0], "STARTED");
            
            // Wait for application to get to running state
            Thread.sleep(30000);
            
            String state = "";
            
            ApplicationStatisticsResponse statsResponse = cfapi.applicationStatistics(testAppIds[index][0]);
            state = statsResponse.get("0").getState();
            
            if (state.compareTo("RUNNING") != 0) {
                throw new CloudFoundryException(HttpStatus.BAD_REQUEST.value(),
                        "test application has not finished staging", "170002");
            }
            
        }

        // wait for idle duration time
        Thread.sleep(60000);
        applications_in_the_spaces_are_bounded_with_the_service_instance();
    }

    @After({"@transientOptOut"})
    public void after_scenario_transientOptOut() {
        log.info("Executing post scenario");

        for (int index = 0; index < organizationIds.length; index++) {

            ListApplicationServiceBindingsResponse response = cfapi
                    .listApplicationServiceBindings(testAppIds[index][0]);
            String bindingId = response.getResources().get(0).getMetadata().getId();
            cfapi.deleteServiceBinding(bindingId);
            cfapi.deleteServiceInstance(serviceInstanceIds[index]);
            cfapi.deleteServicePlanVisibility(planVisibilityGuids[index]);
            cfapi.deleteRoute(routeIds[index][0]);
            cfapi.deleteApplication(testAppIds[index][0]);
            cfapi.deleteSpace(spaceIds[index][0]);
        }

        deleteServiceBroker();
    }

    @Before({"@transientmodeInstanceDeletion"})
    public void before_transientmodeInstanceDeletion() {

        ListServiceBrokersResponse brokerResponse = cfapi.listServiceBroker();
        String serviceId = "";
        
        if (brokerResponse.getTotalResults() == 0) {
            log.error("Service broker with name : " + serviceBrokerName + " does not exists");
            log.info("creating service broker");

            CreateServiceBrokerResponse response = cfapi
                    .createServiceBroker(autosleepUrl, serviceBrokerName, cfUsername, cfUserPassword);
            
            serviceBrokerId = response.getMetadata().getId();
            brokerAlreadyCreated = false;
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
                    CreateServicePlanVisibilityResponse visibilityResponse = cfapi
                            .createServicePlanVisibility(servicePlanId, organizationIds[index]);

                    planVisibilityGuids[index] = visibilityResponse.getMetadata().getId();

                    try {
                        CreateSpaceResponse spaceResponse = cfapi
                                .createSpace(organizationIds[index], "space" + System.nanoTime());

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

    @When("^a request is made to delete the service instance$")
    public void a_request_is_made_to_delete_the_service_instance() throws Throwable {

        for (int index = 0; index < organizationIds.length; index++) {
            cfapi.deleteServiceInstance(serviceInstanceIds[index]);
            // Delay for delete job to complete
            Thread.sleep(5000);
        }
    }

    @Then("^the service instance gets deleted from the space within the organization$")
    public void the_service_instance_gets_deleted_from_the_space_within_the_organization() {

        for (int index = 0; index < organizationIds.length; index++) {
            if (organizationIds[index] != null) {
                ListServiceInstancesResponse responseForFirstSpace = cfapi
                        .listServiceInstance(organizationIds[index], spaceIds[index][0]);
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
            cfapi.deleteServicePlanVisibility(planVisibilityGuids[index]);
            cfapi.deleteSpace(spaceIds[index][0]);
        }

        deleteServiceBroker();
    }
    
    public void deleteServiceBroker() {
        if (serviceBrokerId != null && !brokerAlreadyCreated) {
            cfapi.deleteServiceBroker(serviceBrokerId);
        }
    }
}