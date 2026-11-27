import { Audit } from './audit';
import { WorkScheduleRef } from './workSchedule';

export type SlaPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'NONE';

export type SlaStatus = 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'MET';

export const SLA_PRIORITIES: SlaPriority[] = ['NONE', 'LOW', 'MEDIUM', 'HIGH'];

export interface SlaPolicy extends Audit {
  id: number;
  name: string;
  priority: SlaPriority;
  firstResponseMinutes: number;
  resolutionMinutes: number;
  penaltyPercent: number;
  serviceContract: { id: number; name: string } | null;
  /** Count only working hours of the schedule. */
  useBusinessHours: boolean;
  /** null = assignee's schedule, or the company default one. */
  workSchedule: WorkScheduleRef | null;
}

/** Payload accepted by POST / PATCH (relations are sent by id only). */
export type SlaPolicyInput = Partial<
  Omit<SlaPolicy, keyof Audit | 'serviceContract' | 'workSchedule'>
> & {
  // an object without id clears the relation when editing
  serviceContract?: { id?: number; name?: string } | null;
  workSchedule?: WorkScheduleRef | null;
};
