package org.cloudfoundry.autosleep.access.dao.repositories;


import java.util.List;

import org.cloudfoundry.autosleep.access.dao.model.AutoServiceInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutoServiceInstanceRepository extends JpaRepository<AutoServiceInstance, String> {

    @Query("select a from AutoServiceInstance a where a.organizationId = :id")
    List<AutoServiceInstance> findByOrganizationId(@Param("id") String id);

    @Query("delete from AutoServiceInstance a where a.organizationId = :id")
    void deleteByOrgId(@Param("id") String id);

}
