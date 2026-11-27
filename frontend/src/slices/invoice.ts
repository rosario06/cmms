import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import {
  Invoice,
  InvoicePatch,
  InvoiceSearch,
  InvoiceSummary
} from '../models/owns/invoice';
import api from '../utils/api';
import { getInitialPage, Page } from '../models/owns/page';
import { revertAll } from 'src/utils/redux';

const basePath = 'invoices';

interface InvoiceState {
  invoices: Page<Invoice>;
  summary: InvoiceSummary | null;
  loadingGet: boolean;
}

const initialState: InvoiceState = {
  invoices: getInitialPage<Invoice>(),
  summary: null,
  loadingGet: false
};

const slice = createSlice({
  name: 'invoices',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getInvoices(
      state: InvoiceState,
      action: PayloadAction<{ invoices: Page<Invoice> }>
    ) {
      state.invoices = action.payload.invoices;
    },
    getSummary(
      state: InvoiceState,
      action: PayloadAction<{ summary: InvoiceSummary }>
    ) {
      state.summary = action.payload.summary;
    },
    editInvoice(
      state: InvoiceState,
      action: PayloadAction<{ invoice: Invoice }>
    ) {
      const { invoice } = action.payload;
      state.invoices.content = state.invoices.content.map((item) =>
        item.id === invoice.id ? invoice : item
      );
    },
    deleteInvoice(state: InvoiceState, action: PayloadAction<{ id: number }>) {
      const { id } = action.payload;
      state.invoices.content = state.invoices.content.filter(
        (item) => item.id !== id
      );
    },
    setLoadingGet(
      state: InvoiceState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getInvoices =
  (criteria: InvoiceSearch): AppThunk =>
  async (dispatch) => {
    try {
      dispatch(slice.actions.setLoadingGet({ loading: true }));
      const invoices = await api.post<Page<Invoice>>(
        `${basePath}/search`,
        criteria
      );
      dispatch(slice.actions.getInvoices({ invoices }));
    } finally {
      dispatch(slice.actions.setLoadingGet({ loading: false }));
    }
  };

export const getInvoiceSummary =
  (customerId?: number | null, start?: string, end?: string): AppThunk =>
  async (dispatch) => {
    const params = new URLSearchParams();
    if (customerId) params.append('customerId', String(customerId));
    if (start) params.append('start', start);
    if (end) params.append('end', end);
    const query = params.toString();
    const summary = await api.get<InvoiceSummary>(
      `${basePath}/summary${query ? `?${query}` : ''}`
    );
    dispatch(slice.actions.getSummary({ summary }));
  };

export const editInvoice =
  (id: number, invoice: InvoicePatch): AppThunk =>
  async (dispatch) => {
    const response = await api.patch<Invoice>(`${basePath}/${id}`, invoice);
    dispatch(slice.actions.editInvoice({ invoice: response }));
  };

const invoiceAction =
  (id: number, action: 'issue' | 'mark-paid' | 'cancel'): AppThunk =>
  async (dispatch) => {
    const response = await api.patch<Invoice>(
      `${basePath}/${id}/${action}`,
      {}
    );
    dispatch(slice.actions.editInvoice({ invoice: response }));
  };

export const issueInvoice = (id: number): AppThunk => invoiceAction(id, 'issue');

export const markInvoicePaid = (id: number): AppThunk =>
  invoiceAction(id, 'mark-paid');

export const cancelInvoice = (id: number): AppThunk =>
  invoiceAction(id, 'cancel');

export const deleteInvoice =
  (id: number): AppThunk =>
  async (dispatch) => {
    const response = await api.deletes<{ success: boolean }>(
      `${basePath}/${id}`
    );
    if (response.success) {
      dispatch(slice.actions.deleteInvoice({ id }));
    }
  };

export const createInvoiceFromWorkOrder =
  (workOrderId: number): AppThunk =>
  async () =>
    api.post<Invoice>(`${basePath}/from-work-order/${workOrderId}`, {});

export const createInvoiceFromContract =
  (contractId: number): AppThunk =>
  async () =>
    api.post<Invoice>(`${basePath}/from-contract/${contractId}`, {});

/** Downloads the printable HTML with the authenticated client. */
export const getInvoicePrintHtml =
  (id: number): AppThunk =>
  async () => {
    const accessToken = localStorage.getItem('accessToken');
    const response = await api.get<Response>(`${basePath}/${id}/print`, {
      raw: true,
      headers: {
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        Accept: 'text/html,application/json;q=0.9,*/*;q=0.8'
      }
    });
    return response.text();
  };

export default slice;
