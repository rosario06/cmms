import { ServiceContractStatus } from '../../../models/owns/serviceContract';
import { SlaStatus } from '../../../models/owns/slaPolicy';

/** Formats a number of minutes as a readable string such as "2 h 30 min". */
export const formatMinutes = (totalMinutes: number | null | undefined): string => {
  if (totalMinutes === null || totalMinutes === undefined) return '';
  const total = Math.max(0, Math.round(totalMinutes));
  const hours = Math.floor(total / 60);
  const minutes = total % 60;
  if (hours === 0) return `${minutes} min`;
  if (minutes === 0) return `${hours} h`;
  return `${hours} h ${minutes} min`;
};

export type ChipColor = 'default' | 'success' | 'warning' | 'error' | 'info';

export const getContractStatusColor = (
  status: ServiceContractStatus
): ChipColor => {
  switch (status) {
    case 'ACTIVE':
      return 'success';
    case 'EXPIRED':
      return 'warning';
    case 'CANCELLED':
      return 'error';
    default:
      return 'default';
  }
};

export const getSlaStatusColor = (status: SlaStatus): ChipColor => {
  switch (status) {
    case 'ON_TRACK':
      return 'success';
    case 'AT_RISK':
      return 'warning';
    case 'BREACHED':
      return 'error';
    default:
      return 'info';
  }
};

/** Progress bar color according to the consumed percentage. */
export const getUsageColor = (percent: number): 'success' | 'warning' | 'error' =>
  percent >= 90 ? 'error' : percent >= 70 ? 'warning' : 'success';

export const getConsumedPercent = (
  includedHours: number | null | undefined,
  remainingHours: number | null | undefined
): number => {
  if (!includedHours || includedHours <= 0) return 0;
  const consumed = includedHours - (remainingHours ?? includedHours);
  return Math.min(100, Math.max(0, (consumed / includedHours) * 100));
};

export const toNumberOrNull = (value: unknown): number | null => {
  if (value === null || value === undefined || value === '') return null;
  const parsed = Number(value);
  return Number.isNaN(parsed) ? null : parsed;
};
