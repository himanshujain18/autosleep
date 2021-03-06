package org.cloudfoundry.autosleep.access.dao.repositories;

import org.cloudfoundry.autosleep.access.dao.model.EnrolledOrganizationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface EnrolledOrganizationConfigRepository extends JpaRepository<EnrolledOrganizationConfig,String> {

}
