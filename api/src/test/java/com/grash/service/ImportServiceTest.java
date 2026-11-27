package com.grash.service;

import com.grash.dto.imports.ImportResponse;
import com.grash.dto.imports.WorkOrderImportDTO;
import com.grash.model.Company;
import com.grash.model.WorkOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private AssetService assetService;
    @Mock
    private LocationService locationService;
    @Mock
    private PartService partService;
    @Mock
    private MeterService meterService;
    @Mock
    private WorkOrderService workOrderService;
    @Mock
    private PreventiveMaintenanceService preventiveMaintenanceService;
    @InjectMocks
    private ImportService importService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
    }

    private static WorkOrderImportDTO dto(Long id) {
        WorkOrderImportDTO dto = new WorkOrderImportDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    @SuppressWarnings("unchecked")
    void importWorkOrders_appliesTheSlaOnlyToTheCreatedOnes() {
        WorkOrder existing = new WorkOrder();
        existing.setId(5L);
        when(workOrderService.findByIdsAndCompany(List.of(5L, 99L), 1L)).thenReturn(List.of(existing));

        ImportResponse response = importService.importWorkOrders(List.of(dto(5L), dto(null), dto(99L)), company);

        assertEquals(2, response.getCreated());
        assertEquals(1, response.getUpdated());
        ArgumentCaptor<Collection<WorkOrder>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(workOrderService).applySlaToImported(captor.capture());
        assertEquals(2, captor.getValue().size());
        captor.getValue().forEach(workOrder -> assertNull(workOrder.getId()));
        verify(workOrderService).saveAll(any());
    }

    @Test
    void importWorkOrders_emptyListDoesNotApplyAnything() {
        importService.importWorkOrders(List.of(), company);

        verify(workOrderService, never()).applySlaToImported(anyCollection());
        verify(workOrderService, never()).saveAll(any());
    }
}
