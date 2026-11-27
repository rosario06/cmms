package com.grash.controller;

import com.grash.dto.attendance.AttendanceCheckInDTO;
import com.grash.dto.attendance.AttendanceCheckOutDTO;
import com.grash.dto.attendance.AttendanceLogShowDTO;
import com.grash.model.User;
import com.grash.service.UserService;
import com.grash.service.WorkOrderAttendanceService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/work-orders")
@Tag(name = "Work Orders Attendance", description = "QR check-in / check-out on work orders")
@RequiredArgsConstructor
public class WorkOrderAttendanceController {

    private final WorkOrderAttendanceService attendanceService;
    private final UserService userService;

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AttendanceLogShowDTO checkIn(@Parameter(description = "Scanned code and optional location") @RequestBody AttendanceCheckInDTO dto,
                                        @PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        return attendanceService.checkIn(id, dto, user);
    }

    @PostMapping("/{id}/check-out")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public AttendanceLogShowDTO checkOut(@Parameter(description = "Optional code, location and notes") @RequestBody AttendanceCheckOutDTO dto,
                                         @PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        return attendanceService.checkOut(id, dto, user);
    }

    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public List<AttendanceLogShowDTO> getAttendance(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        return attendanceService.getByWorkOrder(id, user);
    }

    @GetMapping("/{id}/attendance/active")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<AttendanceLogShowDTO> getActive(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        Optional<AttendanceLogShowDTO> active = attendanceService.getActive(id, user);
        return active.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
