import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import {
  IotConfigPatchDTO,
  IotIntegration,
  IotTelemetry,
  IotTokenResponse
} from '../models/owns/integration';
import api from '../utils/api';
import { revertAll } from 'src/utils/redux';

const basePath = 'integrations';

interface IntegrationState {
  iot: IotIntegration | null;
  telemetry: IotTelemetry[];
  loadingGet: boolean;
  loadingTelemetry: boolean;
}

const initialState: IntegrationState = {
  iot: null,
  telemetry: [],
  loadingGet: false,
  loadingTelemetry: false
};

const slice = createSlice({
  name: 'integrations',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getIotIntegration(
      state: IntegrationState,
      action: PayloadAction<{ iot: IotIntegration }>
    ) {
      state.iot = action.payload.iot;
    },
    editIotIntegration(
      state: IntegrationState,
      action: PayloadAction<{ iot: IotIntegration }>
    ) {
      state.iot = action.payload.iot;
    },
    setIotToken(
      state: IntegrationState,
      action: PayloadAction<{ tokenLast4: string }>
    ) {
      if (state.iot) {
        state.iot = {
          ...state.iot,
          hasToken: true,
          tokenLast4: action.payload.tokenLast4
        };
      }
    },
    getIotTelemetry(
      state: IntegrationState,
      action: PayloadAction<{ telemetry: IotTelemetry[] }>
    ) {
      state.telemetry = action.payload.telemetry;
    },
    setLoadingGet(
      state: IntegrationState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    },
    setLoadingTelemetry(
      state: IntegrationState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingTelemetry = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getIntegrations = (): AppThunk => async (dispatch) => {
  dispatch(slice.actions.setLoadingGet({ loading: true }));
  try {
    const integrations = await api.get<{ iot: IotIntegration }>(basePath);
    dispatch(slice.actions.getIotIntegration({ iot: integrations.iot }));
  } catch {
  } finally {
    dispatch(slice.actions.setLoadingGet({ loading: false }));
  }
};

export const editIotIntegration =
  (body: IotConfigPatchDTO): AppThunk =>
  async (dispatch) => {
    const iot = await api.patch<IotIntegration>(`${basePath}/iot`, body);
    dispatch(slice.actions.editIotIntegration({ iot }));
  };

// Returns the token response so the caller can show the token once.
// The token itself is never stored in Redux.
export const rotateIotToken = (): AppThunk => async (dispatch) => {
  const response = await api.post<IotTokenResponse>(
    `${basePath}/iot/token`,
    {}
  );
  dispatch(slice.actions.setIotToken({ tokenLast4: response.tokenLast4 }));
  return response;
};

export const getIotTelemetry =
  (size: number = 20): AppThunk =>
  async (dispatch) => {
    dispatch(slice.actions.setLoadingTelemetry({ loading: true }));
    try {
      const telemetry = await api.get<IotTelemetry[]>(
        `${basePath}/iot/telemetry?size=${size}`
      );
      dispatch(slice.actions.getIotTelemetry({ telemetry }));
    } catch {
    } finally {
      dispatch(slice.actions.setLoadingTelemetry({ loading: false }));
    }
  };

export default slice;
