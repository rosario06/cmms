export type ProgressColor = 'success' | 'warning' | 'error';

/** Budget execution percentage (can exceed 100). */
export const getExecutionPercent = (
  allocatedBudget: number | null | undefined,
  spentAmount: number | null | undefined
): number => {
  if (!allocatedBudget || allocatedBudget <= 0) return 0;
  return Math.max(0, ((spentAmount ?? 0) / allocatedBudget) * 100);
};

/** Green below the threshold, amber from the threshold, red from 100%. */
export const getBudgetColor = (
  percent: number,
  thresholdPercent: number | null | undefined
): ProgressColor => {
  if (percent >= 100) return 'error';
  if (percent >= (thresholdPercent ?? 80)) return 'warning';
  return 'success';
};

/** Colors a margin: red when negative. */
export const getMarginColor = (value: number | null | undefined): string =>
  (value ?? 0) < 0 ? 'error.main' : 'text.primary';
