package com.grash.controller;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.CostCenterPatchDTO;
import com.grash.dto.CostCenterPostDTO;
import com.grash.dto.CostCenterShowDTO;
import com.grash.dto.CostCenterSummaryDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.CostCenterMapper;
import com.grash.model.Company;
import com.grash.model.CostCenter;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleType;
import com.grash.service.CostCenterService;
import com.grash.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostCenterControllerTest {

    @Mock
    private CostCenterService costCenterService;
    @Mock
    private UserService userService;
    @Mock
    private CostCenterMapper costCenterMapper;
    @Mock
    private HttpServletRequest request;

    private CostCenterController controller;
    private Company company;

    @BeforeEach
    void setUp() {
        controller = new CostCenterController(costCenterService, userService, costCenterMapper);
        company = new Company();
        company.setId(1L);
    }

    private User userWith(Set<PermissionEntity> view, Set<PermissionEntity> create) {
        User user = new User();
        user.setId(1L);
        user.setCompany(company);
        user.setRole(Role.builder().id(1L).roleType(RoleType.ROLE_CLIENT).name("Client")
                .viewPermissions(new HashSet<>(view)).createPermissions(new HashSet<>(create)).build());
        when(userService.whoami(request)).thenReturn(user);
        return user;
    }

    private CostCenter costCenterOf(Long companyId) {
        Company other = new Company();
        other.setId(companyId);
        CostCenter costCenter = new CostCenter();
        costCenter.setId(5L);
        costCenter.setCompany(other);
        when(costCenterService.findById(5L)).thenReturn(Optional.of(costCenter));
        return costCenter;
    }

    private void assertForbidden(Runnable action) {
        CustomException e = assertThrows(CustomException.class, action::run);
        assertEquals(HttpStatus.FORBIDDEN, e.getHttpStatus());
    }

    // ---------- reads need ANALYTICS ----------

    @Test
    void search_withoutAnalytics_returns403() {
        userWith(Set.of(PermissionEntity.SETTINGS), Set.of(PermissionEntity.SETTINGS));

        assertForbidden(() -> controller.search(new SearchCriteria(), request));
        verify(costCenterService, never()).findBySearchCriteria(any());
    }

    @Test
    void getById_withoutAnalytics_returns403() {
        userWith(Set.of(PermissionEntity.WORK_ORDERS), Set.of());
        costCenterOf(1L);

        assertForbidden(() -> controller.getById(5L, request));
    }

    @Test
    void summary_withoutAnalytics_returns403() {
        userWith(Set.of(), Set.of());
        costCenterOf(1L);

        assertForbidden(() -> controller.getSummary(5L, request));
        verify(costCenterService, never()).getSummary(any());
    }

    @Test
    void search_withAnalytics_filtersByCompany() {
        userWith(Set.of(PermissionEntity.ANALYTICS), Set.of());
        when(costCenterService.findBySearchCriteria(any())).thenReturn(new PageImpl<>(List.of()));
        SearchCriteria criteria = new SearchCriteria();

        controller.search(criteria, request);

        assertEquals(1, criteria.getFilterFields().size());
        assertEquals("company", criteria.getFilterFields().get(0).getField());
        assertEquals(1L, criteria.getFilterFields().get(0).getValue());
    }

    @Test
    void getById_ofAnotherCompany_returns403() {
        userWith(Set.of(PermissionEntity.ANALYTICS), Set.of());
        costCenterOf(2L);

        assertForbidden(() -> controller.getById(5L, request));
        verify(costCenterMapper, never()).toShowDto(any());
    }

    @Test
    void getById_notFound_returns404() {
        userWith(Set.of(PermissionEntity.ANALYTICS), Set.of());
        when(costCenterService.findById(anyLong())).thenReturn(Optional.empty());

        CustomException e = assertThrows(CustomException.class, () -> controller.getById(5L, request));
        assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
    }

    @Test
    void getById_withAnalytics_returnsDto() {
        userWith(Set.of(PermissionEntity.ANALYTICS), Set.of());
        CostCenter costCenter = costCenterOf(1L);
        CostCenterShowDTO dto = new CostCenterShowDTO();
        when(costCenterMapper.toShowDto(costCenter)).thenReturn(dto);

        assertSame(dto, controller.getById(5L, request));
    }

    @Test
    void summary_withAnalytics_returnsSummary() {
        userWith(Set.of(PermissionEntity.ANALYTICS), Set.of());
        CostCenter costCenter = costCenterOf(1L);
        CostCenterSummaryDTO summary = new CostCenterSummaryDTO();
        when(costCenterService.getSummary(costCenter)).thenReturn(summary);

        assertSame(summary, controller.getSummary(5L, request));
    }

    // ---------- writes need SETTINGS in createPermissions ----------

    @Test
    void create_withoutSettings_returns403_evenWithAnalytics() {
        userWith(Set.of(PermissionEntity.ANALYTICS, PermissionEntity.SETTINGS), Set.of(PermissionEntity.ASSETS));

        assertForbidden(() -> controller.create(new CostCenterPostDTO(), request));
        verify(costCenterService, never()).create(any(), any());
    }

    @Test
    void patch_withoutSettings_returns403() {
        userWith(Set.of(PermissionEntity.ANALYTICS), Set.of());
        costCenterOf(1L);

        assertForbidden(() -> controller.patch(new CostCenterPatchDTO(), 5L, request));
        verify(costCenterService, never()).update(anyLong(), any(), any());
    }

    @Test
    void delete_withoutSettings_returns403() {
        userWith(Set.of(PermissionEntity.ANALYTICS), Set.of(PermissionEntity.CATEGORIES));
        costCenterOf(1L);

        assertForbidden(() -> controller.delete(5L, request));
        verify(costCenterService, never()).delete(anyLong());
    }

    @Test
    void patch_ofAnotherCompany_returns403() {
        userWith(Set.of(), Set.of(PermissionEntity.SETTINGS));
        costCenterOf(2L);

        assertForbidden(() -> controller.patch(new CostCenterPatchDTO(), 5L, request));
        verify(costCenterService, never()).update(anyLong(), any(), any());
    }

    @Test
    void delete_ofAnotherCompany_returns403() {
        userWith(Set.of(), Set.of(PermissionEntity.SETTINGS));
        costCenterOf(2L);

        assertForbidden(() -> controller.delete(5L, request));
        verify(costCenterService, never()).delete(anyLong());
    }

    @Test
    void create_withSettings_delegatesToService() {
        User user = userWith(Set.of(), Set.of(PermissionEntity.SETTINGS));
        CostCenterPostDTO post = new CostCenterPostDTO();
        CostCenter saved = new CostCenter();
        CostCenterShowDTO dto = new CostCenterShowDTO();
        when(costCenterService.create(post, user)).thenReturn(saved);
        when(costCenterMapper.toShowDto(saved)).thenReturn(dto);

        assertSame(dto, controller.create(post, request));
    }

    @Test
    void delete_withSettings_deletes() {
        userWith(Set.of(), Set.of(PermissionEntity.SETTINGS));
        costCenterOf(1L);

        controller.delete(5L, request);

        verify(costCenterService).delete(5L);
    }
}
