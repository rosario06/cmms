import { Box, Chip, debounce, Stack } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useContext, useEffect, useMemo, useState } from 'react';
import { createColumnHelper } from '@tanstack/react-table';
import CustomDatagrid2, {
  CustomDatagridColumn2
} from '../components/CustomDatagrid2';
import ConfirmDialog from '../components/ConfirmDialog';
import SearchInput from '../components/SearchInput';
import useTableState from '../../../hooks/useTableState';
import useAuth from '../../../hooks/useAuth';
import { useDispatch, useSelector } from '../../../store';
import { PermissionEntity } from '../../../models/owns/role';
import { WorkSchedule } from '../../../models/owns/workSchedule';
import { SearchCriteria } from '../../../models/owns/page';
import {
  deleteWorkSchedule,
  getWorkSchedules
} from '../../../slices/workSchedule';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { getErrorMessage } from '../../../utils/api';
import { onSearchQueryChange } from '../../../utils/overall';
import WorkScheduleDialog from './WorkScheduleDialog';
import { getScheduleErrorMessage, summarizeShifts } from './utils';

interface PropsType {
  openModal: boolean;
  handleCloseModal: () => void;
}

const fieldMapping: Record<string, string> = {
  name: 'name',
  timezone: 'timezone',
  defaultSchedule: 'defaultSchedule',
  active: 'active'
};

const Schedules = ({ openModal, handleCloseModal }: PropsType) => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { workSchedules, loadingGet } = useSelector(
    (state) => state.workSchedules
  );
  const { hasCreatePermission } = useAuth();
  const canWrite = hasCreatePermission(PermissionEntity.SETTINGS);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const [criteria, setCriteria] = useState<SearchCriteria>({
    filterFields: [],
    pageSize: 10,
    pageNum: 0,
    direction: 'DESC'
  });
  const [currentSchedule, setCurrentSchedule] = useState<WorkSchedule>();
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
    prefix: 'workSchedules',
    initialSorting: [],
    initialPagination: {
      pageSize: criteria.pageSize,
      pageIndex: criteria.pageNum
    },
    setCriteria,
    fieldMapping
  });

  const onQueryChange = (event) => {
    onSearchQueryChange<WorkSchedule>(event, criteria, setCriteria, ['name']);
  };
  const debouncedQueryChange = useMemo(() => debounce(onQueryChange, 1300), []);

  useEffect(() => {
    dispatch(getWorkSchedules(criteria)).catch((err) =>
      showSnackBar(
        getErrorMessage(err, t('work_schedule_load_failure')),
        'error'
      )
    );
  }, [criteria]);

  const handleDelete = (id: number) => {
    setOpenDelete(false);
    dispatch(deleteWorkSchedule(id))
      .then(() => showSnackBar(t('work_schedule_delete_success'), 'success'))
      .catch((err) =>
        showSnackBar(
          getScheduleErrorMessage(err, t, t('work_schedule_delete_failure')),
          'error'
        )
      );
  };

  const columnHelper = createColumnHelper<WorkSchedule>();

  const columns: CustomDatagridColumn2<WorkSchedule>[] = [
    columnHelper.accessor('name', {
      id: 'name',
      header: () => t('name'),
      cell: (info) => <Box sx={{ fontWeight: 'bold' }}>{info.getValue()}</Box>,
      size: 200
    }),
    columnHelper.accessor('timezone', {
      id: 'timezone',
      header: () => t('time_zone'),
      cell: (info) => info.getValue() || '',
      size: 200
    }),
    columnHelper.accessor('defaultSchedule', {
      id: 'defaultSchedule',
      header: () => t('work_schedule_default'),
      cell: (info) =>
        info.getValue() ? (
          <Chip size="small" color="primary" label={t('yes')} />
        ) : (
          t('no')
        ),
      size: 140
    }),
    columnHelper.accessor('active', {
      id: 'active',
      header: () => t('active'),
      cell: (info) => (
        <Chip
          size="small"
          color={info.getValue() ? 'success' : 'default'}
          label={info.getValue() ? t('yes') : t('no')}
        />
      ),
      size: 100
    }),
    columnHelper.accessor((row) => summarizeShifts(row.shifts, t), {
      id: 'shifts',
      header: () => t('work_schedule_shifts'),
      cell: (info) => info.getValue() || t('work_schedule_no_shifts'),
      size: 340,
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
      <WorkScheduleDialog
        open={openModal}
        schedule={null}
        onClose={handleCloseModal}
      />
      <WorkScheduleDialog
        open={openEdit}
        schedule={currentSchedule}
        onClose={() => setOpenEdit(false)}
        onRequestDelete={() => {
          setOpenEdit(false);
          setOpenDelete(true);
        }}
      />
      <Stack direction="row" width="95%">
        <Box sx={{ my: 0.5 }}>
          <SearchInput onChange={debouncedQueryChange} />
        </Box>
      </Stack>
      <Box sx={{ width: '95%' }}>
        <CustomDatagrid2
          columns={columns}
          data={workSchedules.content}
          loading={loadingGet}
          pagination={pagination}
          onPaginationChange={setPagination}
          totalRows={workSchedules.totalElements}
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
            setCurrentSchedule(row);
            setOpenEdit(true);
          }}
          noRowsMessage={t('work_schedule_no_rows')}
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
        onConfirm={() => handleDelete(currentSchedule?.id)}
        confirmText={t('to_delete')}
        question={t('work_schedule_confirm_delete')}
      />
    </Box>
  );
};

export default Schedules;
