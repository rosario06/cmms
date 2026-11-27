package com.grash.service;

import com.grash.dto.planner.PlannerEventDTO;
import com.grash.dto.planner.PlannerResponseDTO;
import com.grash.dto.planner.WorkOrderScheduleDTO;
import com.grash.dto.planner.WorkOrderScheduleResponseDTO;
import com.grash.dto.workOrder.WorkOrderPatchDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.WorkOrderMapper;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.Status;
import com.grash.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderPlannerService {

    public static final String WARNING_TECHNICIAN_OVERLAP = "TECHNICIAN_OVERLAP";
    public static final int UNASSIGNED_LIMIT = 200;
    /**
     * Maximum duration accepted by the planner (14 days); candidates starting earlier than this before the new start
     * cannot overlap it.
     */
    private static final long LOOKBACK_MILLIS = 14L * 24 * 60 * 60 * 1000;
    private static final long MINUTE_MILLIS = 60_000L;

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;
    private final UserService userService;
    private final WorkOrderMapper workOrderMapper;

    @Transactional(readOnly = true)
    public PlannerResponseDTO getPlanner(Date start, Date end, Long technicianId, User user) {
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.WORK_ORDERS))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        Long companyId = user.getCompany().getId();
        boolean restrictToUser = !user.getRole().getViewOtherPermissions().contains(PermissionEntity.WORK_ORDERS);

        List<PlannerEventDTO> scheduled = workOrderRepository.findPlannerScheduled(companyId, start, end,
                        technicianId == null ? -1L : technicianId, restrictToUser, user.getId())
                .stream().map(this::toEvent).collect(Collectors.toList());
        List<PlannerEventDTO> unassigned = workOrderRepository.findPlannerUnassigned(companyId, restrictToUser,
                        user.getId(), PageRequest.of(0, UNASSIGNED_LIMIT))
                .stream().map(this::toEvent).collect(Collectors.toList());
        return new PlannerResponseDTO(scheduled, unassigned);
    }

    @Transactional
    public WorkOrderScheduleResponseDTO schedule(Long id, WorkOrderScheduleDTO dto, User user) {
        WorkOrder workOrder = workOrderService.findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Work Order not found", HttpStatus.NOT_FOUND));
        if (!workOrder.canBeEditedBy(user))
            throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
        if (Status.COMPLETE.equals(workOrder.getStatus()))
            throw new CustomException("A completed work order cannot be rescheduled", HttpStatus.BAD_REQUEST);

        if (dto.getEstimatedStartDate() != null && dto.getDueDate() != null
                && dto.getDueDate().before(dto.getEstimatedStartDate()))
            throw new CustomException("dueDate must be greater than or equal to estimatedStartDate",
                    HttpStatus.BAD_REQUEST);
        if (dto.getEstimatedDurationMinutes() != null
                && (dto.getEstimatedDurationMinutes() <= 0 || dto.getEstimatedDurationMinutes() > 1440 * 14))
            throw new CustomException("Invalid estimatedDurationMinutes", HttpStatus.BAD_REQUEST);

        User newPrimaryUser = null;
        if (dto.isPrimaryUserIdPresent() && dto.getPrimaryUserId() != null) {
            newPrimaryUser = userService.findByIdAndCompany(dto.getPrimaryUserId(), user.getCompany().getId())
                    .orElseThrow(() -> new CustomException("Technician not found in your company",
                            HttpStatus.BAD_REQUEST));
            if (!newPrimaryUser.isEnabled())
                throw new CustomException("Technician is disabled", HttpStatus.BAD_REQUEST);
        }

        // Effective values after the change (used for the availability check).
        Date effectiveStart = dto.getEstimatedStartDate() != null ? dto.getEstimatedStartDate() :
                workOrder.getEstimatedStartDate();
        long effectiveMinutes = dto.getEstimatedDurationMinutes() != null ? dto.getEstimatedDurationMinutes() :
                hoursToMinutes(workOrder.getEstimatedDuration());
        Long effectiveTechnicianId;
        if (dto.isPrimaryUserIdPresent()) effectiveTechnicianId = dto.getPrimaryUserId();
        else effectiveTechnicianId = workOrder.getPrimaryUser() != null ? workOrder.getPrimaryUser().getId() : null;

        List<Long> conflicts = new ArrayList<>();
        if (effectiveTechnicianId != null && effectiveStart != null) {
            conflicts = findOverlappingWorkOrderIds(user.getCompany().getId(), effectiveTechnicianId, id,
                    effectiveStart, effectiveMinutes);
        }

        // Reuse the regular patch (history, webhooks, notifications, TechnicianAssignedEvent). The patch DTO is a full
        // replacement, so start from the current state and override only the present fields.
        WorkOrderPatchDTO patchDTO = workOrderMapper.toPatchDto(workOrder);
        if (dto.getDueDate() != null) patchDTO.setDueDate(dto.getDueDate());
        if (dto.getEstimatedStartDate() != null) patchDTO.setEstimatedStartDate(dto.getEstimatedStartDate());
        if (dto.getEstimatedDurationMinutes() != null)
            patchDTO.setEstimatedDuration(dto.getEstimatedDurationMinutes() / 60.0);
        if (dto.isPrimaryUserIdPresent()) patchDTO.setPrimaryUser(newPrimaryUser);

        WorkOrder patched = workOrderService.patch(id, patchDTO, user);

        List<String> warnings = new ArrayList<>();
        if (!conflicts.isEmpty()) warnings.add(WARNING_TECHNICIAN_OVERLAP);
        return new WorkOrderScheduleResponseDTO(toEvent(patched), warnings, conflicts);
    }

    /**
     * Ids of the technician's other non-completed work orders whose [start, start + duration] interval overlaps the
     * given one. A zero duration counts as one minute.
     */
    public List<Long> findOverlappingWorkOrderIds(Long companyId, Long technicianId, Long excludedWorkOrderId,
                                                  Date start, long durationMinutes) {
        long newStart = start.getTime();
        long newEnd = newStart + Math.max(durationMinutes, 1L) * MINUTE_MILLIS;
        List<WorkOrder> candidates = workOrderRepository.findTechnicianCandidates(companyId, technicianId,
                excludedWorkOrderId, new Date(newStart - LOOKBACK_MILLIS), new Date(newEnd));
        List<Long> result = new ArrayList<>();
        for (WorkOrder other : candidates) {
            if (other.getEstimatedStartDate() == null || other.getId().equals(excludedWorkOrderId)
                    || Status.COMPLETE.equals(other.getStatus())) continue;
            long otherStart = other.getEstimatedStartDate().getTime();
            long otherEnd = otherStart + Math.max(hoursToMinutes(other.getEstimatedDuration()), 1L) * MINUTE_MILLIS;
            if (otherStart < newEnd && newStart < otherEnd) result.add(other.getId());
        }
        return result;
    }

    public PlannerEventDTO toEvent(WorkOrder workOrder) {
        User primaryUser = workOrder.getPrimaryUser();
        long minutes = hoursToMinutes(workOrder.getEstimatedDuration());
        return PlannerEventDTO.builder()
                .id(workOrder.getId())
                .title(workOrder.getTitle())
                .priority(workOrder.getPriority())
                .status(workOrder.getStatus())
                .dueDate(workOrder.getDueDate())
                .estimatedStartDate(workOrder.getEstimatedStartDate())
                .estimatedDurationMinutes(minutes > 0 ? minutes : null)
                .primaryUserId(primaryUser != null ? primaryUser.getId() : null)
                .primaryUserName(primaryUser != null ? fullName(primaryUser) : null)
                .assetName(workOrder.getAsset() != null ? workOrder.getAsset().getName() : null)
                .locationName(workOrder.getLocation() != null ? workOrder.getLocation().getName() : null)
                .build();
    }

    /**
     * WorkOrderBase.estimatedDuration is expressed in hours.
     */
    static long hoursToMinutes(double hours) {
        return Math.round(hours * 60);
    }

    private static String fullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        return (first + " " + last).trim();
    }
}
