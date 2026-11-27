package com.grash.repository;

import com.grash.model.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CostCenterRepository extends JpaRepository<CostCenter, Long>, JpaSpecificationExecutor<CostCenter> {

    List<CostCenter> findByCompany_IdAndCodeIgnoreCase(Long companyId, String code);

    /**
     * Sum of the internal cost of the work orders charged to the cost center (always recomputed, never accumulated).
     */
    @Query("SELECT COALESCE(SUM(w.totalInternalCost), 0) FROM WorkOrder w WHERE w.costCenter.id = :id")
    Double sumInternalCost(@Param("id") Long id);

    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.costCenter.id = :id")
    long countWorkOrders(@Param("id") Long id);

    /**
     * For each cost center of the company: [id, stored spentAmount, real sum of the internal cost of its work orders].
     * Used to detect centers whose stored amount is stale (e.g. a work order moved to another center).
     */
    @Query("SELECT cc.id, cc.spentAmount, COALESCE(SUM(w.totalInternalCost), 0) " +
            "FROM CostCenter cc LEFT JOIN WorkOrder w ON w.costCenter.id = cc.id " +
            "WHERE cc.company.id = :companyId " +
            "GROUP BY cc.id, cc.spentAmount")
    List<Object[]> findSpentComparisonByCompany(@Param("companyId") Long companyId);
}
