import type { AppThunk } from 'src/store';
import {
  ProfitabilityReport,
  WorkOrderFinancials
} from '../models/owns/financial';
import api from '../utils/api';

// Read-only thunks: results are returned to the caller and not stored in redux.

export const getProfitabilityReport =
  (start: Date, end: Date, customerId?: number | null): AppThunk =>
  async () => {
    const params = new URLSearchParams();
    params.append('start', start.toISOString());
    params.append('end', end.toISOString());
    if (customerId) params.append('customerId', String(customerId));
    return api.get<ProfitabilityReport>(
      `financial-reports/profitability?${params.toString()}`
    );
  };

export const getWorkOrderFinancials =
  (workOrderId: number): AppThunk =>
  async () =>
    api.get<WorkOrderFinancials>(`work-orders/${workOrderId}/financials`);
