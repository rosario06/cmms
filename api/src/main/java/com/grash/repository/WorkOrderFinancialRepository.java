package com.grash.repository;

import com.grash.model.WorkOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Read-only aggregated queries for the profitability report. Kept apart from {@link WorkOrderRepository}.
 * {@code customerId} uses -1 as "no filter" (avoids untyped null parameters).
 */
public interface WorkOrderFinancialRepository extends Repository<WorkOrder, Long> {

    /**
     * Rows: [customerId, customerName, workOrderCount, internalCost, billable].
     */
    @Query("SELECT c.id, c.name, COUNT(DISTINCT w.id), COALESCE(SUM(w.totalInternalCost), 0), " +
            "COALESCE(SUM(w.totalBillableAmount), 0) " +
            "FROM WorkOrder w JOIN w.customers c " +
            "WHERE w.company.id = :companyId AND w.completedOn BETWEEN :start AND :end " +
            "AND (:customerId = -1L OR c.id = :customerId) " +
            "GROUP BY c.id, c.name " +
            "ORDER BY COALESCE(SUM(w.totalBillableAmount), 0) DESC")
    List<Object[]> profitabilityByCustomer(@Param("companyId") Long companyId,
                                           @Param("start") Date start,
                                           @Param("end") Date end,
                                           @Param("customerId") Long customerId);

    /**
     * Rows: [workOrderId, title, internalCost, billable].
     */
    @Query("SELECT w.id, w.title, w.totalInternalCost, w.totalBillableAmount " +
            "FROM WorkOrder w " +
            "WHERE w.company.id = :companyId AND w.completedOn BETWEEN :start AND :end " +
            "AND (:customerId = -1L OR EXISTS (SELECT c.id FROM w.customers c WHERE c.id = :customerId)) " +
            "ORDER BY w.completedOn DESC, w.id DESC")
    List<Object[]> profitabilityByWorkOrder(@Param("companyId") Long companyId,
                                            @Param("start") Date start,
                                            @Param("end") Date end,
                                            @Param("customerId") Long customerId,
                                            Pageable pageable);

    /**
     * Rows: [workOrderId, customerName] for the given work orders (one query for the whole page, no N+1).
     */
    @Query("SELECT w.id, c.name FROM WorkOrder w JOIN w.customers c WHERE w.id IN :ids ORDER BY c.name")
    List<Object[]> findCustomerNames(@Param("ids") Collection<Long> ids);
}
