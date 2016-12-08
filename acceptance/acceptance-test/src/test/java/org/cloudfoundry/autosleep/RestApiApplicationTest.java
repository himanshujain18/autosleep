package org.cloudfoundry.autosleep;

import org.junit.Test;   
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.annotation.ProfileValueSourceConfiguration;
import org.springframework.test.annotation.SystemProfileValueSource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@ProfileValueSourceConfiguration(SystemProfileValueSource.class)
@TestExecutionListeners
@IfProfileValue(name = "acceptance-test", value = "true")
public class RestApiApplicationTest {

    @Test
    public void contextLoads() {
    }
    
}
