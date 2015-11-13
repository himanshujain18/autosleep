package org.cloudfoundry.autosleep.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloudfoundry.autosleep.dao.model.ApplicationBinding;
import org.cloudfoundry.autosleep.dao.model.AutosleepServiceInstance;
import org.cloudfoundry.autosleep.dao.repositories.BindingRepository;
import org.cloudfoundry.autosleep.dao.repositories.ServiceRepository;
import org.cloudfoundry.community.servicebroker.model.Catalog;
import org.cloudfoundry.community.servicebroker.model.CreateServiceInstanceRequest;
import org.cloudfoundry.community.servicebroker.model.Plan;
import org.cloudfoundry.community.servicebroker.model.ServiceDefinition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class DebugControllerTest {

    private static final String serviceInstanceId = "serviceInstanceId";

    private static final String serviceBindingId = "serviceBindingId";

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private BindingRepository bindingRepository;

    @Mock
    private Catalog catalog;

    @InjectMocks
    private DebugController debugController;


    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    private AutosleepServiceInstance serviceInstance;

    private ApplicationBinding serviceBinding;

    @Before
    public void init() {
        objectMapper = new ObjectMapper();
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(debugController).build();

        CreateServiceInstanceRequest createRequestTemplate = new CreateServiceInstanceRequest("definition",
                "plan",
                "org",
                "space");
        serviceInstance = new AutosleepServiceInstance(
                createRequestTemplate.withServiceInstanceId(serviceInstanceId));
        serviceBinding = new ApplicationBinding(serviceBindingId, serviceInstanceId,
                null, null, UUID.randomUUID().toString());
        Mockito.when(catalog.getServiceDefinitions()).thenReturn(Collections.singletonList(
                new ServiceDefinition("serviceDefinitionId", "serviceDefinition", "", true,
                        Collections.singletonList(new Plan("planId", "plan", "")))));
        Mockito.when(serviceRepository.findAll()).thenReturn(Collections.singletonList(serviceInstance));
        Mockito.when(bindingRepository.findAll()).thenReturn(Collections.singletonList(serviceBinding));

    }

    @Test
    public void testServiceInstances() throws Exception {
        mockMvc.perform(get("/admin/debug/")
                .accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
    }

    @Test
    public void testServiceBindings() throws Exception {
        mockMvc.perform(get("/admin/debug/" + serviceInstanceId + "/bindings/")
                .accept(MediaType.TEXT_HTML)).andExpect(status().isOk());
    }


    @Test
    public void testListServiceInstances() throws Exception {
        mockMvc.perform(get("/admin/debug/services/servicesinstances/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(content()
                .contentType(new MediaType(MediaType.APPLICATION_JSON,
                        Collections.singletonMap("charset", Charset.forName("UTF-8").toString()))))
                .andDo(mvcResult -> {
                    org.cloudfoundry.autosleep.admin.model.ServiceInstance[] serviceInstances = objectMapper
                            .readValue(mvcResult.getResponse().getContentAsString(), org.cloudfoundry.autosleep.admin
                                    .model.ServiceInstance[].class);
                    assertThat(serviceInstances.length, is(equalTo(1)));
                    assertThat(serviceInstances[0].getInstanceId(), is(equalTo(serviceInstanceId)));
                });
    }

    @Test
    public void testListServiceBindings() throws Exception {
        mockMvc.perform(get("/admin/debug/services/servicebindings/" + serviceInstanceId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(content()
                .contentType(new MediaType(MediaType.APPLICATION_JSON,
                        Collections.singletonMap("charset", Charset.forName("UTF-8").toString()))))
                .andDo(mvcResult -> {
                    org.cloudfoundry.autosleep.admin.model.ServiceBinding[] serviceBindings = objectMapper
                            .readValue(mvcResult.getResponse().getContentAsString(), org.cloudfoundry.autosleep.admin
                                    .model.ServiceBinding[].class);
                    assertThat(serviceBindings.length, is(equalTo(1)));
                    assertThat(serviceBindings[0].getId(), is(equalTo(serviceBindingId)));
                });
    }
}