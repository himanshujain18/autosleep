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

import org.cloudfoundry.autosleep.access.dao.model.Binding;
import org.cloudfoundry.autosleep.access.dao.model.Binding.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface BindingRepository extends JpaRepository<Binding, String> {

    List<Binding> findAllByResourceType(ResourceType resourceType);

    Binding findByResourceId(String id);

    @Query("select b from Binding b where b.resourceId in (:ids) and b.resourceType = :resType")
    List<Binding> findByResourceIdAndType(@Param("ids") List<String> ids, @Param("resType") ResourceType resType);

    @Query("select b from Binding b where b.serviceInstanceId = :id")
    List<Binding> findByServiceInstanceId(@Param("id") String id);

}
