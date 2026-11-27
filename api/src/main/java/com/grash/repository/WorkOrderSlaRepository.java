package com.grash.repository;

import com.grash.model.WorkOrder;
import com.grash.model.enums.SlaStatus;
import com.grash.model.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * SLA specific queries on work orders (kept apart from {@link WorkOrderRepository}).
 */
public interface WorkOrderSlaRepository extends JpaRepository<WorkOrder, Long> {

    /**
     * Light projection of the not completed work orders that have an SLA deadline. Columns: id, createdAt,
     * slaFirstResponseDueAt, slaResolutionDueAt, firstRespondedAt, slaStatus.
     */
    @Query("SELECT w.id, w.createdAt, w.slaFirstResponseDueAt, w.slaResolutionDueAt, w.firstRespondedAt, " +
            "w.slaStatus FROM WorkOrder w WHERE w.status <> :completeStatus " +
            "AND (w.slaResolutionDueAt IS NOT NULL OR w.slaFirstResponseDueAt IS NOT NULL)")
    List<Object[]> findOpenWithSla(@Param("completeStatus") Status completeStatus);

    /**
     * Bulk update (does not touch updatedAt nor the audit tables, slaStatus is not audited).
     */
    @Modifying
    @Query("UPDATE WorkOrder w SET w.slaStatus = :slaStatus WHERE w.id IN :ids")
    int updateSlaStatus(@Param("ids") Collection<Long> ids, @Param("slaStatus") SlaStatus slaStatus);
}
