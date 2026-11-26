package com.grash.repository;

import com.grash.model.IntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, Long> {
    Optional<IntegrationConfig> findByCompanyId(Long companyId);

    Optional<IntegrationConfig> findByIotTokenHashAndIotEnabledTrue(String iotTokenHash);
}
