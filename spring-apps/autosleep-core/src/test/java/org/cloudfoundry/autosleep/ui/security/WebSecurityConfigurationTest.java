package org.cloudfoundry.autosleep.ui.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic; 
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.Filter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = WebSecurityConfiguration.class)
@WebAppConfiguration
public class WebSecurityConfigurationTest {

    private static final Logger log = LoggerFactory.getLogger(WebSecurityConfiguration.class);

    private MockMvc mvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    @Before
    public void setup() {
        mvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    @Test
    public void test_no_authentication_for_path_dashboard() {
        try {
            mvc
                .perform(get("/dashboard/"))
                .andExpect(status().isOk());
        } catch (Exception e) {
            log.error("Test case failed : " + e.getMessage());
        }
    }

    @Test
    public void test_no_authentication_for_path_css() {
        try {
            mvc
                .perform(get("/css/"))
                .andExpect(status().isOk());
        } catch (Exception e) {
            log.error("Test case failed : " + e.getMessage());
        }
    }

    @Test
    public void test_no_authentication_for_path_fonts() {
        try {
            mvc
                .perform(get("/fonts/"))
                .andExpect(status().isOk());
        } catch (Exception e) {
            log.error("Test case failed : " + e.getMessage());
        }
    }

    @Test
    public void test_no_authentication_for_path_javascript() {
        try {
            mvc
                .perform(get("/javascript/"))
                .andExpect(status().isOk());
        } catch (Exception e) {
            log.error("Test case failed : " + e.getMessage());
        }
    }

    @Test
    public void test_no_authentication_for_path_api_services_applications() {
        try {
            mvc
                .perform(get("/api/services/testPath/applications/"))
                .andExpect(status().isOk());
        } catch (Exception e) {
            log.error("Test case failed : " + e.getMessage());
        }
    }

    @Test
    public void test_basic_authentication_for_any_url_with_valid_credentials() {
        try {
            mvc
                .perform(get("/testPath/").with(user("admin").password("pass")))
                .andExpect(status().isOk());
        } catch (Exception e) {
            log.error("Test case failed : " + e.getMessage());
        }
    }

    @Test
    public void test_basic_authentication_for_any_url_with_invalid_credentials() {
        try {
            mvc
                .perform(get("/testPath/").with(httpBasic("fakeUser", "fakePassword")))
                .andExpect(status().isUnauthorized());
        } catch (Exception e) {
            log.error("Test case failed : " + e.getMessage());
        }
    }
}