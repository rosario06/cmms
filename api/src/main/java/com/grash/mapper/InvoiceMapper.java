package com.grash.mapper;

import com.grash.dto.InvoiceLineShowDTO;
import com.grash.dto.InvoiceShowDTO;
import com.grash.model.CustomerInvoice;
import com.grash.model.CustomerInvoiceLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {CustomerMapper.class})
public interface InvoiceMapper {
    @Mapping(target = "customerBillingName", source = "customer.billingName")
    @Mapping(target = "workOrderId", source = "workOrder.id")
    @Mapping(target = "workOrderTitle", source = "workOrder.title")
    @Mapping(target = "serviceContractId", source = "serviceContract.id")
    @Mapping(target = "contractNumber", source = "serviceContract.contractNumber")
    InvoiceShowDTO toShowDto(CustomerInvoice model);

    InvoiceLineShowDTO toLineShowDto(CustomerInvoiceLine model);
}
