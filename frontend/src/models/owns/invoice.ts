import { Audit } from './audit';

export type InvoiceStatus =
  | 'DRAFT'
  | 'ISSUED'
  | 'PAID'
  | 'OVERDUE'
  | 'CANCELLED';

export type InvoiceItemType =
  | 'LABOR'
  | 'PART'
  | 'CONTRACT_FEE'
  | 'ADDITIONAL_COST';

export const INVOICE_STATUSES: InvoiceStatus[] = [
  'DRAFT',
  'ISSUED',
  'PAID',
  'OVERDUE',
  'CANCELLED'
];

export interface InvoiceLine {
  id: number;
  description: string;
  itemType: InvoiceItemType;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Invoice extends Audit {
  id: number;
  invoiceNumber: string;
  customer: { id: number; name: string } | null;
  workOrder: { id: number; title: string } | null;
  serviceContract: { id: number; name: string } | null;
  issueDate: string;
  dueDate: string;
  status: InvoiceStatus;
  currency: string;
  subtotal: number;
  taxRatePercent: number;
  taxAmount: number;
  totalAmount: number;
  notes: string | null;
  paidAt: string | null;
  lines: InvoiceLine[];
}

export interface InvoiceSummary {
  totalInvoiced: number;
  pendingCollection: number;
  overdue: number;
  paid: number;
  count: number;
}

export interface InvoicePatch {
  lines?: Omit<InvoiceLine, 'id' | 'lineTotal'>[];
  notes?: string | null;
  dueDate?: string | null;
  taxRatePercent?: number;
  currency?: string;
}

export interface InvoiceSearch {
  status?: InvoiceStatus | null;
  customerId?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  text?: string | null;
  pageNum: number;
  pageSize: number;
  sortField?: string;
  direction?: 'ASC' | 'DESC';
}
