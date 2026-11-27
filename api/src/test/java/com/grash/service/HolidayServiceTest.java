package com.grash.service;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.HolidayPatchDTO;
import com.grash.dto.HolidayPostDTO;
import com.grash.dto.IdDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.HolidayMapper;
import com.grash.model.Company;
import com.grash.model.Holiday;
import com.grash.model.WorkSchedule;
import com.grash.repository.HolidayRepository;
import com.grash.repository.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private HolidayMapper holidayMapper;
    @Mock
    private WorkScheduleRepository workScheduleRepository;
    @InjectMocks
    private HolidayService service;

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

    private HolidayPostDTO post(IdDTO schedule) {
        HolidayPostDTO dto = new HolidayPostDTO();
        dto.setName("Navidad");
        dto.setDate(LocalDate.of(2026, 12, 25));
        dto.setWorkSchedule(schedule);
        return dto;
    }

    private Holiday holiday(Long id) {
        Holiday holiday = new Holiday();
        holiday.setId(id);
        holiday.setCompany(company);
        holiday.setName("Navidad");
        holiday.setDate(LocalDate.of(2026, 12, 25));
        return holiday;
    }

    @Test
    void create_withoutScheduleAppliesToTheWholeCompany() {
        HolidayPostDTO dto = post(null);
        when(holidayMapper.fromPostDto(dto)).thenReturn(new Holiday());
        when(holidayRepository.save(any(Holiday.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Holiday created = service.create(dto, company);

        assertSame(company, created.getCompany());
        assertNull(created.getWorkSchedule());
        assertEquals("Navidad", created.getName());
        verify(workScheduleRepository, never()).findByIdAndCompany_Id(any(), any());
    }

    @Test
    void create_withScheduleOfTheCompany() {
        WorkSchedule schedule = new WorkSchedule();
        schedule.setId(4L);
        HolidayPostDTO dto = post(ref(4L));
        when(holidayMapper.fromPostDto(dto)).thenReturn(new Holiday());
        when(workScheduleRepository.findByIdAndCompany_Id(4L, 1L)).thenReturn(Optional.of(schedule));
        when(holidayRepository.save(any(Holiday.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertSame(schedule, service.create(dto, company).getWorkSchedule());
    }

    @Test
    void create_scheduleOfAnotherCompanyIs404() {
        HolidayPostDTO dto = post(ref(4L));
        when(holidayMapper.fromPostDto(dto)).thenReturn(new Holiday());
        when(workScheduleRepository.findByIdAndCompany_Id(4L, 1L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        verify(holidayRepository, never()).save(any());
    }

    @Test
    void update_notFoundIs404() {
        when(holidayRepository.findByIdAndCompany_Id(9L, 1L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> service.update(9L, new HolidayPatchDTO(), company));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void update_withoutScheduleFieldKeepsTheSchedule() {
        Holiday saved = holiday(3L);
        WorkSchedule schedule = new WorkSchedule();
        saved.setWorkSchedule(schedule);
        when(holidayRepository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        when(holidayRepository.save(saved)).thenReturn(saved);
        HolidayPatchDTO dto = new HolidayPatchDTO();
        dto.setName("Renamed");

        assertSame(schedule, service.update(3L, dto, company).getWorkSchedule());
        verify(holidayMapper).updateHoliday(saved, dto);
    }

    @Test
    void update_objectWithoutIdMakesItCompanyWide() {
        Holiday saved = holiday(3L);
        saved.setWorkSchedule(new WorkSchedule());
        when(holidayRepository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        when(holidayRepository.save(saved)).thenReturn(saved);
        HolidayPatchDTO dto = new HolidayPatchDTO();
        dto.setWorkSchedule(new IdDTO());

        assertNull(service.update(3L, dto, company).getWorkSchedule());
    }

    @Test
    void update_scheduleOfAnotherCompanyIs404() {
        Holiday saved = holiday(3L);
        when(holidayRepository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        when(workScheduleRepository.findByIdAndCompany_Id(8L, 1L)).thenReturn(Optional.empty());
        HolidayPatchDTO dto = new HolidayPatchDTO();
        dto.setWorkSchedule(ref(8L));

        CustomException ex = assertThrows(CustomException.class, () -> service.update(3L, dto, company));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        verify(holidayRepository, never()).save(any());
    }

    @Test
    void delete_deletesById() {
        service.delete(3L);

        verify(holidayRepository).deleteById(3L);
    }

    @Test
    void search_withYearAddsASpecificationToTheFilters() {
        when(holidayRepository.findAll(nullable(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(holiday(3L))));
        ArgumentCaptor<Specification> captor = ArgumentCaptor.forClass(Specification.class);

        service.findBySearchCriteria(new SearchCriteria(), 2026);

        verify(holidayRepository).findAll(captor.capture(), any(Pageable.class));
        assertNotNull(captor.getValue());
    }

    @Test
    void search_withoutYearAndWithoutFiltersHasNoSpecification() {
        when(holidayRepository.findAll(nullable(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(holiday(3L))));
        ArgumentCaptor<Specification> captor = ArgumentCaptor.forClass(Specification.class);

        service.findBySearchCriteria(new SearchCriteria(), null);

        verify(holidayRepository).findAll(captor.capture(), any(Pageable.class));
        assertNull(captor.getValue());
    }
}
