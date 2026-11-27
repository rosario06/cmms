package com.grash.service;

import com.grash.dto.IdDTO;
import com.grash.dto.SlaPolicyPatchDTO;
import com.grash.dto.SlaPolicyPostDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.SlaPolicyMapper;
import com.grash.model.Company;
import com.grash.model.SlaPolicy;
import com.grash.model.WorkSchedule;
import com.grash.repository.ServiceContractRepository;
import com.grash.repository.SlaPolicyRepository;
import com.grash.repository.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaPolicyServiceTest {

    @Mock
    private SlaPolicyRepository slaPolicyRepository;
    @Mock
    private SlaPolicyMapper slaPolicyMapper;
    @Mock
    private ServiceContractRepository serviceContractRepository;
    @Mock
    private WorkScheduleRepository workScheduleRepository;
    @InjectMocks
    private SlaPolicyService service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
    }

    private static IdDTO ref(Long id) {
        IdDTO dto = new IdDTO();
        dto.setId(id);
        return dto;
    }

    private SlaPolicyPostDTO post(IdDTO schedule, boolean useBusinessHours) {
        SlaPolicyPostDTO dto = new SlaPolicyPostDTO();
        dto.setName("Urgent");
        dto.setUseBusinessHours(useBusinessHours);
        dto.setWorkSchedule(schedule);
        return dto;
    }

    @Test
    void create_resolvesTheScheduleOfTheCompany() {
        WorkSchedule schedule = new WorkSchedule();
        schedule.setId(4L);
        SlaPolicyPostDTO dto = post(ref(4L), true);
        SlaPolicy mapped = new SlaPolicy();
        mapped.setUseBusinessHours(true);
        when(slaPolicyMapper.fromPostDto(dto)).thenReturn(mapped);
        when(workScheduleRepository.findByIdAndCompany_Id(4L, 1L)).thenReturn(Optional.of(schedule));
        when(slaPolicyRepository.save(any(SlaPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SlaPolicy created = service.create(dto, company);

        assertSame(schedule, created.getWorkSchedule());
        assertTrue(created.isUseBusinessHours());
    }

    @Test
    void create_scheduleOfAnotherCompanyIs404() {
        SlaPolicyPostDTO dto = post(ref(4L), true);
        when(slaPolicyMapper.fromPostDto(dto)).thenReturn(new SlaPolicy());
        when(workScheduleRepository.findByIdAndCompany_Id(4L, 1L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        verify(slaPolicyRepository, never()).save(any());
    }

    @Test
    void create_withoutScheduleLeavesItEmpty() {
        SlaPolicyPostDTO dto = post(null, true);
        when(slaPolicyMapper.fromPostDto(dto)).thenReturn(new SlaPolicy());
        when(slaPolicyRepository.save(any(SlaPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertNull(service.create(dto, company).getWorkSchedule());
    }

    @Test
    void update_objectWithoutIdRemovesTheSchedule() {
        SlaPolicy saved = new SlaPolicy();
        saved.setId(7L);
        saved.setWorkSchedule(new WorkSchedule());
        when(slaPolicyRepository.findByIdAndCompany_Id(7L, 1L)).thenReturn(Optional.of(saved));
        when(slaPolicyRepository.save(saved)).thenReturn(saved);
        SlaPolicyPatchDTO dto = new SlaPolicyPatchDTO();
        dto.setWorkSchedule(new IdDTO());

        assertNull(service.update(7L, dto, company).getWorkSchedule());
    }

    @Test
    void update_withoutScheduleFieldKeepsIt() {
        SlaPolicy saved = new SlaPolicy();
        saved.setId(7L);
        WorkSchedule schedule = new WorkSchedule();
        saved.setWorkSchedule(schedule);
        when(slaPolicyRepository.findByIdAndCompany_Id(7L, 1L)).thenReturn(Optional.of(saved));
        when(slaPolicyRepository.save(saved)).thenReturn(saved);

        assertSame(schedule, service.update(7L, new SlaPolicyPatchDTO(), company).getWorkSchedule());
    }

    @Test
    void update_assignsTheScheduleOfTheCompany() {
        SlaPolicy saved = new SlaPolicy();
        saved.setId(7L);
        WorkSchedule schedule = new WorkSchedule();
        schedule.setId(4L);
        when(slaPolicyRepository.findByIdAndCompany_Id(7L, 1L)).thenReturn(Optional.of(saved));
        when(workScheduleRepository.findByIdAndCompany_Id(4L, 1L)).thenReturn(Optional.of(schedule));
        when(slaPolicyRepository.save(saved)).thenReturn(saved);
        SlaPolicyPatchDTO dto = new SlaPolicyPatchDTO();
        dto.setWorkSchedule(ref(4L));

        assertSame(schedule, service.update(7L, dto, company).getWorkSchedule());
    }
}
