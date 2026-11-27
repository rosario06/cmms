import { Audit } from './audit';

/** 1 = Monday ... 7 = Sunday */
export type DayOfWeekNumber = 1 | 2 | 3 | 4 | 5 | 6 | 7;

export const WORK_SCHEDULE_DAYS: DayOfWeekNumber[] = [1, 2, 3, 4, 5, 6, 7];

export interface WorkShift {
  id?: number;
  name?: string;
  dayOfWeek: number;
  /** "HH:mm" */
  startTime: string;
  /** "HH:mm". If it is <= startTime the shift crosses midnight. */
  endTime: string;
}

export interface WorkSchedule extends Audit {
  id: number;
  name: string;
  /** IANA time zone, e.g. "America/Santo_Domingo" */
  timezone: string;
  defaultSchedule: boolean;
  active: boolean;
  shifts: WorkShift[];
}

export interface WorkScheduleMini {
  id: number;
  name: string;
  defaultSchedule: boolean;
}

/** Reference to a schedule as sent/received inside other entities. */
export interface WorkScheduleRef {
  id?: number;
  name?: string;
}

/** Payload accepted by POST / PATCH. */
export type WorkScheduleInput = Partial<Omit<WorkSchedule, keyof Audit>>;
