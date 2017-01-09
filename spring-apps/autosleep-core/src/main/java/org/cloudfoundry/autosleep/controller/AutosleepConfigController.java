
package org.cloudfoundry.autosleep.controller;

import lombok.extern.slf4j.Slf4j; 

import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApiService;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.EnrolledOrganizationConfigRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerRequest;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerResponse;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerUtils;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller
@RequestMapping(Config.Path.CONTROLLER_BASE_PATH)
public class AutosleepConfigController {

    @Autowired
    private EnrolledOrganizationConfigRepository orgRepository;

    @Autowired
    private SpaceEnrollerConfigRepository spaceEnrollerConfigRepository;

    @Autowired
    private AutosleepConfigControllerUtils utils;

    @Autowired 
    private CloudFoundryApiService cfApi;

    @RequestMapping(value = "enrolled-orgs/{organizationId}", 
            method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ArrayList<AutosleepConfigControllerResponse>> 
    enrollOrganization(@RequestBody AutosleepConfigControllerRequest request, @PathVariable("organizationId") 
    String organizationId) throws CloudFoundryException {

        EnrolledOrganizationConfig orgInfo = EnrolledOrganizationConfig.builder().build();
        if (orgInfo == null) {
            System.out.println("************** ORG INFO IS NULL");
        }
        HttpStatus status = null;
        HttpHeaders responseHeaders = null;
        log.debug("enrollOrganization - " + organizationId);
        ArrayList<AutosleepConfigControllerResponse> validatedRequest = 
                new ArrayList<AutosleepConfigControllerResponse>();
        AutosleepConfigControllerResponse responseJson = new AutosleepConfigControllerResponse();

        try {
            GetOrganizationResponse getOrgResponse = 
                    cfApi.getOrganizationDetails(organizationId);            
            if (getOrgResponse != null) {
                if (request.getIdleDuration() != null) {
                    responseJson = utils.validateRequestBody(request);
                    validatedRequest.add(responseJson);
                }
                if (responseJson.getError() == null) {
                    responseJson = new AutosleepConfigControllerResponse();
                    responseJson.setParameter("organizationId");
                    responseJson.setValue(organizationId); 
                    responseJson.setError(null);
                    validatedRequest.add(0, responseJson);
                    orgInfo.setOrganizationId(organizationId);
                    if (request.getIdleDuration() != null) {
                        orgInfo.setIdleDuration(Duration.parse(request.getIdleDuration())); 
                    }
                    EnrolledOrganizationConfig existingOrg  = orgRepository.findOne(organizationId);
                    orgRepository.save(orgInfo);                         
                    if (existingOrg == null) { 
                        responseHeaders = new HttpHeaders();
                        responseHeaders.add("Location", "/v1/enrolled-orgs/" + organizationId);
                        utils.registerOrganization(orgInfo);
                        log.info("Organization " + organizationId + " is enrolled with Autosleep");
                        status = HttpStatus.CREATED;
                    } else {
                        status = HttpStatus.OK;
                        utils.updateOrganization(orgInfo);
                        log.info("Updated already enrolled organization : " + organizationId);
                    }

                } else {
                    status = HttpStatus.BAD_REQUEST; 
                    log.error("Bad Request:Invalid Parameters");               
                }
            }
        } catch (org.cloudfoundry.client.v2.CloudFoundryException ce) {
            if (ce.getCode() == 30003) {                
                status = HttpStatus.BAD_REQUEST;                 
                log.error("Bad Request:Invalid OrganizationId :" + organizationId);
                responseJson.setParameter(organizationId); 
                responseJson.setValue(null); 
                responseJson.setError(ce.getMessage());
                validatedRequest.add(0, responseJson);
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                log.error("Internal Server Error.Please check logs for more details", ce);
                throw new CloudFoundryException(ce);
            }

        } catch (RuntimeException re) {       
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            if (responseHeaders != null) {
                responseHeaders = null; 
            }
            log.error("Internal Server Error.Please check logs for more details", re);
            throw new CloudFoundryException(re);
        }
        return new ResponseEntity<ArrayList<AutosleepConfigControllerResponse>>(validatedRequest,
                responseHeaders,status); 
    }

    @RequestMapping(value = "enrolled-orgs/{organizationId}", 
            method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AutosleepConfigControllerRequest> 
    fetchEnrolledOrganization(@PathVariable("organizationId") String organizationId) 
            throws CloudFoundryException {

        // System.out.println("Listed ORgs are :: "+ cfApi.listAllOrganizations());
        AutosleepConfigControllerRequest responseObject = null; 
        EnrolledOrganizationConfig orgInfo = null;        
        HttpStatus status = null;

        try {
            orgInfo = orgRepository.findOne(organizationId);
            if (orgInfo != null) {
                responseObject = new AutosleepConfigControllerRequest();
                responseObject.setOrganizationId(organizationId);
                if(orgInfo.getIdleDuration() != null) {
                    responseObject.setIdleDuration(orgInfo.getIdleDuration().toString());
                }
                log.info("Information for organizationId : " + organizationId + " is retrieved");                
                status = HttpStatus.OK;
            } else {
                status = HttpStatus.NOT_FOUND;            
                log.error("OrganizationId : " + organizationId + " is not enrolled with autosleep");
            }

        } catch (RuntimeException re) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            log.error("Internal Server Error.Please check logs for more details");

            throw new CloudFoundryException(re);
        }
        return new ResponseEntity<AutosleepConfigControllerRequest>(responseObject, status);
    }

    @ExceptionHandler(CloudFoundryException.class)
    @ResponseBody
    public ResponseEntity<String> handleException(CloudFoundryException ce) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + ce.getMessage());
    }

    @RequestMapping(value = "enrolled-orgs/{organizationId}", 
            method = RequestMethod.DELETE)
    public ResponseEntity<Void>
    deleteEnrolledOrganization(@PathVariable("organizationId") String organizationId) 
            throws CloudFoundryException {
        EnrolledOrganizationConfig orgInfo = null;
        HttpStatus status = null;

        try {
            orgInfo = orgRepository.findOne(organizationId);
            if (orgInfo != null) {                                 
                status = HttpStatus.OK;                
                //STOP THE ENROLLER THREAD
                utils.stopOrgEnrollerOnDelete(organizationId);
                List<SpaceEnrollerConfig> serviceInstances = spaceEnrollerConfigRepository.listByOrganizationId(organizationId);

               // utils.deleteServiceInstances(serviceInstances);
                serviceInstances.forEach(serviceInstance-> utils.deleteServiceInstance(serviceInstance.getId()));
                orgRepository.delete(orgInfo);
                log.info("Organization Id : "  + organizationId  + " is unenrolled from autosleep");
            } else {
                status = HttpStatus.NOT_FOUND;
                log.error("Organization Id : "  + organizationId  + " is not enrolled with autosleep");
            }
        } catch (RuntimeException re) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            log.error("Internal Server Error.Please check logs for more details");
            throw new CloudFoundryException(re);
        }

        return ResponseEntity.status(status).build();
    }

}

