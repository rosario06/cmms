package com.grash.repository;

import com.grash.model.ServiceContract;
import com.grash.model.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Minimal contract access used by invoicing, kept separate so billing does not depend on the contracts module.
 */
public interface InvoiceContractRepository extends JpaRepository<ServiceContract, Long> {
    List<ServiceContract> findByStatusAndAutoInvoiceTrue(ContractStatus status);
}
