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
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

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
        Boolean fakeFlag = true;
        orgInfo = null; 
        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();
        fakeRequest.setOrganizationId(fakeOrgID);
        fakeResponseJson.setParameter("OrganizationId");        
        GetOrganizationResponse responseOrg = 
                GetOrganizationResponse.builder().metadata(Metadata.builder().id(fakeOrgID).build()).build(); 

        when(cloudFoundryApi.getOrganizationDetails(fakeOrgID)).thenReturn(responseOrg);
        assertEquals("enrolledOrganizationInValidInput",cloudFoundryApi.getOrganizationDetails(fakeOrgID),
                responseOrg);
        fakeResponseJson.setValue(fakeRequest.getOrganizationId());
        fakeResponseJson.setError(null);
        ArrayList<AutosleepConfigControllerResponse> fakeValidatedResponse = 
                new ArrayList<AutosleepConfigControllerResponse>();
        fakeValidatedResponse.add(fakeResponseJson);

        fakeRequest.setIdleDuration("PT1M");
        fakeResponseJson.setParameter("IdleDuration");
        fakeResponseJson.setValue(fakeRequest.getIdleDuration());
        fakeValidatedResponse.add(fakeResponseJson);

        when(fakeUtils.validateRequestBody(fakeRequest)).thenReturn(fakeValidatedResponse);
        assertEquals("validateRequest",fakeUtils.validateRequestBody(fakeRequest),
                fakeValidatedResponse);

        for (AutosleepConfigControllerResponse item:fakeValidatedResponse) {
            if (item.getError() != null) {
                fakeFlag = false;
            }
        }

        assertEquals("NoInvalidInputForUpdate", fakeFlag, true);

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
        Boolean fakeFlag = true;
        orgInfo = null; 

        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();

        fakeRequest.setOrganizationId(fakeOrgID);
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

        when(fakeUtils.validateRequestBody(fakeRequest)).thenReturn(fakeValidatedResponse);
        assertEquals("validateRequest",fakeUtils.validateRequestBody(fakeRequest),
                fakeValidatedResponse);

        for (AutosleepConfigControllerResponse item:fakeValidatedResponse) {
            if (item.getError() != null) {
                fakeFlag = false;
            }
        }

        assertEquals("NoInvalidInputForCreate", fakeFlag, true);

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

        fakeRequest.setOrganizationId(fakeOrgID);
        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();
        fakeResponseJson.setParameter("OrganizationId");        
        GetOrganizationResponse responseOrg = null;
        assertEquals("InvalidOrganiationId",responseOrg, null);
        fakeResponseJson.setValue(null);
        fakeResponseJson.setError("Bad Request:Invalid OrganizationId");
        ArrayList<AutosleepConfigControllerResponse> fakeValidatedResponse = 
                new ArrayList<AutosleepConfigControllerResponse>();
        fakeValidatedResponse.add(fakeResponseJson);

        orgInfo = null;
        ResponseEntity<ArrayList<AutosleepConfigControllerResponse>> fakeValidatedRes = new ResponseEntity
                <ArrayList<AutosleepConfigControllerResponse>>(fakeValidatedResponse,HttpStatus.BAD_REQUEST);

        when(autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID)).thenReturn(fakeValidatedRes);
        assertEquals("enrolledOrganizationCreate",
                autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID), fakeValidatedRes);
    }


    @Test
    public void test_enrollOrganization_inValidParameters() throws CloudFoundryException {

        fakeRequest = new AutosleepConfigControllerRequest();
        Boolean fakeFlag = true;
        orgInfo = null; 

        fakeRequest.setOrganizationId(fakeOrgID);
        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();
        fakeResponseJson.setParameter("OrganizationId");
        fakeResponseJson.setValue("fakeORgID");
        ArrayList<AutosleepConfigControllerResponse> fakeValidatedResponse = 
                new ArrayList<AutosleepConfigControllerResponse>();
        fakeValidatedResponse.add(fakeResponseJson);

        fakeRequest.setIdleDuration("fake time");
        fakeResponseJson.setParameter("idle-duration");
        fakeResponseJson.setError(Config.ServiceInstanceParameters.IDLE_DURATION
                + " param badly formatted (ISO-8601). Example: \"PT15M\" for 15mn");
        fakeValidatedResponse.add(fakeResponseJson);    

        when(fakeUtils.validateRequestBody(fakeRequest)).thenReturn(fakeValidatedResponse);
        assertEquals("validateRequest",fakeUtils.validateRequestBody(fakeRequest),
                fakeValidatedResponse);

        ArrayList<AutosleepConfigControllerResponse> fakeRemoveParams = 
                new ArrayList<AutosleepConfigControllerResponse>();
        for (AutosleepConfigControllerResponse item:fakeValidatedResponse) {
            if (item.getError() != null) {
                fakeFlag = false;
            } else {
                fakeRemoveParams.add(item);
            }
        }

        assertEquals("InvalidInput",fakeFlag,false);
        orgInfo = null; 

        for (AutosleepConfigControllerResponse item:fakeRemoveParams) {
            fakeValidatedResponse.remove(item);
        }

        ResponseEntity<ArrayList<AutosleepConfigControllerResponse>> fakeValidatedRes = new ResponseEntity
                <ArrayList<AutosleepConfigControllerResponse>>(fakeValidatedResponse,HttpStatus.BAD_REQUEST);

        when(autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID)).thenReturn(fakeValidatedRes);
        assertEquals("enrolledOrganizationCreate",
                autosleepConfigController.enrollOrganization(fakeRequest,fakeOrgID), fakeValidatedRes);
    }

    @Test
    public void test_populateOrgObj() {
        fakeRequest = new AutosleepConfigControllerRequest();

        fakeRequest.setOrganizationId(fakeOrgID);
        fakeRequest.setIdleDuration("PT1S");

        orgInfo = EnrolledOrganizationConfig.builder()
                .organizationId(fakeRequest.getOrganizationId())
                .idleDuration(fakeRequest.getIdleDuration())
                .build();
        orgInfo.setOrganizationId(fakeRequest.getOrganizationId());
        orgInfo.setIdleDuration(fakeRequest.getOrganizationId());

        when(autosleepConfigController.populateOrgObj(fakeRequest, orgInfo)).thenReturn(orgInfo);
        assertEquals("enrolledOrganizationPopulateOrgObj",
                autosleepConfigController.populateOrgObj(fakeRequest,orgInfo), orgInfo);       

    }
}
