import {
  WORK_SCHEDULE_DAYS,
  WorkShift
} from '../../../models/owns/workSchedule';

/** Translation keys for each day number (1 = Monday ... 7 = Sunday). */
export const DAY_KEYS: Record<number, string> = {
  1: 'monday',
  2: 'tuesday',
  3: 'wednesday',
  4: 'thursday',
  5: 'friday',
  6: 'saturday',
  7: 'sunday'
};

/** Days that count as working days for the "copy to weekdays" action. */
export const WEEKDAYS: number[] = [1, 2, 3, 4, 5];

export interface ShiftRange {
  start: string;
  end: string;
}

/** Editable representation: day number -> list of ranges. */
export type DayRanges = Record<number, ShiftRange[]>;

const FALLBACK_TIME_ZONES = [
  'UTC',
  'America/Santo_Domingo',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Mexico_City',
  'America/Bogota',
  'America/Lima',
  'America/Caracas',
  'America/Santiago',
  'America/Argentina/Buenos_Aires',
  'America/Sao_Paulo',
  'Europe/London',
  'Europe/Madrid',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/Rome',
  'Africa/Casablanca',
  'Africa/Johannesburg',
  'Asia/Dubai',
  'Asia/Kolkata',
  'Asia/Shanghai',
  'Asia/Tokyo',
  'Australia/Sydney'
];

type IntlWithSupportedValues = typeof Intl & {
  supportedValuesOf?: (key: string) => string[];
};

/**
 * IANA time zones. `Intl.supportedValuesOf` is not typed by the TS version of
 * the project, hence the safe cast; falls back to a short list.
 */
export const getTimeZones = (): string[] => {
  try {
    const supported = (Intl as IntlWithSupportedValues).supportedValuesOf;
    const zones =
      typeof supported === 'function' ? supported('timeZone') : undefined;
    if (zones?.length) {
      return zones.includes('UTC') ? zones : ['UTC', ...zones];
    }
  } catch {
    // fall through to the fallback list
  }
  return FALLBACK_TIME_ZONES;
};

export const getBrowserTimeZone = (): string => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
};

/** Normalizes "HH:mm:ss" (or "HH:mm") to "HH:mm". */
export const normalizeTime = (value: string | null | undefined): string =>
  value ? value.slice(0, 5) : '';

const toMinutes = (value: string): number => {
  const [hours, minutes] = value.split(':').map(Number);
  return hours * 60 + minutes;
};

/** A shift crosses midnight when the end is not after the start. */
export const crossesMidnight = (start: string, end: string): boolean =>
  !!start && !!end && toMinutes(end) <= toMinutes(start);

export const emptyDayRanges = (): DayRanges =>
  WORK_SCHEDULE_DAYS.reduce((acc, day) => {
    acc[day] = [];
    return acc;
  }, {} as DayRanges);

export const defaultDayRanges = (): DayRanges => {
  const days = emptyDayRanges();
  WEEKDAYS.forEach((day) => {
    days[day] = [{ start: '08:00', end: '17:00' }];
  });
  return days;
};

export const shiftsToDayRanges = (
  shifts: WorkShift[] | null | undefined
): DayRanges => {
  const days = emptyDayRanges();
  (shifts ?? []).forEach((shift) => {
    if (days[shift.dayOfWeek]) {
      days[shift.dayOfWeek].push({
        start: normalizeTime(shift.startTime),
        end: normalizeTime(shift.endTime)
      });
    }
  });
  WORK_SCHEDULE_DAYS.forEach((day) => {
    days[day].sort((a, b) => a.start.localeCompare(b.start));
  });
  return days;
};

export const dayRangesToShifts = (days: DayRanges): WorkShift[] =>
  WORK_SCHEDULE_DAYS.flatMap((day) =>
    days[day].map((range) => ({
      dayOfWeek: day,
      startTime: range.start,
      endTime: range.end
    }))
  );

export const hasEmptyTimes = (days: DayRanges): boolean =>
  WORK_SCHEDULE_DAYS.some((day) =>
    days[day].some((range) => !range.start || !range.end)
  );

/**
 * Days involved in an overlap. Works on the weekly timeline, so a night shift
 * that spills into the next day also collides with that day's ranges
 * (Sunday spills into Monday).
 */
export const getOverlappingDays = (days: DayRanges): number[] => {
  const week = 7 * 1440;
  const intervals: { day: number; from: number; to: number }[] = [];
  WORK_SCHEDULE_DAYS.forEach((day) => {
    days[day].forEach((range) => {
      if (!range.start || !range.end) return;
      const from = (day - 1) * 1440 + toMinutes(range.start);
      const endMinutes = toMinutes(range.end);
      const startMinutes = toMinutes(range.start);
      const to =
        (day - 1) * 1440 +
        (endMinutes > startMinutes ? endMinutes : endMinutes + 1440);
      if (to > week) {
        intervals.push({ day, from, to: week });
        intervals.push({ day, from: 0, to: to - week });
      } else {
        intervals.push({ day, from, to });
      }
    });
  });
  const overlapping = new Set<number>();
  for (let i = 0; i < intervals.length; i += 1) {
    for (let j = i + 1; j < intervals.length; j += 1) {
      const a = intervals[i];
      const b = intervals[j];
      if (a.from < b.to && b.from < a.to) {
        overlapping.add(a.day);
        overlapping.add(b.day);
      }
    }
  }
  return Array.from(overlapping).sort((a, b) => a - b);
};

/**
 * Compact human readable summary, e.g. "Mon-Fri 08:00-17:00 | Sat 08:00-12:00".
 * Consecutive days with identical ranges are grouped.
 */
export const summarizeShifts = (
  shifts: WorkShift[] | null | undefined,
  t: (key: string) => string
): string => {
  const days = shiftsToDayRanges(shifts);
  const shortName = (day: number) => t(DAY_KEYS[day]).slice(0, 3);
  const describe = (day: number) =>
    days[day]
      .map(
        (range) =>
          `${range.start}-${range.end}${
            crossesMidnight(range.start, range.end) ? ' (+1)' : ''
          }`
      )
      .join(', ');

  const groups: { from: number; to: number; text: string }[] = [];
  WORK_SCHEDULE_DAYS.forEach((day) => {
    const text = describe(day);
    if (!text) return;
    const last = groups[groups.length - 1];
    if (last && last.to === day - 1 && last.text === text) {
      last.to = day;
    } else {
      groups.push({ from: day, to: day, text });
    }
  });
  return groups
    .map((group) =>
      group.from === group.to
        ? `${shortName(group.from)} ${group.text}`
        : `${shortName(group.from)}-${shortName(group.to)} ${group.text}`
    )
    .join(' | ');
};

/** Translated message for the HTTP errors of schedules and holidays. */
export const getScheduleErrorMessage = (
  error: any,
  t: (key: string) => string,
  fallback: string,
  kind: 'schedule' | 'holiday' = 'schedule'
): string => {
  switch (error?.status) {
    case 400:
      return t(
        kind === 'schedule'
          ? 'work_schedule_invalid_data'
          : 'holiday_invalid_data'
      );
    case 403:
      return t('work_schedule_no_permission');
    case 404:
      return t('work_schedule_not_found');
    case 409:
      return t(
        kind === 'schedule' ? 'work_schedule_conflict' : 'holiday_conflict'
      );
    default:
      return fallback;
  }
};
