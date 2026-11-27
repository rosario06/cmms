package com.grash.service;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.advancedsearch.SpecificationBuilder;
import com.grash.dto.WorkSchedulePatchDTO;
import com.grash.dto.WorkSchedulePostDTO;
import com.grash.dto.WorkScheduleShiftDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.WorkScheduleMapper;
import com.grash.model.Company;
import com.grash.model.WorkSchedule;
import com.grash.model.WorkScheduleShift;
import com.grash.repository.WorkScheduleRepository;
import com.grash.utils.Sanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkScheduleService {
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

    private final WorkScheduleRepository workScheduleRepository;
    private final WorkScheduleMapper workScheduleMapper;

    @Transactional
    public WorkSchedule create(WorkSchedulePostDTO dto, Company company) {
        WorkSchedule schedule = workScheduleMapper.fromPostDto(dto);
        schedule.setId(null);
        schedule.setCompany(company);
        schedule.setName(Sanitizer.cleanText(dto.getName()));
        schedule.setTimezone(validateTimezone(dto.getTimezone()));
        schedule.setActive(dto.getActive() == null || dto.getActive());
        schedule.setShifts(new ArrayList<>());
        replaceShifts(schedule, dto.getShifts());
        if (schedule.isDefaultSchedule()) unmarkOtherDefaults(company.getId(), null);
        return workScheduleRepository.save(schedule);
    }

    @Transactional
    public WorkSchedule update(Long id, WorkSchedulePatchDTO dto, Company company) {
        WorkSchedule saved = findByIdAndCompany(id, company.getId())
                .orElseThrow(() -> new CustomException("Work schedule not found", HttpStatus.NOT_FOUND));
        if (dto.getName() != null) dto.setName(dto.getName().isBlank() ? null : Sanitizer.cleanText(dto.getName()));
        if (dto.getTimezone() != null) dto.setTimezone(validateTimezone(dto.getTimezone()));
        workScheduleMapper.updateWorkSchedule(saved, dto);
        if (dto.getShifts() != null) replaceShifts(saved, dto.getShifts());
        if (Boolean.TRUE.equals(dto.getDefaultSchedule())) unmarkOtherDefaults(company.getId(), saved.getId());
        return workScheduleRepository.save(saved);
    }

    /**
     * The FKs of users and SLA policies are ON DELETE SET NULL, the holidays of the schedule are deleted with it.
     */
    @Transactional
    public void delete(Long id) {
        workScheduleRepository.deleteById(id);
    }

    public Optional<WorkSchedule> findById(Long id) {
        return workScheduleRepository.findById(id);
    }

    public Optional<WorkSchedule> findByIdAndCompany(Long id, Long companyId) {
        return workScheduleRepository.findByIdAndCompany_Id(id, companyId);
    }

    public Collection<WorkSchedule> findByCompany(Long companyId) {
        return workScheduleRepository.findByCompany_IdOrderByNameAsc(companyId);
    }

    public Page<WorkSchedule> findBySearchCriteria(SearchCriteria searchCriteria) {
        SpecificationBuilder<WorkSchedule> builder = new SpecificationBuilder<>();
        searchCriteria.getFilterFields().forEach(builder::with);
        Pageable page = PageRequest.of(searchCriteria.getPageNum(), searchCriteria.getPageSize(),
                searchCriteria.getDirection(), searchCriteria.getSortField());
        return workScheduleRepository.findAll(builder.build(), page);
    }

    // ---------------------------------------------------------------- validation

    static String validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank())
            throw new CustomException("Time zone is required", HttpStatus.BAD_REQUEST);
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException e) {
            throw new CustomException("Invalid time zone: " + timezone, HttpStatus.BAD_REQUEST);
        }
    }

    /** Replaces ALL the shifts of the schedule (orphanRemoval deletes the old ones). */
    private void replaceShifts(WorkSchedule schedule, List<WorkScheduleShiftDTO> shiftDtos) {
        List<WorkScheduleShift> newShifts = new ArrayList<>();
        if (shiftDtos != null) {
            for (WorkScheduleShiftDTO shiftDto : shiftDtos) {
                if (shiftDto.getDayOfWeek() == null || shiftDto.getDayOfWeek() < 1 || shiftDto.getDayOfWeek() > 7)
                    throw new CustomException("Day of week must be between 1 (Monday) and 7 (Sunday)",
                            HttpStatus.BAD_REQUEST);
                if (shiftDto.getStartTime() == null || shiftDto.getEndTime() == null)
                    throw new CustomException("Start time and end time are required", HttpStatus.BAD_REQUEST);
                WorkScheduleShift shift = new WorkScheduleShift();
                shift.setSchedule(schedule);
                shift.setCompany(schedule.getCompany());
                shift.setName(shiftDto.getName() == null || shiftDto.getName().isBlank() ? null :
                        Sanitizer.cleanText(shiftDto.getName()));
                shift.setDayOfWeek(shiftDto.getDayOfWeek());
                shift.setStartTime(shiftDto.getStartTime());
                shift.setEndTime(shiftDto.getEndTime());
                newShifts.add(shift);
            }
        }
        checkNoOverlap(newShifts);
        if (schedule.getShifts() == null) schedule.setShifts(new ArrayList<>());
        schedule.getShifts().clear();
        schedule.getShifts().addAll(newShifts);
    }

    /**
     * Fails with 400 when two shifts overlap. The week is cyclic: a Sunday night shift that ends on Monday morning
     * is compared with the Monday shifts.
     */
    static void checkNoOverlap(List<WorkScheduleShift> shifts) {
        for (int i = 0; i < shifts.size(); i++) {
            for (int j = i + 1; j < shifts.size(); j++) {
                if (overlaps(shifts.get(i), shifts.get(j)))
                    throw new CustomException("Shifts overlap: " + describe(shifts.get(i)) + " and " +
                            describe(shifts.get(j)), HttpStatus.BAD_REQUEST);
            }
        }
    }

    private static boolean overlaps(WorkScheduleShift a, WorkScheduleShift b) {
        int aStart = weekMinute(a);
        int aEnd = aStart + length(a);
        int bStart = weekMinute(b);
        int bEnd = bStart + length(b);
        // b is also tested one week before / after to cover the wrap-around of the week
        for (int offset = -MINUTES_PER_WEEK; offset <= MINUTES_PER_WEEK; offset += MINUTES_PER_WEEK) {
            if (aStart < bEnd + offset && bStart + offset < aEnd) return true;
        }
        return false;
    }

    private static int weekMinute(WorkScheduleShift shift) {
        LocalTime start = shift.getStartTime();
        return (shift.getDayOfWeek() - 1) * MINUTES_PER_DAY + start.getHour() * 60 + start.getMinute();
    }

    private static int length(WorkScheduleShift shift) {
        long minutes = Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes();
        // end <= start crosses midnight
        return (int) (minutes > 0 ? minutes : minutes + MINUTES_PER_DAY);
    }

    private static String describe(WorkScheduleShift shift) {
        return DayOfWeek.of(shift.getDayOfWeek()) + " " + shift.getStartTime() + "-" + shift.getEndTime();
    }

    /** At most one default schedule per company. */
    private void unmarkOtherDefaults(Long companyId, Long keepId) {
        List<WorkSchedule> others = new ArrayList<>();
        for (WorkSchedule other : workScheduleRepository.findByCompany_IdAndDefaultScheduleTrue(companyId)) {
            if (keepId != null && keepId.equals(other.getId())) continue;
            other.setDefaultSchedule(false);
            others.add(other);
        }
        if (!others.isEmpty()) workScheduleRepository.saveAll(others);
    }
}
