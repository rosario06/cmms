package com.grash.utils;

import com.grash.dto.WorkOrderBaseMiniDTO;
import com.grash.dto.WorkOrderBaseShowDTO;
import com.grash.dto.workOrder.WorkOrderMiniDTO;
import com.grash.dto.workOrder.WorkOrderShowDTO;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinancialVisibilityTest {

    private static User userWith(PermissionEntity... viewPermissions) {
        Role role = new Role();
        role.setViewPermissions(new HashSet<>(Arrays.asList(viewPermissions)));
        User user = new User();
        user.setRole(role);
        return user;
    }

    private static WorkOrderShowDTO dto() {
        WorkOrderShowDTO dto = new WorkOrderShowDTO();
        dto.setContractCoveredHours(3.5);
        return dto;
    }

    @Test
    void masksFinancialsWithoutAnalytics() {
        WorkOrderShowDTO result = FinancialVisibility.apply(dto(), userWith(PermissionEntity.WORK_ORDERS));
        assertEquals(0, result.getContractCoveredHours());
    }

    @Test
    void keepsFinancialsWithAnalytics() {
        WorkOrderShowDTO result = FinancialVisibility.apply(dto(), userWith(PermissionEntity.ANALYTICS));
        assertEquals(3.5, result.getContractCoveredHours());
    }

    @Test
    void nullUserRoleOrDtoAreHandled() {
        assertEquals(0, FinancialVisibility.apply(dto(), null).getContractCoveredHours());
        assertEquals(0, FinancialVisibility.apply(dto(), new User()).getContractCoveredHours());
        assertNull(FinancialVisibility.apply(null, userWith()));
        assertFalse(FinancialVisibility.canViewFinancials(null));
    }

    /**
     * Regression guard: the persisted money fields of WorkOrder must never be copied into the DTOs returned by the
     * work order endpoints (they are only exposed by the ANALYTICS-protected financial endpoints).
     */
    @Test
    void workOrderDtosDoNotExposeMoneyFields() {
        List<String> forbidden = List.of("totalInternalCost", "totalBillableAmount", "netMarginAmount",
                "marginPercentage");
        for (Class<?> type : List.of(WorkOrderShowDTO.class, WorkOrderMiniDTO.class, WorkOrderBaseShowDTO.class,
                WorkOrderBaseMiniDTO.class)) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    assertFalse(forbidden.contains(field.getName()), type.getSimpleName() + " exposes " + field.getName());
                }
            }
        }
    }
}
