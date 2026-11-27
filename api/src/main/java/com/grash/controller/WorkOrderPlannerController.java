package com.grash.controller;

import com.grash.dto.planner.PlannerResponseDTO;
import com.grash.dto.planner.WorkOrderScheduleDTO;
import com.grash.dto.planner.WorkOrderScheduleResponseDTO;
import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.service.UserService;
import com.grash.service.WorkOrderPlannerService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;

@RestController
@RequestMapping("/work-orders")
@Tag(name = "Work Orders Planner", description = "Visual planner operations on work orders")
@RequiredArgsConstructor
public class WorkOrderPlannerController {

    static final long MAX_RANGE_DAYS = 92;

    private final WorkOrderPlannerService workOrderPlannerService;
    private final UserService userService;

    // Literal path: takes precedence over GET /work-orders/{id}
    @GetMapping("/planner")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public PlannerResponseDTO getPlanner(@Parameter(description = "Range start (ISO-8601)") @RequestParam("start") String start,
                                         @Parameter(description = "Range end (ISO-8601)") @RequestParam("end") String end,
                                         @Parameter(description = "Optional technician filter") @RequestParam(value =
                                                 "technicianId", required = false) Long technicianId,
                                         HttpServletRequest req) {
        Date startDate = parseDate(start, "start");
        Date endDate = parseDate(end, "end");
        if (endDate.before(startDate))
            throw new CustomException("end must not be before start", HttpStatus.BAD_REQUEST);
        if (endDate.getTime() - startDate.getTime() > MAX_RANGE_DAYS * 24L * 60 * 60 * 1000)
            throw new CustomException("Range must not exceed " + MAX_RANGE_DAYS + " days", HttpStatus.BAD_REQUEST);
        User user = userService.whoami(req);
        return workOrderPlannerService.getPlanner(startDate, endDate, technicianId, user);
    }

    @PatchMapping("/{id}/schedule")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public WorkOrderScheduleResponseDTO schedule(@Parameter(description = "Scheduling fields to update") @Valid @RequestBody WorkOrderScheduleDTO dto,
                                                 @PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        return workOrderPlannerService.schedule(id, dto, user);
    }

    static Date parseDate(String rawValue, String name) {
        // An unencoded "+" in a query string arrives as a space; ISO-8601 never contains spaces.
        String value = rawValue == null ? null : rawValue.trim().replace(' ', '+');
        try {
            return Date.from(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException | NullPointerException e) {
            try {
                // Without offset: interpret as UTC
                return Date.from(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC));
            } catch (DateTimeParseException | NullPointerException e2) {
                try {
                    return Date.from(Instant.parse(value));
                } catch (DateTimeParseException | NullPointerException e3) {
                    throw new CustomException("Invalid ISO-8601 date for " + name, HttpStatus.BAD_REQUEST);
                }
            }
        }
    }
}
