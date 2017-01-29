package org.cloudfoundry.autosleep.util;

import static org.mockito.Mockito.when; 

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.cloudfoundry.autosleep.util.TestUtils.verifyThrown;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApiService;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.AutoServiceInstance;
import org.cloudfoundry.autosleep.access.dao.model.Binding;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledSpaceConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.AutoServiceInstanceRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.BindingRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.worker.OrganizationEnroller;
import org.cloudfoundry.autosleep.worker.WorkerManager;
import org.cloudfoundry.autosleep.worker.scheduling.Clock;
import org.cloudfoundry.client.v2.Resource.Metadata;
import org.cloudfoundry.client.v2.organizations.ListOrganizationSpacesResponse;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceResponse;
import org.cloudfoundry.client.v2.spaces.SpaceResource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AutosleepConfigControllerUtilsTest {  

    private static final Duration INTERVAL = Duration.ofMillis(300);

    private static final Duration ZERO_DELAY = Duration.ofMillis(0);

    private static final Duration UPDATED_INTERVAL = Duration.ofMillis(180);

    private static final String ORGANIZATION_ID = UUID.randomUUID().toString();

    private static final String SPACE_ID = UUID.randomUUID().toString();

    private static final String SERVICE_INSTANCE_ID = UUID.randomUUID().toString();

    @Mock
    private AutosleepConfigControllerRequest fakeRequest;    

    @Spy
    @InjectMocks
    AutosleepConfigControllerUtils utils;

    @Mock
    private AutoServiceInstanceRepository autoServiceInstanceRepository;

    @Mock
    private BindingRepository bindingRepository;

    @Mock
    private CloudFoundryApiService cloudFoundryApi;

    @Mock
    private EnrolledOrganizationConfig enrolledOrganizationConfig; 

    @Mock
    private EnrolledSpaceConfig enrolledSpaceConfig;

    @Mock
    private SpaceEnrollerConfig spaceEnrollerConfig;

    @Mock
    private SpaceEnrollerConfigRepository spaceEnrollerConfigRepo;

    @Mock
    private WorkerManager workerMangager;

    @Mock
    private Clock clock;

    private List<String> bindingIds = Arrays.asList(UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), UUID.randomUUID().toString());

    private List<String> mockSpaceIds = Arrays.asList("spaceId1", "spaceId2");

    private List<String> autoServiceInstanceIDs = Arrays.asList("instanceId1","instanceId2");

    @Test
    public void test_validateRequestBody_inValid() throws CloudFoundryException {    

        fakeRequest = new AutosleepConfigControllerRequest();        
        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();        

        fakeRequest.setIdleDuration("fake time");
        fakeResponseJson.setParameter("idle-duration");
        fakeResponseJson.setError(Config.ServiceInstanceParameters.IDLE_DURATION
                + " param badly formatted (ISO-8601). Example: \"PT15M\" for 15mn");

        when(utils.validateRequestBody(fakeRequest)).thenReturn(fakeResponseJson);
        assertEquals("validateRequest",utils.validateRequestBody(fakeRequest), fakeResponseJson);

    }

    @Test
    public void test_validateRequestBody_valid() throws CloudFoundryException  {

        fakeRequest = new AutosleepConfigControllerRequest();
        AutosleepConfigControllerResponse fakeResponseJson = new AutosleepConfigControllerResponse();        

        fakeRequest.setIdleDuration("PT1M");
        fakeResponseJson.setParameter("idle-duration");
        fakeResponseJson.setValue(fakeRequest.getIdleDuration());

        when(utils.validateRequestBody(fakeRequest)).thenReturn(fakeResponseJson);
        assertEquals("validateRequest",utils.validateRequestBody(fakeRequest), fakeResponseJson);

    }

    @Test
    public void test_checkParameters_matched() {

        when(enrolledOrganizationConfig.getIdleDuration()).thenReturn(INTERVAL);
        when(spaceEnrollerConfig.getIdleDuration()).thenReturn(INTERVAL);
        boolean testFlag = utils.checkParameters(spaceEnrollerConfig, enrolledOrganizationConfig);
        assertTrue(testFlag == true);  
    }

    @Test
    public void test_checkParameters_not_matched() {

        when(enrolledOrganizationConfig.getIdleDuration()).thenReturn(INTERVAL);
        when(spaceEnrollerConfig.getIdleDuration()).thenReturn(UPDATED_INTERVAL);
        boolean testFlag = utils.checkParameters(spaceEnrollerConfig, enrolledOrganizationConfig);
        assertTrue(testFlag == false);  
    }

    @Test
    public void test_createNewServiceInstance_when_created() throws CloudFoundryException {

        EnrolledSpaceConfig enrolledSpace = mock(EnrolledSpaceConfig.class);
        when(enrolledSpace.getSpaceId()).thenReturn(SPACE_ID);
        when(enrolledSpace.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        CreateServiceInstanceResponse createServiceInstanceResponse = CreateServiceInstanceResponse.builder()
                .metadata(Metadata.builder().id(SERVICE_INSTANCE_ID).build()).build();
        when(cloudFoundryApi.createServiceInstance(enrolledSpace)).thenReturn(createServiceInstanceResponse);

        AutoServiceInstance autoServiceInst = AutoServiceInstance.builder()
                .organizationId(ORGANIZATION_ID)
                .spaceId(SPACE_ID)
                .serviceInstanceId(SERVICE_INSTANCE_ID)
                .build();

        utils.createNewServiceInstance(enrolledSpace);
        verify(autoServiceInstanceRepository, times(1)).save(autoServiceInst);

    }

    @Test
    public void test_createNewServiceInstance_when_not_created() throws CloudFoundryException {

        EnrolledSpaceConfig enrolledSpace = mock(EnrolledSpaceConfig.class);
        when(enrolledSpace.getSpaceId()).thenReturn(SPACE_ID);
        when(enrolledSpace.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(cloudFoundryApi.createServiceInstance(any(EnrolledSpaceConfig.class)))
                .thenThrow(new RuntimeException());

        verifyThrown(() -> utils.createNewServiceInstance(enrolledSpace), Throwable.class);
    }

    @Test
    public void test_createNewServiceInstance_when_runtime_exception_is_thrown() throws RuntimeException {
        EnrolledSpaceConfig enrolledSpace = mock(EnrolledSpaceConfig.class);
        when(enrolledSpace.getSpaceId()).thenReturn(SPACE_ID);
        when(enrolledSpace.getOrganizationId()).thenReturn(ORGANIZATION_ID);

        try {
            when(cloudFoundryApi.createServiceInstance(enrolledSpace)).thenReturn(null);
        } catch (CloudFoundryException e) {
            e.printStackTrace();
        }

        verifyThrown(() -> utils.createNewServiceInstance(enrolledSpace), Throwable.class);
    }

    @Test
    public void test_createNewServiceInstances_creates_instances_in_all_spaces() throws CloudFoundryException {

        when(enrolledOrganizationConfig.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(enrolledOrganizationConfig.getIdleDuration()).thenReturn(INTERVAL);

        doNothing().when(utils).createNewServiceInstance(any(EnrolledSpaceConfig.class));
        utils.createNewServiceInstances(mockSpaceIds, enrolledOrganizationConfig);

        verify(utils, times(mockSpaceIds.size())).createNewServiceInstance(any(EnrolledSpaceConfig.class));
    }

    @Test
    public void test_createNewServiceInstances_not_successful() throws CloudFoundryException {
        boolean thrown = false;
        when(enrolledOrganizationConfig.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(enrolledOrganizationConfig.getIdleDuration()).thenReturn(INTERVAL);
        try {
            utils.createNewServiceInstances(mockSpaceIds, enrolledOrganizationConfig);
        } catch (CloudFoundryException ce) {
            thrown = true;
        }
        assertTrue(thrown);
    } 


    @Test
    public void test_deleteServiceInstance_successfully() throws CloudFoundryException {

        utils.deleteServiceInstance(SERVICE_INSTANCE_ID);
        verify(utils, times(1)).deleteServiceInstanceBinding(SERVICE_INSTANCE_ID);
        verify(cloudFoundryApi, times(1)).deleteServiceInstance(SERVICE_INSTANCE_ID);
        verify(autoServiceInstanceRepository, times(1)).delete(SERVICE_INSTANCE_ID);
    }

    @Test
    public void test_deleteServiceInstance_throws_exception() throws CloudFoundryException {
        Throwable cause =  
                new org.cloudfoundry.client.v2.CloudFoundryException(30003,
                        "CloudFoundryException" , "Instance cannot be deleted");
        doThrow(new CloudFoundryException(cause)).when(utils).deleteServiceInstanceBinding(SERVICE_INSTANCE_ID);
        verifyThrown(() -> utils.deleteServiceInstance(SERVICE_INSTANCE_ID), Throwable.class);
    }

    @Test
    public void test_deleteServiceInstanceBinding_successfully() throws CloudFoundryException {

        List<Binding> mockBindings = bindingIds.stream()
                .map(id -> BeanGenerator.createBinding(SERVICE_INSTANCE_ID, id, null))
                .collect(Collectors.toList());

        when(bindingRepository.findByServiceInstanceId(SERVICE_INSTANCE_ID)).thenReturn(mockBindings);
        utils.deleteServiceInstanceBinding(SERVICE_INSTANCE_ID);
        verify(cloudFoundryApi, times(mockBindings.size())).deleteServiceInstanceBinding(anyString());

    }

    @Test
    public void test_deleteServiceInstances_when_no_entry_found() throws CloudFoundryException {
        List<AutoServiceInstance> mockAutoServiceInstances = Arrays.asList();

        when(autoServiceInstanceRepository.findByOrganizationId(ORGANIZATION_ID))
                .thenReturn(mockAutoServiceInstances);
        utils.deleteServiceInstances(ORGANIZATION_ID);
    }

    @Test
    public void test_deleteServiceInstances_when_instances_are_present() throws CloudFoundryException {
        List<AutoServiceInstance> mockAutoServiceInstances = autoServiceInstanceIDs.stream()
                .map(id -> BeanGenerator
                        .createAutoServiceInstance(id, mockSpaceIds
                                .get(autoServiceInstanceIDs.indexOf(id)), ORGANIZATION_ID))
                .collect(Collectors.toList());

        when(autoServiceInstanceRepository.findByOrganizationId(ORGANIZATION_ID))
                .thenReturn(mockAutoServiceInstances);

        List<SpaceEnrollerConfig> mockEnrolledSpaces =  autoServiceInstanceIDs.stream()
                .map(id -> BeanGenerator
                        .createServiceInstance(id, mockSpaceIds
                                .get(autoServiceInstanceIDs.indexOf(id)), ORGANIZATION_ID))
                .collect(Collectors.toList());

        when(spaceEnrollerConfigRepo.listByIds(autoServiceInstanceIDs)).thenReturn(mockEnrolledSpaces);

        utils.deleteServiceInstances(ORGANIZATION_ID);

        verify(utils, times(mockEnrolledSpaces.size())).deleteServiceInstance(anyString());
    }

    @Test
    public void test_cfSpacesList_return_spacesList_for_valid_organization()
            throws CloudFoundryException {
        ListOrganizationSpacesResponse response = ListOrganizationSpacesResponse.builder()
                .resource(SpaceResource.builder()
                        .metadata(Metadata.builder().id("spaceId1").build())
                        .build())
                .totalResults(1)
                .build();
        when(cloudFoundryApi.listOrganizationSpaces(ORGANIZATION_ID)).thenReturn(response);
        List<String> result = utils.cfSpacesList(ORGANIZATION_ID);
        assertEquals(response.getTotalResults().intValue(), result.size());
    }

    @Test
    public void test_cfSpacesList_should_throw_exception_for_invalid_organization()
            throws CloudFoundryException {
        Throwable cause =  
                new org.cloudfoundry.client.v2.CloudFoundryException(30003, "CloudFoundryException" , "invalid org");
        when(cloudFoundryApi.listOrganizationSpaces(ORGANIZATION_ID))
                .thenThrow(new CloudFoundryException(cause));
        verifyThrown(() -> utils.cfSpacesList(ORGANIZATION_ID), Throwable.class);
    }


    @Test
    public void test_registerOrganization() {
        EnrolledOrganizationConfig orgInfo = BeanGenerator.createEnrolledOrganizationConfig(ORGANIZATION_ID);
        utils.registerOrganization(orgInfo);
        verify(workerMangager).setOrganizationObjects(eq(ORGANIZATION_ID), any(OrganizationEnroller.class));
        verify(clock).scheduleTask(eq(ORGANIZATION_ID), eq(ZERO_DELAY),
                any(OrganizationEnroller.class));
    }

    @Test
    public void test_registerOrganization_should_throw_runtimeException() {
        EnrolledOrganizationConfig orgInfo = null;
        verifyThrown(() -> utils.registerOrganization(orgInfo) , Throwable.class);
    }

    @Test
    public void test_updateOrganization_successful() {
        EnrolledOrganizationConfig orgInfo = mock(EnrolledOrganizationConfig.class);
        when(orgInfo.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        OrganizationEnroller enroller = mock(OrganizationEnroller.class);
        Map<String, OrganizationEnroller> orgObjects = new HashMap<>();
        orgObjects.put(ORGANIZATION_ID, enroller);
        when(workerMangager.getOrganizationObjects()).thenReturn(orgObjects);

        utils.updateOrganization(orgInfo);
        verify(enroller).callReschedule(orgInfo);
    }

    @Test
    public void test_updateOrganization_should_throw_runtimeException() {
        EnrolledOrganizationConfig orgInfo = null;
        verifyThrown(() -> utils.updateOrganization(orgInfo) , Throwable.class);
    }

    @Test
    public void test_stopOrgEnrollerOnDelete_successful() {
        OrganizationEnroller enroller = mock(OrganizationEnroller.class);
        Map<String, OrganizationEnroller> orgObjects = new HashMap<>();
        orgObjects.put(ORGANIZATION_ID, enroller);
        when(workerMangager.getOrganizationObjects()).thenReturn(orgObjects);

        utils.stopOrgEnrollerOnDelete(ORGANIZATION_ID);
        verify(enroller).killTask();
    }

    @Test
    public void test_stopOrgEnrollerOnDelete_should_throw_runtimeException() {
        Map<String, OrganizationEnroller> orgObjects = new HashMap<>();
        orgObjects.put(ORGANIZATION_ID, null);
        when(workerMangager.getOrganizationObjects()).thenReturn(orgObjects);

        verifyThrown(() -> utils.stopOrgEnrollerOnDelete(ORGANIZATION_ID), Throwable.class);
    }

    @Test
    public void test_updateServiceInstances_success() throws CloudFoundryException {

        SpaceEnrollerConfig mockSpaceConfig1 = BeanGenerator
                .createServiceInstance(autoServiceInstanceIDs.get(0), mockSpaceIds.get(0), ORGANIZATION_ID);
        SpaceEnrollerConfig mockSpaceConfig2 = BeanGenerator
                .createServiceInstance(autoServiceInstanceIDs.get(1), mockSpaceIds.get(1), ORGANIZATION_ID);

        Map<String, SpaceEnrollerConfig> mockExistingServiceInstances = new HashMap<>();
        mockExistingServiceInstances.put(mockSpaceIds.get(0), mockSpaceConfig1);
        mockExistingServiceInstances.put(mockSpaceIds.get(1), mockSpaceConfig2);

        when(enrolledOrganizationConfig.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(enrolledOrganizationConfig.getIdleDuration()).thenReturn(Duration.ofMillis(100));

        doReturn(false).when(utils)
                .checkParameters(mockSpaceConfig1,enrolledOrganizationConfig); 
        doReturn(false).when(utils)
                .checkParameters(mockSpaceConfig2,enrolledOrganizationConfig); 
        doNothing().when(utils).deleteServiceInstance(anyString());
        doNothing().when(utils).createNewServiceInstance(any(EnrolledSpaceConfig.class));
        
        utils.updateServiceInstances(mockExistingServiceInstances, mockSpaceIds, enrolledOrganizationConfig);
        
        verify(utils, times(mockExistingServiceInstances.size())).deleteServiceInstance(anyString());
        verify(utils, times(mockExistingServiceInstances.size()))
                .createNewServiceInstance(any(EnrolledSpaceConfig.class));
    }

    @Test
    public void test_updateServiceInstances_should_throw_exception() throws CloudFoundryException {
        SpaceEnrollerConfig mockSpaceConfig1 = BeanGenerator
                .createServiceInstance(autoServiceInstanceIDs.get(0), mockSpaceIds.get(0), ORGANIZATION_ID);
        SpaceEnrollerConfig mockSpaceConfig2 = BeanGenerator
                .createServiceInstance(autoServiceInstanceIDs.get(1), mockSpaceIds.get(1), ORGANIZATION_ID);

        Map<String, SpaceEnrollerConfig> mockExistingServiceInstances = new HashMap<>();
        mockExistingServiceInstances.put(mockSpaceIds.get(0), mockSpaceConfig1);
        mockExistingServiceInstances.put(mockSpaceIds.get(1), mockSpaceConfig2);

        when(enrolledOrganizationConfig.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(enrolledOrganizationConfig.getIdleDuration()).thenReturn(Duration.ofMillis(100));
        
        doReturn(false).when(utils)
                .checkParameters(mockSpaceConfig1,enrolledOrganizationConfig); 
        doReturn(false).when(utils)
                .checkParameters(mockSpaceConfig2,enrolledOrganizationConfig); 

        verifyThrown(() -> utils.updateServiceInstances(mockExistingServiceInstances,
                mockSpaceIds, enrolledOrganizationConfig), Throwable.class);
    }
    
}
