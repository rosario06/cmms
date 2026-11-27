package com.grash.controller;

import com.grash.dto.ProfitabilityReportDTO;
import com.grash.dto.WorkOrderFinancialsDTO;
import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.UserService;
import com.grash.service.WorkOrderFinancialService;
import com.grash.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * Costs, billable amounts and margins. All endpoints require ANALYTICS in the role's viewPermissions, so basic
 * technicians cannot see margins.
 */
@RestController
@Tag(name = "Work Order Financials", description = "Costs, billable amounts and margins of work orders")
@RequiredArgsConstructor
public class WorkOrderFinancialController {

    static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final WorkOrderFinancialService workOrderFinancialService;
    private final WorkOrderService workOrderService;
    private final UserService userService;

    @GetMapping("/work-orders/{id}/financials")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public WorkOrderFinancialsDTO getFinancials(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        WorkOrder workOrder = workOrderService.findById(id)
                .orElseThrow(() -> new CustomException("Work order not found", HttpStatus.NOT_FOUND));
        if (workOrder.getCompany() == null || !workOrder.getCompany().getId().equals(user.getCompany().getId()))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        return workOrderFinancialService.getFinancials(workOrder);
    }

    @GetMapping("/financial-reports/profitability")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ProfitabilityReportDTO getProfitability(@Parameter(description = "Range start (ISO-8601), default 90 days ago") @RequestParam(value = "start", required = false) String start,
                                                   @Parameter(description = "Range end (ISO-8601), default now") @RequestParam(value = "end", required = false) String end,
                                                   @Parameter(description = "Optional customer filter") @RequestParam(value = "customerId", required = false) Long customerId,
                                                   HttpServletRequest req) {
        User user = userService.whoami(req);
        checkCanRead(user);
        Date endDate = end == null || end.isBlank() ? new Date() : WorkOrderPlannerController.parseDate(end, "end");
        Date startDate = start == null || start.isBlank()
                ? new Date(endDate.getTime() - WorkOrderFinancialService.DEFAULT_REPORT_DAYS * DAY_MS)
                : WorkOrderPlannerController.parseDate(start, "start");
        if (endDate.before(startDate))
            throw new CustomException("end must not be before start", HttpStatus.BAD_REQUEST);
        return workOrderFinancialService.getProfitability(user.getCompany().getId(), startDate, endDate, customerId);
    }

    private void checkCanRead(User user) {
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.ANALYTICS))
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
    }
}
