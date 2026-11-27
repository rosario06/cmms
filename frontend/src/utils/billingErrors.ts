import { getErrorMessage } from './api';

export type BillingErrorKind =
  | 'invoiceFromWorkOrder'
  | 'invoiceFromContract'
  | 'contract'
  | 'costCenter';

const CONFLICT_KEYS: Record<BillingErrorKind, string> = {
  invoiceFromWorkOrder: 'invoice_already_exists_for_work_order',
  invoiceFromContract: 'invoice_period_already_billed',
  contract: 'contract_number_already_exists',
  costCenter: 'cost_center_code_already_exists'
};

/**
 * Translated message for errors of the billing endpoints. Only meaningful for
 * calls made with `silentConflict: true`, so the user sees a single notice.
 */
export const getBillingErrorMessage = (
  error: any,
  kind: BillingErrorKind,
  t: (key: string) => string,
  fallback: string
): string => {
  if (error?.status === 409) return t(CONFLICT_KEYS[kind]);
  if (error?.status === 403) return t('invoice_no_permission');
  return getErrorMessage(error, fallback);
};
