import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  debounce,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  LinearProgress,
  Link,
  Stack,
  Tooltip,
  Typography
} from '@mui/material';
import { Close } from '@mui/icons-material';
import ReceiptLongTwoToneIcon from '@mui/icons-material/ReceiptLongTwoTone';
import { Link as RouterLink } from 'react-router-dom';
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
import {
  BILLING_CYCLES,
  SERVICE_CONTRACT_STATUSES,
  ServiceContract
} from '../../../models/owns/serviceContract';
import { SearchCriteria } from '../../../models/owns/page';
import {
  addServiceContract,
  deleteServiceContract,
  editServiceContract,
  getServiceContracts
} from '../../../slices/serviceContract';
import { createInvoiceFromContract } from '../../../slices/invoice';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { CompanySettingsContext } from '../../../contexts/CompanySettingsContext';
import { getErrorMessage } from '../../../utils/api';
import { getBillingErrorMessage } from '../../../utils/billingErrors';
import { formatSelect } from '../../../utils/formatters';
import { onSearchQueryChange } from '../../../utils/overall';
import {
  getConsumedPercent,
  getContractStatusColor,
  getUsageColor,
  toNumberOrNull
} from './utils';

interface PropsType {
  openModal: boolean;
  handleCloseModal: () => void;
}

const fieldMapping: Record<string, string> = {
  contractNumber: 'contractNumber',
  name: 'name',
  customer: 'customer.name',
  status: 'status',
  billingCycle: 'billingCycle',
  monthlyFee: 'monthlyFee'
};

const toIsoDate = (value: unknown): string | null => {
  if (!value) return null;
  const date = new Date(value as string | number | Date);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
};

const ServiceContracts = ({ openModal, handleCloseModal }: PropsType) => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { serviceContracts, loadingGet } = useSelector(
    (state) => state.serviceContracts
  );
  const { hasCreatePermission } = useAuth();
  const canWrite = hasCreatePermission(PermissionEntity.SETTINGS);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { getFormattedDate, getFormattedCurrency } = useContext(
    CompanySettingsContext
  );
  const [criteria, setCriteria] = useState<SearchCriteria>({
    filterFields: [],
    pageSize: 10,
    pageNum: 0,
    direction: 'DESC'
  });
  const [currentContract, setCurrentContract] = useState<ServiceContract>();
  const [openEdit, setOpenEdit] = useState<boolean>(false);
  const [openDelete, setOpenDelete] = useState<boolean>(false);
  const [contractToInvoice, setContractToInvoice] =
    useState<ServiceContract | null>(null);
  const [generatingId, setGeneratingId] = useState<number | null>(null);
  const [invoicedContract, setInvoicedContract] =
    useState<ServiceContract | null>(null);

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
    prefix: 'serviceContracts',
    initialSorting: [],
    initialPagination: {
      pageSize: criteria.pageSize,
      pageIndex: criteria.pageNum
    },
    setCriteria,
    fieldMapping
  });

  const onQueryChange = (event) => {
    onSearchQueryChange<ServiceContract>(event, criteria, setCriteria, [
      'name',
      'contractNumber'
    ]);
  };
  const debouncedQueryChange = useMemo(() => debounce(onQueryChange, 1300), []);

  useEffect(() => {
    dispatch(getServiceContracts(criteria)).catch((err) =>
      showSnackBar(getErrorMessage(err, t('contracts_load_failure')), 'error')
    );
  }, [criteria]);

  const statusItems = SERVICE_CONTRACT_STATUSES.map((status) => ({
    label: t(`contract_status_${status}`),
    value: status
  }));
  const cycleItems = BILLING_CYCLES.map((cycle) => ({
    label: t(`billing_cycle_${cycle}`),
    value: cycle
  }));
  const yesNoItems = [
    { label: t('yes'), value: 'true' },
    { label: t('no'), value: 'false' }
  ];

  const fields: Array<IField> = [
    {
      name: 'contractNumber',
      type: 'text',
      label: t('contract_number'),
      placeholder: t('contract_number_auto'),
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
      placeholder: t('contracts_select_customer')
    },
    {
      name: 'status',
      type: 'select',
      label: t('status'),
      items: statusItems,
      required: true,
      midWidth: true
    },
    {
      name: 'billingCycle',
      type: 'select',
      label: t('billing_cycle'),
      items: cycleItems,
      required: true,
      midWidth: true
    },
    {
      name: 'startDate',
      type: 'date',
      label: t('contract_start_date'),
      required: true,
      midWidth: true
    },
    {
      name: 'endDate',
      type: 'date',
      label: t('contract_end_date'),
      required: true,
      midWidth: true
    },
    {
      name: 'monthlyFee',
      type: 'number',
      label: t('contract_monthly_fee'),
      placeholder: t('contract_monthly_fee'),
      midWidth: true
    },
    {
      name: 'includedHours',
      type: 'number',
      label: t('contract_included_hours'),
      placeholder: t('contract_included_hours'),
      midWidth: true
    },
    {
      name: 'hourlyRateOverride',
      type: 'number',
      label: t('contract_hourly_rate_override'),
      placeholder: t('contract_hourly_rate_override'),
      midWidth: true
    },
    {
      name: 'partsMarkupPercent',
      type: 'number',
      label: t('contract_parts_markup_percent'),
      placeholder: t('contract_parts_markup_percent'),
      midWidth: true
    },
    {
      name: 'autoInvoice',
      type: 'select',
      label: t('contract_auto_invoice'),
      items: yesNoItems
    }
  ];

  const nonNegative = (message: string) =>
    Yup.number()
      .transform((value, original) => (original === '' ? undefined : value))
      .typeError(t('contracts_invalid_number'))
      .min(0, message)
      .nullable();

  const shape = {
    name: Yup.string().required(t('required_name')),
    status: Yup.object().nullable().required(t('contracts_required_status')),
    billingCycle: Yup.object()
      .nullable()
      .required(t('contracts_required_billing_cycle')),
    startDate: Yup.mixed().required(t('contracts_required_start_date')),
    endDate: Yup.mixed()
      .required(t('contracts_required_end_date'))
      .test('end-after-start', t('contracts_end_before_start'), function (end) {
        const start = toIsoDate(this.parent.startDate);
        const endIso = toIsoDate(end);
        return !start || !endIso || new Date(endIso) >= new Date(start);
      }),
    monthlyFee: nonNegative(t('contracts_invalid_number')),
    includedHours: nonNegative(t('contracts_invalid_number')),
    hourlyRateOverride: nonNegative(t('contracts_invalid_number')),
    partsMarkupPercent: nonNegative(t('contracts_invalid_number'))
  };

  const formatValues = (values) => ({
    contractNumber: values.contractNumber ? values.contractNumber : undefined,
    name: values.name,
    customer: formatSelect(values.customer),
    status: values.status?.value,
    billingCycle: values.billingCycle?.value,
    startDate: toIsoDate(values.startDate),
    endDate: toIsoDate(values.endDate),
    monthlyFee: toNumberOrNull(values.monthlyFee) ?? 0,
    includedHours: toNumberOrNull(values.includedHours) ?? 0,
    hourlyRateOverride: toNumberOrNull(values.hourlyRateOverride),
    partsMarkupPercent: toNumberOrNull(values.partsMarkupPercent) ?? 0,
    autoInvoice: values.autoInvoice?.value === 'true'
  });

  const getFormValues = (contract?: ServiceContract) => {
    const status = contract?.status ?? 'DRAFT';
    const cycle = contract?.billingCycle ?? 'MONTHLY';
    const autoInvoice = contract?.autoInvoice ?? false;
    return {
      contractNumber: contract?.contractNumber ?? '',
      name: contract?.name ?? '',
      customer: contract?.customer
        ? { label: contract.customer.name, value: contract.customer.id }
        : null,
      status: { label: t(`contract_status_${status}`), value: status },
      billingCycle: { label: t(`billing_cycle_${cycle}`), value: cycle },
      startDate: contract?.startDate ? new Date(contract.startDate) : null,
      endDate: contract?.endDate ? new Date(contract.endDate) : null,
      monthlyFee: contract?.monthlyFee ?? '',
      includedHours: contract?.includedHours ?? '',
      hourlyRateOverride: contract?.hourlyRateOverride ?? '',
      partsMarkupPercent: contract?.partsMarkupPercent ?? 0,
      autoInvoice: {
        label: autoInvoice ? t('yes') : t('no'),
        value: autoInvoice ? 'true' : 'false'
      }
    };
  };

  const handleDelete = (id: number) => {
    setOpenDelete(false);
    dispatch(deleteServiceContract(id))
      .then(() => showSnackBar(t('contract_delete_success'), 'success'))
      .catch((err) =>
        showSnackBar(
          getErrorMessage(err, t('contract_delete_failure')),
          'error'
        )
      );
  };

  const handleGenerateInvoice = async () => {
    const contract = contractToInvoice;
    setContractToInvoice(null);
    if (!contract) return;
    setGeneratingId(contract.id);
    setInvoicedContract(null);
    try {
      await dispatch(createInvoiceFromContract(contract.id));
      setInvoicedContract(contract);
    } catch (err) {
      showSnackBar(
        getBillingErrorMessage(
          err,
          'invoiceFromContract',
          t,
          t('invoice_generate_failure_contract')
        ),
        'error'
      );
    } finally {
      setGeneratingId(null);
    }
  };

  const columnHelper = createColumnHelper<ServiceContract>();

  const columns: CustomDatagridColumn2<ServiceContract>[] = [
    columnHelper.accessor('contractNumber', {
      id: 'contractNumber',
      header: () => t('contract_number'),
      cell: (info) => info.getValue() || '',
      size: 140
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
    columnHelper.accessor('status', {
      id: 'status',
      header: () => t('status'),
      cell: (info) => (
        <Chip
          size="small"
          color={getContractStatusColor(info.getValue())}
          label={t(`contract_status_${info.getValue()}`)}
        />
      ),
      size: 120
    }),
    columnHelper.accessor('billingCycle', {
      id: 'billingCycle',
      header: () => t('billing_cycle'),
      cell: (info) =>
        info.getValue() ? t(`billing_cycle_${info.getValue()}`) : '',
      size: 120
    }),
    columnHelper.accessor((row) => row.startDate, {
      id: 'validity',
      header: () => t('contract_validity'),
      cell: (info) =>
        `${getFormattedDate(info.row.original.startDate, true)} - ${getFormattedDate(
          info.row.original.endDate,
          true
        )}`,
      size: 200,
      enableSorting: false
    }),
    columnHelper.accessor('monthlyFee', {
      id: 'monthlyFee',
      header: () => t('contract_monthly_fee'),
      cell: (info) => getFormattedCurrency(info.getValue() ?? 0),
      size: 130
    }),
    columnHelper.accessor((row) => row.remainingHours, {
      id: 'usage',
      header: () => t('contract_hours_usage'),
      cell: (info) => {
        const contract = info.row.original;
        const percent = getConsumedPercent(
          contract.includedHours,
          contract.remainingHours
        );
        const consumed = Math.max(
          0,
          (contract.includedHours ?? 0) - (contract.remainingHours ?? 0)
        );
        return (
          <Box sx={{ width: '100%', py: 0.5 }}>
            {contract.includedHours > 0 ? (
              <>
                <LinearProgress
                  variant="determinate"
                  value={percent}
                  color={getUsageColor(percent)}
                  sx={{ height: 8, borderRadius: 4 }}
                />
                <Typography variant="caption">
                  {`${consumed.toFixed(1)} / ${contract.includedHours} h (${Math.round(
                    percent
                  )}%)`}
                </Typography>
              </>
            ) : (
              <Typography variant="caption">
                {t('contract_no_included_hours')}
              </Typography>
            )}
            <Typography variant="caption" display="block">
              {`${t('contract_retainer_balance')}: ${getFormattedCurrency(
                contract.retainerBalance ?? 0
              )}`}
            </Typography>
          </Box>
        );
      },
      size: 220,
      enableSorting: false
    }),
    ...(canWrite
      ? [
          columnHelper.display({
            id: 'generateInvoice',
            header: () => t('invoice_generate'),
            cell: ({ row }) => {
              const contract = row.original;
              const isActive = contract.status === 'ACTIVE';
              const isGenerating = generatingId === contract.id;
              return (
                <Tooltip
                  title={
                    isActive
                      ? t('invoice_generate_from_contract_hint')
                      : t('invoice_generate_contract_not_active')
                  }
                >
                  <span>
                    <Button
                      size="small"
                      variant="outlined"
                      disabled={!isActive || generatingId !== null}
                      startIcon={
                        isGenerating ? (
                          <CircularProgress size={16} />
                        ) : (
                          <ReceiptLongTwoToneIcon fontSize="small" />
                        )
                      }
                      onClick={(event) => {
                        event.stopPropagation();
                        setContractToInvoice(contract);
                      }}
                    >
                      {t('invoice_generate')}
                    </Button>
                  </span>
                </Tooltip>
              );
            },
            size: 170,
            enableSorting: false
          })
        ]
      : [])
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
            {t('contract_add')}
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
              dispatch(addServiceContract(formatValues(values)))
                .then(() => {
                  handleCloseModal();
                  showSnackBar(t('contract_create_success'), 'success');
                })
                .catch((err) =>
                  showSnackBar(
                    getBillingErrorMessage(
                      err,
                      'contract',
                      t,
                      t('contract_create_failure')
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
              {t('contract_edit')}
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
          {currentContract && (
            <Form
              fields={fields}
              validation={Yup.object().shape(shape)}
              submitText={t('save')}
              values={getFormValues(currentContract)}
              onChange={() => {}}
              onSubmit={async (values) =>
                dispatch(
                  editServiceContract(currentContract.id, formatValues(values))
                )
                  .then(() => {
                    setOpenEdit(false);
                    showSnackBar(t('changes_saved_success'), 'success');
                  })
                  .catch((err) =>
                    showSnackBar(
                      getBillingErrorMessage(
                        err,
                        'contract',
                        t,
                        t('contract_edit_failure')
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
      {invoicedContract && (
        <Alert
          severity="success"
          sx={{ width: '95%', mb: 1 }}
          onClose={() => setInvoicedContract(null)}
        >
          {`${t('invoice_generate_contract_success', {
            contract: invoicedContract.contractNumber
          })} `}
          <Link component={RouterLink} to="/app/invoices">
            {t('invoice_view_invoices')}
          </Link>
        </Alert>
      )}
      <Box sx={{ width: '95%' }}>
        <CustomDatagrid2
          columns={columns}
          data={serviceContracts.content}
          loading={loadingGet}
          pagination={pagination}
          onPaginationChange={setPagination}
          totalRows={serviceContracts.totalElements}
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
            setCurrentContract(row);
            setOpenEdit(true);
          }}
          noRowsMessage={t('contracts_no_rows')}
          enableColumnReordering
          enableColumnResizing
          pinnedColumns={pinnedColumns}
          onPinnedColumnsChange={setPinnedColumns}
        />
      </Box>
      <ConfirmDialog
        open={!!contractToInvoice}
        onCancel={() => setContractToInvoice(null)}
        onConfirm={handleGenerateInvoice}
        confirmText={t('invoice_generate')}
        question={t('invoice_generate_contract_confirm', {
          contract: contractToInvoice?.contractNumber ?? ''
        })}
      />
      <ConfirmDialog
        open={openDelete}
        onCancel={() => {
          setOpenDelete(false);
          setOpenEdit(true);
        }}
        onConfirm={() => handleDelete(currentContract?.id)}
        confirmText={t('to_delete')}
        question={t('contract_confirm_delete')}
      />
    </Box>
  );
};

export default ServiceContracts;
