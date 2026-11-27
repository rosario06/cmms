package com.grash.service;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.advancedsearch.SpecificationBuilder;
import com.grash.dto.ServiceContractPatchDTO;
import com.grash.dto.ServiceContractPostDTO;
import com.grash.dto.ServiceContractUsageDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.ServiceContractMapper;
import com.grash.model.Company;
import com.grash.model.Customer;
import com.grash.model.ServiceContract;
import com.grash.model.enums.BillingCycle;
import com.grash.model.enums.ContractStatus;
import com.grash.repository.ServiceContractRepository;
import com.grash.utils.Sanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceContractService {
    private static final int MAX_NUMBER_ATTEMPTS = 5;

    private final ServiceContractRepository serviceContractRepository;
    private final ServiceContractMapper serviceContractMapper;
    private final CustomerService customerService;

    /**
     * Not @Transactional on purpose: every save attempt commits on its own, so that a violation of the unique index
     * (company_id, contract_number) caused by a concurrent request can be retried (bounded) with a new number.
     */
    public ServiceContract create(ServiceContractPostDTO dto, Company company) {
        validateDates(dto.getStartDate(), dto.getEndDate());
        ServiceContract contract = serviceContractMapper.fromPostDto(dto);
        contract.setId(null);
        contract.setCompany(company);
        contract.setName(Sanitizer.cleanText(dto.getName()));
        contract.setStatus(dto.getStatus() == null ? ContractStatus.DRAFT : dto.getStatus());
        contract.setBillingCycle(dto.getBillingCycle() == null ? BillingCycle.MONTHLY : dto.getBillingCycle());
        contract.setCustomer(resolveCustomer(dto.getCustomer(), company));
        contract.setLastInvoicedAt(null);
        // hours still available start equal to the included ones unless explicitly indicated
        contract.setRemainingHours(dto.getRemainingHours() == null ? dto.getIncludedHours() :
                dto.getRemainingHours());

        String requested = dto.getContractNumber() == null ? "" : Sanitizer.cleanText(dto.getContractNumber()).trim();
        boolean explicitNumber = !requested.isEmpty();
        if (explicitNumber && serviceContractRepository.existsByCompany_IdAndContractNumber(company.getId(),
                requested)) {
            throw new CustomException("Contract number already exists", HttpStatus.CONFLICT);
        }
        for (int attempt = 1; attempt <= MAX_NUMBER_ATTEMPTS; attempt++) {
            String number = explicitNumber ? requested : generateContractNumber(company.getId());
            contract.setId(null);
            contract.setContractNumber(number);
            try {
                return serviceContractRepository.saveAndFlush(contract);
            } catch (DataIntegrityViolationException e) {
                // only a collision of the number is handled here; any other integrity error is propagated
                if (!serviceContractRepository.existsByCompany_IdAndContractNumber(company.getId(), number)) throw e;
                if (explicitNumber)
                    throw new CustomException("Contract number already exists", HttpStatus.CONFLICT);
                log.warn("Contract number collision for company {} (attempt {}/{})", company.getId(), attempt,
                        MAX_NUMBER_ATTEMPTS);
            }
        }
        throw new CustomException("Could not allocate a unique contract number, please retry", HttpStatus.CONFLICT);
    }

    @Transactional
    public ServiceContract update(Long id, ServiceContractPatchDTO dto, Company company) {
        ServiceContract saved = findByIdAndCompany(id, company.getId())
                .orElseThrow(() -> new CustomException("Service contract not found", HttpStatus.NOT_FOUND));
        ContractStatus statusBefore = saved.getStatus();

        if (dto.getName() != null) dto.setName(dto.getName().isBlank() ? null : Sanitizer.cleanText(dto.getName()));
        if (dto.getContractNumber() != null) {
            String number = Sanitizer.cleanText(dto.getContractNumber()).trim();
            if (number.isEmpty() || number.equals(saved.getContractNumber())) {
                dto.setContractNumber(null);
            } else {
                if (serviceContractRepository.existsByCompany_IdAndContractNumber(company.getId(), number))
                    throw new CustomException("Contract number already exists", HttpStatus.CONFLICT);
                dto.setContractNumber(number);
            }
        }
        serviceContractMapper.updateServiceContract(saved, dto);
        if (dto.getCustomer() != null) saved.setCustomer(resolveCustomer(dto.getCustomer(), company));
        validateDates(saved.getStartDate(), saved.getEndDate());

        // activation of a contract that never had its hours initialised
        if (statusBefore == ContractStatus.DRAFT && saved.getStatus() == ContractStatus.ACTIVE
                && dto.getRemainingHours() == null && saved.getRemainingHours() <= 0
                && saved.getIncludedHours() > 0) {
            saved.setRemainingHours(saved.getIncludedHours());
        }
        try {
            return serviceContractRepository.saveAndFlush(saved);
        } catch (DataIntegrityViolationException e) {
            if (dto.getContractNumber() != null) // the number was changed: most likely a concurrent duplicate
                throw new CustomException("Contract number already exists", HttpStatus.CONFLICT);
            throw e;
        }
    }

    @Transactional
    public void delete(Long id) {
        serviceContractRepository.deleteById(id);
    }

    public Optional<ServiceContract> findById(Long id) {
        return serviceContractRepository.findById(id);
    }

    public Optional<ServiceContract> findByIdAndCompany(Long id, Long companyId) {
        return serviceContractRepository.findByIdAndCompany_Id(id, companyId);
    }

    public Page<ServiceContract> findBySearchCriteria(SearchCriteria searchCriteria) {
        SpecificationBuilder<ServiceContract> builder = new SpecificationBuilder<>();
        searchCriteria.getFilterFields().forEach(builder::with);
        Pageable page = PageRequest.of(searchCriteria.getPageNum(), searchCriteria.getPageSize(),
                searchCriteria.getDirection(), searchCriteria.getSortField());
        return serviceContractRepository.findAll(builder.build(), page);
    }

    @Transactional(readOnly = true)
    public ServiceContractUsageDTO getUsage(Long id) {
        ServiceContract contract = serviceContractRepository.findById(id)
                .orElseThrow(() -> new CustomException("Service contract not found", HttpStatus.NOT_FOUND));
        return getUsage(contract);
    }

    public ServiceContractUsageDTO getUsage(ServiceContract contract) {
        double included = Math.max(0, contract.getIncludedHours());
        double remaining = Math.max(0, contract.getRemainingHours());
        double consumed = Math.max(0, included - remaining);
        double percent = included > 0 ? consumed / included * 100 : 0;
        return new ServiceContractUsageDTO(included, remaining, consumed, percent, contract.getRetainerBalance());
    }

    String generateContractNumber(Long companyId) {
        String prefix = "CT-" + LocalDate.now().getYear() + "-";
        long next = serviceContractRepository.countByCompany_IdAndContractNumberStartingWith(companyId, prefix) + 1;
        String candidate;
        do {
            candidate = prefix + String.format("%04d", next++);
        } while (serviceContractRepository.existsByCompany_IdAndContractNumber(companyId, candidate));
        return candidate;
    }

    private Customer resolveCustomer(Customer reference, Company company) {
        if (reference == null || reference.getId() == null) return null;
        return customerService.findByCompanyAndIdIn(company.getId(), Collections.singletonList(reference.getId()))
                .stream().findFirst()
                .orElseThrow(() -> new CustomException("Customer not found", HttpStatus.NOT_FOUND));
    }

    private static void validateDates(Date start, Date end) {
        if (start != null && end != null && end.before(start))
            throw new CustomException("endDate must not be before startDate", HttpStatus.BAD_REQUEST);
    }
}
