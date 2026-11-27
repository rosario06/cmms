package com.grash.repository;

import com.grash.model.SlaPolicy;
import com.grash.model.enums.Priority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long>, JpaSpecificationExecutor<SlaPolicy> {
    Optional<SlaPolicy> findByIdAndCompany_Id(Long id, Long companyId);

    Optional<SlaPolicy> findFirstByServiceContract_IdAndPriorityOrderByIdAsc(Long serviceContractId,
                                                                             Priority priority);

    Optional<SlaPolicy> findFirstByCompany_IdAndServiceContractIsNullAndPriorityOrderByIdAsc(Long companyId,
                                                                                             Priority priority);
}
