package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Profitability of completed work orders, by customer and by work order")
public class ProfitabilityReportDTO {
    private List<CustomerRow> byCustomer = new ArrayList<>();
    private List<WorkOrderRow> byWorkOrder = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerRow {
        private Long customerId;
        private String customerName;
        private long workOrderCount;
        private double internalCost;
        private double billable;
        private double netMargin;
        private double marginPercent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkOrderRow {
        private Long workOrderId;
        private String title;
        private String customerName;
        private double internalCost;
        private double billable;
        private double netMargin;
        private double marginPercent;
    }
}
