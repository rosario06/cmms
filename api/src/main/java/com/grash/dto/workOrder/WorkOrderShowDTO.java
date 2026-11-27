package com.grash.dto.workOrder;

import com.grash.dto.*;
import com.grash.dto.cutomField.CustomFieldValueShowDTO;
import com.grash.model.enums.SlaStatus;
import com.grash.model.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "Work order DTO for displaying work order details in API responses")
public class WorkOrderShowDTO extends WorkOrderBaseShowDTO {

    @Schema(description = "User who completed the work order")
    private UserMiniDTO completedBy;

    @Schema(description = "Date and time when the work order was completed")
    private Date completedOn;

    @Schema(description = "Whether the work order is archived")
    private boolean archived;

    @Schema(description = "Parent maintenance request that triggered this work order")
    private RequestMiniDTO parentRequest;

    @Schema(description = "Parent preventive maintenance that generated this work order")
    private PreventiveMaintenanceMiniDTO parentPreventiveMaintenance;

    @Schema(description = "Base64 encoded signature for the completed work order")
    private String signature;

    @Schema(description = "Current status of the work order")
    private Status status;

    @Schema(description = "Feedback or notes provided upon work order completion")
    private String feedback;

    @Schema(description = "Audio description file attached to the work order")
    private FileShowDTO audioDescription;

    @Schema(description = "Custom identifier for the work order")
    private String customId;

    @Schema(description = "Service contract this work order is billed under")
    private ServiceContractMiniDTO serviceContract;

    @Schema(description = "SLA policy applied to this work order")
    private SlaPolicyMiniDTO slaPolicy;

    @Schema(description = "SLA deadline for the first response")
    private Date slaFirstResponseDueAt;

    @Schema(description = "SLA deadline for the resolution")
    private Date slaResolutionDueAt;

    @Schema(description = "When the work order was first responded to (check-in or IN_PROGRESS)")
    private Date firstRespondedAt;

    @Schema(description = "SLA status: ON_TRACK, AT_RISK, BREACHED or MET. Null when no SLA applies")
    private SlaStatus slaStatus;

    @Schema(description = "Cost center charged for this work order")
    private WorkOrderCostCenterDTO costCenter;

    @Schema(description = "Labor hours covered by the service contract")
    private double contractCoveredHours;

}
