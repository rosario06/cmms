import {
  Box,
  debounce,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  LinearProgress,
  Stack,
  Typography
} from '@mui/material';
import { Close } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import * as Yup from 'yup';
import { useContext, useEffect, useMemo, useState } from 'react';
import { createColumnHelper } from '@tanstack/react-table';
import Form from '../components/form';
import { IField } from '../type';
import CustomDatagrid2, {
  CustomDatagridColumn2
} from '../components/CustomDatagrid2';
import ConfirmDialog from '../components/ConfirmDialog';
import SearchInput from '../components/SearchInput';
import useTableState from '../../../hooks/useTableState';
import useAuth from '../../../hooks/useAuth';
import { useDispatch, useSelector } from '../../../store';
import { PermissionEntity } from '../../../models/owns/role';
import { CostCenter } from '../../../models/owns/costCenter';
import { SearchCriteria } from '../../../models/owns/page';
import {
  addCostCenter,
  deleteCostCenter,
  editCostCenter,
  getCostCenters
} from '../../../slices/costCenter';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { CompanySettingsContext } from '../../../contexts/CompanySettingsContext';
import { getErrorMessage } from '../../../utils/api';
import { getBillingErrorMessage } from '../../../utils/billingErrors';
import { formatSelect } from '../../../utils/formatters';
import { onSearchQueryChange } from '../../../utils/overall';
import { toNumberOrNull } from '../Contracts/utils';
import { getBudgetColor, getExecutionPercent } from './utils';

interface PropsType {
  openModal: boolean;
  handleCloseModal: () => void;
}

const fieldMapping: Record<string, string> = {
  code: 'code',
  name: 'name',
  customer: 'customer.name',
  fiscalPeriod: 'fiscalPeriod',
  allocatedBudget: 'allocatedBudget',
  spentAmount: 'spentAmount'
};

const CostCentersTable = ({ openModal, handleCloseModal }: PropsType) => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { costCenters, loadingGet } = useSelector((state) => state.costCenters);
  const { hasCreatePermission } = useAuth();
  const canWrite = hasCreatePermission(PermissionEntity.SETTINGS);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { getFormattedCurrency } = useContext(CompanySettingsContext);
  const [criteria, setCriteria] = useState<SearchCriteria>({
    filterFields: [],
    pageSize: 10,
    pageNum: 0,
    direction: 'DESC'
  });
  const [currentCostCenter, setCurrentCostCenter] = useState<CostCenter>();
  const [openEdit, setOpenEdit] = useState<boolean>(false);
  const [openDelete, setOpenDelete] = useState<boolean>(false);

  const {
    sorting,
    setSorting,
    pagination,
    setPagination,
    columnOrder,
    setColumnOrder,
    columnSizing,
    setColumnSizing,
    columnVisibility,
    setColumnVisibility,
    pinnedColumns,
    setPinnedColumns
  } = useTableState({
    prefix: 'costCenters',
    initialSorting: [],
    initialPagination: {
      pageSize: criteria.pageSize,
      pageIndex: criteria.pageNum
    },
    setCriteria,
    fieldMapping
  });

  const onQueryChange = (event) => {
    onSearchQueryChange<CostCenter>(event, criteria, setCriteria, [
      'name',
      'code'
    ]);
  };
  const debouncedQueryChange = useMemo(() => debounce(onQueryChange, 1300), []);

  useEffect(() => {
    dispatch(getCostCenters(criteria)).catch((err) =>
      showSnackBar(getErrorMessage(err, t('cost_center_load_failure')), 'error')
    );
  }, [criteria]);

  const yesNoItems = [
    { label: t('yes'), value: 'true' },
    { label: t('no'), value: 'false' }
  ];

  const fields: Array<IField> = [
    {
      name: 'code',
      type: 'text',
      label: t('cost_center_code'),
      placeholder: t('cost_center_code'),
      required: true,
      midWidth: true
    },
    {
      name: 'name',
      type: 'text',
      label: t('name'),
      placeholder: t('name'),
      required: true,
      midWidth: true
    },
    {
      name: 'customer',
      type: 'select',
      type2: 'customer',
      label: t('contracts_customer'),
      placeholder: t('contracts_select_customer'),
      midWidth: true
    },
    {
      name: 'location',
      type: 'select',
      type2: 'location',
      label: t('location'),
      placeholder: t('select_location'),
      midWidth: true
    },
    {
      name: 'fiscalPeriod',
      type: 'text',
      label: t('cost_center_fiscal_period'),
      placeholder: '2026, 2026-Q1',
      midWidth: true
    },
    {
      name: 'allocatedBudget',
      type: 'number',
      label: t('cost_center_allocated_budget'),
      placeholder: t('cost_center_allocated_budget'),
      midWidth: true
    },
    {
      name: 'alertThresholdPercent',
      type: 'number',
      label: t('cost_center_alert_threshold'),
      placeholder: t('cost_center_alert_threshold'),
      midWidth: true
    },
    {
      name: 'active',
      type: 'select',
      label: t('cost_center_active'),
      items: yesNoItems,
      midWidth: true
    }
  ];

  const nonNegative = Yup.number()
    .transform((value, original) => (original === '' ? undefined : value))
    .typeError(t('cost_center_invalid_number'))
    .min(0, t('cost_center_invalid_number'))
    .nullable();

  const shape = {
    code: Yup.string().required(t('cost_center_required_code')),
    name: Yup.string().required(t('required_name')),
    allocatedBudget: nonNegative,
    alertThresholdPercent: Yup.number()
      .transform((value, original) => (original === '' ? undefined : value))
      .typeError(t('cost_center_invalid_percent'))
      .min(0, t('cost_center_invalid_percent'))
      .max(100, t('cost_center_invalid_percent'))
      .nullable()
  };

  // when editing, an object without id clears the relation (null is ignored)
  const formatValues = (values, isEdit: boolean) => ({
    code: values.code,
    name: values.name,
    customer: formatSelect(values.customer) ?? (isEdit ? {} : null),
    location: formatSelect(values.location) ?? (isEdit ? {} : null),
    fiscalPeriod: values.fiscalPeriod ? values.fiscalPeriod : null,
    allocatedBudget: toNumberOrNull(values.allocatedBudget) ?? 0,
    alertThresholdPercent: toNumberOrNull(values.alertThresholdPercent) ?? 80,
    active: values.active?.value !== 'false'
  });

  const getFormValues = (costCenter?: CostCenter) => {
    const active = costCenter?.active ?? true;
    return {
      code: costCenter?.code ?? '',
      name: costCenter?.name ?? '',
      customer: costCenter?.customer
        ? { label: costCenter.customer.name, value: costCenter.customer.id }
        : null,
      location: costCenter?.location
        ? { label: costCenter.location.name, value: costCenter.location.id }
        : null,
      fiscalPeriod: costCenter?.fiscalPeriod ?? '',
      allocatedBudget: costCenter?.allocatedBudget ?? '',
      alertThresholdPercent: costCenter?.alertThresholdPercent ?? 80,
      active: {
        label: active ? t('yes') : t('no'),
        value: active ? 'true' : 'false'
      }
    };
  };

  const handleDelete = (id: number) => {
    setOpenDelete(false);
    dispatch(deleteCostCenter(id))
      .then(() => showSnackBar(t('cost_center_delete_success'), 'success'))
      .catch((err) =>
        showSnackBar(
          getErrorMessage(err, t('cost_center_delete_failure')),
          'error'
        )
      );
  };

  const columnHelper = createColumnHelper<CostCenter>();

  const columns: CustomDatagridColumn2<CostCenter>[] = [
    columnHelper.accessor('code', {
      id: 'code',
      header: () => t('cost_center_code'),
      cell: (info) => info.getValue() || '',
      size: 110
    }),
    columnHelper.accessor('name', {
      id: 'name',
      header: () => t('name'),
      cell: (info) => <Box sx={{ fontWeight: 'bold' }}>{info.getValue()}</Box>,
      size: 180
    }),
    columnHelper.accessor((row) => row.customer?.name, {
      id: 'customer',
      header: () => t('contracts_customer'),
      cell: (info) => info.getValue() || '',
      size: 160
    }),
    columnHelper.accessor('fiscalPeriod', {
      id: 'fiscalPeriod',
      header: () => t('cost_center_fiscal_period'),
      cell: (info) => info.getValue() || '',
      size: 120
    }),
    columnHelper.accessor('allocatedBudget', {
      id: 'allocatedBudget',
      header: () => t('cost_center_allocated_budget'),
      cell: (info) => getFormattedCurrency(info.getValue() ?? 0),
      size: 140
    }),
    columnHelper.accessor('spentAmount', {
      id: 'spentAmount',
      header: () => t('cost_center_spent'),
      cell: (info) => getFormattedCurrency(info.getValue() ?? 0),
      size: 130
    }),
    columnHelper.accessor((row) => row.spentAmount, {
      id: 'execution',
      header: () => t('cost_center_execution'),
      cell: (info) => {
        const costCenter = info.row.original;
        const percent = getExecutionPercent(
          costCenter.allocatedBudget,
          costCenter.spentAmount
        );
        return (
          <Box sx={{ width: '100%', py: 0.5 }}>
            <LinearProgress
              variant="determinate"
              value={Math.min(100, percent)}
              color={getBudgetColor(percent, costCenter.alertThresholdPercent)}
              sx={{ height: 8, borderRadius: 4 }}
            />
            <Typography variant="caption">{`${Math.round(percent)}%`}</Typography>
          </Box>
        );
      },
      size: 200,
      enableSorting: false
    })
  ];

  return (
    <Box
      sx={{
        py: 2,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        width: '100%'
      }}
    >
      <Dialog
        fullWidth
        maxWidth="md"
        open={openModal}
        onClose={handleCloseModal}
      >
        <DialogTitle sx={{ p: 3 }}>
          <Typography variant="h4" gutterBottom>
            {t('cost_center_add')}
          </Typography>
        </DialogTitle>
        <DialogContent dividers sx={{ p: 3 }}>
          <Form
            fields={fields}
            validation={Yup.object().shape(shape)}
            submitText={t('add')}
            values={getFormValues()}
            onChange={() => {}}
            onSubmit={async (values) =>
              dispatch(addCostCenter(formatValues(values, false)))
                .then(() => {
                  handleCloseModal();
                  showSnackBar(t('cost_center_create_success'), 'success');
                })
                .catch((err) =>
                  showSnackBar(
                    getBillingErrorMessage(
                      err,
                      'costCenter',
                      t,
                      t('cost_center_create_failure')
                    ),
                    'error'
                  )
                )
            }
          />
        </DialogContent>
      </Dialog>
      <Dialog
        fullWidth
        maxWidth="md"
        open={openEdit}
        onClose={() => setOpenEdit(false)}
      >
        <DialogTitle
          sx={{
            p: 3,
            display: 'flex',
            flexDirection: 'row',
            justifyContent: 'space-between'
          }}
        >
          <Box sx={{ display: 'flex', flexDirection: 'row' }}>
            <Typography variant="h4" mr={2}>
              {t('cost_center_edit')}
            </Typography>
            <Typography
              onClick={() => {
                setOpenEdit(false);
                setOpenDelete(true);
              }}
              variant="subtitle1"
              style={{ cursor: 'pointer' }}
            >
              {t('to_delete')}
            </Typography>
          </Box>
          <IconButton
            aria-label="close"
            onClick={() => setOpenEdit(false)}
            sx={{
              position: 'absolute',
              right: 8,
              top: 8,
              color: (theme) => theme.palette.grey[500]
            }}
          >
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers sx={{ p: 3 }}>
          {currentCostCenter && (
            <Form
              fields={fields}
              validation={Yup.object().shape(shape)}
              submitText={t('save')}
              values={getFormValues(currentCostCenter)}
              onChange={() => {}}
              onSubmit={async (values) =>
                dispatch(
                  editCostCenter(currentCostCenter.id, formatValues(values, true))
                )
                  .then(() => {
                    setOpenEdit(false);
                    showSnackBar(t('changes_saved_success'), 'success');
                  })
                  .catch((err) =>
                    showSnackBar(
                      getBillingErrorMessage(
                        err,
                        'costCenter',
                        t,
                        t('cost_center_edit_failure')
                      ),
                      'error'
                    )
                  )
              }
            />
          )}
        </DialogContent>
      </Dialog>
      <Stack direction="row" width="95%">
        <Box sx={{ my: 0.5 }}>
          <SearchInput onChange={debouncedQueryChange} />
        </Box>
      </Stack>
      <Box sx={{ width: '95%' }}>
        <CustomDatagrid2
          columns={columns}
          data={costCenters.content}
          loading={loadingGet}
          pagination={pagination}
          onPaginationChange={setPagination}
          totalRows={costCenters.totalElements}
          pageSizeOptions={[10, 20, 50]}
          sorting={sorting}
          onSortingChange={setSorting}
          columnOrder={columnOrder}
          onColumnOrderChange={setColumnOrder}
          columnSizing={columnSizing}
          onColumnSizingChange={setColumnSizing}
          columnVisibility={columnVisibility}
          onColumnVisibilityChange={setColumnVisibility}
          onRowClick={(row) => {
            if (!canWrite) return;
            setCurrentCostCenter(row);
            setOpenEdit(true);
          }}
          noRowsMessage={t('cost_center_no_rows')}
          enableColumnReordering
          enableColumnResizing
          pinnedColumns={pinnedColumns}
          onPinnedColumnsChange={setPinnedColumns}
        />
      </Box>
      <ConfirmDialog
        open={openDelete}
        onCancel={() => {
          setOpenDelete(false);
          setOpenEdit(true);
        }}
        onConfirm={() => handleDelete(currentCostCenter?.id)}
        confirmText={t('to_delete')}
        question={t('cost_center_confirm_delete')}
      />
    </Box>
  );
};

export default CostCentersTable;
