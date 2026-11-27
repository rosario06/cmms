import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import { Holiday, HolidayInput } from '../models/owns/holiday';
import api from '../utils/api';
import { getInitialPage, Page, SearchCriteria } from '../models/owns/page';
import { revertAll } from 'src/utils/redux';

const basePath = 'holidays';

interface HolidayState {
  holidays: Page<Holiday>;
  loadingGet: boolean;
}

const initialState: HolidayState = {
  holidays: getInitialPage<Holiday>(),
  loadingGet: false
};

const slice = createSlice({
  name: 'holidays',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getHolidays(
      state: HolidayState,
      action: PayloadAction<{ holidays: Page<Holiday> }>
    ) {
      state.holidays = action.payload.holidays;
    },
    addHoliday(
      state: HolidayState,
      action: PayloadAction<{ holiday: Holiday }>
    ) {
      state.holidays.content = [
        action.payload.holiday,
        ...state.holidays.content
      ];
    },
    editHoliday(
      state: HolidayState,
      action: PayloadAction<{ holiday: Holiday }>
    ) {
      const { holiday } = action.payload;
      state.holidays.content = state.holidays.content.map((item) =>
        item.id === holiday.id ? holiday : item
      );
    },
    deleteHoliday(state: HolidayState, action: PayloadAction<{ id: number }>) {
      const { id } = action.payload;
      state.holidays.content = state.holidays.content.filter(
        (item) => item.id !== id
      );
    },
    setLoadingGet(
      state: HolidayState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getHolidays =
  (criteria: SearchCriteria): AppThunk =>
  async (dispatch) => {
    try {
      dispatch(slice.actions.setLoadingGet({ loading: true }));
      const holidays = await api.post<Page<Holiday>>(
        `${basePath}/search`,
        criteria
      );
      dispatch(slice.actions.getHolidays({ holidays }));
    } finally {
      dispatch(slice.actions.setLoadingGet({ loading: false }));
    }
  };

export const addHoliday =
  (holiday: HolidayInput): AppThunk =>
  async (dispatch) => {
    const response = await api.post<Holiday>(basePath, holiday, {
      silentConflict: true
    });
    dispatch(slice.actions.addHoliday({ holiday: response }));
  };

export const editHoliday =
  (id: number, holiday: HolidayInput): AppThunk =>
  async (dispatch) => {
    const response = await api.patch<Holiday>(`${basePath}/${id}`, holiday, {
      silentConflict: true
    });
    dispatch(slice.actions.editHoliday({ holiday: response }));
  };

export const deleteHoliday =
  (id: number): AppThunk =>
  async (dispatch) => {
    const response = await api.deletes<{ success: boolean }>(
      `${basePath}/${id}`
    );
    if (response.success) {
      dispatch(slice.actions.deleteHoliday({ id }));
    }
  };

export default slice;
