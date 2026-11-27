package com.grash.service;

import com.grash.dto.planner.PlannerEventDTO;
import com.grash.dto.planner.PlannerResponseDTO;
import com.grash.dto.planner.WorkOrderScheduleDTO;
import com.grash.dto.planner.WorkOrderScheduleResponseDTO;
import com.grash.dto.workOrder.WorkOrderPatchDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.WorkOrderMapper;
import com.grash.model.Asset;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.Priority;
import com.grash.model.enums.RoleType;
import com.grash.model.enums.Status;
import com.grash.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderPlannerServiceTest {

    private static final long HOUR = 3_600_000L;
    private static final long BASE = 1_740_988_800_000L; // 2025-03-03T08:00:00Z

    @InjectMocks
    private WorkOrderPlannerService service;

    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private WorkOrderService workOrderService;
    @Mock
    private UserService userService;
    @Mock
    private WorkOrderMapper workOrderMapper;

    private Company company;
    private User editor;
    private User viewer;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);

        editor = new User();
        editor.setId(1L);
        editor.setCompany(company);
        editor.setEnabled(true);
        editor.setRole(Role.builder().id(1L).roleType(RoleType.ROLE_CLIENT).name("Client")
                .viewPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)))
                .viewOtherPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)))
                .editOtherPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS))).build());

        viewer = new User();
        viewer.setId(3L);
        viewer.setCompany(company);
        viewer.setEnabled(true);
        viewer.setRole(Role.builder().id(3L).roleType(RoleType.ROLE_CLIENT).name("Viewer")
                .viewPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)))
                .viewOtherPermissions(new HashSet<>())
                .editOtherPermissions(new HashSet<>()).build());
    }

    private WorkOrder workOrder(Long id, Date start, double hours, User primary) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setTitle("WO " + id);
        wo.setStatus(Status.OPEN);
        wo.setPriority(Priority.MEDIUM);
        wo.setEstimatedStartDate(start);
        wo.setEstimatedDuration(hours);
        wo.setPrimaryUser(primary);
        wo.setCompany(company);
        return wo;
    }

    private User technician(Long id, boolean enabled) {
        User tech = new User();
        tech.setId(id);
        tech.setFirstName("Tech");
        tech.setLastName("N" + id);
        tech.setEnabled(enabled);
        tech.setCompany(company);
        return tech;
    }

    // ---------- findOverlappingWorkOrderIds ----------

    @Test
    void overlap_detectsPartialContainedAndIgnoresAdjacent() {
        Date newStart = new Date(BASE);
        // new interval: [08:00, 10:00)
        WorkOrder partial = workOrder(10L, new Date(BASE + HOUR), 2, null);      // 09:00-11:00 -> overlap
        WorkOrder contained = workOrder(11L, new Date(BASE + HOUR / 2), 0.5, null); // 08:30-09:00 -> overlap
        WorkOrder before = workOrder(12L, new Date(BASE - 2 * HOUR), 2, null);   // 06:00-08:00 -> adjacent
        WorkOrder after = workOrder(13L, new Date(BASE + 2 * HOUR), 1, null);    // 10:00-11:00 -> adjacent
        WorkOrder encloses = workOrder(14L, new Date(BASE - HOUR), 5, null);     // 07:00-12:00 -> overlap
        when(workOrderRepository.findTechnicianCandidates(eq(1L), eq(7L), eq(5L), any(Date.class), any(Date.class)))
                .thenReturn(List.of(partial, contained, before, after, encloses));

        List<Long> result = service.findOverlappingWorkOrderIds(1L, 7L, 5L, newStart, 120);

        assertEquals(List.of(10L, 11L, 14L), result);
    }

    @Test
    void overlap_ignoresCompletedAndWithoutStart() {
        WorkOrder done = workOrder(10L, new Date(BASE), 2, null);
        done.setStatus(Status.COMPLETE);
        WorkOrder noStart = workOrder(11L, null, 2, null);
        when(workOrderRepository.findTechnicianCandidates(eq(1L), eq(7L), eq(5L), any(Date.class), any(Date.class)))
                .thenReturn(List.of(done, noStart));

        assertTrue(service.findOverlappingWorkOrderIds(1L, 7L, 5L, new Date(BASE), 60).isEmpty());
    }

    @Test
    void overlap_queriesWindowEndingAtNewEnd() {
        when(workOrderRepository.findTechnicianCandidates(anyLong(), anyLong(), anyLong(), any(Date.class),
                any(Date.class))).thenReturn(Collections.emptyList());

        service.findOverlappingWorkOrderIds(1L, 7L, 5L, new Date(BASE), 90);

        ArgumentCaptor<Date> to = ArgumentCaptor.forClass(Date.class);
        verify(workOrderRepository).findTechnicianCandidates(eq(1L), eq(7L), eq(5L), any(Date.class), to.capture());
        assertEquals(BASE + 90 * 60_000L, to.getValue().getTime());
    }

    // ---------- schedule ----------

    @Test
    void schedule_workOrderNotInCompany_throws404() {
        when(workOrderService.findByIdAndCompany(99L, 1L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> service.schedule(99L, new WorkOrderScheduleDTO(), editor));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        verify(workOrderService, never()).patch(anyLong(), any(), any());
    }

    @Test
    void schedule_userCannotEdit_throws403() {
        WorkOrder wo = workOrder(5L, null, 0, null);
        wo.setCreatedBy(99L);
        when(workOrderService.findByIdAndCompany(5L, 1L)).thenReturn(Optional.of(wo));

        CustomException ex = assertThrows(CustomException.class,
                () -> service.schedule(5L, new WorkOrderScheduleDTO(), viewer));

        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        verify(workOrderService, never()).patch(anyLong(), any(), any());
    }

    @Test
    void schedule_completeWorkOrder_throws400() {
        WorkOrder wo = workOrder(5L, null, 0, null);
        wo.setStatus(Status.COMPLETE);
        when(workOrderService.findByIdAndCompany(5L, 1L)).thenReturn(Optional.of(wo));

        CustomException ex = assertThrows(CustomException.class,
                () -> service.schedule(5L, new WorkOrderScheduleDTO(), editor));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void schedule_dueDateBeforeStart_throws400() {
        WorkOrder wo = workOrder(5L, null, 0, null);
        when(workOrderService.findByIdAndCompany(5L, 1L)).thenReturn(Optional.of(wo));
        WorkOrderScheduleDTO dto = new WorkOrderScheduleDTO();
        dto.setEstimatedStartDate(new Date(BASE));
        dto.setDueDate(new Date(BASE - HOUR));

        CustomException ex = assertThrows(CustomException.class, () -> service.schedule(5L, dto, editor));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void schedule_technicianOfOtherCompany_throws400() {
        WorkOrder wo = workOrder(5L, null, 0, null);
        when(workOrderService.findByIdAndCompany(5L, 1L)).thenReturn(Optional.of(wo));
        when(userService.findByIdAndCompany(8L, 1L)).thenReturn(Optional.empty());
        WorkOrderScheduleDTO dto = new WorkOrderScheduleDTO();
        dto.setPrimaryUserId(8L);

        CustomException ex = assertThrows(CustomException.class, () -> service.schedule(5L, dto, editor));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        verify(workOrderService, never()).patch(anyLong(), any(), any());
    }

    @Test
    void schedule_disabledTechnician_throws400() {
        WorkOrder wo = workOrder(5L, null, 0, null);
        when(workOrderService.findByIdAndCompany(5L, 1L)).thenReturn(Optional.of(wo));
        when(userService.findByIdAndCompany(8L, 1L)).thenReturn(Optional.of(technician(8L, false)));
        WorkOrderScheduleDTO dto = new WorkOrderScheduleDTO();
        dto.setPrimaryUserId(8L);

        CustomException ex = assertThrows(CustomException.class, () -> service.schedule(5L, dto, editor));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void schedule_happyPath_appliesOnlyPresentFieldsViaPatch() {
        User tech = technician(7L, true);
        WorkOrder wo = workOrder(5L, null, 0, null);
        Date oldDue = new Date(BASE + 24 * HOUR);
        wo.setDueDate(oldDue);
        when(workOrderService.findByIdAndCompany(5L, 1L)).thenReturn(Optional.of(wo));
        when(userService.findByIdAndCompany(7L, 1L)).thenReturn(Optional.of(tech));
        when(workOrderRepository.findTechnicianCandidates(eq(1L), eq(7L), eq(5L), any(Date.class), any(Date.class)))
                .thenReturn(Collections.emptyList());
        WorkOrderPatchDTO patchDto = new WorkOrderPatchDTO();
        patchDto.setDueDate(oldDue);
        when(workOrderMapper.toPatchDto(wo)).thenReturn(patchDto);
        when(workOrderService.patch(eq(5L), any(WorkOrderPatchDTO.class), eq(editor))).thenReturn(wo);

        WorkOrderScheduleDTO dto = new WorkOrderScheduleDTO();
        dto.setEstimatedStartDate(new Date(BASE));
        dto.setEstimatedDurationMinutes(90);
        dto.setPrimaryUserId(7L);

        WorkOrderScheduleResponseDTO response = service.schedule(5L, dto, editor);

        ArgumentCaptor<WorkOrderPatchDTO> captor = ArgumentCaptor.forClass(WorkOrderPatchDTO.class);
        verify(workOrderService).patch(eq(5L), captor.capture(), eq(editor));
        assertEquals(new Date(BASE), captor.getValue().getEstimatedStartDate());
        assertEquals(1.5, captor.getValue().getEstimatedDuration(), 0.0001);
        assertEquals(tech, captor.getValue().getPrimaryUser());
        assertEquals(oldDue, captor.getValue().getDueDate()); // untouched
        assertTrue(response.getWarnings().isEmpty());
        assertEquals(5L, response.getEvent().getId());
    }

    @Test
    void schedule_explicitNullPrimaryUser_unassigns() {
        User tech = technician(7L, true);
        WorkOrder wo = workOrder(5L, new Date(BASE), 1, tech);
        when(workOrderService.findByIdAndCompany(5L, 1L)).thenReturn(Optional.of(wo));
        WorkOrderPatchDTO patchDto = new WorkOrderPatchDTO();
        patchDto.setPrimaryUser(tech);
        when(workOrderMapper.toPatchDto(wo)).thenReturn(patchDto);
        when(workOrderService.patch(eq(5L), any(WorkOrderPatchDTO.class), eq(editor))).thenReturn(wo);
        WorkOrderScheduleDTO dto = new WorkOrderScheduleDTO();
        dto.setPrimaryUserId(null); // explicit null -> present

        WorkOrderScheduleResponseDTO response = service.schedule(5L, dto, editor);

        ArgumentCaptor<WorkOrderPatchDTO> captor = ArgumentCaptor.forClass(WorkOrderPatchDTO.class);
        verify(workOrderService).patch(eq(5L), captor.capture(), eq(editor));
        assertNull(captor.getValue().getPrimaryUser());
        assertTrue(response.getWarnings().isEmpty());
        verify(workOrderRepository, never()).findTechnicianCandidates(anyLong(), anyLong(), anyLong(),
                any(Date.class), any(Date.class));
    }

    @Test
    void schedule_absentPrimaryUser_keepsCurrentOne() {
        User tech = technician(7L, true);
        WorkOrder wo = workOrder(5L, new Date(BASE), 1, tech);
        when(workOrderService.findByIdAndCompany(5L, 1L)).thenReturn(Optional.of(wo));
        WorkOrderPatchDTO patchDto = new WorkOrderPatchDTO();
        patchDto.setPrimaryUser(tech);
        when(workOrderMapper.toPatchDto(wo)).thenReturn(patchDto);
        when(workOrderRepository.findTechnicianCandidates(eq(1L), eq(7L), eq(5L), any(Date.class), any(Date.class)))
                .thenReturn(Collections.emptyList());
        when(workOrderService.patch(eq(5L), any(WorkOrderPatchDTO.class), eq(editor))).thenReturn(wo);
        WorkOrderScheduleDTO dto = new WorkOrderScheduleDTO();
        dto.setEstimatedDurationMinutes(30);

        service.schedule(5L, dto, editor);

        ArgumentCaptor<WorkOrderPatchDTO> captor = ArgumentCaptor.forClass(WorkOrderPatchDTO.class);
        verify(workOrderService).patch(eq(5L), captor.capture(), eq(editor));
        assertEquals(tech, captor.getValue().getPrimaryUser());
    }

    @Test
    void schedule_overlap_savesAndReturnsWarning() {
        User tech = technician(7L, true);
        WorkOrder wo = workOrder(5L, null, 0, null);
        WorkOrder other = workOrder(9L, new Date(BASE + HOUR / 2), 1, tech);
        when(workOrderService.findByIdAndCompany(5L, 1L)).thenReturn(Optional.of(wo));
        when(userService.findByIdAndCompany(7L, 1L)).thenReturn(Optional.of(tech));
        when(workOrderRepository.findTechnicianCandidates(eq(1L), eq(7L), eq(5L), any(Date.class), any(Date.class)))
                .thenReturn(List.of(other));
        when(workOrderMapper.toPatchDto(wo)).thenReturn(new WorkOrderPatchDTO());
        when(workOrderService.patch(eq(5L), any(WorkOrderPatchDTO.class), eq(editor))).thenReturn(wo);
        WorkOrderScheduleDTO dto = new WorkOrderScheduleDTO();
        dto.setEstimatedStartDate(new Date(BASE));
        dto.setEstimatedDurationMinutes(60);
        dto.setPrimaryUserId(7L);

        WorkOrderScheduleResponseDTO response = service.schedule(5L, dto, editor);

        verify(workOrderService).patch(eq(5L), any(WorkOrderPatchDTO.class), eq(editor)); // not blocked
        assertEquals(List.of("TECHNICIAN_OVERLAP"), response.getWarnings());
        assertEquals(List.of(9L), response.getConflictingWorkOrderIds());
    }

    // ---------- planner / event mapping ----------

    @Test
    void getPlanner_withoutViewPermission_throws403() {
        User noView = technician(4L, true);
        noView.setRole(Role.builder().id(4L).roleType(RoleType.ROLE_CLIENT).name("None")
                .viewPermissions(new HashSet<>()).viewOtherPermissions(new HashSet<>()).build());

        CustomException ex = assertThrows(CustomException.class,
                () -> service.getPlanner(new Date(BASE), new Date(BASE + HOUR), null, noView));

        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    void getPlanner_restrictsUsersWithoutViewOtherAndUsesNoTechnicianSentinel() {
        when(workOrderRepository.findPlannerScheduled(eq(1L), any(Date.class), any(Date.class), eq(-1L), eq(true),
                eq(3L))).thenReturn(Collections.emptyList());
        when(workOrderRepository.findPlannerUnassigned(eq(1L), eq(true), eq(3L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        PlannerResponseDTO result = service.getPlanner(new Date(BASE), new Date(BASE + HOUR), null, viewer);

        assertTrue(result.getScheduled().isEmpty());
        assertTrue(result.getUnassigned().isEmpty());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(workOrderRepository).findPlannerUnassigned(eq(1L), eq(true), eq(3L), pageable.capture());
        assertEquals(200, pageable.getValue().getPageSize());
    }

    @Test
    void getPlanner_userWithViewOther_isNotRestricted() {
        when(workOrderRepository.findPlannerScheduled(eq(1L), any(Date.class), any(Date.class), eq(7L), eq(false),
                eq(1L))).thenReturn(Collections.emptyList());
        when(workOrderRepository.findPlannerUnassigned(eq(1L), eq(false), eq(1L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        service.getPlanner(new Date(BASE), new Date(BASE + HOUR), 7L, editor);

        verify(workOrderRepository).findPlannerScheduled(eq(1L), any(Date.class), any(Date.class), eq(7L),
                eq(false), eq(1L));
    }

    @Test
    void toEvent_convertsHoursToMinutesAndHandlesNulls() {
        User tech = technician(7L, true);
        WorkOrder wo = workOrder(5L, new Date(BASE), 1.5, tech);
        Asset asset = new Asset();
        asset.setName("Pump");
        wo.setAsset(asset);

        PlannerEventDTO event = service.toEvent(wo);

        assertEquals(90L, event.getEstimatedDurationMinutes());
        assertEquals("Tech N7", event.getPrimaryUserName());
        assertEquals("Pump", event.getAssetName());
        assertNull(event.getLocationName());

        WorkOrder empty = workOrder(6L, null, 0, null);
        PlannerEventDTO emptyEvent = service.toEvent(empty);
        assertNull(emptyEvent.getEstimatedDurationMinutes());
        assertNull(emptyEvent.getPrimaryUserId());
    }
}
