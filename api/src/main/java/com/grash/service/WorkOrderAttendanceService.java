package com.grash.service;

import com.grash.dto.WorkOrderChangeStatusDTO;
import com.grash.dto.attendance.AttendanceCheckInDTO;
import com.grash.dto.attendance.AttendanceCheckOutDTO;
import com.grash.dto.attendance.AttendanceLogShowDTO;
import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.WorkOrderAttendanceLog;
import com.grash.model.enums.Status;
import com.grash.repository.WorkOrderAttendanceLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderAttendanceService {

    static final int MAX_CODE_LENGTH = 500;
    static final int MAX_NOTES_LENGTH = 1000;
    private static final long MINUTE_MILLIS = 60_000L;

    // A path segment must match exactly: "/work-orders/12" must not validate work order 123 (nor "/work-orders/12abc").
    private static final Pattern WORK_ORDER_PATH = Pattern.compile("/work-orders/(\\d+)(?=$|[/?#\\s])");
    private static final Pattern ASSET_PATH = Pattern.compile("/assets/(\\d+)(?=$|[/?#\\s])");

    private final WorkOrderAttendanceLogRepository attendanceLogRepository;
    private final WorkOrderService workOrderService;

    @Transactional
    public AttendanceLogShowDTO checkIn(Long workOrderId, AttendanceCheckInDTO dto, User user) {
        WorkOrder workOrder = findActionableWorkOrder(workOrderId, user);
        if (Status.COMPLETE.equals(workOrder.getStatus()))
            throw new CustomException("A completed work order does not accept check-ins", HttpStatus.BAD_REQUEST);

        String code = dto.getCode() == null ? "" : dto.getCode().trim();
        if (code.isEmpty()) throw new CustomException("code is required", HttpStatus.BAD_REQUEST);
        if (code.length() > MAX_CODE_LENGTH)
            throw new CustomException("code must not exceed " + MAX_CODE_LENGTH + " characters",
                    HttpStatus.BAD_REQUEST);
        if (!isValidCode(code, workOrder))
            throw new CustomException("The scanned code does not match this work order or its asset",
                    HttpStatus.BAD_REQUEST);
        validateCoordinates(dto.getLatitude(), dto.getLongitude());
        String notes = cleanNotes(dto.getNotes());

        Long companyId = user.getCompany().getId();
        Optional<WorkOrderAttendanceLog> open = attendanceLogRepository
                .findFirstByTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(user.getId(),
                        companyId);
        if (open.isPresent()) {
            Long openWorkOrderId = open.get().getWorkOrder().getId();
            throw new CustomException("You already have an open check-in in work order " + openWorkOrderId
                    + ". Check out first.", HttpStatus.CONFLICT);
        }

        // Reuse the regular status change (history, webhooks, notifications). Any exception propagates and rolls back
        // the transaction, so no record is created.
        if (!Status.IN_PROGRESS.equals(workOrder.getStatus())) {
            WorkOrderChangeStatusDTO statusDTO = new WorkOrderChangeStatusDTO();
            statusDTO.setStatus(Status.IN_PROGRESS);
            workOrderService.changeStatus(statusDTO, workOrder.getId(), user, null);
        }

        WorkOrderAttendanceLog log = new WorkOrderAttendanceLog();
        log.setCompany(user.getCompany());
        log.setWorkOrder(workOrder);
        log.setTechnician(user);
        log.setAsset(workOrder.getAsset());
        log.setCheckInTime(now());
        log.setCheckInLat(dto.getLatitude());
        log.setCheckInLng(dto.getLongitude());
        log.setQrCodeScanned(code);
        log.setNotes(notes);
        return toDto(attendanceLogRepository.save(log));
    }

    @Transactional
    public AttendanceLogShowDTO checkOut(Long workOrderId, AttendanceCheckOutDTO dto, User user) {
        // A completed work order still accepts the check-out: otherwise an open record could never be closed.
        WorkOrder workOrder = findActionableWorkOrder(workOrderId, user);

        String code = dto.getCode() == null ? "" : dto.getCode().trim();
        if (!code.isEmpty()) {
            if (code.length() > MAX_CODE_LENGTH)
                throw new CustomException("code must not exceed " + MAX_CODE_LENGTH + " characters",
                        HttpStatus.BAD_REQUEST);
            if (!isValidCode(code, workOrder))
                throw new CustomException("The scanned code does not match this work order or its asset",
                        HttpStatus.BAD_REQUEST);
        }
        validateCoordinates(dto.getLatitude(), dto.getLongitude());
        String notes = cleanNotes(dto.getNotes());

        WorkOrderAttendanceLog log = attendanceLogRepository
                .findFirstByWorkOrderIdAndTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                        workOrder.getId(), user.getId(), user.getCompany().getId())
                .orElseThrow(() -> new CustomException("No open check-in found for you in this work order",
                        HttpStatus.NOT_FOUND));

        Date checkOutTime = now();
        log.setCheckOutTime(checkOutTime);
        log.setCheckOutLat(dto.getLatitude());
        log.setCheckOutLng(dto.getLongitude());
        log.setTotalMinutes(computeMinutes(log.getCheckInTime(), checkOutTime));
        if (notes != null) {
            String merged = log.getNotes() == null || log.getNotes().isEmpty() ? notes : log.getNotes() + "\n" + notes;
            if (merged.length() > MAX_NOTES_LENGTH)
                throw new CustomException("notes must not exceed " + MAX_NOTES_LENGTH + " characters in total",
                        HttpStatus.BAD_REQUEST);
            log.setNotes(merged);
        }
        return toDto(attendanceLogRepository.save(log));
    }

    @Transactional(readOnly = true)
    public List<AttendanceLogShowDTO> getByWorkOrder(Long workOrderId, User user) {
        WorkOrder workOrder = findViewableWorkOrder(workOrderId, user);
        return attendanceLogRepository
                .findTop100ByWorkOrderIdAndCompanyIdOrderByCheckInTimeDesc(workOrder.getId(),
                        user.getCompany().getId())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceLogShowDTO> getActive(Long workOrderId, User user) {
        WorkOrder workOrder = findViewableWorkOrder(workOrderId, user);
        return attendanceLogRepository
                .findFirstByWorkOrderIdAndTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                        workOrder.getId(), user.getId(), user.getCompany().getId())
                .map(this::toDto);
    }

    /**
     * Current time; overridable in tests (no global Clock bean exists).
     */
    Date now() {
        return new Date();
    }

    private WorkOrder findCompanyWorkOrder(Long id, User user) {
        return workOrderService.findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Work Order not found", HttpStatus.NOT_FOUND));
    }

    private WorkOrder findActionableWorkOrder(Long id, User user) {
        WorkOrder workOrder = findCompanyWorkOrder(id, user);
        // canBeEditedBy covers: editOther permission on work orders, creator, primaryUser, assignedTo and team members.
        if (!workOrder.canBeEditedBy(user)) throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
        return workOrder;
    }

    private WorkOrder findViewableWorkOrder(Long id, User user) {
        WorkOrder workOrder = findCompanyWorkOrder(id, user);
        if (!workOrder.canBeViewedBy(user)) throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        return workOrder;
    }

    static long computeMinutes(Date checkIn, Date checkOut) {
        long millis = checkOut.getTime() - checkIn.getTime();
        return Math.max(0L, Math.round(millis / (double) MINUTE_MILLIS));
    }

    static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) return;
        if (latitude == null || longitude == null)
            throw new CustomException("latitude and longitude must be provided together", HttpStatus.BAD_REQUEST);
        if (latitude.isNaN() || latitude.isInfinite() || latitude < -90 || latitude > 90)
            throw new CustomException("latitude must be between -90 and 90", HttpStatus.BAD_REQUEST);
        if (longitude.isNaN() || longitude.isInfinite() || longitude < -180 || longitude > 180)
            throw new CustomException("longitude must be between -180 and 180", HttpStatus.BAD_REQUEST);
    }

    static String cleanNotes(String notes) {
        if (notes == null) return null;
        String trimmed = notes.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_NOTES_LENGTH)
            throw new CustomException("notes must not exceed " + MAX_NOTES_LENGTH + " characters",
                    HttpStatus.BAD_REQUEST);
        return trimmed;
    }

    private static boolean isValidCode(String code, WorkOrder workOrder) {
        Asset asset = workOrder.getAsset();
        return isValidCode(code, workOrder.getId(), workOrder.getCustomId(),
                asset == null ? null : asset.getId(), asset == null ? null : asset.getCustomId());
    }

    /**
     * A code is valid if, after trim, it (a) contains the path segment /work-orders/{workOrderId}, (b) contains
     * /assets/{assetId}, (c) is exactly WO:{workOrderId} or ASSET:{assetId} (case-insensitive) or (d) equals the
     * customId of the work order or of the asset (case-insensitive).
     */
    static boolean isValidCode(String rawCode, Long workOrderId, String workOrderCustomId, Long assetId,
                               String assetCustomId) {
        if (rawCode == null) return false;
        String code = rawCode.trim();
        if (code.isEmpty()) return false;

        if (workOrderId != null && containsSegment(WORK_ORDER_PATH, code, workOrderId)) return true;
        if (assetId != null && containsSegment(ASSET_PATH, code, assetId)) return true;
        if (workOrderId != null && code.equalsIgnoreCase("WO:" + workOrderId)) return true;
        if (assetId != null && code.equalsIgnoreCase("ASSET:" + assetId)) return true;
        if (workOrderCustomId != null && !workOrderCustomId.trim().isEmpty()
                && code.equalsIgnoreCase(workOrderCustomId.trim())) return true;
        return assetCustomId != null && !assetCustomId.trim().isEmpty()
                && code.equalsIgnoreCase(assetCustomId.trim());
    }

    private static boolean containsSegment(Pattern pattern, String code, Long expectedId) {
        String expected = String.valueOf(expectedId);
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            if (expected.equals(matcher.group(1))) return true;
        }
        return false;
    }

    AttendanceLogShowDTO toDto(WorkOrderAttendanceLog log) {
        AttendanceLogShowDTO dto = new AttendanceLogShowDTO();
        dto.setId(log.getId());
        dto.setWorkOrderId(log.getWorkOrder().getId());
        dto.setTechnicianId(log.getTechnician().getId());
        dto.setTechnicianName(fullName(log.getTechnician()));
        dto.setAssetId(log.getAsset() == null ? null : log.getAsset().getId());
        dto.setCheckInTime(log.getCheckInTime());
        dto.setCheckOutTime(log.getCheckOutTime());
        dto.setCheckInLat(log.getCheckInLat());
        dto.setCheckInLng(log.getCheckInLng());
        dto.setCheckOutLat(log.getCheckOutLat());
        dto.setCheckOutLng(log.getCheckOutLng());
        dto.setQrCodeScanned(log.getQrCodeScanned());
        dto.setTotalMinutes(log.getTotalMinutes());
        dto.setNotes(log.getNotes());
        dto.setActive(log.getCheckOutTime() == null);
        return dto;
    }

    private static String fullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        return (first + " " + last).trim();
    }
}
