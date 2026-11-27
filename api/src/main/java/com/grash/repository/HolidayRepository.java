package com.grash.repository;

import com.grash.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long>, JpaSpecificationExecutor<Holiday> {
    Optional<Holiday> findByIdAndCompany_Id(Long id, Long companyId);

    List<Holiday> findByCompany_Id(Long companyId);
}
