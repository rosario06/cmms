import { Audit } from './audit';

export interface CostCenter extends Audit {
  id: number;
  code: string;
  name: string;
  customer: { id: number; name: string } | null;
  location: { id: number; name: string } | null;
  fiscalPeriod: string;
  allocatedBudget: number;
  spentAmount: number;
  alertThresholdPercent: number;
  active: boolean;
}

export interface CostCenterMini {
  id: number;
  name: string;
  code?: string;
}

export interface CostCenterSummary {
  allocatedBudget: number;
  spentAmount: number;
  executionPercent: number;
  remaining: number;
  alertThresholdPercent: number;
  overThreshold: boolean;
  workOrderCount: number;
}
