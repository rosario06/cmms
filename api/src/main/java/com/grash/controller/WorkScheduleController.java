package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.SuccessResponse;
import com.grash.dto.WorkScheduleMiniDTO;
import com.grash.dto.WorkSchedulePatchDTO;
import com.grash.dto.WorkSchedulePostDTO;
import com.grash.dto.WorkScheduleShowDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.WorkScheduleMapper;
import com.grash.model.User;
import com.grash.model.WorkSchedule;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.UserService;
import com.grash.service.WorkScheduleService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Permissions: reading requires ANALYTICS in the viewPermissions of the role, writing requires SETTINGS in its
 * createPermissions. The mini list (used by the selects) is also readable with PEOPLE_AND_TEAMS in viewPermissions
 * because the user form needs it.
 */
@RestController
@RequestMapping("/work-schedules")
@Tag(name = "Work schedules", description = "Operations on work schedules (business hours)")
@RequiredArgsConstructor
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;
    private final UserService userService;
    private final WorkScheduleMapper workScheduleMapper;

    @PostMapping("/search")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<Page<WorkScheduleShowDTO>> search(@Parameter(description = "Work schedule search criteria")
                                                            @RequestBody SearchCriteria searchCriteria,
                                                            HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        searchCriteria.filterCompany(user);
        return ResponseEntity.ok(workScheduleService.findBySearchCriteria(searchCriteria)
                .map(workScheduleMapper::toShowDto));
    }

    @GetMapping("/mini")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<WorkScheduleMiniDTO> getMini(HttpServletRequest req) {
        User user = userService.whoami(req);
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.ANALYTICS)
                && !user.getRole().getViewPermissions().contains(PermissionEntity.PEOPLE_AND_TEAMS))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        return workScheduleService.findByCompany(user.getCompany().getId()).stream()
                .map(workScheduleMapper::toMiniDto).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public WorkScheduleShowDTO getById(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        return workScheduleMapper.toShowDto(findOwned(id, user));
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public WorkScheduleShowDTO create(@Parameter(description = "Work schedule to create") @Valid
                                      @RequestBody WorkSchedulePostDTO scheduleReq, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        return workScheduleMapper.toShowDto(workScheduleService.create(scheduleReq, user.getCompany()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public WorkScheduleShowDTO patch(@Parameter(description = "Work schedule fields to update") @Valid
                                     @RequestBody WorkSchedulePatchDTO scheduleReq, @PathVariable("id") Long id,
                                     HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        return workScheduleMapper.toShowDto(workScheduleService.update(id, scheduleReq, user.getCompany()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        workScheduleService.delete(id);
        return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"), HttpStatus.OK);
    }

    private WorkSchedule findOwned(Long id, User user) {
        WorkSchedule schedule = workScheduleService.findById(id)
                .orElseThrow(() -> new CustomException("Work schedule not found", HttpStatus.NOT_FOUND));
        if (schedule.getCompany() == null || !schedule.getCompany().getId().equals(user.getCompany().getId()))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        return schedule;
    }

    private static void checkCanRead(User user) {
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.ANALYTICS))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
    }

    private static void checkCanWrite(User user) {
        if (!user.getRole().getCreatePermissions().contains(PermissionEntity.SETTINGS))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
    }
}
