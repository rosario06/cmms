import {
  Box,
  Chip,
  debounce,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
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
import { SLA_PRIORITIES, SlaPolicy } from '../../../models/owns/slaPolicy';
import { SearchCriteria } from '../../../models/owns/page';
import {
  addSlaPolicy,
  deleteSlaPolicy,
  editSlaPolicy,
  getSlaPolicies
} from '../../../slices/slaPolicy';
import { getServiceContractOptions } from '../../../slices/serviceContract';
import { getWorkScheduleOptions } from '../../../slices/workSchedule';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { getErrorMessage } from '../../../utils/api';
import { formatSelect } from '../../../utils/formatters';
import { onSearchQueryChange } from '../../../utils/overall';
import { formatMinutes, toNumberOrNull } from './utils';

interface PropsType {
  openModal: boolean;
  handleCloseModal: () => void;
}

const fieldMapping: Record<string, string> = {
  name: 'name',
  priority: 'priority',
  firstResponseMinutes: 'firstResponseMinutes',
  resolutionMinutes: 'resolutionMinutes',
  penaltyPercent: 'penaltyPercent',
  serviceContract: 'serviceContract.name',
  useBusinessHours: 'useBusinessHours'
};

const SlaPolicies = ({ openModal, handleCloseModal }: PropsType) => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { slaPolicies, loadingGet } = useSelector((state) => state.slaPolicies);
  const { contractOptions } = useSelector((state) => state.serviceContracts);
  const { workScheduleOptions } = useSelector((state) => state.workSchedules);
  const { hasCreatePermission } = useAuth();
  const canWrite = hasCreatePermission(PermissionEntity.SETTINGS);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [criteria, setCriteria] = useState<SearchCriteria>({
    filterFields: [],
    pageSize: 10,
    pageNum: 0,
    direction: 'DESC'
  });
  const [currentPolicy, setCurrentPolicy] = useState<SlaPolicy>();
  const [openEdit, setOpenEdit] = useState<boolean>(false);
  const [openDelete, setOpenDelete] = useState<boolean>(false);
  const [preview, setPreview] = useState<{
    firstResponseMinutes: number | null;
    resolutionMinutes: number | null;
  }>({ firstResponseMinutes: null, resolutionMinutes: null });

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
    prefix: 'slaPolicies',
    initialSorting: [],
    initialPagination: {
      pageSize: criteria.pageSize,
      pageIndex: criteria.pageNum
    },
    setCriteria,
    fieldMapping
  });

  const onQueryChange = (event) => {
    onSearchQueryChange<SlaPolicy>(event, criteria, setCriteria, ['name']);
  };
  const debouncedQueryChange = useMemo(() => debounce(onQueryChange, 1300), []);

  useEffect(() => {
    dispatch(getSlaPolicies(criteria)).catch((err) =>
      showSnackBar(getErrorMessage(err, t('sla_load_failure')), 'error')
    );
  }, [criteria]);

  useEffect(() => {
    if (canWrite) {
      dispatch(getServiceContractOptions()).catch(() => null);
      dispatch(getWorkScheduleOptions()).catch(() => null);
    }
  }, [canWrite]);

  const handleFormChange = ({ field, e }: { field: string; e: unknown }) => {
    if (field === 'firstResponseMinutes' || field === 'resolutionMinutes') {
      setPreview((prev) => ({ ...prev, [field]: toNumberOrNull(e) }));
    }
  };

  const resetPreview = (policy?: SlaPolicy) =>
    setPreview({
      firstResponseMinutes: policy?.firstResponseMinutes ?? null,
      resolutionMinutes: policy?.resolutionMinutes ?? null
    });

  const priorityItems = SLA_PRIORITIES.map((priority) => ({
    label: t(priority),
    value: priority
  }));
  const contractItems = contractOptions.map((contract) => ({
    label: contract.contractNumber
      ? `${contract.contractNumber} - ${contract.name}`
      : contract.name,
    value: contract.id
  }));
  const scheduleItems = workScheduleOptions.map((schedule) => ({
    label: schedule.defaultSchedule
      ? `${schedule.name} (${t('work_schedule_default')})`
      : schedule.name,
    value: schedule.id
  }));

  const fields: Array<IField> = [
    {
      name: 'name',
      type: 'text',
      label: t('name'),
      placeholder: t('name'),
      required: true,
      midWidth: true
    },
    {
      name: 'priority',
      type: 'select',
      label: t('priority'),
      items: priorityItems,
      required: true,
      midWidth: true
    },
    {
      name: 'firstResponseMinutes',
      type: 'number',
      label: t('sla_first_response_minutes'),
      placeholder: t('sla_first_response_minutes'),
      helperText: formatMinutes(preview.firstResponseMinutes),
      required: true,
      midWidth: true
    },
    {
      name: 'resolutionMinutes',
      type: 'number',
      label: t('sla_resolution_minutes'),
      placeholder: t('sla_resolution_minutes'),
      helperText: formatMinutes(preview.resolutionMinutes),
      required: true,
      midWidth: true
    },
    {
      name: 'penaltyPercent',
      type: 'number',
      label: t('sla_penalty_percent'),
      placeholder: t('sla_penalty_percent')
    },
    {
      name: 'serviceContract',
      type: 'select',
      label: t('sla_contract_optional'),
      placeholder: t('sla_general_policy'),
      items: contractItems
    },
    {
      name: 'useBusinessHours',
      type: 'checkbox',
      label: t('sla_business_hours_only'),
      relatedFields: [{ field: 'workSchedule', value: false, hide: true }]
    },
    {
      name: 'workSchedule',
      type: 'select',
      label: t('sla_business_hours_schedule'),
      placeholder: t('sla_business_hours_schedule_empty'),
      items: scheduleItems
    }
  ];

  const shape = {
    name: Yup.string().required(t('required_name')),
    priority: Yup.object().nullable().required(t('sla_required_priority')),
    firstResponseMinutes: Yup.number()
      .typeError(t('contracts_invalid_number'))
      .integer(t('contracts_invalid_number'))
      .min(1, t('contracts_invalid_number'))
      .required(t('required')),
    resolutionMinutes: Yup.number()
      .typeError(t('contracts_invalid_number'))
      .integer(t('contracts_invalid_number'))
      .min(1, t('contracts_invalid_number'))
      .required(t('required')),
    penaltyPercent: Yup.number()
      .transform((value, original) => (original === '' ? undefined : value))
      .typeError(t('contracts_invalid_number'))
      .min(0, t('sla_invalid_percent'))
      .max(100, t('sla_invalid_percent'))
      .nullable()
  };

  const formatValues = (values, isEdit: boolean) => {
    const useBusinessHours = !!values.useBusinessHours;
    return {
      name: values.name,
      priority: values.priority?.value,
      firstResponseMinutes: toNumberOrNull(values.firstResponseMinutes),
      resolutionMinutes: toNumberOrNull(values.resolutionMinutes),
      penaltyPercent: toNumberOrNull(values.penaltyPercent) ?? 0,
      serviceContract: formatSelect(values.serviceContract),
      useBusinessHours,
      // empty = assignee's schedule or the default one; an object without id
      // clears the relation when editing
      workSchedule:
        (useBusinessHours ? formatSelect(values.workSchedule) : null) ??
        (isEdit ? {} : null)
    };
  };

  const getFormValues = (policy?: SlaPolicy) => {
    const priority = policy?.priority ?? 'MEDIUM';
    return {
      name: policy?.name ?? '',
      priority: { label: t(priority), value: priority },
      firstResponseMinutes: policy?.firstResponseMinutes ?? '',
      resolutionMinutes: policy?.resolutionMinutes ?? '',
      penaltyPercent: policy?.penaltyPercent ?? 0,
      serviceContract: policy?.serviceContract
        ? {
            label: policy.serviceContract.name,
            value: policy.serviceContract.id
          }
        : null,
      useBusinessHours: policy?.useBusinessHours ?? false,
      workSchedule: policy?.workSchedule?.id
        ? {
            label: policy.workSchedule.name ?? String(policy.workSchedule.id),
            value: policy.workSchedule.id
          }
        : null
    };
  };

  const handleDelete = (id: number) => {
    setOpenDelete(false);
    dispatch(deleteSlaPolicy(id))
      .then(() => showSnackBar(t('sla_delete_success'), 'success'))
      .catch((err) =>
        showSnackBar(getErrorMessage(err, t('sla_delete_failure')), 'error')
      );
  };

  const columnHelper = createColumnHelper<SlaPolicy>();

  const columns: CustomDatagridColumn2<SlaPolicy>[] = [
    columnHelper.accessor('name', {
      id: 'name',
      header: () => t('name'),
      cell: (info) => <Box sx={{ fontWeight: 'bold' }}>{info.getValue()}</Box>,
      size: 200
    }),
    columnHelper.accessor('priority', {
      id: 'priority',
      header: () => t('priority'),
      cell: (info) => (info.getValue() ? t(info.getValue()) : ''),
      size: 120
    }),
    columnHelper.accessor('firstResponseMinutes', {
      id: 'firstResponseMinutes',
      header: () => t('sla_first_response'),
      cell: (info) => formatMinutes(info.getValue()),
      size: 150
    }),
    columnHelper.accessor('resolutionMinutes', {
      id: 'resolutionMinutes',
      header: () => t('sla_resolution'),
      cell: (info) => formatMinutes(info.getValue()),
      size: 150
    }),
    columnHelper.accessor('penaltyPercent', {
      id: 'penaltyPercent',
      header: () => t('sla_penalty_percent'),
      cell: (info) => `${info.getValue() ?? 0}%`,
      size: 120
    }),
    columnHelper.accessor((row) => row.serviceContract?.name, {
      id: 'serviceContract',
      header: () => t('sla_contract'),
      cell: (info) => info.getValue() || t('sla_general_policy'),
      size: 200
    }),
    columnHelper.accessor('useBusinessHours', {
      id: 'useBusinessHours',
      header: () => t('sla_business_hours_column'),
      cell: (info) => {
        const policy = info.row.original;
        return info.getValue() ? (
          <Box>
            <Chip
              size="small"
              color="info"
              label={t('sla_business_hours_short')}
            />
            <Typography variant="caption" display="block">
              {policy.workSchedule?.name ?? t('sla_business_hours_auto')}
            </Typography>
          </Box>
        ) : (
          t('sla_calendar_hours')
        );
      },
      size: 180
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
        TransitionProps={{ onEnter: () => resetPreview() }}
      >
        <DialogTitle sx={{ p: 3 }}>
          <Typography variant="h4" gutterBottom>
            {t('sla_add')}
          </Typography>
        </DialogTitle>
        <DialogContent dividers sx={{ p: 3 }}>
          <Form
            fields={fields}
            validation={Yup.object().shape(shape)}
            submitText={t('add')}
            values={getFormValues()}
            onChange={handleFormChange}
            onSubmit={async (values) =>
              dispatch(addSlaPolicy(formatValues(values, false)))
                .then(() => {
                  handleCloseModal();
                  showSnackBar(t('sla_create_success'), 'success');
                })
                .catch((err) =>
                  showSnackBar(
                    getErrorMessage(err, t('sla_create_failure')),
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
              {t('sla_edit')}
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
          {currentPolicy && (
            <Form
              fields={fields}
              validation={Yup.object().shape(shape)}
              submitText={t('save')}
              values={getFormValues(currentPolicy)}
              onChange={handleFormChange}
              onSubmit={async (values) =>
                dispatch(
                  editSlaPolicy(currentPolicy.id, formatValues(values, true))
                )
                  .then(() => {
                    setOpenEdit(false);
                    showSnackBar(t('changes_saved_success'), 'success');
                  })
                  .catch((err) =>
                    showSnackBar(
                      getErrorMessage(err, t('sla_edit_failure')),
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
          data={slaPolicies.content}
          loading={loadingGet}
          pagination={pagination}
          onPaginationChange={setPagination}
          totalRows={slaPolicies.totalElements}
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
            setCurrentPolicy(row);
            resetPreview(row);
            setOpenEdit(true);
          }}
          noRowsMessage={t('sla_no_rows')}
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
        onConfirm={() => handleDelete(currentPolicy?.id)}
        confirmText={t('to_delete')}
        question={t('sla_confirm_delete')}
      />
    </Box>
  );
};

export default SlaPolicies;
