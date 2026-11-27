package com.grash.service;

import com.grash.model.Customer;
import com.grash.model.CustomerInvoice;
import com.grash.model.CustomerInvoiceLine;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Renders an invoice as a self-contained HTML page (inline CSS) that can be printed or saved as PDF from the
 * browser. Every user-provided value goes through {@link #esc(String)}.
 */
@Service
public class InvoiceDocumentService {

    public String renderHtml(CustomerInvoice invoice) {
        String currency = invoice.getCurrency() == null ? "" : invoice.getCurrency();
        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">")
                .append("<title>").append(esc(invoice.getInvoiceNumber())).append("</title>")
                .append("<style>")
                .append("body{font-family:Arial,Helvetica,sans-serif;color:#222;margin:32px;font-size:14px}")
                .append("h1{margin:0;font-size:26px}.muted{color:#666}.head{display:flex;justify-content:space-between;")
                .append("margin-bottom:24px}.box{margin-bottom:20px}")
                .append("table{width:100%;border-collapse:collapse;margin-top:12px}")
                .append("th,td{border-bottom:1px solid #ddd;padding:8px;text-align:left}")
                .append("th{background:#f4f4f4}.num{text-align:right}")
                .append(".totals{margin-top:16px;margin-left:auto;width:300px}")
                .append(".totals td{border:none;padding:4px 8px}.grand td{font-weight:bold;font-size:16px;")
                .append("border-top:2px solid #222}")
                .append("@media print{body{margin:12mm}}")
                .append("</style></head><body>");

        html.append("<div class=\"head\"><div><h1>")
                .append(esc(invoice.getCompany() == null ? "" : invoice.getCompany().getName()))
                .append("</h1>");
        if (invoice.getCompany() != null) {
            appendLine(html, invoice.getCompany().getAddress());
            appendLine(html, invoice.getCompany().getPhone());
            appendLine(html, invoice.getCompany().getEmail());
        }
        html.append("</div><div style=\"text-align:right\"><h1>Factura</h1><div><strong>")
                .append(esc(invoice.getInvoiceNumber())).append("</strong></div>")
                .append("<div>Estado: ").append(esc(invoice.getStatus() == null ? "" : invoice.getStatus().name()))
                .append("</div>")
                .append("<div>Fecha de emisi&oacute;n: ").append(esc(formatDate(invoice.getIssueDate())))
                .append("</div>")
                .append("<div>Vencimiento: ").append(esc(formatDate(invoice.getDueDate()))).append("</div>")
                .append("</div></div>");

        Customer customer = invoice.getCustomer();
        html.append("<div class=\"box\"><strong>Facturar a</strong><br>");
        if (customer == null) {
            html.append("<span class=\"muted\">Sin cliente</span>");
        } else {
            String billingName = customer.getBillingName() == null || customer.getBillingName().isBlank()
                    ? customer.getName() : customer.getBillingName();
            html.append(esc(billingName));
            appendLine(html, customer.getBillingAddress());
            appendLine(html, customer.getBillingAddress2());
        }
        html.append("</div>");

        html.append("<table><thead><tr><th>Descripci&oacute;n</th><th>Tipo</th><th class=\"num\">Cantidad</th>")
                .append("<th class=\"num\">Precio unitario</th><th class=\"num\">Total</th></tr></thead><tbody>");
        if (invoice.getLines() != null) {
            for (CustomerInvoiceLine line : invoice.getLines()) {
                html.append("<tr><td>").append(esc(line.getDescription())).append("</td><td>")
                        .append(esc(line.getItemType() == null ? "" : line.getItemType().name()))
                        .append("</td><td class=\"num\">").append(esc(money(line.getQuantity())))
                        .append("</td><td class=\"num\">").append(esc(money(line.getUnitPrice())))
                        .append("</td><td class=\"num\">").append(esc(money(line.getLineTotal())))
                        .append("</td></tr>");
            }
        }
        html.append("</tbody></table>");

        html.append("<table class=\"totals\"><tr><td>Subtotal</td><td class=\"num\">")
                .append(esc(money(invoice.getSubtotal()) + " " + currency)).append("</td></tr>")
                .append("<tr><td>Impuesto (").append(esc(money(invoice.getTaxRatePercent())))
                .append("%)</td><td class=\"num\">").append(esc(money(invoice.getTaxAmount()) + " " + currency))
                .append("</td></tr><tr class=\"grand\"><td>Total</td><td class=\"num\">")
                .append(esc(money(invoice.getTotalAmount()) + " " + currency)).append("</td></tr></table>");

        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            html.append("<div class=\"box\" style=\"margin-top:24px\"><strong>Notas</strong><br>")
                    .append(esc(invoice.getNotes()).replace("\n", "<br>")).append("</div>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private void appendLine(StringBuilder html, String value) {
        if (value != null && !value.isBlank()) html.append("<br>").append(esc(value));
    }

    /** HTML-escapes user text (&amp; &lt; &gt; quotes). Null becomes an empty string. */
    static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    private static String formatDate(Date date) {
        return date == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(date);
    }
}
