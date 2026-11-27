package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.SlaPolicyPatchDTO;
import com.grash.dto.SlaPolicyPostDTO;
import com.grash.dto.SlaPolicyShowDTO;
import com.grash.mapper.SlaPolicyMapper;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.SlaPolicy;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.Priority;
import com.grash.model.enums.RoleType;
import com.grash.service.SlaPolicyService;
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

import java.util.Collections;
import java.util.HashSet;
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

@WebMvcTest(SlaPolicyController.class)
@AutoConfigureMockMvc(addFilters = false)
class SlaPolicyControllerTest extends AbstractControllerTest {

    private static final String SEARCH_BODY = "{\"filterFields\":[],\"pageNum\":0,\"pageSize\":10," +
            "\"sortField\":\"id\",\"direction\":\"ASC\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlaPolicyService slaPolicyService;
    @MockitoBean
    private SlaPolicyMapper slaPolicyMapper;
    @MockitoBean
    private UserService userService;

    private Company company;
    private SlaPolicy policy;
    private SlaPolicyShowDTO showDto;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);

        policy = new SlaPolicy();
        policy.setId(7L);
        policy.setCompany(company);

        showDto = new SlaPolicyShowDTO();
        showDto.setId(7L);
        showDto.setName("Urgent");
        showDto.setPriority(Priority.HIGH);
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
    void getById_readerGetsThePolicy() throws Exception {
        reader();
        when(slaPolicyService.findById(7L)).thenReturn(Optional.of(policy));
        when(slaPolicyMapper.toShowDto(policy)).thenReturn(showDto);

        mockMvc.perform(get("/sla-policies/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Urgent"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void getById_withoutAnalyticsPermissionIs403() throws Exception {
        withoutPermissions();

        mockMvc.perform(get("/sla-policies/7")).andExpect(status().isForbidden());

        verifyNoInteractions(slaPolicyService);
    }

    @Test
    void getById_policyOfAnotherCompanyIs403() throws Exception {
        reader();
        Company other = new Company();
        other.setId(2L);
        policy.setCompany(other);
        when(slaPolicyService.findById(7L)).thenReturn(Optional.of(policy));

        mockMvc.perform(get("/sla-policies/7")).andExpect(status().isForbidden());
    }

    @Test
    void getById_nonClientRoleIs403() throws Exception {
        login(RoleType.ROLE_SUPER_ADMIN, Set.of(PermissionEntity.ANALYTICS), Set.of());

        mockMvc.perform(get("/sla-policies/7")).andExpect(status().isForbidden());
    }

    @Test
    void search_readerSearchesOnlyItsCompany() throws Exception {
        reader();
        when(slaPolicyService.findBySearchCriteria(any(SearchCriteria.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(policy)));
        when(slaPolicyMapper.toShowDto(policy)).thenReturn(showDto);

        mockMvc.perform(post("/sla-policies/search").contentType(MediaType.APPLICATION_JSON).content(SEARCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7));

        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(slaPolicyService).findBySearchCriteria(captor.capture());
        assertEquals("company", captor.getValue().getFilterFields().get(0).getField());
    }

    @Test
    void search_withoutAnalyticsPermissionIs403() throws Exception {
        writer();

        mockMvc.perform(post("/sla-policies/search").contentType(MediaType.APPLICATION_JSON).content(SEARCH_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(slaPolicyService);
    }

    @Test
    void create_writerCreatesTheGeneralPolicy() throws Exception {
        User writer = writer();
        when(slaPolicyService.create(any(SlaPolicyPostDTO.class), eq(writer.getCompany()))).thenReturn(policy);
        when(slaPolicyMapper.toShowDto(policy)).thenReturn(showDto);

        mockMvc.perform(post("/sla-policies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Urgent\",\"priority\":\"HIGH\",\"firstResponseMinutes\":30," +
                                "\"resolutionMinutes\":240}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        ArgumentCaptor<SlaPolicyPostDTO> captor = ArgumentCaptor.forClass(SlaPolicyPostDTO.class);
        verify(slaPolicyService).create(captor.capture(), eq(writer.getCompany()));
        assertEquals(Priority.HIGH, captor.getValue().getPriority());
        assertEquals(240, captor.getValue().getResolutionMinutes());
    }

    @Test
    void create_readerIs403() throws Exception {
        reader();

        mockMvc.perform(post("/sla-policies").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(slaPolicyService);
    }

    @Test
    void create_negativeMinutesIs400() throws Exception {
        writer();

        mockMvc.perform(post("/sla-policies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"resolutionMinutes\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_writerUpdatesThePolicy() throws Exception {
        User writer = writer();
        when(slaPolicyService.findById(7L)).thenReturn(Optional.of(policy));
        when(slaPolicyService.update(eq(7L), any(SlaPolicyPatchDTO.class), eq(writer.getCompany())))
                .thenReturn(policy);
        when(slaPolicyMapper.toShowDto(policy)).thenReturn(showDto);

        mockMvc.perform(patch("/sla-policies/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionMinutes\":120}"))
                .andExpect(status().isOk());
    }

    @Test
    void patch_readerIs403() throws Exception {
        reader();

        mockMvc.perform(patch("/sla-policies/7").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(slaPolicyService);
    }

    @Test
    void delete_writerDeletesThePolicy() throws Exception {
        writer();
        when(slaPolicyService.findById(7L)).thenReturn(Optional.of(policy));

        mockMvc.perform(delete("/sla-policies/7")).andExpect(status().isOk());

        verify(slaPolicyService).delete(7L);
    }

    @Test
    void delete_readerIs403() throws Exception {
        reader();

        mockMvc.perform(delete("/sla-policies/7")).andExpect(status().isForbidden());

        verifyNoInteractions(slaPolicyService);
    }

    @Test
    void delete_policyOfAnotherCompanyIs403() throws Exception {
        writer();
        Company other = new Company();
        other.setId(2L);
        policy.setCompany(other);
        when(slaPolicyService.findById(7L)).thenReturn(Optional.of(policy));

        mockMvc.perform(delete("/sla-policies/7")).andExpect(status().isForbidden());

        verify(slaPolicyService, never()).delete(any());
    }
}
