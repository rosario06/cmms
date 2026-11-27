import { Audit } from './audit';

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
}
