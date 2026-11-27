package com.grash.service;

import com.grash.dto.CostCenterPatchDTO;
import com.grash.dto.CostCenterPostDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.CostCenterMapper;
import com.grash.model.Company;
import com.grash.model.CostCenter;
import com.grash.model.User;
import com.grash.repository.CostCenterRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostCenterServiceTest {

    @Mock
    private CostCenterRepository repository;
    @Mock
    private CostCenterMapper mapper;
    @Mock
    private WorkOrderFinancialService workOrderFinancialService;
    @Mock
    private EntityManager em;
    @InjectMocks
    private CostCenterService service;

    private User user;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(1L);
        user = new User();
        user.setCompany(company);
    }

    private CostCenter costCenter(Long id, String code) {
        CostCenter costCenter = new CostCenter();
        costCenter.setId(id);
        costCenter.setCode(code);
        costCenter.setName("Name");
        return costCenter;
    }

    @Test
    void create_rejectsADuplicatedCodeBeforeSaving() {
        CostCenterPostDTO dto = new CostCenterPostDTO();
        when(mapper.fromPostDto(dto)).thenReturn(costCenter(null, " CC-1 "));
        when(repository.findByCompany_IdAndCodeIgnoreCase(1L, "CC-1")).thenReturn(List.of(costCenter(9L, "cc-1")));

        CustomException e = assertThrows(CustomException.class, () -> service.create(dto, user));

        assertEquals(HttpStatus.NOT_ACCEPTABLE, e.getHttpStatus());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_aConcurrentDuplicateRejectedByTheDatabaseIsReportedAsDuplicatedCode() {
        CostCenterPostDTO dto = new CostCenterPostDTO();
        when(mapper.fromPostDto(dto)).thenReturn(costCenter(null, "CC-1"));
        when(repository.findByCompany_IdAndCodeIgnoreCase(1L, "CC-1")).thenReturn(List.of());
        when(repository.saveAndFlush(any(CostCenter.class)))
                .thenThrow(new DataIntegrityViolationException("uq_cost_center_company_code"));

        CustomException e = assertThrows(CustomException.class, () -> service.create(dto, user));

        assertEquals(HttpStatus.NOT_ACCEPTABLE, e.getHttpStatus());
    }

    @Test
    void create_savesWhenTheCodeIsFree() {
        CostCenterPostDTO dto = new CostCenterPostDTO();
        CostCenter mapped = costCenter(null, " CC-1 ");
        when(mapper.fromPostDto(dto)).thenReturn(mapped);
        when(repository.findByCompany_IdAndCodeIgnoreCase(1L, "CC-1")).thenReturn(List.of());
        when(repository.saveAndFlush(mapped)).thenReturn(mapped);

        CostCenter created = service.create(dto, user);

        assertEquals("CC-1", created.getCode());
        assertEquals(user.getCompany(), created.getCompany());
    }

    @Test
    void update_aConcurrentDuplicateRejectedByTheDatabaseIsReportedAsDuplicatedCode() {
        CostCenter saved = costCenter(5L, "CC-5");
        CostCenterPatchDTO dto = new CostCenterPatchDTO();
        when(repository.findById(5L)).thenReturn(Optional.of(saved));
        when(mapper.updateCostCenter(saved, dto)).thenReturn(saved);
        when(repository.findByCompany_IdAndCodeIgnoreCase(1L, "CC-5")).thenReturn(List.of(saved));
        when(repository.saveAndFlush(saved)).thenThrow(new DataIntegrityViolationException("duplicate"));

        CustomException e = assertThrows(CustomException.class, () -> service.update(5L, dto, user));

        assertEquals(HttpStatus.NOT_ACCEPTABLE, e.getHttpStatus());
        verify(workOrderFinancialService, never()).recalculateForCostCenter(any());
    }
}
