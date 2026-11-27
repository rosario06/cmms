import {
  Autocomplete,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Stack,
  Switch,
  TextField,
  Typography
} from '@mui/material';
import { Close } from '@mui/icons-material';
import { useContext, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDispatch } from '../../../store';
import { WorkSchedule } from '../../../models/owns/workSchedule';
import {
  addWorkSchedule,
  editWorkSchedule
} from '../../../slices/workSchedule';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import ShiftsEditor from './ShiftsEditor';
import {
  DayRanges,
  dayRangesToShifts,
  defaultDayRanges,
  getBrowserTimeZone,
  getOverlappingDays,
  getScheduleErrorMessage,
  getTimeZones,
  hasEmptyTimes,
  shiftsToDayRanges
} from './utils';

interface PropsType {
  open: boolean;
  /** undefined/null = create */
  schedule?: WorkSchedule | null;
  onClose: () => void;
  onRequestDelete?: () => void;
}

const WorkScheduleDialog = ({
  open,
  schedule,
  onClose,
  onRequestDelete
}: PropsType) => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const timeZones = useMemo(() => getTimeZones(), []);
  const [name, setName] = useState<string>('');
  const [timezone, setTimezone] = useState<string>('');
  const [defaultSchedule, setDefaultSchedule] = useState<boolean>(false);
  const [active, setActive] = useState<boolean>(true);
  const [days, setDays] = useState<DayRanges>(defaultDayRanges());
  const [submitted, setSubmitted] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);
  const isEdit = !!schedule;

  useEffect(() => {
    if (!open) return;
    setSubmitted(false);
    setSaving(false);
    if (schedule) {
      setName(schedule.name ?? '');
      setTimezone(schedule.timezone ?? '');
      setDefaultSchedule(!!schedule.defaultSchedule);
      setActive(schedule.active ?? true);
      setDays(shiftsToDayRanges(schedule.shifts));
    } else {
      setName('');
      setTimezone(getBrowserTimeZone());
      setDefaultSchedule(false);
      setActive(true);
      setDays(defaultDayRanges());
    }
  }, [open, schedule]);

  const overlappingDays = useMemo(() => getOverlappingDays(days), [days]);
  const emptyTimes = hasEmptyTimes(days);
  const nameError = submitted && !name.trim();
  const timezoneError = submitted && !timezone;

  const handleSubmit = async () => {
    setSubmitted(true);
    if (!name.trim() || !timezone) return;
    if (emptyTimes) {
      showSnackBar(t('work_schedule_required_times'), 'error');
      return;
    }
    if (overlappingDays.length) {
      showSnackBar(t('work_schedule_overlap'), 'error');
      return;
    }
    const payload = {
      name: name.trim(),
      timezone,
      defaultSchedule,
      active,
      shifts: dayRangesToShifts(days)
    };
    setSaving(true);
    try {
      if (schedule) {
        await dispatch(editWorkSchedule(schedule.id, payload));
        showSnackBar(t('changes_saved_success'), 'success');
      } else {
        await dispatch(addWorkSchedule(payload));
        showSnackBar(t('work_schedule_create_success'), 'success');
      }
      onClose();
    } catch (err) {
      showSnackBar(
        getScheduleErrorMessage(
          err,
          t,
          t(schedule ? 'work_schedule_edit_failure' : 'work_schedule_create_failure')
        ),
        'error'
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog fullWidth maxWidth="md" open={open} onClose={onClose}>
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
            {isEdit ? t('work_schedule_edit') : t('work_schedule_add')}
          </Typography>
          {isEdit && onRequestDelete && (
            <Typography
              onClick={onRequestDelete}
              variant="subtitle1"
              style={{ cursor: 'pointer' }}
            >
              {t('to_delete')}
            </Typography>
          )}
        </Box>
        <IconButton
          aria-label="close"
          onClick={onClose}
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
          <Autocomplete
            fullWidth
            disableClearable
            options={
              !timezone || timeZones.includes(timezone)
                ? timeZones
                : [timezone, ...timeZones]
            }
            value={timezone || null}
            disabled={saving}
            onChange={(_event, newValue) => setTimezone(newValue ?? '')}
            renderInput={(params) => (
              <TextField
                {...params}
                required
                label={t('time_zone')}
                error={timezoneError}
                helperText={timezoneError ? t('work_schedule_required_timezone') : ''}
              />
            )}
          />
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <FormControlLabel
              control={
                <Switch
                  checked={defaultSchedule}
                  disabled={saving}
                  onChange={(event) => setDefaultSchedule(event.target.checked)}
                />
              }
              label={t('work_schedule_default')}
            />
            <FormControlLabel
              control={
                <Switch
                  checked={active}
                  disabled={saving}
                  onChange={(event) => setActive(event.target.checked)}
                />
              }
              label={t('active')}
            />
          </Stack>
          {defaultSchedule && (
            <Typography variant="caption" color="text.secondary">
              {t('work_schedule_default_hint')}
            </Typography>
          )}
          <ShiftsEditor
            value={days}
            onChange={setDays}
            disabled={saving}
            overlappingDays={overlappingDays}
            showEmptyErrors={submitted}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <Button onClick={onClose} disabled={saving}>
          {t('cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={saving}
          startIcon={saving ? <CircularProgress size="1rem" /> : null}
        >
          {isEdit ? t('save') : t('add')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default WorkScheduleDialog;
