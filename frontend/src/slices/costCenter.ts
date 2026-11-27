import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import {
  CostCenter,
  CostCenterInput,
  CostCenterMini,
  CostCenterSummary
} from '../models/owns/costCenter';
import api from '../utils/api';
import { getInitialPage, Page, SearchCriteria } from '../models/owns/page';
import { revertAll } from 'src/utils/redux';

const basePath = 'cost-centers';

interface CostCenterState {
  costCenters: Page<CostCenter>;
  costCenterOptions: CostCenterMini[];
  loadingGet: boolean;
}

const initialState: CostCenterState = {
  costCenters: getInitialPage<CostCenter>(),
  costCenterOptions: [],
  loadingGet: false
};

const slice = createSlice({
  name: 'costCenters',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getCostCenters(
      state: CostCenterState,
      action: PayloadAction<{ costCenters: Page<CostCenter> }>
    ) {
      state.costCenters = action.payload.costCenters;
    },
    getCostCenterOptions(
      state: CostCenterState,
      action: PayloadAction<{ options: CostCenterMini[] }>
    ) {
      state.costCenterOptions = action.payload.options;
    },
    addCostCenter(
      state: CostCenterState,
      action: PayloadAction<{ costCenter: CostCenter }>
    ) {
      const { costCenter } = action.payload;
      state.costCenters.content = [costCenter, ...state.costCenters.content];
    },
    editCostCenter(
      state: CostCenterState,
      action: PayloadAction<{ costCenter: CostCenter }>
    ) {
      const { costCenter } = action.payload;
      state.costCenters.content = state.costCenters.content.map((item) =>
        item.id === costCenter.id ? costCenter : item
      );
    },
    deleteCostCenter(
      state: CostCenterState,
      action: PayloadAction<{ id: number }>
    ) {
      const { id } = action.payload;
      state.costCenters.content = state.costCenters.content.filter(
        (item) => item.id !== id
      );
    },
    setLoadingGet(
      state: CostCenterState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getCostCenters =
  (criteria: SearchCriteria): AppThunk =>
  async (dispatch) => {
    try {
      dispatch(slice.actions.setLoadingGet({ loading: true }));
      const costCenters = await api.post<Page<CostCenter>>(
        `${basePath}/search`,
        criteria
      );
      dispatch(slice.actions.getCostCenters({ costCenters }));
    } finally {
      dispatch(slice.actions.setLoadingGet({ loading: false }));
    }
  };

export const getCostCenterOptions = (): AppThunk => async (dispatch) => {
  const page = await api.post<Page<CostCenter>>(`${basePath}/search`, {
    filterFields: [],
    pageSize: 200,
    pageNum: 0,
    direction: 'ASC'
  });
  dispatch(
    slice.actions.getCostCenterOptions({
      options: page.content
        .filter((costCenter) => costCenter.active !== false)
        .map((costCenter) => ({
          id: costCenter.id,
          name: costCenter.name,
          code: costCenter.code
        }))
    })
  );
};

export const getCostCenterSummary =
  (id: number): AppThunk =>
  async () =>
    api.get<CostCenterSummary>(`${basePath}/${id}/summary`);

export const addCostCenter =
  (costCenter: CostCenterInput): AppThunk =>
  async (dispatch) => {
    const response = await api.post<CostCenter>(basePath, costCenter, {
      silentConflict: true
    });
    dispatch(slice.actions.addCostCenter({ costCenter: response }));
  };

export const editCostCenter =
  (id: number, costCenter: CostCenterInput): AppThunk =>
  async (dispatch) => {
    const response = await api.patch<CostCenter>(
      `${basePath}/${id}`,
      costCenter,
      { silentConflict: true }
    );
    dispatch(slice.actions.editCostCenter({ costCenter: response }));
  };

export const deleteCostCenter =
  (id: number): AppThunk =>
  async (dispatch) => {
    const response = await api.deletes<{ success: boolean }>(
      `${basePath}/${id}`
    );
    if (response.success) {
      dispatch(slice.actions.deleteCostCenter({ id }));
    }
  };

export default slice;
