package org.cloudfoundry.autosleep.access.dao.repositories;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.cloudfoundry.autosleep.access.dao.config.RepositoryConfig;
import org.cloudfoundry.autosleep.access.dao.model.AutoServiceInstance;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.util.ApplicationConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import lombok.extern.slf4j.Slf4j;
@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ApplicationConfiguration.class, RepositoryConfig.class, EnableJpaConfiguration.class})
public abstract class AutoServiceInstanceRepositoryTest extends CrudRepositoryTest<AutoServiceInstance> {

    private static final String ORGANIZATION_ID = UUID.randomUUID().toString();

    @Autowired
    private AutoServiceInstanceRepository autoServiceInstanceRepository;

    @Override
    protected AutoServiceInstance build(String serviceInstanceId) {
        return AutoServiceInstance.builder()
                .serviceInstanceId(serviceInstanceId)
                .organizationId(ORGANIZATION_ID)
                .spaceId("spaceId")
                .build();

    }

    @Override
    protected void compareReloaded(AutoServiceInstance original, AutoServiceInstance reloaded) {
        assertEquals(reloaded.getServiceInstanceId(), original.getServiceInstanceId());  
        assertEquals(reloaded.getOrganizationId(), original.getOrganizationId());
        assertEquals(reloaded.getSpaceId(), original.getSpaceId());

        assertThat("Two objects should be equal", reloaded, is(equalTo(original)));
    }

    /**
     * Init DAO with test data.
     */
    @Before
    @After
    public void setAndCleanDao() {
        setDao(autoServiceInstanceRepository);
        autoServiceInstanceRepository.deleteAll();
    }


    @Test
    public void test_find_by_organization_id_existing() {        

        List<String> ids = Arrays.asList("serviceInstance1", "serviceInstance2");
        ids.forEach(id -> autoServiceInstanceRepository.save(build(id)));
        int count = autoServiceInstanceRepository.findByOrganizationId(ORGANIZATION_ID).size();
        assertTrue("Retrieving all elements should return the same quantity", count == ids.size());        

    }

    @Test
    public void test_find_by_organization_id_not_existing() {        

        List<String> ids = Arrays.asList("serviceInstance1", "serviceInstance2");
        ids.forEach(id -> autoServiceInstanceRepository.save(build(id)));
        int count = autoServiceInstanceRepository.findByOrganizationId("false_org_id").size();
        assertTrue("Retrieving all elements should return the same quantity", count == 0);        

    }
}
