package org.cloudfoundry.autosleep.worker;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryApiService;
import org.cloudfoundry.autosleep.access.cloudfoundry.CloudFoundryException;
import org.cloudfoundry.autosleep.access.dao.model.AutoServiceInstance;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.cloudfoundry.autosleep.access.dao.repositories.AutoServiceInstanceRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.EnrolledOrganizationConfigRepository;
import org.cloudfoundry.autosleep.access.dao.repositories.SpaceEnrollerConfigRepository;
import org.cloudfoundry.autosleep.util.AutosleepConfigControllerUtils;
import org.cloudfoundry.autosleep.util.BeanGenerator;
import org.cloudfoundry.autosleep.worker.scheduling.Clock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OrganizationEnrollerTest {

    private static final Duration INTERVAL = Duration.ofMillis(300);

    private static final Duration ZERO_DELAY = Duration.ofMillis(0);

    private static final Duration UPDATED_INTERVAL = Duration.ofMillis(180);

    private static final String ORGANIZATION_ID = UUID.randomUUID().toString();

    private OrganizationEnroller orgEnroller;

    @Mock
    private AutosleepConfigControllerUtils utils;

    @Mock
    private AutoServiceInstanceRepository autoServiceInstanceRepository;

    @Mock
    private Clock clock;

    @Mock
    private CloudFoundryApiService cloudFoundryApi;

    @Mock
    private EnrolledOrganizationConfig enrolledOrganizationConfig;

    @Mock
    private EnrolledOrganizationConfigRepository orgRepository;

    @Mock
    private SpaceEnrollerConfigRepository spaceEnrollerConfigRepository;

    @Mock
    private WorkerManagerService workerManagerService;

    List<String> mockOldCfSpaces = Arrays.asList("spaceId1", "spaceId2");

    List<String> mockCfSpaces = new ArrayList<>(Arrays.asList("spaceId2", "spaceId3"));

    List<String> mockAutoServiceInstanceIDs = Arrays.asList("instanceId1", "instanceId2");

    @Before
    public void buildMocks() throws CloudFoundryException {
        when(enrolledOrganizationConfig.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(enrolledOrganizationConfig.getIdleDuration()).thenReturn(INTERVAL);

        orgEnroller = spy(OrganizationEnroller.builder()
                .autoServiceInstanceRepository(autoServiceInstanceRepository)
                .clock(clock)
                .period(INTERVAL)
                .organizationId(ORGANIZATION_ID)
                .cloudFoundryApi(cloudFoundryApi)
                .enrolledOrganizationConfig(enrolledOrganizationConfig)
                .orgRepository(orgRepository)          
                .spaceEnrollerConfigRepository(spaceEnrollerConfigRepository)                
                .utils(utils)
                .build());
    }

    @Test
    public void test_enroller_does_not_enroll_invalid_organization() {

        when(cloudFoundryApi.isValidOrganization(ORGANIZATION_ID)).thenReturn(false);

        orgEnroller.run();
        verify(orgEnroller, times(0)).enrollOrganizationSpaces();
        verify(orgEnroller, times(0)).reschedule(INTERVAL);

        verify(autoServiceInstanceRepository, times(1)).deleteByOrgId(ORGANIZATION_ID);
        verify(orgRepository, times(1)).delete(ORGANIZATION_ID);
        verify(orgEnroller, times(1)).killTask();
    }

    @Test
    public void test_enroller_does_enroll_valid_organization_and_reschedules() {

        when(cloudFoundryApi.isValidOrganization(ORGANIZATION_ID)).thenReturn(true);

        orgEnroller.run();
        verify(orgEnroller, times(1)).enrollOrganizationSpaces();
        verify(orgEnroller, times(1)).reschedule(INTERVAL);
    }

    @Test
    public void test_call_reschedule() {
        EnrolledOrganizationConfig enrolledOrganizationConfig = mock(EnrolledOrganizationConfig.class);

        when(enrolledOrganizationConfig.getIdleDuration()).thenReturn(UPDATED_INTERVAL);
        when(orgEnroller.getEnrolledOrganizationConfig()).thenReturn(enrolledOrganizationConfig);
        when(orgEnroller.getRescheduleTime()).thenReturn(UPDATED_INTERVAL);

        orgEnroller.callReschedule(enrolledOrganizationConfig);
        verify(orgEnroller, times(1)).start(ZERO_DELAY);
    }

    @Test
    public void test_get_current_enroller_object() {
        when(orgEnroller.getObj()).thenReturn(orgEnroller);
        orgEnroller.getObj();
    }

    @Test
    public void test_create_service_instances_when_new_organization_is_enrolled() throws CloudFoundryException {
        List<String> mockCfSpaces = Arrays.asList("spaceId1", "spaceId2");
        List<AutoServiceInstance> mockAutoServiceInstanceList = Arrays.asList();

        when(utils.cfSpacesList(ORGANIZATION_ID)).thenReturn(mockCfSpaces);
        when(autoServiceInstanceRepository.findByOrganizationId(ORGANIZATION_ID))
                .thenReturn(mockAutoServiceInstanceList);

        orgEnroller.enrollOrganizationSpaces();
        verify(utils, times(1)).createNewServiceInstances(mockCfSpaces, enrolledOrganizationConfig);
    }

    @Test
    public void test_update_service_instances_when_existing_organization_enrollment_is_updated()
            throws CloudFoundryException {

        when(utils.cfSpacesList(ORGANIZATION_ID)).thenReturn(mockCfSpaces);

        List<AutoServiceInstance> mockAutoServiceInstanceList = mockAutoServiceInstanceIDs.stream()
                .map(id -> BeanGenerator.createAutoServiceInstance(id, 
                        mockOldCfSpaces.get(mockAutoServiceInstanceIDs.indexOf(id)),ORGANIZATION_ID))
                .collect(Collectors.toList());

        when(autoServiceInstanceRepository.findByOrganizationId(ORGANIZATION_ID))
                .thenReturn(mockAutoServiceInstanceList);

        SpaceEnrollerConfig mockSpaceConfig1 = BeanGenerator
                .createServiceInstance(mockAutoServiceInstanceIDs.get(0), mockOldCfSpaces.get(0), ORGANIZATION_ID);
        SpaceEnrollerConfig mockSpaceConfig2 = BeanGenerator
                .createServiceInstance(mockAutoServiceInstanceIDs.get(1), mockOldCfSpaces.get(1), ORGANIZATION_ID);

        Map<String, SpaceEnrollerConfig> mockExistingServiceInstances = new HashMap<>();
        mockExistingServiceInstances.put(mockOldCfSpaces.get(0), mockSpaceConfig1);
        mockExistingServiceInstances.put(mockOldCfSpaces.get(1), mockSpaceConfig2);

        when(utils.alreadyEnrolledSpaces(mockAutoServiceInstanceIDs)).thenReturn(mockExistingServiceInstances);

        Collection<String> mockNewSpaces = new HashSet<>();
        mockNewSpaces.add("spaceId3");
        Collection<String> mockDeletedSpaces = new HashSet<>();
        mockDeletedSpaces.add("spaceId1");
        orgEnroller.enrollOrganizationSpaces();
        assertTrue(mockNewSpaces.size() == 1);
        verify(utils, times(1)).createNewServiceInstances(mockNewSpaces, enrolledOrganizationConfig);

        assertTrue(mockDeletedSpaces.size() == 1);
        verify(utils, times(mockDeletedSpaces.size())).deleteServiceInstance(mockDeletedSpaces.iterator().next());

        verify(utils, times(1))
                .updateServiceInstances(mockExistingServiceInstances, mockCfSpaces, enrolledOrganizationConfig);
    }
}