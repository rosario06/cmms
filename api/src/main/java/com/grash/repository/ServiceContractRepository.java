package com.grash.repository;

import com.grash.model.ServiceContract;
import com.grash.model.enums.ContractStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface ServiceContractRepository extends JpaRepository<ServiceContract, Long>,
        JpaSpecificationExecutor<ServiceContract> {
    Optional<ServiceContract> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsByCompany_IdAndContractNumber(Long companyId, String contractNumber);

    long countByCompany_IdAndContractNumberStartingWith(Long companyId, String prefix);

    /**
     * Loads the contract with a write lock: used to deduct hours / retainer without losing concurrent updates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ServiceContract c WHERE c.id = :id")
    Optional<ServiceContract> findByIdForUpdate(@Param("id") Long id);

    /**
     * Contracts of the given status of any of the customers that are in force at {@code now}
     * (startDate/endDate are optional). The most recently started contract comes first.
     */
    @Query("SELECT c FROM ServiceContract c WHERE c.company.id = :companyId AND c.customer.id IN :customerIds " +
            "AND c.status = :status AND (c.startDate IS NULL OR c.startDate <= :now) " +
            "AND (c.endDate IS NULL OR c.endDate >= :now) ORDER BY c.startDate DESC, c.id DESC")
    List<ServiceContract> findInForceByCustomers(@Param("companyId") Long companyId,
                                                 @Param("customerIds") Collection<Long> customerIds,
                                                 @Param("status") ContractStatus status,
                                                 @Param("now") Date now);
}
