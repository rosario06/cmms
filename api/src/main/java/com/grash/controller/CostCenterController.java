package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.CostCenterPatchDTO;
import com.grash.dto.CostCenterPostDTO;
import com.grash.dto.CostCenterShowDTO;
import com.grash.dto.CostCenterSummaryDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.mapper.CostCenterMapper;
import com.grash.model.CostCenter;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.CostCenterService;
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
 * Read access requires ANALYTICS (viewPermissions); write access requires SETTINGS (createPermissions).
 */
@RestController
@RequestMapping("/cost-centers")
@Tag(name = "Cost Centers", description = "Operations on cost centers")
@RequiredArgsConstructor
public class CostCenterController {

    private final CostCenterService costCenterService;
    private final UserService userService;
    private final CostCenterMapper costCenterMapper;

    @PostMapping("/search")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<Page<CostCenterShowDTO>> search(@Parameter(description = "Cost center search criteria") @RequestBody SearchCriteria searchCriteria,
                                                          HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        searchCriteria.filterCompany(user);
        return ResponseEntity.ok(costCenterService.findBySearchCriteria(searchCriteria)
                .map(costCenterMapper::toShowDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public CostCenterShowDTO getById(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        return costCenterMapper.toShowDto(findOwned(id, user));
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public CostCenterSummaryDTO getSummary(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        return costCenterService.getSummary(findOwned(id, user));
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public CostCenterShowDTO create(@Parameter(description = "Cost center to create") @Valid @RequestBody CostCenterPostDTO costCenterReq,
                                    HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        return costCenterMapper.toShowDto(costCenterService.create(costCenterReq, user));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public CostCenterShowDTO patch(@Parameter(description = "Cost center fields to update") @Valid @RequestBody CostCenterPatchDTO costCenterReq,
                                   @PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        return costCenterMapper.toShowDto(costCenterService.update(id, costCenterReq, user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        costCenterService.delete(id);
        return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"), HttpStatus.OK);
    }

    private void checkCanRead(User user) {
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.ANALYTICS))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
    }

    private void checkCanWrite(User user) {
        if (!user.getRole().getCreatePermissions().contains(PermissionEntity.SETTINGS))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
    }

    private CostCenter findOwned(Long id, User user) {
        CostCenter costCenter = costCenterService.findById(id)
                .orElseThrow(() -> new CustomException("Cost center not found", HttpStatus.NOT_FOUND));
        if (costCenter.getCompany() == null || !costCenter.getCompany().getId().equals(user.getCompany().getId()))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        return costCenter;
    }
}
