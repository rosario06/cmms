package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.SlaPolicyPatchDTO;
import com.grash.dto.SlaPolicyPostDTO;
import com.grash.dto.SlaPolicyShowDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.mapper.SlaPolicyMapper;
import com.grash.model.SlaPolicy;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.SlaPolicyService;
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
 * createPermissions. A policy without service contract is a general policy of the company.
 */
@RestController
@RequestMapping("/sla-policies")
@Tag(name = "SLA policies", description = "Operations on SLA policies")
@RequiredArgsConstructor
public class SlaPolicyController {

    private final SlaPolicyService slaPolicyService;
    private final UserService userService;
    private final SlaPolicyMapper slaPolicyMapper;

    @PostMapping("/search")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<Page<SlaPolicyShowDTO>> search(@Parameter(description = "SLA policy search criteria")
                                                         @RequestBody SearchCriteria searchCriteria,
                                                         HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        searchCriteria.filterCompany(user);
        return ResponseEntity.ok(slaPolicyService.findBySearchCriteria(searchCriteria)
                .map(slaPolicyMapper::toShowDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public SlaPolicyShowDTO getById(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        return slaPolicyMapper.toShowDto(findOwned(id, user));
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public SlaPolicyShowDTO create(@Parameter(description = "SLA policy to create") @Valid
                                   @RequestBody SlaPolicyPostDTO policyReq, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        return slaPolicyMapper.toShowDto(slaPolicyService.create(policyReq, user.getCompany()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public SlaPolicyShowDTO patch(@Parameter(description = "SLA policy fields to update") @Valid
                                  @RequestBody SlaPolicyPatchDTO policyReq, @PathVariable("id") Long id,
                                  HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        return slaPolicyMapper.toShowDto(slaPolicyService.update(id, policyReq, user.getCompany()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        slaPolicyService.delete(id);
        return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"), HttpStatus.OK);
    }

    private SlaPolicy findOwned(Long id, User user) {
        SlaPolicy policy = slaPolicyService.findById(id)
                .orElseThrow(() -> new CustomException("SLA policy not found", HttpStatus.NOT_FOUND));
        if (policy.getCompany() == null || !policy.getCompany().getId().equals(user.getCompany().getId()))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        return policy;
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
