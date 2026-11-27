package com.grash.service;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.advancedsearch.SpecificationBuilder;
import com.grash.dto.IdDTO;
import com.grash.dto.SlaPolicyPatchDTO;
import com.grash.dto.SlaPolicyPostDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.SlaPolicyMapper;
import com.grash.model.Company;
import com.grash.model.ServiceContract;
import com.grash.model.SlaPolicy;
import com.grash.model.WorkSchedule;
import com.grash.model.enums.Priority;
import com.grash.repository.ServiceContractRepository;
import com.grash.repository.SlaPolicyRepository;
import com.grash.repository.WorkScheduleRepository;
import com.grash.utils.Sanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SlaPolicyService {
    private final SlaPolicyRepository slaPolicyRepository;
    private final SlaPolicyMapper slaPolicyMapper;
    private final ServiceContractRepository serviceContractRepository;
    private final WorkScheduleRepository workScheduleRepository;

    @Transactional
    public SlaPolicy create(SlaPolicyPostDTO dto, Company company) {
        SlaPolicy policy = slaPolicyMapper.fromPostDto(dto);
        policy.setId(null);
        policy.setCompany(company);
        policy.setName(Sanitizer.cleanText(dto.getName()));
        policy.setPriority(dto.getPriority() == null ? Priority.NONE : dto.getPriority());
        policy.setServiceContract(resolveContract(dto.getServiceContract(), company));
        policy.setWorkSchedule(resolveSchedule(dto.getWorkSchedule(), company));
        return slaPolicyRepository.save(policy);
    }

    @Transactional
    public SlaPolicy update(Long id, SlaPolicyPatchDTO dto, Company company) {
        SlaPolicy saved = findByIdAndCompany(id, company.getId())
                .orElseThrow(() -> new CustomException("SLA policy not found", HttpStatus.NOT_FOUND));
        if (dto.getName() != null) dto.setName(dto.getName().isBlank() ? null : Sanitizer.cleanText(dto.getName()));
        slaPolicyMapper.updateSlaPolicy(saved, dto);
        // an object without id detaches the contract (general policy)
        if (dto.getServiceContract() != null)
            saved.setServiceContract(resolveContract(dto.getServiceContract(), company));
        // an object without id removes the schedule (technician / company default is used)
        if (dto.getWorkSchedule() != null) saved.setWorkSchedule(resolveSchedule(dto.getWorkSchedule(), company));
        return slaPolicyRepository.save(saved);
    }

    @Transactional
    public void delete(Long id) {
        slaPolicyRepository.deleteById(id);
    }

    public Optional<SlaPolicy> findById(Long id) {
        return slaPolicyRepository.findById(id);
    }

    public Optional<SlaPolicy> findByIdAndCompany(Long id, Long companyId) {
        return slaPolicyRepository.findByIdAndCompany_Id(id, companyId);
    }

    public Page<SlaPolicy> findBySearchCriteria(SearchCriteria searchCriteria) {
        SpecificationBuilder<SlaPolicy> builder = new SpecificationBuilder<>();
        searchCriteria.getFilterFields().forEach(builder::with);
        Pageable page = PageRequest.of(searchCriteria.getPageNum(), searchCriteria.getPageSize(),
                searchCriteria.getDirection(), searchCriteria.getSortField());
        return slaPolicyRepository.findAll(builder.build(), page);
    }

    private WorkSchedule resolveSchedule(IdDTO reference, Company company) {
        if (reference == null || reference.getId() == null) return null;
        return workScheduleRepository.findByIdAndCompany_Id(reference.getId(), company.getId())
                .orElseThrow(() -> new CustomException("Work schedule not found", HttpStatus.NOT_FOUND));
    }

    private ServiceContract resolveContract(ServiceContract reference, Company company) {
        if (reference == null || reference.getId() == null) return null;
        return serviceContractRepository.findByIdAndCompany_Id(reference.getId(), company.getId())
                .orElseThrow(() -> new CustomException("Service contract not found", HttpStatus.NOT_FOUND));
    }
}
