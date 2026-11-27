import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import {
  PlannerEvent,
  PlannerResponse,
  ScheduleRequest,
  ScheduleResponse
} from '../models/owns/planner';
import api from '../utils/api';
import { revertAll } from 'src/utils/redux';

const basePath = 'work-orders';

interface PlannerState {
  scheduled: PlannerEvent[];
  unassigned: PlannerEvent[];
  loadingGet: boolean;
}

const initialState: PlannerState = {
  scheduled: [],
  unassigned: [],
  loadingGet: false
};

const slice = createSlice({
  name: 'planner',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getPlanner(state: PlannerState, action: PayloadAction<PlannerResponse>) {
      state.scheduled = action.payload.scheduled ?? [];
      state.unassigned = action.payload.unassigned ?? [];
    },
    upsertScheduled(state: PlannerState, action: PayloadAction<PlannerEvent>) {
      const event = action.payload;
      state.unassigned = state.unassigned.filter((e) => e.id !== event.id);
      const exists = state.scheduled.some((e) => e.id === event.id);
      state.scheduled = exists
        ? state.scheduled.map((e) => (e.id === event.id ? event : e))
        : [...state.scheduled, event];
    },
    setLoadingGet(
      state: PlannerState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getPlanner =
  (start: Date, end: Date, technicianId?: number | null): AppThunk =>
  async (dispatch) => {
    dispatch(slice.actions.setLoadingGet({ loading: true }));
    try {
      const params = new URLSearchParams({
        start: start.toISOString(),
        end: end.toISOString()
      });
      if (technicianId) params.append('technicianId', technicianId.toString());
      const response = await api.get<PlannerResponse>(
        `${basePath}/planner?${params.toString()}`
      );
      dispatch(slice.actions.getPlanner(response));
    } finally {
      dispatch(slice.actions.setLoadingGet({ loading: false }));
    }
  };

export const scheduleWorkOrder =
  (id: number, body: ScheduleRequest): AppThunk =>
  async (dispatch) => {
    const response = await api.patch<ScheduleResponse>(
      `${basePath}/${id}/schedule`,
      body
    );
    dispatch(slice.actions.upsertScheduled(response.event));
    return response;
  };

export default slice;
