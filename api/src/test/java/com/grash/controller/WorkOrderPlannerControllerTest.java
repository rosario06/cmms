package com.grash.controller;

import com.grash.dto.planner.PlannerEventDTO;
import com.grash.dto.planner.PlannerResponseDTO;
import com.grash.dto.planner.WorkOrderScheduleDTO;
import com.grash.dto.planner.WorkOrderScheduleResponseDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.Priority;
import com.grash.model.enums.RoleType;
import com.grash.model.enums.Status;
import com.grash.service.UserService;
import com.grash.service.WorkOrderPlannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.grash.utils.Helper.setCurrentUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkOrderPlannerController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkOrderPlannerControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkOrderPlannerService workOrderPlannerService;
    @MockitoBean
    private UserService userService;

    private User clientUser;
    private User nonClientUser;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(1L);

        clientUser = new User();
        clientUser.setId(1L);
        clientUser.setEmail("john@test.com");
        clientUser.setEnabled(true);
        clientUser.setCompany(company);
        clientUser.setRole(Role.builder().id(1L).roleType(RoleType.ROLE_CLIENT).name("Client")
                .viewPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)))
                .viewOtherPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)))
                .editOtherPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS))).build());

        nonClientUser = new User();
        nonClientUser.setId(2L);
        nonClientUser.setEmail("admin@test.com");
        nonClientUser.setEnabled(true);
        nonClientUser.setRole(Role.builder().id(2L).roleType(RoleType.ROLE_SUPER_ADMIN).name("Super").build());
    }

    private PlannerEventDTO event(Long id) {
        return PlannerEventDTO.builder().id(id).title("Fix pump").priority(Priority.HIGH).status(Status.OPEN)
                .estimatedDurationMinutes(90L).primaryUserId(7L).primaryUserName("Jane Doe").assetName("Pump")
                .locationName("Plant").build();
    }

    // ---------- GET /work-orders/planner ----------

    @Test
    void planner_happyPath_returnsScheduledAndUnassigned() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(workOrderPlannerService.getPlanner(any(Date.class), any(Date.class), eq(7L), eq(clientUser)))
                .thenReturn(new PlannerResponseDTO(List.of(event(1L)), List.of(event(2L))));

        mockMvc.perform(get("/work-orders/planner")
                        .param("start", "2025-03-01T00:00:00Z")
                        .param("end", "2025-03-08T00:00:00Z")
                        .param("technicianId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduled[0].id").value(1))
                .andExpect(jsonPath("$.scheduled[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.scheduled[0].estimatedDurationMinutes").value(90))
                .andExpect(jsonPath("$.scheduled[0].primaryUserName").value("Jane Doe"))
                .andExpect(jsonPath("$.unassigned[0].id").value(2));
    }

    @Test
    void planner_endBeforeStart_returns400() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);

        mockMvc.perform(get("/work-orders/planner")
                        .param("start", "2025-03-08T00:00:00Z")
                        .param("end", "2025-03-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        verify(workOrderPlannerService, never()).getPlanner(any(), any(), any(), any());
    }

    @Test
    void planner_rangeOver92Days_returns400() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);

        mockMvc.perform(get("/work-orders/planner")
                        .param("start", "2025-01-01T00:00:00Z")
                        .param("end", "2025-04-05T00:00:00Z"))
                .andExpect(status().isBadRequest());
        verify(workOrderPlannerService, never()).getPlanner(any(), any(), any(), any());
    }

    @Test
    void planner_exactly92Days_isAccepted() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(workOrderPlannerService.getPlanner(any(Date.class), any(Date.class), any(), eq(clientUser)))
                .thenReturn(new PlannerResponseDTO());

        mockMvc.perform(get("/work-orders/planner")
                        .param("start", "2025-01-01T00:00:00Z")
                        .param("end", "2025-04-03T00:00:00Z"))
                .andExpect(status().isOk());
    }

    @Test
    void planner_invalidDate_returns400() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);

        mockMvc.perform(get("/work-orders/planner")
                        .param("start", "not-a-date")
                        .param("end", "2025-03-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void planner_requiresRoleClient() throws Exception {
        setCurrentUser(nonClientUser);
        when(userService.whoami(any())).thenReturn(nonClientUser);

        mockMvc.perform(get("/work-orders/planner")
                        .param("start", "2025-03-01T00:00:00Z")
                        .param("end", "2025-03-08T00:00:00Z"))
                .andExpect(status().isForbidden());
    }

    // ---------- PATCH /work-orders/{id}/schedule ----------

    @Test
    void schedule_happyPath_returnsEventAndWarnings() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(workOrderPlannerService.schedule(eq(5L), any(WorkOrderScheduleDTO.class), eq(clientUser)))
                .thenReturn(new WorkOrderScheduleResponseDTO(event(5L), Collections.emptyList(),
                        Collections.emptyList()));

        mockMvc.perform(patch("/work-orders/5/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estimatedStartDate\":\"2025-03-03T08:00:00.000Z\"," +
                                "\"estimatedDurationMinutes\":90,\"primaryUserId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.id").value(5))
                .andExpect(jsonPath("$.warnings").isEmpty());

        ArgumentCaptor<WorkOrderScheduleDTO> captor = ArgumentCaptor.forClass(WorkOrderScheduleDTO.class);
        verify(workOrderPlannerService).schedule(eq(5L), captor.capture(), eq(clientUser));
        assertEquals(90, captor.getValue().getEstimatedDurationMinutes());
        assertEquals(7L, captor.getValue().getPrimaryUserId());
        assertTrue(captor.getValue().isPrimaryUserIdPresent());
        assertEquals(1740988800000L, captor.getValue().getEstimatedStartDate().getTime());
    }

    @Test
    void schedule_explicitNullPrimaryUser_isPresentAndNull() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(workOrderPlannerService.schedule(eq(5L), any(WorkOrderScheduleDTO.class), eq(clientUser)))
                .thenReturn(new WorkOrderScheduleResponseDTO(event(5L), Collections.emptyList(),
                        Collections.emptyList()));

        mockMvc.perform(patch("/work-orders/5/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryUserId\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<WorkOrderScheduleDTO> captor = ArgumentCaptor.forClass(WorkOrderScheduleDTO.class);
        verify(workOrderPlannerService).schedule(eq(5L), captor.capture(), eq(clientUser));
        assertTrue(captor.getValue().isPrimaryUserIdPresent());
        assertNull(captor.getValue().getPrimaryUserId());
    }

    @Test
    void schedule_absentPrimaryUser_isNotPresent() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(workOrderPlannerService.schedule(eq(5L), any(WorkOrderScheduleDTO.class), eq(clientUser)))
                .thenReturn(new WorkOrderScheduleResponseDTO(event(5L), Collections.emptyList(),
                        Collections.emptyList()));

        mockMvc.perform(patch("/work-orders/5/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estimatedDurationMinutes\":30}"))
                .andExpect(status().isOk());

        ArgumentCaptor<WorkOrderScheduleDTO> captor = ArgumentCaptor.forClass(WorkOrderScheduleDTO.class);
        verify(workOrderPlannerService).schedule(eq(5L), captor.capture(), eq(clientUser));
        assertFalse(captor.getValue().isPrimaryUserIdPresent());
    }

    @Test
    void schedule_overlap_returnsWarning() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(workOrderPlannerService.schedule(eq(5L), any(WorkOrderScheduleDTO.class), eq(clientUser)))
                .thenReturn(new WorkOrderScheduleResponseDTO(event(5L), List.of("TECHNICIAN_OVERLAP"),
                        List.of(9L)));

        mockMvc.perform(patch("/work-orders/5/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estimatedStartDate\":\"2025-03-03T08:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0]").value("TECHNICIAN_OVERLAP"))
                .andExpect(jsonPath("$.conflictingWorkOrderIds[0]").value(9));
    }

    @Test
    void schedule_durationOutOfRange_returns400() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);

        mockMvc.perform(patch("/work-orders/5/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estimatedDurationMinutes\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/work-orders/5/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estimatedDurationMinutes\":20161}"))
                .andExpect(status().isBadRequest());
        verify(workOrderPlannerService, never()).schedule(any(), any(), any());
    }

    @Test
    void schedule_cannotEdit_returns403() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(workOrderPlannerService.schedule(eq(5L), any(WorkOrderScheduleDTO.class), eq(clientUser)))
                .thenThrow(new CustomException("Forbidden", HttpStatus.FORBIDDEN));

        mockMvc.perform(patch("/work-orders/5/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryUserId\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void schedule_workOrderOfAnotherCompany_returns404() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(workOrderPlannerService.schedule(eq(99L), any(WorkOrderScheduleDTO.class), eq(clientUser)))
                .thenThrow(new CustomException("Work Order not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(patch("/work-orders/99/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryUserId\":7}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void schedule_requiresRoleClient() throws Exception {
        setCurrentUser(nonClientUser);
        when(userService.whoami(any())).thenReturn(nonClientUser);

        mockMvc.perform(patch("/work-orders/5/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryUserId\":7}"))
                .andExpect(status().isForbidden());
    }
}
