import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import {
  ServiceContract,
  ServiceContractInput,
  ServiceContractMini,
  ServiceContractUsage
} from '../models/owns/serviceContract';
import api from '../utils/api';
import { getInitialPage, Page, SearchCriteria } from '../models/owns/page';
import { revertAll } from 'src/utils/redux';

const basePath = 'service-contracts';

interface ServiceContractState {
  serviceContracts: Page<ServiceContract>;
  contractOptions: ServiceContractMini[];
  loadingGet: boolean;
}

const initialState: ServiceContractState = {
  serviceContracts: getInitialPage<ServiceContract>(),
  contractOptions: [],
  loadingGet: false
};

const slice = createSlice({
  name: 'serviceContracts',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getServiceContracts(
      state: ServiceContractState,
      action: PayloadAction<{ serviceContracts: Page<ServiceContract> }>
    ) {
      state.serviceContracts = action.payload.serviceContracts;
    },
    getContractOptions(
      state: ServiceContractState,
      action: PayloadAction<{ options: ServiceContractMini[] }>
    ) {
      state.contractOptions = action.payload.options;
    },
    addServiceContract(
      state: ServiceContractState,
      action: PayloadAction<{ serviceContract: ServiceContract }>
    ) {
      const { serviceContract } = action.payload;
      state.serviceContracts.content = [
        serviceContract,
        ...state.serviceContracts.content
      ];
    },
    editServiceContract(
      state: ServiceContractState,
      action: PayloadAction<{ serviceContract: ServiceContract }>
    ) {
      const { serviceContract } = action.payload;
      state.serviceContracts.content = state.serviceContracts.content.map(
        (contract) =>
          contract.id === serviceContract.id ? serviceContract : contract
      );
    },
    deleteServiceContract(
      state: ServiceContractState,
      action: PayloadAction<{ id: number }>
    ) {
      const { id } = action.payload;
      state.serviceContracts.content = state.serviceContracts.content.filter(
        (contract) => contract.id !== id
      );
    },
    setLoadingGet(
      state: ServiceContractState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getServiceContracts =
  (criteria: SearchCriteria): AppThunk =>
  async (dispatch) => {
    try {
      dispatch(slice.actions.setLoadingGet({ loading: true }));
      const serviceContracts = await api.post<Page<ServiceContract>>(
        `${basePath}/search`,
        criteria
      );
      dispatch(slice.actions.getServiceContracts({ serviceContracts }));
    } finally {
      dispatch(slice.actions.setLoadingGet({ loading: false }));
    }
  };

export const getServiceContractOptions = (): AppThunk => async (dispatch) => {
  const page = await api.post<Page<ServiceContract>>(`${basePath}/search`, {
    filterFields: [],
    pageSize: 200,
    pageNum: 0,
    direction: 'ASC'
  });
  dispatch(
    slice.actions.getContractOptions({
      options: page.content.map((contract) => ({
        id: contract.id,
        name: contract.name,
        contractNumber: contract.contractNumber
      }))
    })
  );
};

export const getServiceContractUsage =
  (id: number): AppThunk =>
  async () =>
    api.get<ServiceContractUsage>(`${basePath}/${id}/usage`);

export const addServiceContract =
  (serviceContract: ServiceContractInput): AppThunk =>
  async (dispatch) => {
    const response = await api.post<ServiceContract>(
      basePath,
      serviceContract,
      { silentConflict: true }
    );
    dispatch(
      slice.actions.addServiceContract({ serviceContract: response })
    );
  };

export const editServiceContract =
  (id: number, serviceContract: ServiceContractInput): AppThunk =>
  async (dispatch) => {
    const response = await api.patch<ServiceContract>(
      `${basePath}/${id}`,
      serviceContract,
      { silentConflict: true }
    );
    dispatch(
      slice.actions.editServiceContract({ serviceContract: response })
    );
  };

export const deleteServiceContract =
  (id: number): AppThunk =>
  async (dispatch) => {
    const response = await api.deletes<{ success: boolean }>(
      `${basePath}/${id}`
    );
    if (response.success) {
      dispatch(slice.actions.deleteServiceContract({ id }));
    }
  };

export default slice;
