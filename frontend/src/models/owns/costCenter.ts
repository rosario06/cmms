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
  alertSent?: boolean;
}

/** Payload accepted by POST / PATCH (relations are sent by id only). */
export type CostCenterInput = Partial<
  Omit<CostCenter, keyof Audit | 'customer' | 'location'>
> & {
  // an object without id clears the relation when editing
  customer?: { id?: number; name?: string } | null;
  location?: { id?: number; name?: string } | null;
};

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
