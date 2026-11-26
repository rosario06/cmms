package com.grash.repository;

import com.grash.model.IotTelemetryLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IotTelemetryLogRepository extends JpaRepository<IotTelemetryLog, Long> {
    List<IotTelemetryLog> findByCompanyIdOrderByCreatedAtDescIdDesc(Long companyId, Pageable pageable);
}
