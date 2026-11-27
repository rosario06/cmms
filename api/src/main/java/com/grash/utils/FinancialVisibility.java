package com.grash.utils;

import com.grash.dto.workOrder.WorkOrderShowDTO;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;

/**
 * Single point deciding whether a user may see the financial data of work orders (same rule as
 * WorkOrderFinancialController: ANALYTICS in the role's viewPermissions). The masking only touches the response DTO,
 * never the persisted entity.
 */
public final class FinancialVisibility {

    private FinancialVisibility() {
    }

    public static boolean canViewFinancials(User user) {
        return user != null && user.getRole() != null && user.getRole().getViewPermissions() != null
                && user.getRole().getViewPermissions().contains(PermissionEntity.ANALYTICS);
    }

    /**
     * Zeroes the financial fields of the DTO when the user cannot see them. Returns the same instance (null safe).
     */
    public static WorkOrderShowDTO apply(WorkOrderShowDTO dto, User user) {
        if (dto != null && !canViewFinancials(user)) {
            dto.setContractCoveredHours(0);
        }
        return dto;
    }
}
