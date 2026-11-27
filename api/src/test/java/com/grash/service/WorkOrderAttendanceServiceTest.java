package com.grash.service;

import com.grash.dto.WorkOrderChangeStatusDTO;
import com.grash.dto.attendance.AttendanceCheckInDTO;
import com.grash.dto.attendance.AttendanceCheckOutDTO;
import com.grash.dto.attendance.AttendanceLogShowDTO;
import com.grash.exception.CustomException;
import com.grash.model.Asset;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.WorkOrderAttendanceLog;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import com.grash.model.enums.Status;
import com.grash.repository.WorkOrderAttendanceLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderAttendanceServiceTest {

    private static final long BASE = 1_740_988_800_000L; // 2025-03-03T08:00:00Z

    @Mock
    private WorkOrderAttendanceLogRepository repository;
    @Mock
    private WorkOrderService workOrderService;

    private Date fixedNow = new Date(BASE);
    private WorkOrderAttendanceService service;

    private Company company;
    private User technician;
    private User stranger;
    private Asset asset;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        service = new WorkOrderAttendanceService(repository, workOrderService) {
            @Override
            Date now() {
                return fixedNow;
            }
        };

        company = new Company();
        company.setId(1L);

        technician = user(7L, "Jane", "Doe");
        stranger = user(8L, "Bob", "Roe");

        asset = new Asset();
        asset.setId(50L);
        asset.setCustomId("AST-000050");

        workOrder = new WorkOrder();
        workOrder.setId(12L);
        workOrder.setCustomId("WO-000012");
        workOrder.setStatus(Status.OPEN);
        workOrder.setPrimaryUser(technician);
        workOrder.setAsset(asset);
    }

    private User user(Long id, String first, String last) {
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setCompany(company);
        user.setEnabled(true);
        user.setRole(Role.builder().id(id).roleType(RoleType.ROLE_CLIENT).name("Client")
                .viewPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)))
                .viewOtherPermissions(new HashSet<>())
                .editOtherPermissions(new HashSet<>()).build());
        return user;
    }

    private AttendanceCheckInDTO checkInDto(String code) {
        AttendanceCheckInDTO dto = new AttendanceCheckInDTO();
        dto.setCode(code);
        return dto;
    }

    private void stubWorkOrder() {
        when(workOrderService.findByIdAndCompany(12L, 1L)).thenReturn(Optional.of(workOrder));
    }

    private WorkOrderAttendanceLog openLog(Date checkIn) {
        WorkOrderAttendanceLog log = new WorkOrderAttendanceLog();
        log.setId(99L);
        log.setWorkOrder(workOrder);
        log.setTechnician(technician);
        log.setAsset(asset);
        log.setCheckInTime(checkIn);
        return log;
    }

    // ---------- code parsing ----------

    @Test
    void code_workOrderPathSegment_isExact() {
        assertTrue(WorkOrderAttendanceService.isValidCode("https://x.com/app/work-orders/12", 12L, null, null, null));
        assertTrue(WorkOrderAttendanceService.isValidCode("  /work-orders/12  ", 12L, null, null, null));
        assertTrue(WorkOrderAttendanceService.isValidCode("https://x.com/app/work-orders/12/files", 12L, null, null,
                null));
        assertTrue(WorkOrderAttendanceService.isValidCode("https://x.com/app/work-orders/12?tab=1", 12L, null, null,
                null));
        assertTrue(WorkOrderAttendanceService.isValidCode("https://x.com/app/work-orders/12#top", 12L, null, null,
                null));
    }

    @Test
    void code_workOrder12DoesNotValidate123() {
        assertFalse(WorkOrderAttendanceService.isValidCode("https://x.com/app/work-orders/123", 12L, null, null,
                null));
        assertFalse(WorkOrderAttendanceService.isValidCode("https://x.com/app/work-orders/12abc", 12L, null, null,
                null));
        assertFalse(WorkOrderAttendanceService.isValidCode("https://x.com/app/work-orders/012", 12L, null, null,
                null));
        assertFalse(WorkOrderAttendanceService.isValidCode("https://x.com/app/work-orders/1", 12L, null, null, null));
        assertTrue(WorkOrderAttendanceService.isValidCode("https://x.com/app/work-orders/123", 123L, null, null,
                null));
    }

    @Test
    void code_segmentMustStartWithSlash() {
        assertFalse(WorkOrderAttendanceService.isValidCode("https://x.com/old-work-orders/12", 12L, null, null,
                null));
        assertFalse(WorkOrderAttendanceService.isValidCode("work-orders/12", 12L, null, null, null));
    }

    @Test
    void code_assetPathSegment() {
        assertTrue(WorkOrderAttendanceService.isValidCode("https://x.com/app/assets/50", 12L, null, 50L, null));
        assertTrue(WorkOrderAttendanceService.isValidCode("https://x.com/app/assets/50/work-orders", 12L, null, 50L,
                null));
        assertFalse(WorkOrderAttendanceService.isValidCode("https://x.com/app/assets/500", 12L, null, 50L, null));
        assertFalse(WorkOrderAttendanceService.isValidCode("https://x.com/app/assets/5", 12L, null, 50L, null));
        // order without asset: asset URL cannot validate
        assertFalse(WorkOrderAttendanceService.isValidCode("https://x.com/app/assets/50", 12L, null, null, null));
    }

    @Test
    void code_prefixForms_ignoreCase() {
        assertTrue(WorkOrderAttendanceService.isValidCode("WO:12", 12L, null, 50L, null));
        assertTrue(WorkOrderAttendanceService.isValidCode("wo:12", 12L, null, 50L, null));
        assertTrue(WorkOrderAttendanceService.isValidCode(" Asset:50 ", 12L, null, 50L, null));
        assertFalse(WorkOrderAttendanceService.isValidCode("WO:123", 12L, null, 50L, null));
        assertFalse(WorkOrderAttendanceService.isValidCode("WO:1", 12L, null, 50L, null));
        assertFalse(WorkOrderAttendanceService.isValidCode("ASSET:500", 12L, null, 50L, null));
        assertFalse(WorkOrderAttendanceService.isValidCode("xWO:12", 12L, null, 50L, null));
        assertFalse(WorkOrderAttendanceService.isValidCode("WO:12 ", 13L, null, 50L, null));
    }

    @Test
    void code_customIds_ignoreCase() {
        assertTrue(WorkOrderAttendanceService.isValidCode("wo-000012", 12L, "WO-000012", 50L, "AST-000050"));
        assertTrue(WorkOrderAttendanceService.isValidCode("ast-000050", 12L, "WO-000012", 50L, "AST-000050"));
        assertFalse(WorkOrderAttendanceService.isValidCode("WO-0000", 12L, "WO-000012", 50L, "AST-000050"));
        assertFalse(WorkOrderAttendanceService.isValidCode("WO-000013", 12L, "WO-000012", 50L, "AST-000050"));
    }

    @Test
    void code_blankOrNull_isInvalid() {
        assertFalse(WorkOrderAttendanceService.isValidCode(null, 12L, "", 50L, ""));
        assertFalse(WorkOrderAttendanceService.isValidCode("   ", 12L, "  ", 50L, "  "));
        assertFalse(WorkOrderAttendanceService.isValidCode("", 12L, "", 50L, ""));
    }

    // ---------- check-in ----------

    @Test
    void checkIn_workOrderNotFound_404() {
        when(workOrderService.findByIdAndCompany(12L, 1L)).thenReturn(Optional.empty());
        CustomException e = assertThrows(CustomException.class,
                () -> service.checkIn(12L, checkInDto("WO:12"), technician));
        assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void checkIn_notAssignedWithoutEditPermission_403() {
        stubWorkOrder();
        CustomException e = assertThrows(CustomException.class,
                () -> service.checkIn(12L, checkInDto("WO:12"), stranger));
        assertEquals(HttpStatus.FORBIDDEN, e.getHttpStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void checkIn_userWithEditOtherPermission_isAllowed() {
        stranger.getRole().getEditOtherPermissions().add(PermissionEntity.WORK_ORDERS);
        stubWorkOrder();
        when(repository.findFirstByTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(8L, 1L))
                .thenReturn(Optional.empty());
        when(repository.save(any(WorkOrderAttendanceLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceLogShowDTO dto = service.checkIn(12L, checkInDto("WO:12"), stranger);
        assertEquals(8L, dto.getTechnicianId());
    }

    @Test
    void checkIn_completedWorkOrder_400() {
        workOrder.setStatus(Status.COMPLETE);
        stubWorkOrder();
        CustomException e = assertThrows(CustomException.class,
                () -> service.checkIn(12L, checkInDto("WO:12"), technician));
        assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void checkIn_invalidOrMissingCode_400() {
        stubWorkOrder();
        CustomException wrong = assertThrows(CustomException.class,
                () -> service.checkIn(12L, checkInDto("https://x.com/app/work-orders/123"), technician));
        assertEquals(HttpStatus.BAD_REQUEST, wrong.getHttpStatus());
        CustomException missing = assertThrows(CustomException.class,
                () -> service.checkIn(12L, checkInDto("   "), technician));
        assertEquals(HttpStatus.BAD_REQUEST, missing.getHttpStatus());
        CustomException tooLong = assertThrows(CustomException.class,
                () -> service.checkIn(12L, checkInDto("WO:12" + "x".repeat(500)), technician));
        assertEquals(HttpStatus.BAD_REQUEST, tooLong.getHttpStatus());
        verify(workOrderService, never()).changeStatus(any(), anyLong(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void checkIn_coordinatesOutOfRangeOrIncomplete_400() {
        stubWorkOrder();
        for (double[] pair : new double[][]{{91, 0}, {-90.1, 0}, {0, 180.5}, {0, -181}}) {
            AttendanceCheckInDTO dto = checkInDto("WO:12");
            dto.setLatitude(pair[0]);
            dto.setLongitude(pair[1]);
            CustomException e = assertThrows(CustomException.class, () -> service.checkIn(12L, dto, technician));
            assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
        }
        AttendanceCheckInDTO onlyLat = checkInDto("WO:12");
        onlyLat.setLatitude(10.0);
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(CustomException.class, () -> service.checkIn(12L, onlyLat, technician)).getHttpStatus());
        AttendanceCheckInDTO onlyLng = checkInDto("WO:12");
        onlyLng.setLongitude(10.0);
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(CustomException.class, () -> service.checkIn(12L, onlyLng, technician)).getHttpStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void checkIn_boundaryCoordinatesAreAccepted() {
        stubWorkOrder();
        when(repository.findFirstByTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(7L, 1L))
                .thenReturn(Optional.empty());
        when(repository.save(any(WorkOrderAttendanceLog.class))).thenAnswer(inv -> inv.getArgument(0));
        AttendanceCheckInDTO dto = checkInDto("WO:12");
        dto.setLatitude(-90.0);
        dto.setLongitude(180.0);
        AttendanceLogShowDTO result = service.checkIn(12L, dto, technician);
        assertEquals(-90.0, result.getCheckInLat());
        assertEquals(180.0, result.getCheckInLng());
    }

    @Test
    void checkIn_secondOpenCheckIn_409WithWorkOrderId() {
        stubWorkOrder();
        WorkOrder other = new WorkOrder();
        other.setId(77L);
        WorkOrderAttendanceLog open = openLog(new Date(BASE - 1000));
        open.setWorkOrder(other);
        when(repository.findFirstByTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(7L, 1L))
                .thenReturn(Optional.of(open));

        CustomException e = assertThrows(CustomException.class,
                () -> service.checkIn(12L, checkInDto("WO:12"), technician));
        assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
        assertTrue(e.getMessage().contains("77"));
        verify(workOrderService, never()).changeStatus(any(), anyLong(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void checkIn_happyPath_changesStatusToInProgressAndSavesLog() {
        stubWorkOrder();
        when(repository.findFirstByTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(7L, 1L))
                .thenReturn(Optional.empty());
        when(repository.save(any(WorkOrderAttendanceLog.class))).thenAnswer(inv -> {
            WorkOrderAttendanceLog saved = inv.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        AttendanceCheckInDTO in = checkInDto("  https://x.com/app/work-orders/12  ");
        in.setLatitude(18.48);
        in.setLongitude(-69.93);
        in.setNotes("  on site  ");

        AttendanceLogShowDTO result = service.checkIn(12L, in, technician);

        ArgumentCaptor<WorkOrderChangeStatusDTO> statusCaptor = ArgumentCaptor.forClass(WorkOrderChangeStatusDTO.class);
        verify(workOrderService).changeStatus(statusCaptor.capture(), eq(12L), eq(technician), isNull());
        assertEquals(Status.IN_PROGRESS, statusCaptor.getValue().getStatus());

        assertEquals(5L, result.getId());
        assertEquals(12L, result.getWorkOrderId());
        assertEquals(7L, result.getTechnicianId());
        assertEquals("Jane Doe", result.getTechnicianName());
        assertEquals(50L, result.getAssetId());
        assertEquals(fixedNow, result.getCheckInTime());
        assertNull(result.getCheckOutTime());
        assertNull(result.getTotalMinutes());
        assertEquals(18.48, result.getCheckInLat());
        assertEquals("https://x.com/app/work-orders/12", result.getQrCodeScanned());
        assertEquals("on site", result.getNotes());
        assertTrue(result.isActive());
    }

    @Test
    void checkIn_alreadyInProgress_doesNotChangeStatus() {
        workOrder.setStatus(Status.IN_PROGRESS);
        stubWorkOrder();
        when(repository.findFirstByTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(7L, 1L))
                .thenReturn(Optional.empty());
        when(repository.save(any(WorkOrderAttendanceLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.checkIn(12L, checkInDto("ASSET:50"), technician);

        verify(workOrderService, never()).changeStatus(any(), anyLong(), any(), any());
        verify(repository).save(any(WorkOrderAttendanceLog.class));
    }

    @Test
    void checkIn_statusChangeFails_propagatesAndDoesNotSave() {
        stubWorkOrder();
        when(repository.findFirstByTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(7L, 1L))
                .thenReturn(Optional.empty());
        when(workOrderService.changeStatus(any(), eq(12L), eq(technician), any()))
                .thenThrow(new CustomException("Forbidden", HttpStatus.FORBIDDEN));

        CustomException e = assertThrows(CustomException.class,
                () -> service.checkIn(12L, checkInDto("WO:12"), technician));
        assertEquals(HttpStatus.FORBIDDEN, e.getHttpStatus());
        verify(repository, never()).save(any());
    }

    // ---------- check-out ----------

    @Test
    void checkOut_withoutOpenCheckIn_404() {
        stubWorkOrder();
        when(repository.findFirstByWorkOrderIdAndTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                12L, 7L, 1L)).thenReturn(Optional.empty());
        CustomException e = assertThrows(CustomException.class,
                () -> service.checkOut(12L, new AttendanceCheckOutDTO(), technician));
        assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void checkOut_forbiddenAndNotFound() {
        when(workOrderService.findByIdAndCompany(12L, 1L)).thenReturn(Optional.of(workOrder));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> service.checkOut(12L, new AttendanceCheckOutDTO(), stranger)).getHttpStatus());
        when(workOrderService.findByIdAndCompany(13L, 1L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(CustomException.class,
                () -> service.checkOut(13L, new AttendanceCheckOutDTO(), technician)).getHttpStatus());
    }

    @Test
    void checkOut_computesTotalMinutes_andDoesNotChangeStatus() {
        stubWorkOrder();
        WorkOrderAttendanceLog log = openLog(new Date(BASE));
        log.setNotes("on site");
        when(repository.findFirstByWorkOrderIdAndTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                12L, 7L, 1L)).thenReturn(Optional.of(log));
        when(repository.save(any(WorkOrderAttendanceLog.class))).thenAnswer(inv -> inv.getArgument(0));
        fixedNow = new Date(BASE + 90 * 60_000L + 20_000L); // 90 min 20 s
        AttendanceCheckOutDTO dto = new AttendanceCheckOutDTO();
        dto.setLatitude(1.5);
        dto.setLongitude(2.5);
        dto.setNotes("done");

        AttendanceLogShowDTO result = service.checkOut(12L, dto, technician);

        assertEquals(90L, result.getTotalMinutes());
        assertEquals(fixedNow, result.getCheckOutTime());
        assertEquals(1.5, result.getCheckOutLat());
        assertEquals(2.5, result.getCheckOutLng());
        assertEquals("on site\ndone", result.getNotes());
        assertFalse(result.isActive());
        verify(workOrderService, never()).changeStatus(any(), anyLong(), any(), any());
    }

    @Test
    void checkOut_roundsToNearestMinute_andNeverNegative() {
        assertEquals(0L, WorkOrderAttendanceService.computeMinutes(new Date(BASE), new Date(BASE + 29_999L)));
        assertEquals(1L, WorkOrderAttendanceService.computeMinutes(new Date(BASE), new Date(BASE + 30_000L)));
        assertEquals(0L, WorkOrderAttendanceService.computeMinutes(new Date(BASE), new Date(BASE - 5 * 60_000L)));
    }

    @Test
    void checkOut_onCompletedWorkOrder_isAllowed() {
        workOrder.setStatus(Status.COMPLETE);
        stubWorkOrder();
        when(repository.findFirstByWorkOrderIdAndTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                12L, 7L, 1L)).thenReturn(Optional.of(openLog(new Date(BASE))));
        when(repository.save(any(WorkOrderAttendanceLog.class))).thenAnswer(inv -> inv.getArgument(0));
        assertNull(service.checkOut(12L, new AttendanceCheckOutDTO(), technician).getCheckOutLat());
    }

    @Test
    void checkOut_invalidCodeOrCoordinates_400() {
        stubWorkOrder();
        AttendanceCheckOutDTO badCode = new AttendanceCheckOutDTO();
        badCode.setCode("WO:13");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(CustomException.class,
                () -> service.checkOut(12L, badCode, technician)).getHttpStatus());
        AttendanceCheckOutDTO badCoords = new AttendanceCheckOutDTO();
        badCoords.setLatitude(95.0);
        badCoords.setLongitude(0.0);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(CustomException.class,
                () -> service.checkOut(12L, badCoords, technician)).getHttpStatus());
        verify(repository, never()).save(any());
    }

    // ---------- queries ----------

    @Test
    void getByWorkOrder_withoutViewAccess_403() {
        stubWorkOrder();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> service.getByWorkOrder(12L, stranger)).getHttpStatus());
    }

    @Test
    void getByWorkOrder_returnsMappedLogs() {
        stubWorkOrder();
        when(repository.findTop100ByWorkOrderIdAndCompanyIdOrderByCheckInTimeDesc(12L, 1L))
                .thenReturn(List.of(openLog(new Date(BASE))));
        List<AttendanceLogShowDTO> result = service.getByWorkOrder(12L, technician);
        assertEquals(1, result.size());
        assertEquals(99L, result.get(0).getId());
        assertTrue(result.get(0).isActive());
    }

    @Test
    void getActive_emptyWhenNoOpenRecord() {
        stubWorkOrder();
        when(repository.findFirstByWorkOrderIdAndTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                12L, 7L, 1L)).thenReturn(Optional.empty());
        assertFalse(service.getActive(12L, technician).isPresent());
    }

    @Test
    void getActive_returnsOpenRecord() {
        stubWorkOrder();
        when(repository.findFirstByWorkOrderIdAndTechnicianIdAndCompanyIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
                12L, 7L, 1L)).thenReturn(Optional.of(openLog(new Date(BASE))));
        assertEquals(99L, service.getActive(12L, technician).get().getId());
    }
}
