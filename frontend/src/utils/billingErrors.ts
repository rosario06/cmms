import { getErrorMessage } from './api';

export type BillingErrorKind =
  | 'invoiceFromWorkOrder'
  | 'invoiceFromContract'
  | 'contract'
  | 'costCenter'
  | 'invoice_issue'
  | 'invoice_pay'
  | 'invoice_cancel'
  | 'invoice_delete'
  | 'invoice_edit'
  | 'invoice_print';

/** Kinds whose 409 means "duplicate record". */
const CONFLICT_KEYS: Partial<Record<BillingErrorKind, string>> = {
  invoiceFromWorkOrder: 'invoice_already_exists_for_work_order',
  invoiceFromContract: 'invoice_period_already_billed',
  contract: 'contract_number_already_exists',
  costCenter: 'cost_center_code_already_exists'
};

/** 400 (invalid state) per invoice action; the backend only uses 400 for that. */
const INVALID_STATE_KEYS: Partial<Record<BillingErrorKind, string>> = {
  invoice_issue: 'invoice_issue_invalid_state',
  invoice_pay: 'invoice_pay_invalid_state',
  invoice_cancel: 'invoice_cancel_invalid_state',
  invoice_delete: 'invoice_delete_not_draft',
  invoice_edit: 'invoice_edit_not_draft'
};

/** 404: which resource was not found. */
const NOT_FOUND_KEYS: Partial<Record<BillingErrorKind, string>> = {
  invoiceFromContract: 'invoice_contract_not_found',
  invoice_issue: 'invoice_not_found',
  invoice_pay: 'invoice_not_found',
  invoice_cancel: 'invoice_not_found',
  invoice_delete: 'invoice_not_found',
  invoice_edit: 'invoice_not_found',
  invoice_print: 'invoice_not_found'
};

const isInvoiceKind = (kind: BillingErrorKind): boolean =>
  kind.startsWith('invoice_');

const getBackendMessage = (error: any): string => {
  try {
    const parsed = JSON.parse(error?.message);
    return String(parsed?.message ?? '').toLowerCase();
  } catch {
    return String(error?.message ?? '').toLowerCase();
  }
};

/**
 * Whether the error means the invoice state seen by the user is stale (the
 * caller should reload the list/invoice).
 */
export const isInvoiceStateError = (error: any): boolean =>
  error?.status === 400 || error?.status === 404 || error?.status === 409;

/**
 * Translated message for errors of the billing endpoints. Only meaningful for
 * calls made with `silentConflict: true`, so the user sees a single notice.
 *
 * Backend status map: 403 no permission / other company, 404 not found,
 * 400 invalid state (or invalid data), 409 duplicate (create) or concurrent
 * modification (invoice actions).
 */
export const getBillingErrorMessage = (
  error: any,
  kind: BillingErrorKind,
  t: (key: string) => string,
  fallback: string
): string => {
  const status = error?.status;
  if (status === 409) {
    const conflictKey = CONFLICT_KEYS[kind];
    if (conflictKey) return t(conflictKey);
    if (isInvoiceKind(kind)) return t('invoice_concurrent_modification');
  }
  if (status === 403) return t('invoice_no_permission');
  if (status === 404) {
    const key = NOT_FOUND_KEYS[kind];
    if (key) return t(key);
  }
  if (status === 400) {
    const message = getBackendMessage(error);
    if (kind === 'invoiceFromContract' && message.includes('active')) {
      return t('invoice_contract_not_active');
    }
    if (kind === 'invoice_issue' && message.includes('without lines')) {
      return t('invoice_issue_no_lines');
    }
    if (kind === 'invoice_edit' && !message.includes('draft')) {
      return t('invoice_edit_invalid_data');
    }
    const key = INVALID_STATE_KEYS[kind];
    if (key) return t(key);
  }
  return getErrorMessage(error, fallback);
};
