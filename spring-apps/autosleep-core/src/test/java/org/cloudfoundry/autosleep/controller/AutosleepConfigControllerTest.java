package org.cloudfoundry.autosleep.controller;

import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApiService;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.EnrolledOrganizationConfigRepository;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerRequest;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerResponse;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerUtils;
import org.cloudfoundry.client.v2.Resource.Metadata;
import org.cloudfoundry.client.v2.organizations.GetOrganizationResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
//import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;

@RunWith(MockitoJUnitRunner.class)
public class AutosleepConfigControllerTest { 

    private static final String fakeOrgID = "fakeId";

    @Mock
    private EnrolledOrganizationConfig orgInfo;  

    @Mock
    private AutosleepConfigControllerRequest fakeRequest;

    @Mock
    private AutosleepConfigControllerResponse fakeResponse;

    @Mock
    private EnrolledOrganizationConfigRepository orgRepository;

    @Mock
    AutosleepConfigControllerUtils fakeUtils;

    @Mock
    private CloudFoundryApiService cloudFoundryApi;

    @Mock
    AutosleepConfigController autosleepConfigController;

    @Test
    public void test_fetchEnrolledOrganization_when_found() throws CloudFoundryException {

        orgInfo = EnrolledOrganizationConfig.builder().organizationId(fakeOrgID).build();

        when(orgRepository.findOne(fakeOrgID)).thenReturn(orgInfo);
        assertEquals("findOneAssert",orgRepository.findOne(fakeOrgID),orgInfo);

        ResponseEntity<EnrolledOrganizationConfig>  response = 
                new ResponseEntity<EnrolledOrganizationConfig>(orgInfo,HttpStatus.OK);
        when(autosleepConfigController.fetchEnrolledOrganization(fakeOrgID)).thenReturn(response);
        assertEquals("fetchEnrolledOrganization_Found",autosleepConfigController.fetchEnrolledOrganization(fakeOrgID), 
                response); 
    }

    @Test
    public void test_fetchEnrolledOrganization_when_not_found() throws CloudFoundryException {

        orgInfo = null;

        when(orgRepository.findOne(fakeOrgID)).thenReturn(orgInfo);
        assertEquals("findOneAssert",orgRepository.findOne(fakeOrgID),orgInfo);

        ResponseEntity<EnrolledOrganizationConfig>  response = 
                new ResponseEntity<EnrolledOrganizationConfig>(orgInfo,HttpStatus.NOT_FOUND);
        when(autosleepConfigController.fetchEnrolledOrganization(fakeOrgID)).thenReturn(response);
        assertEquals("fetchEnrolledOrganization_NotFound",
                autosleepConfigController.fetchEnrolledOrganization(fakeOrgID), response);
    }

    @Test
    public void test_enrollOrganization_update() throws CloudFoundryException { 

        fakeRequest = new AutosleepConfigControllerRequest();  
        orgInfo = null; 
        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse(); 
        fakeResponseJson.setParameter("OrganizationId");        
        GetOrganizationResponse responseOrg = 
                GetOrganizationResponse.builder().metadata(Metadata.builder().id(fakeOrgID).build()).build(); 

        when(cloudFoundryApi.getOrganizationDetails(fakeOrgID)).thenReturn(responseOrg);
        assertEquals("enrolledOrganizationInValidInput",cloudFoundryApi.getOrganizationDetails(fakeOrgID),
                responseOrg);
        fakeResponseJson.setValue(fakeOrgID);
        fakeResponseJson.setError(null);
        ArrayList<AutosleepConfigControllerResponse> fakeValidatedResponse = 
                new ArrayList<AutosleepConfigControllerResponse>();
        fakeValidatedResponse.add(fakeResponseJson);

        fakeRequest.setIdleDuration("PT1M");
        fakeResponseJson.setParameter("IdleDuration");
        fakeResponseJson.setValue(fakeRequest.getIdleDuration());
        fakeValidatedResponse.add(fakeResponseJson);

        when(fakeUtils.validateRequestBody(fakeRequest)).thenReturn(fakeResponseJson);
        assertEquals("validateRequest",fakeUtils.validateRequestBody(fakeRequest),
                fakeResponseJson);

        assertEquals("NoInvalidInputForUpdate", fakeResponseJson.getError(), null);

        orgInfo = EnrolledOrganizationConfig.builder().organizationId(fakeOrgID).build();

        when(orgRepository.findOne(fakeOrgID)).thenReturn(orgInfo);
        assertEquals("findOneAssert",orgRepository.findOne(fakeOrgID),orgInfo);

        EnrolledOrganizationConfig orgInfoFake = null;
        when(orgRepository.save(orgInfoFake)).thenReturn(orgInfo);
        assertEquals("saveAssert",orgRepository.save(orgInfoFake),orgInfo);

        ResponseEntity<ArrayList<AutosleepConfigControllerResponse>> fakeValidatedRes = new ResponseEntity
                <ArrayList<AutosleepConfigControllerResponse>>(fakeValidatedResponse,HttpStatus.OK);

        when(autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID)).thenReturn(fakeValidatedRes);
        assertEquals("enrolledOrganizationCreate",autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID),
                fakeValidatedRes);
    }

    @Test
    public void test_enrollOrganization_created() throws CloudFoundryException { 

        fakeRequest = new AutosleepConfigControllerRequest();
        orgInfo = null; 

        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();
        fakeResponseJson.setParameter("OrganizationId");
        GetOrganizationResponse responseOrg = 
                GetOrganizationResponse.builder().metadata(Metadata.builder().id(fakeOrgID).build()).build(); 

        when(cloudFoundryApi.getOrganizationDetails(fakeOrgID)).thenReturn(responseOrg);
        assertEquals("enrolledOrganizationInValidInput",cloudFoundryApi.getOrganizationDetails(fakeOrgID),
                responseOrg);
        fakeResponseJson.setValue(fakeOrgID);
        ArrayList<AutosleepConfigControllerResponse> fakeValidatedResponse = 
                new ArrayList<AutosleepConfigControllerResponse>();
        fakeValidatedResponse.add(fakeResponseJson);

        fakeRequest.setIdleDuration("PT1M");
        fakeResponseJson.setParameter("idle-duration");
        fakeResponseJson.setValue("PT1M");
        fakeValidatedResponse.add(fakeResponseJson);

        when(fakeUtils.validateRequestBody(fakeRequest)).thenReturn(fakeResponseJson);
        assertEquals("validateRequest",fakeUtils.validateRequestBody(fakeRequest),
                fakeResponseJson);
        assertEquals("NoInvalidInputForCreate", fakeResponseJson.getError(), null);

        orgInfo = EnrolledOrganizationConfig.builder().organizationId(fakeOrgID).build();
        when(orgRepository.findOne(fakeOrgID)).thenReturn(orgInfo);
        assertEquals("findOneAssert",orgRepository.findOne(fakeOrgID),orgInfo);

        EnrolledOrganizationConfig orgInfoFake = null;
        when(orgRepository.save(orgInfoFake)).thenReturn(orgInfo);
        assertEquals("saveAssert",orgRepository.save(orgInfoFake),orgInfo);

        ResponseEntity<ArrayList<AutosleepConfigControllerResponse>> fakeValidatedRes = new ResponseEntity
                <ArrayList<AutosleepConfigControllerResponse>>(fakeValidatedResponse,HttpStatus.CREATED);

        when(autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID)).thenReturn(fakeValidatedRes);
        assertEquals("enrolledOrganizationCreate",autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID),
                fakeValidatedRes);
    }

    @Test
    public void test_enrollOrganization_inValidOrganizationId() throws CloudFoundryException {

        fakeRequest = new AutosleepConfigControllerRequest();
        orgInfo = null; 

        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();
        fakeResponseJson.setParameter("OrganizationId");        
        GetOrganizationResponse responseOrg = null;
        assertEquals("InvalidOrganiationId",responseOrg, null);
        fakeResponseJson.setValue(null);
        fakeResponseJson.setError("Bad Request:Invalid OrganizationId");
        ArrayList<AutosleepConfigControllerResponse> fakeValidatedResponse = 
                new ArrayList<AutosleepConfigControllerResponse>();
        fakeValidatedResponse.add(fakeResponseJson);

        ResponseEntity<ArrayList<AutosleepConfigControllerResponse>> fakeValidatedRes = new ResponseEntity
                <ArrayList<AutosleepConfigControllerResponse>>(fakeValidatedResponse,HttpStatus.BAD_REQUEST);

        when(autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID)).thenReturn(fakeValidatedRes);
        assertEquals("enrolledOrganizationCreate",
                autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID), fakeValidatedRes);
    }

    @Test
    public void test_enrollOrganization_inValidParameters() throws CloudFoundryException {

        fakeRequest = new AutosleepConfigControllerRequest();
        orgInfo = null;

        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();

        fakeRequest.setIdleDuration("fake time");
        fakeResponseJson.setParameter("idle-duration");
        fakeResponseJson.setValue(null);
        fakeResponseJson.setError(Config.ServiceInstanceParameters.IDLE_DURATION
                + " param badly formatted (ISO-8601). Example: \"PT15M\" for 15mn");

        when(fakeUtils.validateRequestBody(fakeRequest)).thenReturn(fakeResponseJson);

        assertEquals("validateRequest",fakeUtils.validateRequestBody(fakeRequest),
                fakeResponseJson);
        ArrayList<AutosleepConfigControllerResponse> fakeValidatedResponse = 
                new ArrayList<AutosleepConfigControllerResponse>();
        fakeValidatedResponse.add(fakeResponseJson);   
        assertEquals("InvalidInput",fakeResponseJson.getValue(),null);

        ResponseEntity<ArrayList<AutosleepConfigControllerResponse>> fakeValidatedRes = new ResponseEntity
                <ArrayList<AutosleepConfigControllerResponse>>(fakeValidatedResponse,HttpStatus.BAD_REQUEST);

        when(autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID)).thenReturn(fakeValidatedRes);
        assertEquals("enrolledOrganizationCreate",
                autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID), fakeValidatedRes);
    }    
    
    @Test
    public void test_deleteEnrolledOrganization_when_found() throws CloudFoundryException {

        orgInfo = EnrolledOrganizationConfig.builder().organizationId(fakeOrgID).build();

        when(orgRepository.findOne(fakeOrgID)).thenReturn(orgInfo);
        assertEquals("findOneAssert",orgRepository.findOne(fakeOrgID),orgInfo);

        ResponseEntity<Void> response = ResponseEntity.status(HttpStatus.OK).build();

        doReturn(response).when(autosleepConfigController).deleteEnrolledOrganization(fakeOrgID);
        assertEquals("deleteEnrolledOrganization_Found",autosleepConfigController.deleteEnrolledOrganization(fakeOrgID),
                response); 
    }

    @Test
    public void test_deleteEnrolledOrganization_when_not_found() throws CloudFoundryException {

        orgInfo = null;

        when(orgRepository.findOne(fakeOrgID)).thenReturn(orgInfo);
        assertEquals("findOneAssert",orgRepository.findOne(fakeOrgID),orgInfo);

        ResponseEntity<Void> response = ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        doReturn(response).when(autosleepConfigController).deleteEnrolledOrganization(fakeOrgID);
        assertEquals("deleteEnrolledOrganization_Not_Found",
                autosleepConfigController.deleteEnrolledOrganization(fakeOrgID), response);
    }
    
}
