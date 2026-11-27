package com.grash.controller;

import com.grash.dto.attendance.AttendanceCheckInDTO;
import com.grash.dto.attendance.AttendanceCheckOutDTO;
import com.grash.dto.attendance.AttendanceLogShowDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import com.grash.service.UserService;
import com.grash.service.WorkOrderAttendanceService;
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

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.grash.utils.Helper.setCurrentUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkOrderAttendanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkOrderAttendanceControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkOrderAttendanceService attendanceService;
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
                .viewPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS))).build());

        nonClientUser = new User();
        nonClientUser.setId(2L);
        nonClientUser.setEmail("admin@test.com");
        nonClientUser.setEnabled(true);
        nonClientUser.setRole(Role.builder().id(2L).roleType(RoleType.ROLE_SUPER_ADMIN).name("Super").build());
    }

    private AttendanceLogShowDTO logDto(boolean active) {
        AttendanceLogShowDTO dto = new AttendanceLogShowDTO();
        dto.setId(5L);
        dto.setWorkOrderId(12L);
        dto.setTechnicianId(1L);
        dto.setTechnicianName("John Doe");
        dto.setCheckInTime(new Date(1_740_988_800_000L));
        dto.setQrCodeScanned("WO:12");
        dto.setActive(active);
        if (!active) dto.setTotalMinutes(30L);
        return dto;
    }

    @Test
    void checkIn_happyPath_returnsDto() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(attendanceService.checkIn(eq(12L), any(AttendanceCheckInDTO.class), eq(clientUser)))
                .thenReturn(logDto(true));

        mockMvc.perform(post("/work-orders/12/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WO:12\",\"latitude\":18.48,\"longitude\":-69.93,\"notes\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.workOrderId").value(12))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.checkOutTime").isEmpty());

        ArgumentCaptor<AttendanceCheckInDTO> captor = ArgumentCaptor.forClass(AttendanceCheckInDTO.class);
        verify(attendanceService).checkIn(eq(12L), captor.capture(), eq(clientUser));
        assertEquals("WO:12", captor.getValue().getCode());
        assertEquals(18.48, captor.getValue().getLatitude());
        assertEquals(-69.93, captor.getValue().getLongitude());
        assertEquals("hi", captor.getValue().getNotes());
    }

    @Test
    void checkIn_serviceError_isMappedToStatus() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(attendanceService.checkIn(eq(12L), any(AttendanceCheckInDTO.class), eq(clientUser)))
                .thenThrow(new CustomException("already open in work order 7", HttpStatus.CONFLICT));

        mockMvc.perform(post("/work-orders/12/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WO:12\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void checkIn_requiresRoleClient() throws Exception {
        setCurrentUser(nonClientUser);
        when(userService.whoami(any())).thenReturn(nonClientUser);

        mockMvc.perform(post("/work-orders/12/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WO:12\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkOut_happyPath_returnsTotalMinutes() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(attendanceService.checkOut(eq(12L), any(AttendanceCheckOutDTO.class), eq(clientUser)))
                .thenReturn(logDto(false));

        mockMvc.perform(post("/work-orders/12/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes").value(30))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void attendance_returnsList() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(attendanceService.getByWorkOrder(12L, clientUser)).thenReturn(List.of(logDto(true), logDto(false)));

        mockMvc.perform(get("/work-orders/12/attendance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].technicianName").value("John Doe"));
    }

    @Test
    void active_returns200WhenOpenRecordExists() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(attendanceService.getActive(12L, clientUser)).thenReturn(Optional.of(logDto(true)));

        mockMvc.perform(get("/work-orders/12/attendance/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void active_returns204WhenNoOpenRecord() throws Exception {
        setCurrentUser(clientUser);
        when(userService.whoami(any())).thenReturn(clientUser);
        when(attendanceService.getActive(12L, clientUser)).thenReturn(Optional.empty());

        mockMvc.perform(get("/work-orders/12/attendance/active"))
                .andExpect(status().isNoContent());
    }
}
