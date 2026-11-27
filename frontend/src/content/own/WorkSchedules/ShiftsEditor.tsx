import {
  Alert,
  Box,
  Button,
  Chip,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import ContentCopyTwoToneIcon from '@mui/icons-material/ContentCopyTwoTone';
import { useTranslation } from 'react-i18next';
import { WORK_SCHEDULE_DAYS } from '../../../models/owns/workSchedule';
import {
  crossesMidnight,
  DAY_KEYS,
  DayRanges,
  ShiftRange,
  WEEKDAYS
} from './utils';

interface PropsType {
  value: DayRanges;
  onChange: (value: DayRanges) => void;
  disabled?: boolean;
  /** Days whose ranges overlap (highlighted). */
  overlappingDays?: number[];
  /** Highlights empty time fields. */
  showEmptyErrors?: boolean;
}

const ShiftsEditor = ({
  value,
  onChange,
  disabled,
  overlappingDays = [],
  showEmptyErrors
}: PropsType) => {
  const { t }: { t: any } = useTranslation();

  const setDay = (day: number, ranges: ShiftRange[]) =>
    onChange({ ...value, [day]: ranges });

  const updateRange = (
    day: number,
    index: number,
    field: keyof ShiftRange,
    time: string
  ) =>
    setDay(
      day,
      value[day].map((range, i) =>
        i === index ? { ...range, [field]: time } : range
      )
    );

  const addRange = (day: number) => {
    const last = value[day][value[day].length - 1];
    setDay(day, [
      ...value[day],
      last?.end ? { start: last.end, end: '' } : { start: '08:00', end: '17:00' }
    ]);
  };

  const removeRange = (day: number, index: number) =>
    setDay(
      day,
      value[day].filter((_, i) => i !== index)
    );

  const copyToWeekdays = (fromDay: number) => {
    const next = { ...value };
    WEEKDAYS.forEach((day) => {
      next[day] = value[fromDay].map((range) => ({ ...range }));
    });
    onChange(next);
  };

  return (
    <Stack spacing={1.5}>
      <Typography variant="h5">{t('work_schedule_shifts')}</Typography>
      <Typography variant="body2" color="text.secondary">
        {t('work_schedule_shifts_hint')}
      </Typography>
      {WORK_SCHEDULE_DAYS.map((day) => {
        const ranges = value[day] ?? [];
        const overlaps = overlappingDays.includes(day);
        return (
          <Box
            key={day}
            sx={{
              display: 'flex',
              flexDirection: { xs: 'column', md: 'row' },
              alignItems: { md: 'flex-start' },
              gap: 1,
              p: 1,
              borderRadius: 1,
              border: 1,
              borderColor: overlaps ? 'error.main' : 'divider'
            }}
          >
            <Box sx={{ width: 120, pt: 1 }}>
              <Typography fontWeight="bold">{t(DAY_KEYS[day])}</Typography>
              {!ranges.length && (
                <Typography variant="caption" color="text.secondary">
                  {t('work_schedule_day_off')}
                </Typography>
              )}
            </Box>
            <Stack spacing={1} sx={{ flexGrow: 1 }}>
              {ranges.map((range, index) => (
                <Stack
                  // eslint-disable-next-line react/no-array-index-key
                  key={index}
                  direction="row"
                  spacing={1}
                  alignItems="center"
                  flexWrap="wrap"
                >
                  <TextField
                    size="small"
                    type="time"
                    label={t('work_schedule_start')}
                    value={range.start}
                    disabled={disabled}
                    error={showEmptyErrors && !range.start}
                    onChange={(event) =>
                      updateRange(day, index, 'start', event.target.value)
                    }
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ step: 60 }}
                    sx={{ width: 140 }}
                  />
                  <TextField
                    size="small"
                    type="time"
                    label={t('work_schedule_end')}
                    value={range.end}
                    disabled={disabled}
                    error={showEmptyErrors && !range.end}
                    onChange={(event) =>
                      updateRange(day, index, 'end', event.target.value)
                    }
                    InputLabelProps={{ shrink: true }}
                    inputProps={{ step: 60 }}
                    sx={{ width: 140 }}
                  />
                  {crossesMidnight(range.start, range.end) && (
                    <Chip
                      size="small"
                      color="info"
                      label={t('work_schedule_crosses_midnight')}
                    />
                  )}
                  <Tooltip title={t('work_schedule_remove_range')}>
                    <span>
                      <IconButton
                        size="small"
                        color="error"
                        disabled={disabled}
                        onClick={() => removeRange(day, index)}
                      >
                        <DeleteTwoToneIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </Stack>
              ))}
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  startIcon={<AddTwoToneIcon />}
                  disabled={disabled}
                  onClick={() => addRange(day)}
                >
                  {t('work_schedule_add_range')}
                </Button>
                {ranges.length > 0 && (
                  <Button
                    size="small"
                    startIcon={<ContentCopyTwoToneIcon />}
                    disabled={disabled}
                    onClick={() => copyToWeekdays(day)}
                  >
                    {t('work_schedule_copy_to_weekdays')}
                  </Button>
                )}
              </Stack>
              {overlaps && (
                <Typography variant="caption" color="error">
                  {t('work_schedule_overlap_day')}
                </Typography>
              )}
            </Stack>
          </Box>
        );
      })}
      {overlappingDays.length > 0 && (
        <Alert severity="error">{t('work_schedule_overlap')}</Alert>
      )}
    </Stack>
  );
};

export default ShiftsEditor;
