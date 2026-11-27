import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import AttendanceLog, {
  CheckInPayload,
  CheckOutPayload
} from '../models/owns/attendance';
import api from '../utils/api';
import { revertAll } from 'src/utils/redux';

interface AttendanceState {
  activeByWorkOrder: { [id: number]: AttendanceLog | null };
  logsByWorkOrder: { [id: number]: AttendanceLog[] };
  loadingLogs: { [id: number]: boolean };
}

const initialState: AttendanceState = {
  activeByWorkOrder: {},
  logsByWorkOrder: {},
  loadingLogs: {}
};

const slice = createSlice({
  name: 'attendance',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    setActive(
      state: AttendanceState,
      action: PayloadAction<{ id: number; log: AttendanceLog | null }>
    ) {
      const { id, log } = action.payload;
      state.activeByWorkOrder[id] = log;
    },
    getLogs(
      state: AttendanceState,
      action: PayloadAction<{ id: number; logs: AttendanceLog[] }>
    ) {
      const { id, logs } = action.payload;
      state.logsByWorkOrder[id] = logs;
    },
    setLoadingLogs(
      state: AttendanceState,
      action: PayloadAction<{ id: number; loading: boolean }>
    ) {
      const { id, loading } = action.payload;
      state.loadingLogs = { ...state.loadingLogs, [id]: loading };
    }
  }
});

export const reducer = slice.reducer;

const basePath = 'work-orders';

export const getActiveAttendance =
  (id: number): AppThunk =>
  async (dispatch) => {
    try {
      // api.ts always parses JSON; a 204 (no active record) has no body, so
      // we ask for the raw response and handle the status ourselves.
      const response = await api.get<Response>(
        `${basePath}/${id}/attendance/active`,
        { raw: true }
      );
      let log: AttendanceLog | null = null;
      if (response.status !== 204) {
        log = (await response.json()) as AttendanceLog;
      }
      dispatch(slice.actions.setActive({ id, log: log ?? null }));
    } catch {
      dispatch(slice.actions.setActive({ id, log: null }));
    }
  };

export const getAttendanceLogs =
  (id: number): AppThunk =>
  async (dispatch) => {
    dispatch(slice.actions.setLoadingLogs({ id, loading: true }));
    try {
      const logs = await api.get<AttendanceLog[]>(
        `${basePath}/${id}/attendance`
      );
      dispatch(slice.actions.getLogs({ id, logs }));
    } catch {
    } finally {
      dispatch(slice.actions.setLoadingLogs({ id, loading: false }));
    }
  };

export const checkIn =
  (id: number, payload: CheckInPayload): AppThunk =>
  async (dispatch) => {
    const log = await api.post<AttendanceLog>(
      `${basePath}/${id}/check-in`,
      payload
    );
    dispatch(slice.actions.setActive({ id, log }));
    return log;
  };

export const checkOut =
  (id: number, payload: CheckOutPayload): AppThunk =>
  async (dispatch) => {
    const log = await api.post<AttendanceLog>(
      `${basePath}/${id}/check-out`,
      payload
    );
    dispatch(slice.actions.setActive({ id, log: null }));
    return log;
  };

export default slice;
