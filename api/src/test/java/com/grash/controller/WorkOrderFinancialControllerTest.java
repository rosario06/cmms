package com.grash.controller;

import com.grash.dto.ProfitabilityReportDTO;
import com.grash.dto.WorkOrderFinancialsDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import com.grash.service.UserService;
import com.grash.service.WorkOrderFinancialService;
import com.grash.service.WorkOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderFinancialControllerTest {

    @Mock
    private WorkOrderFinancialService workOrderFinancialService;
    @Mock
    private WorkOrderService workOrderService;
    @Mock
    private UserService userService;
    @Mock
    private HttpServletRequest request;

    private WorkOrderFinancialController controller;
    private Company company;

    @BeforeEach
    void setUp() {
        controller = new WorkOrderFinancialController(workOrderFinancialService, workOrderService, userService);
        company = new Company();
        company.setId(1L);
    }

    private User userWithView(Set<PermissionEntity> view) {
        User user = new User();
        user.setId(1L);
        user.setCompany(company);
        user.setRole(Role.builder().id(1L).roleType(RoleType.ROLE_CLIENT).name("Client")
                .viewPermissions(new HashSet<>(view)).build());
        when(userService.whoami(request)).thenReturn(user);
        return user;
    }

    private WorkOrder workOrderOf(Long companyId) {
        Company other = new Company();
        other.setId(companyId);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(10L);
        workOrder.setCompany(other);
        when(workOrderService.findById(10L)).thenReturn(Optional.of(workOrder));
        return workOrder;
    }

    @Test
    void financials_withoutAnalytics_returns403() {
        userWithView(Set.of(PermissionEntity.WORK_ORDERS));
        workOrderOf(1L);

        CustomException e = assertThrows(CustomException.class, () -> controller.getFinancials(10L, request));

        assertEquals(HttpStatus.FORBIDDEN, e.getHttpStatus());
        verify(workOrderFinancialService, never()).getFinancials(any());
    }

    @Test
    void financials_ofAnotherCompany_returns403() {
        userWithView(Set.of(PermissionEntity.ANALYTICS));
        workOrderOf(2L);

        CustomException e = assertThrows(CustomException.class, () -> controller.getFinancials(10L, request));

        assertEquals(HttpStatus.FORBIDDEN, e.getHttpStatus());
        verify(workOrderFinancialService, never()).getFinancials(any());
    }

    @Test
    void financials_notFound_returns404() {
        userWithView(Set.of(PermissionEntity.ANALYTICS));
        when(workOrderService.findById(10L)).thenReturn(Optional.empty());

        CustomException e = assertThrows(CustomException.class, () -> controller.getFinancials(10L, request));

        assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
    }

    @Test
    void financials_withAnalytics_returnsBreakdown() {
        userWithView(Set.of(PermissionEntity.ANALYTICS));
        WorkOrder workOrder = workOrderOf(1L);
        WorkOrderFinancialsDTO dto = new WorkOrderFinancialsDTO();
        when(workOrderFinancialService.getFinancials(workOrder)).thenReturn(dto);

        assertSame(dto, controller.getFinancials(10L, request));
    }

    @Test
    void profitability_withoutAnalytics_returns403() {
        userWithView(Set.of(PermissionEntity.WORK_ORDERS));

        CustomException e = assertThrows(CustomException.class,
                () -> controller.getProfitability(null, null, null, request));

        assertEquals(HttpStatus.FORBIDDEN, e.getHttpStatus());
        verify(workOrderFinancialService, never()).getProfitability(any(), any(), any(), any());
    }

    @Test
    void profitability_defaultsToLast90Days_ofTheUserCompany() {
        userWithView(Set.of(PermissionEntity.ANALYTICS));
        ProfitabilityReportDTO report = new ProfitabilityReportDTO();
        when(workOrderFinancialService.getProfitability(eq(1L), any(), any(), eq(7L))).thenReturn(report);

        assertSame(report, controller.getProfitability(null, null, 7L, request));

        ArgumentCaptor<Date> start = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(workOrderFinancialService).getProfitability(eq(1L), start.capture(), end.capture(), eq(7L));
        assertEquals(90L * 24 * 60 * 60 * 1000, end.getValue().getTime() - start.getValue().getTime());
    }

    @Test
    void profitability_endBeforeStart_returns400() {
        userWithView(Set.of(PermissionEntity.ANALYTICS));

        CustomException e = assertThrows(CustomException.class, () -> controller.getProfitability(
                "2026-02-01T00:00:00Z", "2026-01-01T00:00:00Z", null, request));

        assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
        assertTrue(e.getMessage().contains("end"));
    }
}
