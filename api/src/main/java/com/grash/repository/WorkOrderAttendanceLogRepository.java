package com.grash.repository;

import com.grash.model.WorkOrderAttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderAttendanceLogRepository extends JpaRepository<WorkOrderAttendanceLog, Long> {
    /**
     * Open (not checked out) record of a technician in any work order.
     */
    Optional<WorkOrderAttendanceLog> findFirstByTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
            Long technicianId, Long companyId);

    /**
     * Open (not checked out) record of a technician in one work order.
     */
    Optional<WorkOrderAttendanceLog>
    findFirstByWorkOrderIdAndTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
            Long workOrderId, Long technicianId, Long companyId);

    List<WorkOrderAttendanceLog> findTop100ByWorkOrderIdAndCompanyIdOrderByCheckInTimeDesc(Long workOrderId,
                                                                                           Long companyId);
}
