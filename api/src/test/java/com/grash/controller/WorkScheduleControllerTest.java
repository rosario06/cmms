package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.WorkScheduleMiniDTO;
import com.grash.dto.WorkSchedulePatchDTO;
import com.grash.dto.WorkSchedulePostDTO;
import com.grash.dto.WorkScheduleShiftDTO;
import com.grash.dto.WorkScheduleShowDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.WorkScheduleMapper;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.WorkSchedule;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import com.grash.service.UserService;
import com.grash.service.WorkScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.grash.utils.Helper.setCurrentUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkScheduleController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkScheduleControllerTest extends AbstractControllerTest {

    private static final String SEARCH_BODY = "{\"filterFields\":[],\"pageNum\":0,\"pageSize\":10," +
            "\"sortField\":\"id\",\"direction\":\"ASC\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkScheduleService workScheduleService;
    @MockitoBean
    private WorkScheduleMapper workScheduleMapper;
    @MockitoBean
    private UserService userService;

    private WorkSchedule schedule;
    private WorkScheduleShowDTO showDto;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(1L);
        schedule = new WorkSchedule();
        schedule.setId(7L);
        schedule.setCompany(company);

        showDto = new WorkScheduleShowDTO();
        showDto.setId(7L);
        showDto.setName("Office");
        showDto.setTimezone("America/Santo_Domingo");
        WorkScheduleShiftDTO shift = new WorkScheduleShiftDTO();
        shift.setId(1L);
        shift.setDayOfWeek(1);
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 30));
        showDto.getShifts().add(shift);
    }

    private User login(RoleType roleType, Set<PermissionEntity> view, Set<PermissionEntity> create) {
        Company userCompany = new Company();
        userCompany.setId(1L);
        User user = new User();
        user.setId(1L);
        user.setEmail("u@test.com");
        user.setEnabled(true);
        user.setCompany(userCompany);
        user.setRole(Role.builder().id(1L).roleType(roleType).name("r").viewPermissions(new HashSet<>(view))
                .createPermissions(new HashSet<>(create)).build());
        setCurrentUser(user);
        when(userService.whoami(any())).thenReturn(user);
        return user;
    }

    private User reader() {
        return login(RoleType.ROLE_CLIENT, Set.of(PermissionEntity.ANALYTICS), Set.of());
    }

    private User writer() {
        return login(RoleType.ROLE_CLIENT, Set.of(), Set.of(PermissionEntity.SETTINGS));
    }

    private User withoutPermissions() {
        return login(RoleType.ROLE_CLIENT, Set.of(PermissionEntity.WORK_ORDERS),
                Set.of(PermissionEntity.WORK_ORDERS));
    }

    @Test
    void getById_readerGetsTheScheduleWithItsShifts() throws Exception {
        reader();
        when(workScheduleService.findById(7L)).thenReturn(Optional.of(schedule));
        when(workScheduleMapper.toShowDto(schedule)).thenReturn(showDto);

        mockMvc.perform(get("/work-schedules/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Office"))
                .andExpect(jsonPath("$.timezone").value("America/Santo_Domingo"))
                .andExpect(jsonPath("$.shifts[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.shifts[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.shifts[0].endTime").value("17:30"));
    }

    @Test
    void getById_withoutAnalyticsPermissionIs403() throws Exception {
        withoutPermissions();

        mockMvc.perform(get("/work-schedules/7")).andExpect(status().isForbidden());

        verifyNoInteractions(workScheduleService);
    }

    @Test
    void getById_notFoundIs404() throws Exception {
        reader();
        when(workScheduleService.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/work-schedules/7")).andExpect(status().isNotFound());
    }

    @Test
    void getById_scheduleOfAnotherCompanyIs403() throws Exception {
        reader();
        Company other = new Company();
        other.setId(2L);
        schedule.setCompany(other);
        when(workScheduleService.findById(7L)).thenReturn(Optional.of(schedule));

        mockMvc.perform(get("/work-schedules/7")).andExpect(status().isForbidden());
    }

    @Test
    void getById_nonClientRoleIs403() throws Exception {
        login(RoleType.ROLE_SUPER_ADMIN, Set.of(PermissionEntity.ANALYTICS), Set.of());

        mockMvc.perform(get("/work-schedules/7")).andExpect(status().isForbidden());
    }

    @Test
    void search_readerSearchesOnlyItsCompany() throws Exception {
        reader();
        when(workScheduleService.findBySearchCriteria(any(SearchCriteria.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(schedule)));
        when(workScheduleMapper.toShowDto(schedule)).thenReturn(showDto);

        mockMvc.perform(post("/work-schedules/search").contentType(MediaType.APPLICATION_JSON)
                        .content(SEARCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7));

        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(workScheduleService).findBySearchCriteria(captor.capture());
        assertEquals("company", captor.getValue().getFilterFields().get(0).getField());
    }

    @Test
    void search_withoutAnalyticsPermissionIs403() throws Exception {
        writer();

        mockMvc.perform(post("/work-schedules/search").contentType(MediaType.APPLICATION_JSON)
                        .content(SEARCH_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workScheduleService);
    }

    @Test
    void mini_isReadableWithAnalytics() throws Exception {
        reader();
        WorkScheduleMiniDTO mini = new WorkScheduleMiniDTO();
        mini.setId(7L);
        mini.setName("Office");
        mini.setDefaultSchedule(true);
        when(workScheduleService.findByCompany(1L)).thenReturn(List.of(schedule));
        when(workScheduleMapper.toMiniDto(schedule)).thenReturn(mini);

        mockMvc.perform(get("/work-schedules/mini"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].defaultSchedule").value(true));
    }

    @Test
    void mini_isReadableWithPeopleAndTeams() throws Exception {
        login(RoleType.ROLE_CLIENT, Set.of(PermissionEntity.PEOPLE_AND_TEAMS), Set.of());
        when(workScheduleService.findByCompany(1L)).thenReturn(List.of());

        mockMvc.perform(get("/work-schedules/mini"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void mini_withoutAnyOfThePermissionsIs403() throws Exception {
        withoutPermissions();

        mockMvc.perform(get("/work-schedules/mini")).andExpect(status().isForbidden());

        verifyNoInteractions(workScheduleService);
    }

    @Test
    void create_writerCreatesTheSchedule() throws Exception {
        User writer = writer();
        when(workScheduleService.create(any(WorkSchedulePostDTO.class), eq(writer.getCompany())))
                .thenReturn(schedule);
        when(workScheduleMapper.toShowDto(schedule)).thenReturn(showDto);

        mockMvc.perform(post("/work-schedules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Office\",\"timezone\":\"America/Santo_Domingo\"," +
                                "\"defaultSchedule\":true,\"shifts\":[{\"dayOfWeek\":1,\"startTime\":\"08:00\"," +
                                "\"endTime\":\"17:00\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        ArgumentCaptor<WorkSchedulePostDTO> captor = ArgumentCaptor.forClass(WorkSchedulePostDTO.class);
        verify(workScheduleService).create(captor.capture(), eq(writer.getCompany()));
        assertEquals(true, captor.getValue().isDefaultSchedule());
        assertEquals(LocalTime.of(17, 0), captor.getValue().getShifts().get(0).getEndTime());
    }

    @Test
    void create_readerIs403() throws Exception {
        reader();

        mockMvc.perform(post("/work-schedules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workScheduleService);
    }

    @Test
    void create_missingNameIs400() throws Exception {
        writer();

        mockMvc.perform(post("/work-schedules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"UTC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_dayOfWeekOutOfRangeIs400() throws Exception {
        writer();

        mockMvc.perform(post("/work-schedules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"timezone\":\"UTC\",\"shifts\":[{\"dayOfWeek\":9," +
                                "\"startTime\":\"08:00\",\"endTime\":\"17:00\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_overlapReportedByTheServiceIs400() throws Exception {
        writer();
        when(workScheduleService.create(any(WorkSchedulePostDTO.class), any()))
                .thenThrow(new CustomException("Shifts overlap: MONDAY 08:00-12:00 and MONDAY 11:00-15:00",
                        HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/work-schedules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_writerUpdatesTheSchedule() throws Exception {
        User writer = writer();
        when(workScheduleService.findById(7L)).thenReturn(Optional.of(schedule));
        when(workScheduleService.update(eq(7L), any(WorkSchedulePatchDTO.class), eq(writer.getCompany())))
                .thenReturn(schedule);
        when(workScheduleMapper.toShowDto(schedule)).thenReturn(showDto);

        mockMvc.perform(patch("/work-schedules/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shifts\":[]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<WorkSchedulePatchDTO> captor = ArgumentCaptor.forClass(WorkSchedulePatchDTO.class);
        verify(workScheduleService).update(eq(7L), captor.capture(), eq(writer.getCompany()));
        assertEquals(0, captor.getValue().getShifts().size());
    }

    @Test
    void patch_readerIs403() throws Exception {
        reader();

        mockMvc.perform(patch("/work-schedules/7").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workScheduleService);
    }

    @Test
    void patch_notFoundIs404() throws Exception {
        writer();
        when(workScheduleService.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/work-schedules/7").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_writerDeletesTheSchedule() throws Exception {
        writer();
        when(workScheduleService.findById(7L)).thenReturn(Optional.of(schedule));

        mockMvc.perform(delete("/work-schedules/7")).andExpect(status().isOk());

        verify(workScheduleService).delete(7L);
    }

    @Test
    void delete_readerIs403() throws Exception {
        reader();

        mockMvc.perform(delete("/work-schedules/7")).andExpect(status().isForbidden());

        verifyNoInteractions(workScheduleService);
    }

    @Test
    void delete_scheduleOfAnotherCompanyIs403() throws Exception {
        writer();
        Company other = new Company();
        other.setId(2L);
        schedule.setCompany(other);
        when(workScheduleService.findById(7L)).thenReturn(Optional.of(schedule));

        mockMvc.perform(delete("/work-schedules/7")).andExpect(status().isForbidden());

        verify(workScheduleService, never()).delete(any());
    }
}
