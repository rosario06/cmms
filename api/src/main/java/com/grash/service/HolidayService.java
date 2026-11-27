package com.grash.service;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.advancedsearch.SpecificationBuilder;
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
import com.grash.utils.Sanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HolidayService {
    private final HolidayRepository holidayRepository;
    private final HolidayMapper holidayMapper;
    private final WorkScheduleRepository workScheduleRepository;

    @Transactional
    public Holiday create(HolidayPostDTO dto, Company company) {
        Holiday holiday = holidayMapper.fromPostDto(dto);
        holiday.setId(null);
        holiday.setCompany(company);
        holiday.setName(Sanitizer.cleanText(dto.getName()));
        holiday.setWorkSchedule(resolveSchedule(dto.getWorkSchedule(), company));
        return holidayRepository.save(holiday);
    }

    @Transactional
    public Holiday update(Long id, HolidayPatchDTO dto, Company company) {
        Holiday saved = findByIdAndCompany(id, company.getId())
                .orElseThrow(() -> new CustomException("Holiday not found", HttpStatus.NOT_FOUND));
        if (dto.getName() != null) dto.setName(dto.getName().isBlank() ? null : Sanitizer.cleanText(dto.getName()));
        holidayMapper.updateHoliday(saved, dto);
        // an object without id makes the holiday apply to every schedule
        if (dto.getWorkSchedule() != null) saved.setWorkSchedule(resolveSchedule(dto.getWorkSchedule(), company));
        return holidayRepository.save(saved);
    }

    @Transactional
    public void delete(Long id) {
        holidayRepository.deleteById(id);
    }

    public Optional<Holiday> findById(Long id) {
        return holidayRepository.findById(id);
    }

    public Optional<Holiday> findByIdAndCompany(Long id, Long companyId) {
        return holidayRepository.findByIdAndCompany_Id(id, companyId);
    }

    /**
     * @param year optional: only the holidays that fall in that year (the recurring ones always match)
     */
    public Page<Holiday> findBySearchCriteria(SearchCriteria searchCriteria, Integer year) {
        SpecificationBuilder<Holiday> builder = new SpecificationBuilder<>();
        searchCriteria.getFilterFields().forEach(builder::with);
        if (year != null) {
            LocalDate from = LocalDate.of(year, 1, 1);
            LocalDate to = LocalDate.of(year, 12, 31);
            Specification<Holiday> inYear = (root, query, cb) -> cb.or(
                    cb.between(root.<LocalDate>get("date"), from, to),
                    cb.isTrue(root.<Boolean>get("recurringAnnually")));
            builder.with(inYear);
        }
        Pageable page = PageRequest.of(searchCriteria.getPageNum(), searchCriteria.getPageSize(),
                searchCriteria.getDirection(), searchCriteria.getSortField());
        return holidayRepository.findAll(builder.build(), page);
    }

    private WorkSchedule resolveSchedule(IdDTO reference, Company company) {
        if (reference == null || reference.getId() == null) return null;
        return workScheduleRepository.findByIdAndCompany_Id(reference.getId(), company.getId())
                .orElseThrow(() -> new CustomException("Work schedule not found", HttpStatus.NOT_FOUND));
    }
}
