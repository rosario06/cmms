package com.grash.event;

import lombok.Getter;

/**
 * A cost center reached its alert threshold (percentage of the allocated budget). Fired once per crossing.
 * Carries only plain values so it is safe to handle after the transaction ended or in another thread.
 */
@Getter
public class CostCenterBudgetAlertEvent {
    private final Long companyId;
    private final Long costCenterId;
    private final String code;
    private final String name;
    private final double allocatedBudget;
    private final double spentAmount;
    private final double executionPercent;
    private final double alertThresholdPercent;

    public CostCenterBudgetAlertEvent(Long companyId, Long costCenterId, String code, String name,
                                      double allocatedBudget, double spentAmount, double executionPercent,
                                      double alertThresholdPercent) {
        this.companyId = companyId;
        this.costCenterId = costCenterId;
        this.code = code;
        this.name = name;
        this.allocatedBudget = allocatedBudget;
        this.spentAmount = spentAmount;
        this.executionPercent = executionPercent;
        this.alertThresholdPercent = alertThresholdPercent;
    }
}
