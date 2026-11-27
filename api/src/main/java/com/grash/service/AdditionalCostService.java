package com.grash.service;

import com.grash.dto.AdditionalCostPatchDTO;
import com.grash.dto.license.LicenseEntitlement;
import com.grash.exception.CustomException;
import com.grash.mapper.AdditionalCostMapper;
import com.grash.model.AdditionalCost;
import com.grash.repository.AdditionalCostRepository;
import com.grash.utils.Sanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdditionalCostService {

    private final AdditionalCostRepository additionalCostRepository;
    private final EntityManager em;


    private final AdditionalCostMapper additionalCostMapper;
    private final LicenseService licenseService;
    private final WorkOrderFinancialService workOrderFinancialService;

    @Transactional
    public AdditionalCost create(AdditionalCost additionalCost) {
        if (!licenseService.hasEntitlement(LicenseEntitlement.COST_TRACKING))
            throw new CustomException("You need a license to create a additional cost", HttpStatus.FORBIDDEN);
        Sanitizer.sanitizeAdditionalCost(additionalCost);
        AdditionalCost savedAdditionalCost = additionalCostRepository.saveAndFlush(additionalCost);
        em.refresh(savedAdditionalCost);
        recalculateFinancials(savedAdditionalCost);
        return savedAdditionalCost;
    }

    @Transactional
    public AdditionalCost update(Long id, AdditionalCostPatchDTO additionalCost) {
        if (additionalCostRepository.existsById(id)) {
            AdditionalCost savedAdditionalCost = additionalCostRepository.findById(id).get();
            AdditionalCost updatedAdditionalCost = additionalCostMapper.updateAdditionalCost(savedAdditionalCost,
                    additionalCost);
            Sanitizer.sanitizeAdditionalCost(updatedAdditionalCost);
            updatedAdditionalCost = additionalCostRepository.saveAndFlush(updatedAdditionalCost);
            em.refresh(updatedAdditionalCost);
            recalculateFinancials(updatedAdditionalCost);
            return updatedAdditionalCost;
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    public Collection<AdditionalCost> getAll() {
        return additionalCostRepository.findAll();
    }

    private void recalculateFinancials(AdditionalCost additionalCost) {
        if (additionalCost != null && additionalCost.getWorkOrder() != null)
            workOrderFinancialService.recalculateQuietly(additionalCost.getWorkOrder().getId());
    }

    @Transactional
    public void delete(Long id) {
        Optional<AdditionalCost> optionalAdditionalCost = additionalCostRepository.findById(id);
        additionalCostRepository.deleteById(id);
        optionalAdditionalCost.ifPresent(additionalCost -> {
            additionalCostRepository.flush();
            recalculateFinancials(additionalCost);
        });
    }

    public Optional<AdditionalCost> findById(Long id) {
        return additionalCostRepository.findById(id);
    }

    public Collection<AdditionalCost> findByWorkOrder(Long id) {
        return additionalCostRepository.findByWorkOrder_Id(id);
    }
}

