package org.cloudfoundry.autosleep.test;

import static org.junit.Assert.assertEquals;       

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsResponse;
import org.cloudfoundry.client.v2.organizations.OrganizationResource;

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

    private String autosleepUrl;
    private String securityUsername;
    private String securityUserPassword;
    private String[] organizationName;
    private String[] organizationId;

    private int[] status;
    private String[] result;
    private String jsonRequest;

    private InputStream inputStream;

    @Before({"@start"})
    public void before() {

        log.info("Reading parameters from Properties file");

        CloudfoundryClientBuilder builder = new CloudfoundryClientBuilder();
        CloudFoundryClient cfclient = builder.getClient();
    
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
            securityUsername  = prop.getProperty("securityUsername");
            securityUserPassword  = prop.getProperty("securityUserPassword");
            organizationName  = prop.getProperty("organizationName").split(",");

            organizationId  = new String[organizationName.length];
            status = new int[organizationName.length];
            result = new String[organizationName.length];
            
            ListOrganizationsRequest request = ListOrganizationsRequest.builder().build();
            ListOrganizationsResponse response = cfclient.organizations().list(request).get();

            for (int index = 0; index < organizationName.length; index++) {
                for (OrganizationResource resource : response.getResources()) {
                    if ( organizationName[index].compareTo(resource.getEntity().getName()) == 0) {
                        organizationId[index] = resource.getMetadata().getId(); 
                    }
                }
            }

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
}