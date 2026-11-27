package com.grash.service;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.dto.WorkSchedulePatchDTO;
import com.grash.dto.WorkSchedulePostDTO;
import com.grash.dto.WorkScheduleShiftDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.WorkScheduleMapper;
import com.grash.model.Company;
import com.grash.model.WorkSchedule;
import com.grash.model.WorkScheduleShift;
import com.grash.repository.WorkScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkScheduleServiceTest {

    @Mock
    private WorkScheduleRepository repository;
    @Mock
    private WorkScheduleMapper mapper;
    @InjectMocks
    private WorkScheduleService service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
    }

    private static WorkScheduleShiftDTO shiftDto(int day, String start, String end) {
        WorkScheduleShiftDTO dto = new WorkScheduleShiftDTO();
        dto.setDayOfWeek(day);
        dto.setStartTime(LocalTime.parse(start));
        dto.setEndTime(LocalTime.parse(end));
        return dto;
    }

    private WorkSchedulePostDTO post(boolean defaultSchedule, WorkScheduleShiftDTO... shifts) {
        WorkSchedulePostDTO dto = new WorkSchedulePostDTO();
        dto.setName("Office");
        dto.setTimezone("America/Santo_Domingo");
        dto.setDefaultSchedule(defaultSchedule);
        dto.setShifts(new ArrayList<>(List.of(shifts)));
        return dto;
    }

    private void stubCreate(WorkSchedulePostDTO dto) {
        WorkSchedule mapped = new WorkSchedule();
        mapped.setDefaultSchedule(dto.isDefaultSchedule());
        when(mapper.fromPostDto(dto)).thenReturn(mapped);
        when(repository.save(any(WorkSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private WorkSchedule existing(long id, boolean defaultSchedule) {
        WorkSchedule schedule = new WorkSchedule();
        schedule.setId(id);
        schedule.setCompany(company);
        schedule.setName("Existing " + id);
        schedule.setTimezone("America/Santo_Domingo");
        schedule.setDefaultSchedule(defaultSchedule);
        return schedule;
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_buildsTheShiftsWithTheCompanyAndTheSchedule() {
        WorkSchedulePostDTO dto = post(false, shiftDto(1, "08:00", "12:00"), shiftDto(1, "14:00", "18:00"));
        stubCreate(dto);

        WorkSchedule created = service.create(dto, company);

        assertSame(company, created.getCompany());
        assertTrue(created.isActive());
        assertEquals("America/Santo_Domingo", created.getTimezone());
        assertEquals(2, created.getShifts().size());
        for (WorkScheduleShift shift : created.getShifts()) {
            assertSame(created, shift.getSchedule());
            assertSame(company, shift.getCompany());
        }
        verify(repository, never()).saveAll(anyCollection());
    }

    @Test
    void create_activeFalseIsKept() {
        WorkSchedulePostDTO dto = post(false);
        dto.setActive(false);
        stubCreate(dto);

        assertFalse(service.create(dto, company).isActive());
    }

    @Test
    void create_invalidTimezoneIs400() {
        WorkSchedulePostDTO dto = post(false);
        dto.setTimezone("Mars/Olympus");
        when(mapper.fromPostDto(dto)).thenReturn(new WorkSchedule());

        CustomException ex = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void create_overlappingShiftsInTheSameDayIs400() {
        WorkSchedulePostDTO dto = post(false, shiftDto(1, "08:00", "12:00"), shiftDto(1, "11:00", "15:00"));
        when(mapper.fromPostDto(dto)).thenReturn(new WorkSchedule());

        CustomException ex = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("overlap"));
        verify(repository, never()).save(any());
    }

    @Test
    void create_nightShiftOverlappingTheNextDayIs400() {
        // Monday 22:00-06:00 ends on Tuesday 06:00, which overlaps Tuesday 05:00-09:00
        WorkSchedulePostDTO dto = post(false, shiftDto(1, "22:00", "06:00"), shiftDto(2, "05:00", "09:00"));
        when(mapper.fromPostDto(dto)).thenReturn(new WorkSchedule());

        CustomException ex = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void create_sundayNightShiftOverlappingMondayMorningIs400() {
        // the week wraps around: Sunday 22:00-06:00 ends on Monday 06:00
        WorkSchedulePostDTO dto = post(false, shiftDto(7, "22:00", "06:00"), shiftDto(1, "00:00", "08:00"));
        when(mapper.fromPostDto(dto)).thenReturn(new WorkSchedule());

        CustomException ex = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void create_adjacentShiftsAndNightShiftFollowedByLaterShiftAreAccepted() {
        WorkSchedulePostDTO dto = post(false, shiftDto(1, "08:00", "12:00"), shiftDto(1, "12:00", "16:00"),
                shiftDto(1, "22:00", "06:00"), shiftDto(2, "06:00", "08:00"), shiftDto(7, "22:00", "06:00"));
        stubCreate(dto);

        assertEquals(5, service.create(dto, company).getShifts().size());
    }

    @Test
    void create_invalidShiftsAre400() {
        WorkSchedulePostDTO badDay = post(false, shiftDto(8, "08:00", "12:00"));
        when(mapper.fromPostDto(badDay)).thenReturn(new WorkSchedule());
        WorkScheduleShiftDTO noEnd = shiftDto(1, "08:00", "12:00");
        noEnd.setEndTime(null);
        WorkSchedulePostDTO missingTime = post(false, noEnd);
        when(mapper.fromPostDto(missingTime)).thenReturn(new WorkSchedule());

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(CustomException.class, () -> service.create(badDay, company)).getHttpStatus());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(CustomException.class, () -> service.create(missingTime, company)).getHttpStatus());
    }

    @Test
    void create_markingDefaultUnmarksTheOthers() {
        WorkSchedulePostDTO dto = post(true, shiftDto(1, "08:00", "17:00"));
        stubCreate(dto);
        WorkSchedule previousDefault = existing(5L, true);
        when(repository.findByCompany_IdAndDefaultScheduleTrue(1L)).thenReturn(List.of(previousDefault));

        WorkSchedule created = service.create(dto, company);

        assertTrue(created.isDefaultSchedule());
        assertFalse(previousDefault.isDefaultSchedule());
        verify(repository).saveAll(List.of(previousDefault));
    }

    // ------------------------------------------------------------------ update

    @Test
    void update_notFoundIs404() {
        when(repository.findByIdAndCompany_Id(9L, 1L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> service.update(9L, new WorkSchedulePatchDTO(), company));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void update_withoutShiftsKeepsTheCurrentOnes() {
        WorkSchedule saved = existing(3L, false);
        WorkScheduleShift kept = new WorkScheduleShift();
        saved.getShifts().add(kept);
        when(repository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        when(repository.save(saved)).thenReturn(saved);
        WorkSchedulePatchDTO dto = new WorkSchedulePatchDTO();
        dto.setName("Renamed");

        WorkSchedule updated = service.update(3L, dto, company);

        assertEquals(List.of(kept), updated.getShifts());
        verify(mapper).updateWorkSchedule(saved, dto);
    }

    @Test
    void update_replacesAllTheShifts() {
        WorkSchedule saved = existing(3L, false);
        saved.getShifts().add(new WorkScheduleShift());
        saved.getShifts().add(new WorkScheduleShift());
        when(repository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        when(repository.save(saved)).thenReturn(saved);
        WorkSchedulePatchDTO dto = new WorkSchedulePatchDTO();
        dto.setShifts(List.of(shiftDto(3, "09:00", "18:00")));

        WorkSchedule updated = service.update(3L, dto, company);

        assertEquals(1, updated.getShifts().size());
        assertEquals(3, updated.getShifts().get(0).getDayOfWeek());
        assertSame(saved, updated.getShifts().get(0).getSchedule());
    }

    @Test
    void update_emptyShiftsListRemovesAll() {
        WorkSchedule saved = existing(3L, false);
        saved.getShifts().add(new WorkScheduleShift());
        when(repository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        when(repository.save(saved)).thenReturn(saved);
        WorkSchedulePatchDTO dto = new WorkSchedulePatchDTO();
        dto.setShifts(Collections.emptyList());

        assertTrue(service.update(3L, dto, company).getShifts().isEmpty());
    }

    @Test
    void update_overlappingShiftsIs400AndNothingIsSaved() {
        WorkSchedule saved = existing(3L, false);
        when(repository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        WorkSchedulePatchDTO dto = new WorkSchedulePatchDTO();
        dto.setShifts(List.of(shiftDto(3, "09:00", "18:00"), shiftDto(3, "17:00", "20:00")));

        CustomException ex = assertThrows(CustomException.class, () -> service.update(3L, dto, company));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void update_invalidTimezoneIs400() {
        WorkSchedule saved = existing(3L, false);
        when(repository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        WorkSchedulePatchDTO dto = new WorkSchedulePatchDTO();
        dto.setTimezone("nope");

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(CustomException.class, () -> service.update(3L, dto, company)).getHttpStatus());
    }

    @Test
    void update_markingDefaultUnmarksTheOthersButNotItself() {
        WorkSchedule saved = existing(3L, false);
        saved.setDefaultSchedule(true); // as if the mapper already applied the patch
        WorkSchedule other = existing(5L, true);
        when(repository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        when(repository.findByCompany_IdAndDefaultScheduleTrue(1L)).thenReturn(List.of(saved, other));
        when(repository.save(saved)).thenReturn(saved);
        WorkSchedulePatchDTO dto = new WorkSchedulePatchDTO();
        dto.setDefaultSchedule(true);

        service.update(3L, dto, company);

        assertFalse(other.isDefaultSchedule());
        assertTrue(saved.isDefaultSchedule());
        verify(repository).saveAll(List.of(other));
    }

    @Test
    void update_unmarkingDefaultDoesNotTouchTheOthers() {
        WorkSchedule saved = existing(3L, true);
        when(repository.findByIdAndCompany_Id(3L, 1L)).thenReturn(Optional.of(saved));
        when(repository.save(saved)).thenReturn(saved);
        WorkSchedulePatchDTO dto = new WorkSchedulePatchDTO();
        dto.setDefaultSchedule(false);

        service.update(3L, dto, company);

        verify(repository, never()).findByCompany_IdAndDefaultScheduleTrue(any());
        verify(repository, never()).saveAll(anyList());
    }

    // ------------------------------------------------------------------ others

    @Test
    void delete_deletesById() {
        service.delete(3L);

        verify(repository).deleteById(3L);
    }

    @Test
    void findBySearchCriteria_usesTheRepositoryPage() {
        WorkSchedule schedule = existing(3L, false);
        when(repository.findAll(nullable(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(schedule)));

        assertEquals(1, service.findBySearchCriteria(new SearchCriteria()).getTotalElements());
    }

    @Test
    void validateTimezone_trimsAndRejectsBlank() {
        assertEquals("America/Santo_Domingo", WorkScheduleService.validateTimezone(" America/Santo_Domingo "));
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(CustomException.class, () -> WorkScheduleService.validateTimezone(" ")).getHttpStatus());
    }
}
