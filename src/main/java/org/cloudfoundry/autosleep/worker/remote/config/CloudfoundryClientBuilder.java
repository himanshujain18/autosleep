/**
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

package org.cloudfoundry.autosleep.worker.remote.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.autosleep.config.Config;
import org.cloudfoundry.autosleep.config.Config.DefaultClientIdentification;
import org.cloudfoundry.spring.client.SpringCloudFoundryClient;
import org.cloudfoundry.spring.logging.SpringLoggingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;

@Configuration
@PropertySource(value = "classpath:cloudfoundry_client.properties", ignoreResourceNotFound = true)
@EnableAutoConfiguration
@Slf4j
public class CloudfoundryClientBuilder {

    @Getter(onMethod = @__(@Bean))
    private SpringCloudFoundryClient cfClient;

    @Autowired
    private Environment env;

    @Getter(onMethod = @__(@Bean))
    private SpringLoggingClient logClient;

    @PostConstruct
    public void initClients() {
        final String targetEndpoint = env.getProperty(Config.EnvKey.CF_ENDPOINT);
        final boolean skipSslValidation = Boolean.parseBoolean(env.getProperty(
                Config.EnvKey.CF_SKIP_SSL_VALIDATION,
                Boolean.FALSE.toString()));
        final String username = env.getProperty(Config.EnvKey.CF_USERNAME);
        final String password = env.getProperty(Config.EnvKey.CF_PASSWORD);
        final String clientId = env.getProperty(Config.EnvKey.CF_CLIENT_ID, DefaultClientIdentification.ID);
        final String clientSecret = env.getProperty(Config.EnvKey.CF_CLIENT_SECRET, DefaultClientIdentification.SECRET);
        try {

            log.debug("buildClient - targetEndpoint={}", targetEndpoint);
            log.debug("buildClient - skipSslValidation={}", skipSslValidation);
            log.debug("buildClient - username={}", username);
            cfClient = SpringCloudFoundryClient.builder()
                    .host(targetEndpoint)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .skipSslValidation(skipSslValidation)
                    .username(username)
                    .password(password)
                    .build();

            logClient = SpringLoggingClient.builder().cloudFoundryClient(cfClient).build();
        } catch (RuntimeException r) {
            log.error("CloudFoundryApi - failure while login", r);
        }
    }

}