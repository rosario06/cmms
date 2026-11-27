package com.grash.repository;

import com.grash.model.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long>,
        JpaSpecificationExecutor<WorkSchedule> {
    Optional<WorkSchedule> findByIdAndCompany_Id(Long id, Long companyId);

    List<WorkSchedule> findByCompany_IdOrderByNameAsc(Long companyId);

    List<WorkSchedule> findByCompany_IdAndDefaultScheduleTrue(Long companyId);

    Optional<WorkSchedule> findFirstByCompany_IdAndDefaultScheduleTrueAndActiveTrueOrderByIdAsc(Long companyId);
}
