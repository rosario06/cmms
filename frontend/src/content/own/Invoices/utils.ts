import { InvoiceStatus } from '../../../models/owns/invoice';
import { ChipColor } from '../Contracts/utils';

export const getInvoiceStatusColor = (status: InvoiceStatus): ChipColor => {
  switch (status) {
    case 'ISSUED':
      return 'info';
    case 'PAID':
      return 'success';
    case 'OVERDUE':
      return 'error';
    case 'CANCELLED':
      return 'warning';
    default:
      return 'default';
  }
};

/** Formats an amount using the invoice currency; falls back to the app format. */
export const formatInvoiceAmount = (
  amount: number | null | undefined,
  currency: string | null | undefined,
  fallback: (value: number) => string
): string => {
  const value = amount ?? 0;
  if (!currency) return fallback(value);
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency
    }).format(value);
  } catch {
    return `${currency} ${value.toFixed(2)}`;
  }
};

/** Converts a yyyy-MM-dd input value into an ISO date at local noon. */
export const inputDateToIso = (value: string): string | null => {
  if (!value) return null;
  const date = new Date(`${value}T12:00:00`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
};

/** Converts an ISO date into a yyyy-MM-dd input value. */
export const isoToInputDate = (value: string | null | undefined): string => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
};
