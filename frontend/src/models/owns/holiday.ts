import { Audit } from './audit';
import { WorkScheduleRef } from './workSchedule';

export interface Holiday extends Audit {
  id: number;
  name: string;
  /** "YYYY-MM-DD" */
  date: string;
  recurringAnnually: boolean;
  /** null = applies to all schedules */
  workSchedule: WorkScheduleRef | null;
}

/** Payload accepted by POST / PATCH. */
export type HolidayInput = Partial<Omit<Holiday, keyof Audit>>;
