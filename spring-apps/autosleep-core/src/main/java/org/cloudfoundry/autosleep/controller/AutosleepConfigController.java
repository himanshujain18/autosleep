
package org.cloudfoundry.autosleep.controller;

import lombok.extern.slf4j.Slf4j;

import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApi;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.EnrolledOrganizationConfigRepository;
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

import java.util.ArrayList;

@Slf4j
@Controller
@RequestMapping(Config.Path.CONTROLLER_BASE_PATH)
public class AutosleepConfigController {

    @Autowired
    private EnrolledOrganizationConfigRepository orgRepository;

    @Autowired
    AutosleepConfigControllerUtils utils;

    @Autowired 
    private CloudFoundryApi cfApi;

    @RequestMapping(value = "enrolled-orgs/{organizationId}", 
            method = RequestMethod.PUT, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ArrayList<AutosleepConfigControllerResponse>> 
            enrollOrganization(@RequestBody AutosleepConfigControllerRequest request, @PathVariable("organizationId") 
            String organizationId) throws CloudFoundryException {

        EnrolledOrganizationConfig orgInfo = EnrolledOrganizationConfig.builder().build();
        request.setOrganizationId(organizationId);
        HttpStatus status = null;
        HttpHeaders responseHeaders = null;
        Boolean flag = true;
        log.debug("enrollOrganization - " + organizationId);
        ArrayList<AutosleepConfigControllerResponse> validatedRequest = 
                new ArrayList<AutosleepConfigControllerResponse>();
        AutosleepConfigControllerResponse responseJson = new AutosleepConfigControllerResponse();
        try {
            responseJson.setParameter("organizationId");
            GetOrganizationResponse getOrgResponse = 
                    cfApi.getOrganizationDetails(request.getOrganizationId());            
            if (getOrgResponse != null) {
                validatedRequest = utils.validateRequestBody(request);
                responseJson.setValue(request.getOrganizationId()); 
                responseJson.setError(null);
                validatedRequest.add(0, responseJson);
                ArrayList<AutosleepConfigControllerResponse> removeParams = 
                        new ArrayList<AutosleepConfigControllerResponse>();
                for (AutosleepConfigControllerResponse item:validatedRequest) {
                    if (item.getError() != null) {
                        flag = false;
                    } else {
                        removeParams.add(item);
                    }
                }              
                if (flag) {
                    EnrolledOrganizationConfig existingOrg  = orgRepository.findOne(organizationId);    
                    orgInfo = populateOrgObj(request,orgInfo);
                    orgRepository.save(orgInfo);                         
                    if (existingOrg == null) { 
                        responseHeaders = new HttpHeaders();
                        responseHeaders.add("Location", "/v1/enrolled-orgs/" + organizationId);
                        log.info("Organization " + organizationId + " is enrolled with Autosleep");
                        status = HttpStatus.CREATED;
                    } else {
                        status = HttpStatus.OK;
                        log.info("Updated already enrolled organization : " + organizationId);
                    }
                } else {
                    status = HttpStatus.BAD_REQUEST; 
                    log.error("Bad Request:Invalid Parameters");    
                    for (AutosleepConfigControllerResponse item:removeParams) {
                        validatedRequest.remove(item);
                    }            
                }
            }
        } catch (org.cloudfoundry.client.v2.CloudFoundryException ce) {
            if (ce.getCode() == 30003) {                
                status = HttpStatus.BAD_REQUEST;                 
                log.error("Bad Request:Invalid OrganizationId :" + organizationId);
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
    public ResponseEntity<EnrolledOrganizationConfig> 
            fetchEnrolledOrganization(@PathVariable("organizationId") String organizationId) 
            throws CloudFoundryException {
        EnrolledOrganizationConfig orgInfo = null;        
        HttpStatus status = null;

        try {
            orgInfo = orgRepository.findOne(organizationId);
            if (orgInfo != null) { 
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
        return new ResponseEntity<EnrolledOrganizationConfig>(orgInfo, status);
    }

    @ExceptionHandler(CloudFoundryException.class)
    @ResponseBody
    public ResponseEntity<String> handleException(CloudFoundryException ce) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + ce.getMessage());
    }

    EnrolledOrganizationConfig populateOrgObj(AutosleepConfigControllerRequest request, 
            EnrolledOrganizationConfig orgInfo) {
        orgInfo.setOrganizationId(request.getOrganizationId());
        orgInfo.setIdleDuration(request.getIdleDuration());
        return orgInfo;
    } 
}

