package com.grash.job;

import com.grash.model.Company;
import com.grash.model.ServiceContract;
import com.grash.model.enums.BillingCycle;
import com.grash.model.enums.ContractStatus;
import com.grash.repository.InvoiceContractRepository;
import com.grash.service.CustomerInvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractInvoiceJobTest {

    @Mock
    private InvoiceContractRepository contractRepository;
    @Mock
    private CustomerInvoiceService invoiceService;

    @InjectMocks
    private ContractInvoiceJob job;

    private ServiceContract contract(Long id, boolean alreadyInvoiced) {
        ServiceContract c = new ServiceContract();
        c.setId(id);
        c.setCompany(new Company());
        c.setStatus(ContractStatus.ACTIVE);
        c.setAutoInvoice(true);
        c.setBillingCycle(BillingCycle.MONTHLY);
        if (alreadyInvoiced) c.setLastInvoicedAt(new Date());
        return c;
    }

    @Test
    void run_isolatesFailuresPerContractAndSkipsNotDue() {
        Date now = new Date();
        ServiceContract failing = contract(1L, false);
        ServiceContract ok = contract(2L, false);
        ServiceContract notDue = contract(3L, true);
        when(contractRepository.findByStatusAndAutoInvoiceTrue(ContractStatus.ACTIVE))
                .thenReturn(List.of(failing, ok, notDue));
        doThrow(new RuntimeException("boom")).when(invoiceService).generateScheduledContractInvoice(failing, now);

        job.run(now);

        verify(invoiceService).generateScheduledContractInvoice(failing, now);
        verify(invoiceService).generateScheduledContractInvoice(ok, now);
        verify(invoiceService, never()).generateScheduledContractInvoice(notDue, now);
        verify(invoiceService).markOverdueInvoices(now);
    }

    @Test
    void run_overdueStepFailureDoesNotPropagate() {
        Date now = new Date();
        when(contractRepository.findByStatusAndAutoInvoiceTrue(ContractStatus.ACTIVE)).thenReturn(List.of());
        doThrow(new RuntimeException("db down")).when(invoiceService).markOverdueInvoices(any(Date.class));

        job.run(now);

        verify(invoiceService).markOverdueInvoices(now);
    }
}
