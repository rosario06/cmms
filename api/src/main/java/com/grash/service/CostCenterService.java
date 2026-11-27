package com.grash.service;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.advancedsearch.SpecificationBuilder;
import com.grash.dto.CostCenterPatchDTO;
import com.grash.dto.CostCenterPostDTO;
import com.grash.dto.CostCenterSummaryDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.CostCenterMapper;
import com.grash.model.CostCenter;
import com.grash.model.User;
import com.grash.repository.CostCenterRepository;
import jakarta.persistence.EntityManager;
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
public class CostCenterService {
    private final CostCenterRepository costCenterRepository;
    private final CostCenterMapper costCenterMapper;
    private final WorkOrderFinancialService workOrderFinancialService;
    private final EntityManager em;

    @Transactional
    public CostCenter create(CostCenterPostDTO dto, User user) {
        CostCenter costCenter = costCenterMapper.fromPostDto(dto);
        costCenter.setCode(costCenter.getCode().trim());
        costCenter.setName(costCenter.getName().trim());
        costCenter.setCompany(user.getCompany());
        checkCodeIsFree(user.getCompany().getId(), costCenter.getCode(), null);
        CostCenter saved = costCenterRepository.saveAndFlush(costCenter);
        em.refresh(saved);
        return saved;
    }

    @Transactional
    public CostCenter update(Long id, CostCenterPatchDTO dto, User user) {
        CostCenter saved = costCenterRepository.findById(id)
                .orElseThrow(() -> new CustomException("Not found", HttpStatus.NOT_FOUND));
        CostCenter updated = costCenterMapper.updateCostCenter(saved, dto);
        if (updated.getCode() != null) updated.setCode(updated.getCode().trim());
        if (updated.getName() != null) updated.setName(updated.getName().trim());
        checkCodeIsFree(user.getCompany().getId(), updated.getCode(), id);
        updated = costCenterRepository.saveAndFlush(updated);
        // budget / threshold may have changed: re-evaluate the alert
        workOrderFinancialService.recalculateForCostCenter(id);
        em.refresh(updated);
        return updated;
    }

    private void checkCodeIsFree(Long companyId, String code, Long currentId) {
        boolean exists = costCenterRepository.findByCompany_IdAndCodeIgnoreCase(companyId, code).stream()
                .anyMatch(other -> !other.getId().equals(currentId));
        if (exists)
            throw new CustomException("Cost center with same code already exists", HttpStatus.NOT_ACCEPTABLE);
    }

    public void delete(Long id) {
        costCenterRepository.deleteById(id);
    }

    public Optional<CostCenter> findById(Long id) {
        return costCenterRepository.findById(id);
    }

    public Page<CostCenter> findBySearchCriteria(SearchCriteria searchCriteria) {
        SpecificationBuilder<CostCenter> builder = new SpecificationBuilder<>();
        searchCriteria.getFilterFields().forEach(builder::with);
        Pageable page = PageRequest.of(searchCriteria.getPageNum(), searchCriteria.getPageSize(),
                searchCriteria.getDirection(), searchCriteria.getSortField());
        return costCenterRepository.findAll(builder.build(), page);
    }

    @Transactional(readOnly = true)
    public CostCenterSummaryDTO getSummary(CostCenter costCenter) {
        double budget = costCenter.getAllocatedBudget();
        double spent = costCenter.getSpentAmount();
        double executionPercent = WorkOrderFinancialService.round2(budget > 0 ? spent / budget * 100 : 0);
        boolean over = budget > 0 && spent / budget * 100 >= costCenter.getAlertThresholdPercent();
        return new CostCenterSummaryDTO(budget, spent, executionPercent, WorkOrderFinancialService.round2(budget - spent),
                costCenter.getAlertThresholdPercent(), over, costCenterRepository.countWorkOrders(costCenter.getId()));
    }
}
