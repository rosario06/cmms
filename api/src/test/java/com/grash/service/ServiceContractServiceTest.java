package com.grash.service;

import com.grash.dto.ServiceContractPatchDTO;
import com.grash.dto.ServiceContractPostDTO;
import com.grash.dto.ServiceContractUsageDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.ServiceContractMapper;
import com.grash.model.Company;
import com.grash.model.ServiceContract;
import com.grash.model.enums.BillingCycle;
import com.grash.model.enums.ContractStatus;
import com.grash.repository.ServiceContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceContractServiceTest {

    private static final double DELTA = 1e-9;

    @Mock
    private ServiceContractRepository repository;
    @Mock
    private ServiceContractMapper mapper;
    @Mock
    private CustomerService customerService;
    @InjectMocks
    private ServiceContractService service;

    private Company company;
    private final int year = LocalDate.now().getYear();

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
    }

    private ServiceContractPostDTO post(String number, double included, Double remaining) {
        ServiceContractPostDTO dto = new ServiceContractPostDTO();
        dto.setName("Maintenance plan");
        dto.setContractNumber(number);
        dto.setIncludedHours(included);
        dto.setRemainingHours(remaining);
        return dto;
    }

    private void stubMapperFromPost(ServiceContractPostDTO dto) {
        ServiceContract mapped = new ServiceContract();
        mapped.setName(dto.getName());
        mapped.setIncludedHours(dto.getIncludedHours());
        when(mapper.fromPostDto(dto)).thenReturn(mapped);
        when(repository.saveAndFlush(any(ServiceContract.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_generatesTheNumberPerCompanyWhenEmpty() {
        ServiceContractPostDTO dto = post("  ", 10, null);
        stubMapperFromPost(dto);
        String prefix = "CT-" + year + "-";
        when(repository.countByCompany_IdAndContractNumberStartingWith(1L, prefix)).thenReturn(0L);
        when(repository.existsByCompany_IdAndContractNumber(1L, prefix + "0001")).thenReturn(false);

        ServiceContract created = service.create(dto, company);

        assertEquals(prefix + "0001", created.getContractNumber());
        assertEquals(company, created.getCompany());
    }

    @Test
    void create_skipsNumbersThatAlreadyExist() {
        ServiceContractPostDTO dto = post(null, 10, null);
        stubMapperFromPost(dto);
        String prefix = "CT-" + year + "-";
        when(repository.countByCompany_IdAndContractNumberStartingWith(1L, prefix)).thenReturn(2L);
        when(repository.existsByCompany_IdAndContractNumber(1L, prefix + "0003")).thenReturn(true);
        when(repository.existsByCompany_IdAndContractNumber(1L, prefix + "0004")).thenReturn(false);

        assertEquals(prefix + "0004", service.create(dto, company).getContractNumber());
    }

    @Test
    void create_remainingHoursDefaultToIncludedHours() {
        ServiceContractPostDTO dto = post("CT-X", 40, null);
        stubMapperFromPost(dto);
        when(repository.existsByCompany_IdAndContractNumber(1L, "CT-X")).thenReturn(false);

        ServiceContract created = service.create(dto, company);

        assertEquals(40, created.getRemainingHours(), DELTA);
        assertEquals(ContractStatus.DRAFT, created.getStatus());
        assertEquals(BillingCycle.MONTHLY, created.getBillingCycle());
    }

    @Test
    void create_keepsTheRemainingHoursWhenIndicated() {
        ServiceContractPostDTO dto = post("CT-X", 40, 12.5);
        stubMapperFromPost(dto);
        when(repository.existsByCompany_IdAndContractNumber(1L, "CT-X")).thenReturn(false);

        assertEquals(12.5, service.create(dto, company).getRemainingHours(), DELTA);
    }

    @Test
    void create_rejectsADuplicatedNumber() {
        ServiceContractPostDTO dto = post("CT-X", 40, null);
        when(mapper.fromPostDto(dto)).thenReturn(new ServiceContract());
        when(repository.existsByCompany_IdAndContractNumber(1L, "CT-X")).thenReturn(true);

        CustomException e = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsAnEndDateBeforeTheStartDate() {
        ServiceContractPostDTO dto = post("CT-X", 40, null);
        dto.setStartDate(new Date(2_000_000L));
        dto.setEndDate(new Date(1_000_000L));

        CustomException e = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.BAD_REQUEST, e.getHttpStatus());
    }

    @Test
    void update_activatingADraftInitialisesTheRemainingHours() {
        ServiceContract saved = new ServiceContract();
        saved.setId(5L);
        saved.setStatus(ContractStatus.DRAFT);
        saved.setIncludedHours(30);
        saved.setRemainingHours(0);
        when(repository.findByIdAndCompany_Id(5L, 1L)).thenReturn(Optional.of(saved));
        when(repository.saveAndFlush(saved)).thenReturn(saved);
        when(mapper.updateServiceContract(eq(saved), any(ServiceContractPatchDTO.class))).thenAnswer(invocation -> {
            saved.setStatus(ContractStatus.ACTIVE);
            return saved;
        });
        ServiceContractPatchDTO dto = new ServiceContractPatchDTO();
        dto.setStatus(ContractStatus.ACTIVE);

        ServiceContract updated = service.update(5L, dto, company);

        assertEquals(30, updated.getRemainingHours(), DELTA);
    }

    @Test
    void update_neverTouchesContractsOfAnotherCompany() {
        when(repository.findByIdAndCompany_Id(5L, 1L)).thenReturn(Optional.empty());

        CustomException e = assertThrows(CustomException.class,
                () -> service.update(5L, new ServiceContractPatchDTO(), company));

        assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_rejectsANumberUsedByAnotherContract() {
        ServiceContract saved = new ServiceContract();
        saved.setId(5L);
        saved.setContractNumber("CT-1");
        when(repository.findByIdAndCompany_Id(5L, 1L)).thenReturn(Optional.of(saved));
        when(repository.existsByCompany_IdAndContractNumber(1L, "CT-2")).thenReturn(true);
        ServiceContractPatchDTO dto = new ServiceContractPatchDTO();
        dto.setContractNumber("CT-2");

        CustomException e = assertThrows(CustomException.class, () -> service.update(5L, dto, company));

        assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
        verify(mapper, never()).updateServiceContract(any(), any());
        verify(repository, never()).existsByCompany_IdAndContractNumber(eq(1L), eq("CT-1"));
    }

    @Test
    void usage_reportsConsumedHoursAndPercent() {
        ServiceContract contract = new ServiceContract();
        contract.setId(5L);
        contract.setIncludedHours(40);
        contract.setRemainingHours(10);
        contract.setRetainerBalance(75);
        when(repository.findById(5L)).thenReturn(Optional.of(contract));

        ServiceContractUsageDTO usage = service.getUsage(5L);

        assertEquals(40, usage.getIncludedHours(), DELTA);
        assertEquals(10, usage.getRemainingHours(), DELTA);
        assertEquals(30, usage.getConsumedHours(), DELTA);
        assertEquals(75, usage.getConsumedPercent(), DELTA);
        assertEquals(75, usage.getRetainerBalance(), DELTA);
    }

    @Test
    void usage_withoutIncludedHoursHasZeroPercent() {
        ServiceContract contract = new ServiceContract();
        contract.setRemainingHours(3);

        ServiceContractUsageDTO usage = service.getUsage(contract);

        assertEquals(0, usage.getConsumedHours(), DELTA);
        assertEquals(0, usage.getConsumedPercent(), DELTA);
    }

    @Test
    void usage_notFoundIs404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        CustomException e = assertThrows(CustomException.class, () -> service.getUsage(9L));

        assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
    }

    @Test
    void create_retriesWithANewNumberWhenAConcurrentRequestTookIt() {
        ServiceContractPostDTO dto = post(null, 10, null);
        ServiceContract mapped = new ServiceContract();
        when(mapper.fromPostDto(dto)).thenReturn(mapped);
        String prefix = "CT-" + year + "-";
        when(repository.countByCompany_IdAndContractNumberStartingWith(1L, prefix)).thenReturn(0L);
        // the first candidate looks free, but another transaction takes it before our insert
        when(repository.existsByCompany_IdAndContractNumber(1L, prefix + "0001")).thenReturn(false, true);
        when(repository.existsByCompany_IdAndContractNumber(1L, prefix + "0002")).thenReturn(false);
        when(repository.saveAndFlush(any(ServiceContract.class)))
                .thenThrow(new DataIntegrityViolationException("uq_service_contract_company_number"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServiceContract created = service.create(dto, company);

        assertEquals(prefix + "0002", created.getContractNumber());
        verify(repository, times(2)).saveAndFlush(any(ServiceContract.class));
    }

    @Test
    void create_givesUpWithConflictAfterBoundedAttempts() {
        ServiceContractPostDTO dto = post(null, 10, null);
        when(mapper.fromPostDto(dto)).thenReturn(new ServiceContract());
        when(repository.countByCompany_IdAndContractNumberStartingWith(any(), any())).thenReturn(0L);
        // generation always believes the candidate is free, the insert always collides
        // (odd calls: number generation, even calls: check after the failed insert)
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(repository.existsByCompany_IdAndContractNumber(any(), any()))
                .thenAnswer(invocation -> calls.incrementAndGet() % 2 == 0);
        when(repository.saveAndFlush(any(ServiceContract.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        CustomException e = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
        verify(repository, times(5)).saveAndFlush(any(ServiceContract.class));
    }

    @Test
    void create_explicitNumberCollidingInTheDatabaseIs409() {
        ServiceContractPostDTO dto = post("CT-X", 40, null);
        when(mapper.fromPostDto(dto)).thenReturn(new ServiceContract());
        when(repository.existsByCompany_IdAndContractNumber(1L, "CT-X")).thenReturn(false, true);
        when(repository.saveAndFlush(any(ServiceContract.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        CustomException e = assertThrows(CustomException.class, () -> service.create(dto, company));

        assertEquals(HttpStatus.CONFLICT, e.getHttpStatus());
        verify(repository, times(1)).saveAndFlush(any(ServiceContract.class));
    }

    @Test
    void create_otherIntegrityErrorsAreNotSwallowed() {
        ServiceContractPostDTO dto = post("CT-X", 40, null);
        when(mapper.fromPostDto(dto)).thenReturn(new ServiceContract());
        when(repository.existsByCompany_IdAndContractNumber(1L, "CT-X")).thenReturn(false);
        when(repository.saveAndFlush(any(ServiceContract.class)))
                .thenThrow(new DataIntegrityViolationException("null value in column"));

        assertThrows(DataIntegrityViolationException.class, () -> service.create(dto, company));
    }
}
