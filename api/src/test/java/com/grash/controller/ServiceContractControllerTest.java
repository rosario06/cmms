package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.ServiceContractPatchDTO;
import com.grash.dto.ServiceContractPostDTO;
import com.grash.dto.ServiceContractShowDTO;
import com.grash.dto.ServiceContractUsageDTO;
import com.grash.mapper.ServiceContractMapper;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.ServiceContract;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import com.grash.service.ServiceContractService;
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

@WebMvcTest(ServiceContractController.class)
@AutoConfigureMockMvc(addFilters = false)
class ServiceContractControllerTest extends AbstractControllerTest {

    private static final String SEARCH_BODY = "{\"filterFields\":[],\"pageNum\":0,\"pageSize\":10," +
            "\"sortField\":\"id\",\"direction\":\"ASC\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceContractService serviceContractService;
    @MockitoBean
    private ServiceContractMapper serviceContractMapper;
    @MockitoBean
    private UserService userService;

    private Company company;
    private ServiceContract contract;
    private ServiceContractShowDTO showDto;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);

        contract = new ServiceContract();
        contract.setId(5L);
        contract.setCompany(company);

        showDto = new ServiceContractShowDTO();
        showDto.setId(5L);
        showDto.setName("Plan");
        showDto.setContractNumber("CT-2026-0001");
    }

    private User user(RoleType roleType, Set<PermissionEntity> view, Set<PermissionEntity> create, Long companyId) {
        Company userCompany = new Company();
        userCompany.setId(companyId);
        User user = new User();
        user.setId(1L);
        user.setEmail("u@test.com");
        user.setEnabled(true);
        user.setCompany(userCompany);
        user.setRole(Role.builder().id(1L).roleType(roleType).name("r").viewPermissions(new HashSet<>(view))
                .createPermissions(new HashSet<>(create)).build());
        return user;
    }

    private User reader() {
        return login(user(RoleType.ROLE_CLIENT, Set.of(PermissionEntity.ANALYTICS), Set.of(), 1L));
    }

    private User writer() {
        return login(user(RoleType.ROLE_CLIENT, Set.of(), Set.of(PermissionEntity.SETTINGS), 1L));
    }

    private User withoutPermissions() {
        return login(user(RoleType.ROLE_CLIENT, Set.of(PermissionEntity.WORK_ORDERS),
                Set.of(PermissionEntity.WORK_ORDERS), 1L));
    }

    private User login(User user) {
        setCurrentUser(user);
        when(userService.whoami(any())).thenReturn(user);
        return user;
    }

    // ------------------------------------------------------------ read

    @Test
    void getById_readerGetsTheContract() throws Exception {
        reader();
        when(serviceContractService.findById(5L)).thenReturn(Optional.of(contract));
        when(serviceContractMapper.toShowDto(contract)).thenReturn(showDto);

        mockMvc.perform(get("/service-contracts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.contractNumber").value("CT-2026-0001"));
    }

    @Test
    void getById_withoutAnalyticsPermissionIs403() throws Exception {
        withoutPermissions();

        mockMvc.perform(get("/service-contracts/5")).andExpect(status().isForbidden());

        verifyNoInteractions(serviceContractService);
    }

    @Test
    void getById_writerWithoutAnalyticsCannotRead() throws Exception {
        writer();

        mockMvc.perform(get("/service-contracts/5")).andExpect(status().isForbidden());
    }

    @Test
    void getById_contractOfAnotherCompanyIs403() throws Exception {
        reader();
        Company other = new Company();
        other.setId(2L);
        contract.setCompany(other);
        when(serviceContractService.findById(5L)).thenReturn(Optional.of(contract));

        mockMvc.perform(get("/service-contracts/5")).andExpect(status().isForbidden());

        verify(serviceContractMapper, never()).toShowDto(any());
    }

    @Test
    void getById_unknownContractIs404() throws Exception {
        reader();
        when(serviceContractService.findById(5L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/service-contracts/5")).andExpect(status().isNotFound());
    }

    @Test
    void getById_nonClientRoleIs403() throws Exception {
        login(user(RoleType.ROLE_SUPER_ADMIN, Set.of(PermissionEntity.ANALYTICS), Set.of(), 1L));

        mockMvc.perform(get("/service-contracts/5")).andExpect(status().isForbidden());
    }

    @Test
    void search_readerSearchesOnlyItsCompany() throws Exception {
        User reader = reader();
        when(serviceContractService.findBySearchCriteria(any(SearchCriteria.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(contract)));
        when(serviceContractMapper.toShowDto(contract)).thenReturn(showDto);

        mockMvc.perform(post("/service-contracts/search").contentType(MediaType.APPLICATION_JSON)
                        .content(SEARCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5));

        ArgumentCaptor<SearchCriteria> captor = ArgumentCaptor.forClass(SearchCriteria.class);
        verify(serviceContractService).findBySearchCriteria(captor.capture());
        assertEquals(1, captor.getValue().getFilterFields().size());
        assertEquals("company", captor.getValue().getFilterFields().get(0).getField());
        assertEquals(reader.getCompany().getId(), captor.getValue().getFilterFields().get(0).getValue());
    }

    @Test
    void search_withoutAnalyticsPermissionIs403() throws Exception {
        withoutPermissions();

        mockMvc.perform(post("/service-contracts/search").contentType(MediaType.APPLICATION_JSON)
                        .content(SEARCH_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(serviceContractService);
    }

    @Test
    void usage_readerGetsTheUsage() throws Exception {
        reader();
        when(serviceContractService.findById(5L)).thenReturn(Optional.of(contract));
        when(serviceContractService.getUsage(5L)).thenReturn(new ServiceContractUsageDTO(40, 10, 30, 75, 120));

        mockMvc.perform(get("/service-contracts/5/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.includedHours").value(40.0))
                .andExpect(jsonPath("$.remainingHours").value(10.0))
                .andExpect(jsonPath("$.consumedHours").value(30.0))
                .andExpect(jsonPath("$.consumedPercent").value(75.0))
                .andExpect(jsonPath("$.retainerBalance").value(120.0));
    }

    @Test
    void usage_withoutAnalyticsPermissionIs403() throws Exception {
        withoutPermissions();

        mockMvc.perform(get("/service-contracts/5/usage")).andExpect(status().isForbidden());

        verifyNoInteractions(serviceContractService);
    }

    // ------------------------------------------------------------ write

    @Test
    void create_writerCreatesTheContract() throws Exception {
        User writer = writer();
        when(serviceContractService.create(any(ServiceContractPostDTO.class), eq(writer.getCompany())))
                .thenReturn(contract);
        when(serviceContractMapper.toShowDto(contract)).thenReturn(showDto);

        mockMvc.perform(post("/service-contracts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Plan\",\"includedHours\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        ArgumentCaptor<ServiceContractPostDTO> captor = ArgumentCaptor.forClass(ServiceContractPostDTO.class);
        verify(serviceContractService).create(captor.capture(), eq(writer.getCompany()));
        assertEquals(40, captor.getValue().getIncludedHours());
    }

    @Test
    void create_readerWithoutSettingsPermissionIs403() throws Exception {
        reader();

        mockMvc.perform(post("/service-contracts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Plan\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(serviceContractService);
    }

    @Test
    void create_withoutNameIs400() throws Exception {
        writer();

        mockMvc.perform(post("/service-contracts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_writerUpdatesTheContract() throws Exception {
        User writer = writer();
        when(serviceContractService.findById(5L)).thenReturn(Optional.of(contract));
        when(serviceContractService.update(eq(5L), any(ServiceContractPatchDTO.class), eq(writer.getCompany())))
                .thenReturn(contract);
        when(serviceContractMapper.toShowDto(contract)).thenReturn(showDto);

        mockMvc.perform(patch("/service-contracts/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Plan"));
    }

    @Test
    void patch_readerIs403() throws Exception {
        reader();

        mockMvc.perform(patch("/service-contracts/5").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(serviceContractService);
    }

    @Test
    void patch_contractOfAnotherCompanyIs403() throws Exception {
        writer();
        Company other = new Company();
        other.setId(2L);
        contract.setCompany(other);
        when(serviceContractService.findById(5L)).thenReturn(Optional.of(contract));

        mockMvc.perform(patch("/service-contracts/5").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(serviceContractService, never()).update(any(), any(), any());
    }

    @Test
    void delete_writerDeletesTheContract() throws Exception {
        writer();
        when(serviceContractService.findById(5L)).thenReturn(Optional.of(contract));

        mockMvc.perform(delete("/service-contracts/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(serviceContractService).delete(5L);
    }

    @Test
    void delete_readerIs403() throws Exception {
        reader();

        mockMvc.perform(delete("/service-contracts/5")).andExpect(status().isForbidden());

        verifyNoInteractions(serviceContractService);
    }

    @Test
    void delete_contractOfAnotherCompanyIs403() throws Exception {
        writer();
        Company other = new Company();
        other.setId(2L);
        contract.setCompany(other);
        when(serviceContractService.findById(5L)).thenReturn(Optional.of(contract));

        mockMvc.perform(delete("/service-contracts/5")).andExpect(status().isForbidden());

        verify(serviceContractService, never()).delete(any());
    }
}
