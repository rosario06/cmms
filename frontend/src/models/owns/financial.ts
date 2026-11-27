export interface ProfitabilityByCustomer {
  customerId: number | null;
  customerName: string | null;
  workOrderCount: number;
  internalCost: number;
  billable: number;
  netMargin: number;
  marginPercent: number;
}

export interface ProfitabilityByWorkOrder {
  workOrderId: number;
  title: string;
  customerName: string | null;
  internalCost: number;
  billable: number;
  netMargin: number;
  marginPercent: number;
}

export interface ProfitabilityReport {
  byCustomer: ProfitabilityByCustomer[];
  byWorkOrder: ProfitabilityByWorkOrder[];
}

export interface WorkOrderFinancials {
  laborInternal: number;
  laborBillable: number;
  partsInternal: number;
  partsBillable: number;
  additionalInternal: number;
  additionalBillable: number;
  totalInternalCost: number;
  totalBillableAmount: number;
  netMarginAmount: number;
  marginPercentage: number;
  contractCoveredHours: number;
}
