package org.cloudfoundry.autosleep.access.dao.repositories;


import java.util.List;

import org.cloudfoundry.autosleep.access.dao.model.AutoServiceInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutoServiceInstanceRepository extends JpaRepository<AutoServiceInstance, String> {
    
    @Query("select a from AutoServiceInstance a where a.spaceId IN (:ids)")
    List<AutoServiceInstance> findBySpaceId(@Param("ids") List<String> ids);

}
