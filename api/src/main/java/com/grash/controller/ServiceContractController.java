package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.ServiceContractPatchDTO;
import com.grash.dto.ServiceContractPostDTO;
import com.grash.dto.ServiceContractShowDTO;
import com.grash.dto.ServiceContractUsageDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.mapper.ServiceContractMapper;
import com.grash.model.ServiceContract;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.ServiceContractService;
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
 * createPermissions.
 */
@RestController
@RequestMapping("/service-contracts")
@Tag(name = "Service contracts", description = "Operations on service contracts")
@RequiredArgsConstructor
public class ServiceContractController {

    private final ServiceContractService serviceContractService;
    private final UserService userService;
    private final ServiceContractMapper serviceContractMapper;

    @PostMapping("/search")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<Page<ServiceContractShowDTO>> search(@Parameter(description = "Service contract search " +
            "criteria") @RequestBody SearchCriteria searchCriteria, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        searchCriteria.filterCompany(user);
        return ResponseEntity.ok(serviceContractService.findBySearchCriteria(searchCriteria)
                .map(serviceContractMapper::toShowDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ServiceContractShowDTO getById(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        return serviceContractMapper.toShowDto(findOwned(id, user));
    }

    @GetMapping("/{id}/usage")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ServiceContractUsageDTO getUsage(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        ServiceContract contract = findOwned(id, user);
        return serviceContractService.getUsage(contract.getId());
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ServiceContractShowDTO create(@Parameter(description = "Service contract to create") @Valid
                                         @RequestBody ServiceContractPostDTO contractReq,
                                         HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        return serviceContractMapper.toShowDto(serviceContractService.create(contractReq, user.getCompany()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ServiceContractShowDTO patch(@Parameter(description = "Service contract fields to update") @Valid
                                        @RequestBody ServiceContractPatchDTO contractReq,
                                        @PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        return serviceContractMapper.toShowDto(serviceContractService.update(id, contractReq, user.getCompany()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanWrite(user);
        findOwned(id, user);
        serviceContractService.delete(id);
        return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"), HttpStatus.OK);
    }

    private ServiceContract findOwned(Long id, User user) {
        ServiceContract contract = serviceContractService.findById(id)
                .orElseThrow(() -> new CustomException("Service contract not found", HttpStatus.NOT_FOUND));
        if (contract.getCompany() == null || !contract.getCompany().getId().equals(user.getCompany().getId()))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        return contract;
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
