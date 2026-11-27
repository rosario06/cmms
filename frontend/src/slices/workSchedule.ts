import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import {
  WorkSchedule,
  WorkScheduleInput,
  WorkScheduleMini
} from '../models/owns/workSchedule';
import api from '../utils/api';
import { getInitialPage, Page, SearchCriteria } from '../models/owns/page';
import { revertAll } from 'src/utils/redux';

const basePath = 'work-schedules';

interface WorkScheduleState {
  workSchedules: Page<WorkSchedule>;
  workScheduleOptions: WorkScheduleMini[];
  loadingGet: boolean;
}

const initialState: WorkScheduleState = {
  workSchedules: getInitialPage<WorkSchedule>(),
  workScheduleOptions: [],
  loadingGet: false
};

const slice = createSlice({
  name: 'workSchedules',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getWorkSchedules(
      state: WorkScheduleState,
      action: PayloadAction<{ workSchedules: Page<WorkSchedule> }>
    ) {
      state.workSchedules = action.payload.workSchedules;
    },
    getWorkScheduleOptions(
      state: WorkScheduleState,
      action: PayloadAction<{ options: WorkScheduleMini[] }>
    ) {
      state.workScheduleOptions = action.payload.options;
    },
    addWorkSchedule(
      state: WorkScheduleState,
      action: PayloadAction<{ workSchedule: WorkSchedule }>
    ) {
      const { workSchedule } = action.payload;
      // only one default schedule per company
      const others = workSchedule.defaultSchedule
        ? state.workSchedules.content.map((schedule) => ({
            ...schedule,
            defaultSchedule: false
          }))
        : state.workSchedules.content;
      state.workSchedules.content = [workSchedule, ...others];
    },
    editWorkSchedule(
      state: WorkScheduleState,
      action: PayloadAction<{ workSchedule: WorkSchedule }>
    ) {
      const { workSchedule } = action.payload;
      state.workSchedules.content = state.workSchedules.content.map(
        (schedule) => {
          if (schedule.id === workSchedule.id) return workSchedule;
          return workSchedule.defaultSchedule
            ? { ...schedule, defaultSchedule: false }
            : schedule;
        }
      );
    },
    deleteWorkSchedule(
      state: WorkScheduleState,
      action: PayloadAction<{ id: number }>
    ) {
      const { id } = action.payload;
      state.workSchedules.content = state.workSchedules.content.filter(
        (schedule) => schedule.id !== id
      );
      state.workScheduleOptions = state.workScheduleOptions.filter(
        (schedule) => schedule.id !== id
      );
    },
    setLoadingGet(
      state: WorkScheduleState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getWorkSchedules =
  (criteria: SearchCriteria): AppThunk =>
  async (dispatch) => {
    try {
      dispatch(slice.actions.setLoadingGet({ loading: true }));
      const workSchedules = await api.post<Page<WorkSchedule>>(
        `${basePath}/search`,
        criteria
      );
      dispatch(slice.actions.getWorkSchedules({ workSchedules }));
    } finally {
      dispatch(slice.actions.setLoadingGet({ loading: false }));
    }
  };

export const getWorkScheduleOptions = (): AppThunk => async (dispatch) => {
  const options = await api.get<WorkScheduleMini[]>(`${basePath}/mini`);
  dispatch(slice.actions.getWorkScheduleOptions({ options }));
};

export const addWorkSchedule =
  (workSchedule: WorkScheduleInput): AppThunk =>
  async (dispatch) => {
    const response = await api.post<WorkSchedule>(basePath, workSchedule, {
      silentConflict: true
    });
    dispatch(slice.actions.addWorkSchedule({ workSchedule: response }));
  };

export const editWorkSchedule =
  (id: number, workSchedule: WorkScheduleInput): AppThunk =>
  async (dispatch) => {
    const response = await api.patch<WorkSchedule>(
      `${basePath}/${id}`,
      workSchedule,
      { silentConflict: true }
    );
    dispatch(slice.actions.editWorkSchedule({ workSchedule: response }));
  };

export const deleteWorkSchedule =
  (id: number): AppThunk =>
  async (dispatch) => {
    const response = await api.deletes<{ success: boolean }>(
      `${basePath}/${id}`,
      { silentConflict: true }
    );
    if (response.success) {
      dispatch(slice.actions.deleteWorkSchedule({ id }));
    }
  };

export default slice;
