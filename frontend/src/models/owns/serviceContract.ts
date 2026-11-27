import { Audit } from './audit';

export type ServiceContractStatus =
  | 'DRAFT'
  | 'ACTIVE'
  | 'EXPIRED'
  | 'CANCELLED';

export type BillingCycle = 'MONTHLY' | 'QUARTERLY' | 'ANNUAL' | 'ONE_TIME';

export const SERVICE_CONTRACT_STATUSES: ServiceContractStatus[] = [
  'DRAFT',
  'ACTIVE',
  'EXPIRED',
  'CANCELLED'
];

export const BILLING_CYCLES: BillingCycle[] = [
  'MONTHLY',
  'QUARTERLY',
  'ANNUAL',
  'ONE_TIME'
];

export interface ServiceContract extends Audit {
  id: number;
  contractNumber: string;
  name: string;
  customer: { id: number; name: string } | null;
  status: ServiceContractStatus;
  billingCycle: BillingCycle;
  startDate: string;
  endDate: string;
  monthlyFee: number;
  includedHours: number;
  remainingHours: number;
  retainerBalance: number;
  hourlyRateOverride: number | null;
  partsMarkupPercent: number;
  autoInvoice: boolean;
  lastInvoicedAt: string | null;
}

export interface ServiceContractMini {
  id: number;
  name: string;
  contractNumber?: string;
}

export interface ServiceContractUsage {
  includedHours: number;
  remainingHours: number;
  consumedHours: number;
  consumedPercent: number;
  retainerBalance: number;
}
