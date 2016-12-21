/*
 * Autosleep
 * Copyright (C) 2016 Orange
 * Authors: Benjamin Einaudi   benjamin.einaudi@orange.com
 *          Arnaud Ruffin      arnaud.ruffin@orange.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.autosleep.config;

import java.io.FileNotFoundException;  
import java.io.InputStream;
import java.util.Properties;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.spring.client.SpringCloudFoundryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Bean;
import lombok.Builder;
import lombok.Getter;

public class CloudfoundryClientBuilder {

    private static final Logger log = LoggerFactory.getLogger(CloudfoundryClientBuilder.class);
    
    @Builder
    @Getter
    private static class ClientContainer {

        private CloudFoundryClient client;

        @Builder
        ClientContainer(CloudFoundryClient client) {
            this.client = client;
        }
    }

    private ClientContainer clientContainer;

    private RuntimeException initializationError;

    private synchronized ClientContainer buildIfNeeded() {
        
        if (clientContainer == null && initializationError == null) {
            Properties prop = new Properties();
            try {
                String fileName = "config.properties";
    
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
    
                if (inputStream != null) {
                    prop.load(inputStream);
                } else {
                    throw new FileNotFoundException("property file " + fileName + " not found");
                }
            } catch (Exception e) {
                log.error("Error while reading file : " + e);
            }
            
            String targetApiEndpoint = prop.getProperty("targetApiEndpoint");
            String cfUsername = prop.getProperty("email");
            String cfUserPassword = prop.getProperty("password");
            
            try {

                log.info("buildClient - targetHost={}", targetApiEndpoint);
                log.info("buildClient - username={}", cfUsername);
                
                SpringCloudFoundryClient client = SpringCloudFoundryClient.builder()
                        .host(targetApiEndpoint)
                        .username(cfUsername)
                        .password(cfUserPassword)
                        .skipSslValidation(true)
                        .build();

                this.clientContainer = ClientContainer.builder()
                        .client(client)
                        .build();
                return this.clientContainer;
            } catch (RuntimeException r) {
                initializationError = new ApplicationContextException("Failed to build client", r);
                throw initializationError;
            }
        } else if (initializationError != null) {
            throw initializationError;
        } else {
            return clientContainer;
        }
    }

    @Bean
    @ConditionalOnMissingBean(CloudFoundryClient.class)
    public CloudFoundryClient getClient() {
        return buildIfNeeded().getClient();
    }

}
