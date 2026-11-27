package com.grash.job;

import com.grash.model.ServiceContract;
import com.grash.model.enums.ContractStatus;
import com.grash.repository.InvoiceContractRepository;
import com.grash.service.CustomerInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Daily billing job: generates DRAFT invoices for auto-invoiced contracts whose period is due and flips ISSUED
 * invoices past their due date to OVERDUE. Runs without a security context; company is taken from each record.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContractInvoiceJob {

    private final InvoiceContractRepository contractRepository;
    private final CustomerInvoiceService invoiceService;

    @Scheduled(cron = "${invoicing.job.cron:0 0 3 * * *}")
    public void runDaily() {
        run(new Date());
    }

    public void run(Date now) {
        int generated = 0;
        int failed = 0;
        List<ServiceContract> candidates = contractRepository.findByStatusAndAutoInvoiceTrue(ContractStatus.ACTIVE);
        for (ServiceContract contract : candidates) {
            if (!CustomerInvoiceService.isDue(contract, now)) continue;
            try {
                invoiceService.generateScheduledContractInvoice(contract, now);
                generated++;
            } catch (Exception e) {
                failed++;
                log.error("Could not auto-invoice contract {}", contract.getId(), e);
            }
        }
        log.info("Contract invoicing: {} generated, {} failed", generated, failed);
        try {
            int overdue = invoiceService.markOverdueInvoices(now);
            log.info("Invoices marked as overdue: {}", overdue);
        } catch (Exception e) {
            log.error("Could not mark overdue invoices", e);
        }
    }
}
