package org.cloudfoundry.autosleep.access.dao.repositories;

import org.cloudfoundry.autosleep.access.dao.config.RepositoryConfig;
import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.cloudfoundry.autosleep.util.ApplicationConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import lombok.extern.slf4j.Slf4j;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ApplicationConfiguration.class, RepositoryConfig.class, EnableJpaConfiguration.class})
public abstract class EnrolledOrganizationConfigRepositoryTest extends CrudRepositoryTest<EnrolledOrganizationConfig> {

    private static final String duration = "PT2M";
    
    @Autowired
    private EnrolledOrganizationConfigRepository enrolledOrganizationConfigRepository;
    
    @Override
    protected EnrolledOrganizationConfig build(String orgId) {
        return EnrolledOrganizationConfig.builder()
                .organizationId(orgId)
                .idleDuration(duration)
                .build();                
    }
    
    @Override
    protected void compareReloaded(EnrolledOrganizationConfig original, EnrolledOrganizationConfig reloaded) {
        assertEquals(reloaded.getOrganizationId(), original.getOrganizationId());
        assertEquals(reloaded.getIdleDuration(), original.getIdleDuration());        
        assertThat("Two objects should be equal", reloaded, is(equalTo(original)));
    }
    
    /**
     * Init DAO with test data.
     */
    @Before
    @After
    public void setAndCleanDao() {
        setDao(enrolledOrganizationConfigRepository);
        enrolledOrganizationConfigRepository.deleteAll();
    }

}
