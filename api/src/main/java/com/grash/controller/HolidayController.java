package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.HolidayPatchDTO;
import com.grash.dto.HolidayPostDTO;
import com.grash.dto.HolidayShowDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.mapper.HolidayMapper;
import com.grash.model.Holiday;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.HolidayService;
import com.grash.service.UserService;
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

/**
 * Permissions: reading requires ANALYTICS in the viewPermissions of the role, writing requires SETTINGS in its
 * createPermissions. Holidays only affect the SLA deadlines computed after the change (no retroactive recalculation).
 */
@RestController
@RequestMapping("/holidays")
@Tag(name = "Holidays", description = "Operations on holidays (excluded from the business hours)")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;
    private final UserService userService;
    private final HolidayMapper holidayMapper;

    @PostMapping("/search")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<Page<HolidayShowDTO>> search(@Parameter(description = "Holiday search criteria")
                                                       @RequestBody SearchCriteria searchCriteria,
                                                       @Parameter(description = "Only the holidays of this year " +
                                                               "(recurring ones always match)")
                                                       @RequestParam(required = false) Integer year,
                                                       HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        if (year != null && (year < 1900 || year > 2200))
            throw new CustomException("Invalid year", HttpStatus.BAD_REQUEST);
        searchCriteria.filterCompany(user);
        return ResponseEntity.ok(holidayService.findBySearchCriteria(searchCriteria, year)
                .map(holidayMapper::toShowDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public HolidayShowDTO getById(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        return holidayMapper.toShowDto(findOwned(id, user));
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public HolidayShowDTO create(@Parameter(description = "Holiday to create") @Valid
                                 @RequestBody HolidayPostDTO holidayReq, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        return holidayMapper.toShowDto(holidayService.create(holidayReq, user.getCompany()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public HolidayShowDTO patch(@Parameter(description = "Holiday fields to update") @Valid
                                @RequestBody HolidayPatchDTO holidayReq, @PathVariable("id") Long id,
                                HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        return holidayMapper.toShowDto(holidayService.update(id, holidayReq, user.getCompany()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        holidayService.delete(id);
        return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"), HttpStatus.OK);
    }

    private Holiday findOwned(Long id, User user) {
        Holiday holiday = holidayService.findById(id)
                .orElseThrow(() -> new CustomException("Holiday not found", HttpStatus.NOT_FOUND));
        if (holiday.getCompany() == null || !holiday.getCompany().getId().equals(user.getCompany().getId()))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        return holiday;
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
