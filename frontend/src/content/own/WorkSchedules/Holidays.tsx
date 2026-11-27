import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  LinearProgress,
  MenuItem,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import { Close } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useContext, useEffect, useMemo, useState } from 'react';
import ConfirmDialog from '../components/ConfirmDialog';
import useAuth from '../../../hooks/useAuth';
import { useDispatch, useSelector } from '../../../store';
import { PermissionEntity } from '../../../models/owns/role';
import { Holiday } from '../../../models/owns/holiday';
import { SearchCriteria } from '../../../models/owns/page';
import {
  addHoliday,
  deleteHoliday,
  editHoliday,
  getHolidays
} from '../../../slices/holiday';
import { getWorkScheduleOptions } from '../../../slices/workSchedule';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { getErrorMessage } from '../../../utils/api';
import { getScheduleErrorMessage } from './utils';

interface PropsType {
  openModal: boolean;
  handleCloseModal: () => void;
}

const ALL_SCHEDULES = '';

const holidayCriteria: SearchCriteria = {
  filterFields: [],
  pageSize: 500,
  pageNum: 0,
  direction: 'ASC',
  sortField: 'date'
};

/** Month and day ("MM-DD") of a "YYYY-MM-DD" date. */
const monthDay = (date: string): string => (date ?? '').slice(5, 10);

const Holidays = ({ openModal, handleCloseModal }: PropsType) => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { holidays, loadingGet } = useSelector((state) => state.holidays);
  const { workScheduleOptions } = useSelector((state) => state.workSchedules);
  const { hasCreatePermission, companySettings } = useAuth();
  const canWrite = hasCreatePermission(PermissionEntity.SETTINGS);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState<number>(currentYear);
  const [currentHoliday, setCurrentHoliday] = useState<Holiday>();
  const [openEdit, setOpenEdit] = useState<boolean>(false);
  const [openDelete, setOpenDelete] = useState<boolean>(false);

  // form state (shared by the create and the edit dialogs)
  const [name, setName] = useState<string>('');
  const [date, setDate] = useState<string>('');
  const [recurringAnnually, setRecurringAnnually] = useState<boolean>(false);
  const [scheduleId, setScheduleId] = useState<string>(ALL_SCHEDULES);
  const [submitted, setSubmitted] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);

  const dialogOpen = openModal || openEdit;
  const editing = openEdit && currentHoliday ? currentHoliday : null;

  useEffect(() => {
    dispatch(getHolidays(holidayCriteria)).catch((err) =>
      showSnackBar(getErrorMessage(err, t('holiday_load_failure')), 'error')
    );
  }, []);

  useEffect(() => {
    if (canWrite) dispatch(getWorkScheduleOptions()).catch(() => null);
  }, [canWrite]);

  useEffect(() => {
    if (!dialogOpen) return;
    setSubmitted(false);
    setSaving(false);
    if (editing) {
      setName(editing.name ?? '');
      setDate((editing.date ?? '').slice(0, 10));
      setRecurringAnnually(!!editing.recurringAnnually);
      setScheduleId(
        editing.workSchedule?.id
          ? String(editing.workSchedule.id)
          : ALL_SCHEDULES
      );
    } else {
      setName('');
      setDate('');
      setRecurringAnnually(false);
      setScheduleId(ALL_SCHEDULES);
    }
  }, [dialogOpen, editing]);

  const yearOptions = useMemo(() => {
    const years = new Set<number>();
    for (let y = currentYear - 1; y <= currentYear + 2; y += 1) years.add(y);
    holidays.content.forEach((holiday) => {
      const holidayYear = Number((holiday.date ?? '').slice(0, 4));
      if (holidayYear) years.add(holidayYear);
    });
    years.add(year);
    return Array.from(years).sort((a, b) => a - b);
  }, [holidays.content, currentYear, year]);

  const visibleHolidays = useMemo(
    () =>
      holidays.content
        .filter(
          (holiday) =>
            holiday.recurringAnnually ||
            Number((holiday.date ?? '').slice(0, 4)) === year
        )
        .sort((a, b) => monthDay(a.date).localeCompare(monthDay(b.date))),
    [holidays.content, year]
  );

  const formatDate = (holiday: Holiday): string => {
    // recurring holidays are shown in the selected year
    const [, month, day] = (holiday.date ?? '').slice(0, 10).split('-');
    const shownYear = holiday.recurringAnnually
      ? String(year)
      : (holiday.date ?? '').slice(0, 4);
    if (!month || !day) return '';
    return companySettings?.generalPreferences?.dateFormat === 'MMDDYY'
      ? `${month}/${day}/${shownYear}`
      : `${day}/${month}/${shownYear}`;
  };

  const closeDialog = () => {
    if (openEdit) setOpenEdit(false);
    else handleCloseModal();
  };

  const nameError = submitted && !name.trim();
  const dateError = submitted && !date;

  const handleSubmit = async () => {
    setSubmitted(true);
    if (!name.trim() || !date) return;
    let workSchedule: { id?: number } | null = null;
    if (scheduleId) workSchedule = { id: Number(scheduleId) };
    // an object without id clears the relation on edit
    else if (editing) workSchedule = {};
    const payload = {
      name: name.trim(),
      date,
      recurringAnnually,
      workSchedule
    };
    setSaving(true);
    try {
      if (editing) {
        await dispatch(editHoliday(editing.id, payload));
        showSnackBar(t('changes_saved_success'), 'success');
      } else {
        await dispatch(addHoliday(payload));
        showSnackBar(t('holiday_create_success'), 'success');
      }
      closeDialog();
    } catch (err) {
      showSnackBar(
        getScheduleErrorMessage(
          err,
          t,
          t(editing ? 'holiday_edit_failure' : 'holiday_create_failure'),
          'holiday'
        ),
        'error'
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = (id: number) => {
    setOpenDelete(false);
    dispatch(deleteHoliday(id))
      .then(() => showSnackBar(t('holiday_delete_success'), 'success'))
      .catch((err) =>
        showSnackBar(
          getScheduleErrorMessage(
            err,
            t,
            t('holiday_delete_failure'),
            'holiday'
          ),
          'error'
        )
      );
  };

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
      <Dialog fullWidth maxWidth="sm" open={dialogOpen} onClose={closeDialog}>
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
              {editing ? t('holiday_edit') : t('holiday_add')}
            </Typography>
            {editing && (
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
            )}
          </Box>
          <IconButton
            aria-label="close"
            onClick={closeDialog}
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
          <Stack spacing={2}>
            <TextField
              fullWidth
              required
              label={t('name')}
              value={name}
              disabled={saving}
              error={nameError}
              helperText={nameError ? t('required_name') : ''}
              onChange={(event) => setName(event.target.value)}
            />
            <TextField
              fullWidth
              required
              type="date"
              label={t('date')}
              value={date}
              disabled={saving}
              error={dateError}
              helperText={dateError ? t('holiday_required_date') : ''}
              InputLabelProps={{ shrink: true }}
              onChange={(event) => setDate(event.target.value)}
            />
            <FormControlLabel
              control={
                <Switch
                  checked={recurringAnnually}
                  disabled={saving}
                  onChange={(event) =>
                    setRecurringAnnually(event.target.checked)
                  }
                />
              }
              label={t('holiday_recurring')}
            />
            <TextField
              select
              fullWidth
              label={t('holiday_applies_to')}
              value={scheduleId}
              disabled={saving}
              onChange={(event) => setScheduleId(event.target.value)}
            >
              <MenuItem value={ALL_SCHEDULES}>
                {t('holiday_all_schedules')}
              </MenuItem>
              {workScheduleOptions.map((schedule) => (
                <MenuItem key={schedule.id} value={String(schedule.id)}>
                  {schedule.name}
                </MenuItem>
              ))}
              {/* keeps a schedule that is not in the list (e.g. inactive) selectable */}
              {scheduleId &&
                !workScheduleOptions.some(
                  (schedule) => String(schedule.id) === scheduleId
                ) && (
                  <MenuItem value={scheduleId}>
                    {editing?.workSchedule?.name ?? scheduleId}
                  </MenuItem>
                )}
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button onClick={closeDialog} disabled={saving}>
            {t('cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={saving}
            startIcon={saving ? <CircularProgress size="1rem" /> : null}
          >
            {editing ? t('save') : t('add')}
          </Button>
        </DialogActions>
      </Dialog>
      <Stack direction="row" width="95%" sx={{ my: 1 }}>
        <TextField
          select
          size="small"
          label={t('holiday_year')}
          value={year}
          onChange={(event) => setYear(Number(event.target.value))}
          sx={{ minWidth: 140 }}
        >
          {yearOptions.map((option) => (
            <MenuItem key={option} value={option}>
              {option}
            </MenuItem>
          ))}
        </TextField>
      </Stack>
      <Box sx={{ width: '95%' }}>
        {loadingGet && <LinearProgress />}
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('name')}</TableCell>
                <TableCell>{t('date')}</TableCell>
                <TableCell>{t('holiday_recurring_short')}</TableCell>
                <TableCell>{t('holiday_applies_to')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {visibleHolidays.map((holiday) => (
                <TableRow
                  key={holiday.id}
                  hover={canWrite}
                  sx={{ cursor: canWrite ? 'pointer' : 'default' }}
                  onClick={() => {
                    if (!canWrite) return;
                    setCurrentHoliday(holiday);
                    setOpenEdit(true);
                  }}
                >
                  <TableCell sx={{ fontWeight: 'bold' }}>
                    {holiday.name}
                  </TableCell>
                  <TableCell>{formatDate(holiday)}</TableCell>
                  <TableCell>
                    {holiday.recurringAnnually ? (
                      <Chip size="small" color="primary" label={t('yes')} />
                    ) : (
                      t('no')
                    )}
                  </TableCell>
                  <TableCell>
                    {holiday.workSchedule?.name ?? t('holiday_all_schedules')}
                  </TableCell>
                </TableRow>
              ))}
              {!visibleHolidays.length && !loadingGet && (
                <TableRow>
                  <TableCell colSpan={4} align="center">
                    {t('holiday_no_rows')}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
      <ConfirmDialog
        open={openDelete}
        onCancel={() => {
          setOpenDelete(false);
          setOpenEdit(true);
        }}
        onConfirm={() => handleDelete(currentHoliday?.id)}
        confirmText={t('to_delete')}
        question={t('holiday_confirm_delete')}
      />
    </Box>
  );
};

export default Holidays;
