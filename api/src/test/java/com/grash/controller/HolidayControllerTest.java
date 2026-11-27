package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.HolidayPatchDTO;
import com.grash.dto.HolidayPostDTO;
import com.grash.dto.HolidayShowDTO;
import com.grash.dto.WorkScheduleMiniDTO;
import com.grash.mapper.HolidayMapper;
import com.grash.model.Company;
import com.grash.model.Holiday;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import com.grash.service.HolidayService;
import com.grash.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.grash.utils.Helper.setCurrentUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

@WebMvcTest(HolidayController.class)
@AutoConfigureMockMvc(addFilters = false)
class HolidayControllerTest extends AbstractControllerTest {

    private static final String SEARCH_BODY = "{\"filterFields\":[],\"pageNum\":0,\"pageSize\":10," +
            "\"sortField\":\"id\",\"direction\":\"ASC\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HolidayService holidayService;
    @MockitoBean
    private HolidayMapper holidayMapper;
    @MockitoBean
    private UserService userService;

    private Holiday holiday;
    private HolidayShowDTO showDto;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(1L);
        holiday = new Holiday();
        holiday.setId(7L);
        holiday.setCompany(company);

        showDto = new HolidayShowDTO();
        showDto.setId(7L);
        showDto.setName("Navidad");
        showDto.setDate(LocalDate.of(2026, 12, 25));
        showDto.setRecurringAnnually(true);
        WorkScheduleMiniDTO mini = new WorkScheduleMiniDTO();
        mini.setId(3L);
        mini.setName("Office");
        showDto.setWorkSchedule(mini);
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
    void getById_readerGetsTheHoliday() throws Exception {
        reader();
        when(holidayService.findById(7L)).thenReturn(Optional.of(holiday));
        when(holidayMapper.toShowDto(holiday)).thenReturn(showDto);

        mockMvc.perform(get("/holidays/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Navidad"))
                .andExpect(jsonPath("$.date").value("2026-12-25"))
                .andExpect(jsonPath("$.recurringAnnually").value(true))
                .andExpect(jsonPath("$.workSchedule.id").value(3));
    }

    @Test
    void getById_withoutAnalyticsPermissionIs403() throws Exception {
        withoutPermissions();

        mockMvc.perform(get("/holidays/7")).andExpect(status().isForbidden());

        verifyNoInteractions(holidayService);
    }

    @Test
    void getById_notFoundIs404() throws Exception {
        reader();
        when(holidayService.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/holidays/7")).andExpect(status().isNotFound());
    }

    @Test
    void getById_holidayOfAnotherCompanyIs403() throws Exception {
        reader();
        Company other = new Company();
        other.setId(2L);
        holiday.setCompany(other);
        when(holidayService.findById(7L)).thenReturn(Optional.of(holiday));

        mockMvc.perform(get("/holidays/7")).andExpect(status().isForbidden());
    }

    @Test
    void search_readerSearchesOnlyItsCompanyWithoutYear() throws Exception {
        reader();
        when(holidayService.findBySearchCriteria(any(SearchCriteria.class), any()))
                .thenReturn(new PageImpl<>(Collections.singletonList(holiday)));
        when(holidayMapper.toShowDto(holiday)).thenReturn(showDto);

        mockMvc.perform(post("/holidays/search").contentType(MediaType.APPLICATION_JSON).content(SEARCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7));

        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(holidayService).findBySearchCriteria(captor.capture(), isNull());
        assertEquals("company", captor.getValue().getFilterFields().get(0).getField());
    }

    @Test
    void search_passesTheYearFilter() throws Exception {
        reader();
        when(holidayService.findBySearchCriteria(any(SearchCriteria.class), eq(2026)))
                .thenReturn(new PageImpl<>(Collections.singletonList(holiday)));
        when(holidayMapper.toShowDto(holiday)).thenReturn(showDto);

        mockMvc.perform(post("/holidays/search?year=2026").contentType(MediaType.APPLICATION_JSON)
                        .content(SEARCH_BODY))
                .andExpect(status().isOk());

        verify(holidayService).findBySearchCriteria(any(SearchCriteria.class), eq(2026));
    }

    @Test
    void search_absurdYearIs400() throws Exception {
        reader();

        mockMvc.perform(post("/holidays/search?year=99999").contentType(MediaType.APPLICATION_JSON)
                        .content(SEARCH_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(holidayService);
    }

    @Test
    void search_withoutAnalyticsPermissionIs403() throws Exception {
        writer();

        mockMvc.perform(post("/holidays/search").contentType(MediaType.APPLICATION_JSON).content(SEARCH_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(holidayService);
    }

    @Test
    void create_writerCreatesTheHoliday() throws Exception {
        User writer = writer();
        when(holidayService.create(any(HolidayPostDTO.class), eq(writer.getCompany()))).thenReturn(holiday);
        when(holidayMapper.toShowDto(holiday)).thenReturn(showDto);

        mockMvc.perform(post("/holidays").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Navidad\",\"date\":\"2026-12-25\",\"recurringAnnually\":true," +
                                "\"workSchedule\":{\"id\":3}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        ArgumentCaptor<HolidayPostDTO> captor = ArgumentCaptor.forClass(HolidayPostDTO.class);
        verify(holidayService).create(captor.capture(), eq(writer.getCompany()));
        assertEquals(LocalDate.of(2026, 12, 25), captor.getValue().getDate());
        assertTrue(captor.getValue().isRecurringAnnually());
        assertEquals(3L, captor.getValue().getWorkSchedule().getId());
    }

    @Test
    void create_readerIs403() throws Exception {
        reader();

        mockMvc.perform(post("/holidays").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"date\":\"2026-12-25\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(holidayService);
    }

    @Test
    void create_missingDateIs400() throws Exception {
        writer();

        mockMvc.perform(post("/holidays").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_writerUpdatesTheHoliday() throws Exception {
        User writer = writer();
        when(holidayService.findById(7L)).thenReturn(Optional.of(holiday));
        when(holidayService.update(eq(7L), any(HolidayPatchDTO.class), eq(writer.getCompany())))
                .thenReturn(holiday);
        when(holidayMapper.toShowDto(holiday)).thenReturn(showDto);

        mockMvc.perform(patch("/holidays/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recurringAnnually\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void patch_readerIs403() throws Exception {
        reader();

        mockMvc.perform(patch("/holidays/7").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(holidayService);
    }

    @Test
    void delete_writerDeletesTheHoliday() throws Exception {
        writer();
        when(holidayService.findById(7L)).thenReturn(Optional.of(holiday));

        mockMvc.perform(delete("/holidays/7")).andExpect(status().isOk());

        verify(holidayService).delete(7L);
    }

    @Test
    void delete_readerIs403() throws Exception {
        reader();

        mockMvc.perform(delete("/holidays/7")).andExpect(status().isForbidden());

        verifyNoInteractions(holidayService);
    }

    @Test
    void delete_holidayOfAnotherCompanyIs403() throws Exception {
        writer();
        Company other = new Company();
        other.setId(2L);
        holiday.setCompany(other);
        when(holidayService.findById(7L)).thenReturn(Optional.of(holiday));

        mockMvc.perform(delete("/holidays/7")).andExpect(status().isForbidden());

        verify(holidayService, never()).delete(any());
    }
}
