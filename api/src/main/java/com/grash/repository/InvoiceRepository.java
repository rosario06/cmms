package com.grash.repository;

import com.grash.model.CustomerInvoice;
import com.grash.model.enums.InvoiceStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<CustomerInvoice, Long>,
        JpaSpecificationExecutor<CustomerInvoice> {

    boolean existsByWorkOrder_IdAndStatusNot(Long workOrderId, InvoiceStatus status);

    List<CustomerInvoice> findByStatusAndDueDateBefore(InvoiceStatus status, Date date);

    /**
     * Invoice numbers of a company starting with the given prefix, highest first (longest then
     * lexicographically greatest, so numbers above 9999 still sort correctly). Use a Pageable of size 1.
     */
    @Query("SELECT i.invoiceNumber FROM CustomerInvoice i WHERE i.company.id = :companyId " +
            "AND i.invoiceNumber LIKE :prefix ORDER BY LENGTH(i.invoiceNumber) DESC, i.invoiceNumber DESC")
    List<String> findLatestNumbers(@Param("companyId") Long companyId, @Param("prefix") String prefix,
                                   Pageable pageable);
}
