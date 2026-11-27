package com.grash.service;

import com.grash.model.Company;
import com.grash.model.Customer;
import com.grash.model.CustomerInvoice;
import com.grash.model.CustomerInvoiceLine;
import com.grash.model.enums.InvoiceItemType;
import com.grash.model.enums.InvoiceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceDocumentServiceTest {

    private final InvoiceDocumentService service = new InvoiceDocumentService();

    @Test
    void renderHtml_escapesEveryUserProvidedValue() {
        Company company = new Company();
        company.setName("<script>alert('co')</script>");
        company.setAddress("Street <b>1</b>");
        Customer customer = new Customer();
        customer.setName("plain");
        customer.setBillingName("\"><img src=x onerror=alert(1)>");
        customer.setBillingAddress("A & B <i>");

        CustomerInvoice invoice = new CustomerInvoice();
        invoice.setCompany(company);
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber("INV-2026-0001<script>");
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setCurrency("USD");
        invoice.setNotes("line1\n<script>alert(2)</script>");
        CustomerInvoiceLine line = new CustomerInvoiceLine();
        line.setItemType(InvoiceItemType.PART);
        line.setDescription("<b>bold</b> & 'quote'");
        line.setQuantity(2);
        line.setUnitPrice(1234.5);
        line.setLineTotal(2469);
        invoice.getLines().add(line);

        String html = service.renderHtml(invoice);

        assertFalse(html.contains("<script"), "raw script tag leaked");
        assertFalse(html.contains("<img"), "raw img tag leaked");
        assertFalse(html.contains("<b>"), "raw b tag leaked");
        assertFalse(html.contains("<i>"), "raw i tag leaked");
        assertTrue(html.contains("&lt;script&gt;alert(&#39;co&#39;)&lt;/script&gt;"));
        assertTrue(html.contains("&quot;&gt;&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(html.contains("&lt;b&gt;bold&lt;/b&gt; &amp; &#39;quote&#39;"));
        assertTrue(html.contains("line1<br>&lt;script&gt;"));
        assertTrue(html.contains("2,469.00"));
        assertTrue(html.contains("1,234.50"));
    }

    @Test
    void renderHtml_isSelfContainedAndHandlesMissingData() {
        CustomerInvoice invoice = new CustomerInvoice();
        invoice.setInvoiceNumber("INV-2026-0002");

        String html = service.renderHtml(invoice);

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<style>"));
        assertFalse(html.contains("<link"));
        assertFalse(html.contains("src="));
        assertTrue(html.contains("Sin cliente"));
    }

    @Test
    void esc_nullIsEmpty() {
        assertEquals("", InvoiceDocumentService.esc(null));
        assertEquals("&lt;&amp;&gt;", InvoiceDocumentService.esc("<&>"));
    }
}
