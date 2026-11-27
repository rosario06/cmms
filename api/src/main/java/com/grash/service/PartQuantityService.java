package com.grash.service;

import com.grash.dto.PartQuantityPatchDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.PartQuantityMapper;
import com.grash.model.PartQuantity;
import com.grash.repository.PartQuantityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PartQuantityService {
    private final PartQuantityRepository partQuantityRepository;
    private final CompanyService companyService;
    private final PartService partService;
    private final PurchaseOrderService purchaseOrderService;
    private final WorkOrderService workOrderService;
    private final PartQuantityMapper partQuantityMapper;
    private final WorkOrderFinancialService workOrderFinancialService;

    @Transactional
    public PartQuantity create(PartQuantity PartQuantity) {
        PartQuantity saved = partQuantityRepository.save(PartQuantity);
        recalculateFinancials(saved);
        return saved;
    }

    private void recalculateFinancials(PartQuantity partQuantity) {
        if (partQuantity != null && partQuantity.getWorkOrder() != null)
            workOrderFinancialService.recalculateQuietly(partQuantity.getWorkOrder().getId());
    }

    @Transactional
    public PartQuantity update(Long id, PartQuantityPatchDTO partQuantity) {
        if (partQuantityRepository.existsById(id)) {
            PartQuantity savedPartQuantity = partQuantityRepository.findById(id).get();
            PartQuantity updated =
                    partQuantityRepository.save(partQuantityMapper.updatePartQuantity(savedPartQuantity, partQuantity));
            recalculateFinancials(updated);
            return updated;
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    public Collection<PartQuantity> getAll() {
        return partQuantityRepository.findAll();
    }

    @Transactional
    public void delete(Long id) {
        Optional<PartQuantity> optionalPartQuantity = partQuantityRepository.findById(id);
        partQuantityRepository.deleteById(id);
        optionalPartQuantity.ifPresent(partQuantity -> {
            partQuantityRepository.flush();
            recalculateFinancials(partQuantity);
        });
    }

    public Optional<PartQuantity> findById(Long id) {
        return partQuantityRepository.findById(id);
    }

    public Collection<PartQuantity> findByCompany(Long id) {
        return partQuantityRepository.findByCompany_Id(id);
    }

    public Collection<PartQuantity> findByWorkOrder(Long id) {
        return partQuantityRepository.findByWorkOrder_Id(id);
    }

    public Collection<PartQuantity> findByPart(Long id) {
        return partQuantityRepository.findByPart_Id(id);
    }

    public Collection<PartQuantity> findByPurchaseOrder(Long id) {
        return partQuantityRepository.findByPurchaseOrder_Id(id);
    }


    @Transactional
    public void save(PartQuantity partQuantity) {
        recalculateFinancials(partQuantityRepository.save(partQuantity));
    }
}
