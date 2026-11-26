import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import {
  Integrations,
  IntegrationTestResult,
  IotConfigPatchDTO,
  IotIntegration,
  IotTelemetry,
  IotTokenResponse,
  MapsConfigPatchDTO,
  MapsIntegration,
  NotificationsConfigPatchDTO,
  NotificationsIntegration,
  PaymentsConfigPatchDTO,
  PaymentsIntegration
} from '../models/owns/integration';
import api from '../utils/api';
import { revertAll } from 'src/utils/redux';

const basePath = 'integrations';

interface IntegrationState {
  iot: IotIntegration | null;
  maps: MapsIntegration | null;
  payments: PaymentsIntegration | null;
  notifications: NotificationsIntegration | null;
  telemetry: IotTelemetry[];
  loadingGet: boolean;
  loadingTelemetry: boolean;
}

const initialState: IntegrationState = {
  iot: null,
  maps: null,
  payments: null,
  notifications: null,
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
    getIntegrations(
      state: IntegrationState,
      action: PayloadAction<{ integrations: Integrations }>
    ) {
      const { iot, maps, payments, notifications } =
        action.payload.integrations;
      state.iot = iot;
      state.maps = maps ?? null;
      state.payments = payments ?? null;
      state.notifications = notifications ?? null;
    },
    setMaps(
      state: IntegrationState,
      action: PayloadAction<{ maps: MapsIntegration }>
    ) {
      state.maps = action.payload.maps;
    },
    setPayments(
      state: IntegrationState,
      action: PayloadAction<{ payments: PaymentsIntegration }>
    ) {
      state.payments = action.payload.payments;
    },
    setNotifications(
      state: IntegrationState,
      action: PayloadAction<{ notifications: NotificationsIntegration }>
    ) {
      state.notifications = action.payload.notifications;
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
    const integrations = await api.get<Integrations>(basePath);
    dispatch(slice.actions.getIntegrations({ integrations }));
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

export const editMapsIntegration =
  (body: MapsConfigPatchDTO): AppThunk =>
  async (dispatch) => {
    const maps = await api.patch<MapsIntegration>(`${basePath}/maps`, body);
    dispatch(slice.actions.setMaps({ maps }));
  };

export const testMapsIntegration =
  (): AppThunk => async () => {
    return api.post<IntegrationTestResult>(`${basePath}/maps/test`, {});
  };

export const editPaymentsIntegration =
  (body: PaymentsConfigPatchDTO): AppThunk =>
  async (dispatch) => {
    const payments = await api.patch<PaymentsIntegration>(
      `${basePath}/payments`,
      body
    );
    dispatch(slice.actions.setPayments({ payments }));
  };

export const testPaymentsIntegration =
  (): AppThunk => async () => {
    return api.post<IntegrationTestResult>(`${basePath}/payments/test`, {});
  };

export const editNotificationsIntegration =
  (body: NotificationsConfigPatchDTO): AppThunk =>
  async (dispatch) => {
    const notifications = await api.patch<NotificationsIntegration>(
      `${basePath}/notifications`,
      body
    );
    dispatch(slice.actions.setNotifications({ notifications }));
  };

export const testWhatsappNotification =
  (phone: string): AppThunk =>
  async () => {
    return api.post<IntegrationTestResult>(
      `${basePath}/notifications/test-whatsapp`,
      { phone }
    );
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
