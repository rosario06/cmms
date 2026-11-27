import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import { SlaPolicy } from '../models/owns/slaPolicy';
import api from '../utils/api';
import { getInitialPage, Page, SearchCriteria } from '../models/owns/page';
import { revertAll } from 'src/utils/redux';

const basePath = 'sla-policies';

interface SlaPolicyState {
  slaPolicies: Page<SlaPolicy>;
  loadingGet: boolean;
}

const initialState: SlaPolicyState = {
  slaPolicies: getInitialPage<SlaPolicy>(),
  loadingGet: false
};

const slice = createSlice({
  name: 'slaPolicies',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getSlaPolicies(
      state: SlaPolicyState,
      action: PayloadAction<{ slaPolicies: Page<SlaPolicy> }>
    ) {
      state.slaPolicies = action.payload.slaPolicies;
    },
    addSlaPolicy(
      state: SlaPolicyState,
      action: PayloadAction<{ slaPolicy: SlaPolicy }>
    ) {
      state.slaPolicies.content = [
        action.payload.slaPolicy,
        ...state.slaPolicies.content
      ];
    },
    editSlaPolicy(
      state: SlaPolicyState,
      action: PayloadAction<{ slaPolicy: SlaPolicy }>
    ) {
      const { slaPolicy } = action.payload;
      state.slaPolicies.content = state.slaPolicies.content.map((policy) =>
        policy.id === slaPolicy.id ? slaPolicy : policy
      );
    },
    deleteSlaPolicy(
      state: SlaPolicyState,
      action: PayloadAction<{ id: number }>
    ) {
      const { id } = action.payload;
      state.slaPolicies.content = state.slaPolicies.content.filter(
        (policy) => policy.id !== id
      );
    },
    setLoadingGet(
      state: SlaPolicyState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getSlaPolicies =
  (criteria: SearchCriteria): AppThunk =>
  async (dispatch) => {
    try {
      dispatch(slice.actions.setLoadingGet({ loading: true }));
      const slaPolicies = await api.post<Page<SlaPolicy>>(
        `${basePath}/search`,
        criteria
      );
      dispatch(slice.actions.getSlaPolicies({ slaPolicies }));
    } finally {
      dispatch(slice.actions.setLoadingGet({ loading: false }));
    }
  };

export const addSlaPolicy =
  (slaPolicy: Partial<SlaPolicy>): AppThunk =>
  async (dispatch) => {
    const response = await api.post<SlaPolicy>(basePath, slaPolicy);
    dispatch(slice.actions.addSlaPolicy({ slaPolicy: response }));
  };

export const editSlaPolicy =
  (id: number, slaPolicy: Partial<SlaPolicy>): AppThunk =>
  async (dispatch) => {
    const response = await api.patch<SlaPolicy>(
      `${basePath}/${id}`,
      slaPolicy
    );
    dispatch(slice.actions.editSlaPolicy({ slaPolicy: response }));
  };

export const deleteSlaPolicy =
  (id: number): AppThunk =>
  async (dispatch) => {
    const response = await api.deletes<{ success: boolean }>(
      `${basePath}/${id}`
    );
    if (response.success) {
      dispatch(slice.actions.deleteSlaPolicy({ id }));
    }
  };

export default slice;
