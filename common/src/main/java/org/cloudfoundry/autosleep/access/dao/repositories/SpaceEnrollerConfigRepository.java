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

package org.cloudfoundry.autosleep.access.dao.repositories;

import java.util.List;

import org.cloudfoundry.autosleep.access.dao.model.SpaceEnrollerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface SpaceEnrollerConfigRepository extends JpaRepository<SpaceEnrollerConfig, String> {
    
    //@Query("select s.id from SpaceEnrollerConfig s where s.organizationId NOT IN (:ids)")
    //List<String> deRegisteredOrganizationServiceInstances(@Param("ids") List<String> ids);
    
    @Query("select s from SpaceEnrollerConfig s where s.organizationId NOT IN (:ids)")
    List<SpaceEnrollerConfig> deRegisteredOrganizationServiceInstances(@Param("ids") List<String> ids);
    
    @Query("select s from SpaceEnrollerConfig s where s.organizationId = :id")
    List<SpaceEnrollerConfig> listByOrganizationId(@Param("id") String id);
    
}
