import { PlannerEvent } from '../../../models/owns/planner';
import { Priority } from '../../../models/owns/workOrder';

export const PRIORITY_COLORS: Record<Priority, string> = {
  HIGH: '#d32f2f',
  MEDIUM: '#ed6c02',
  LOW: '#1976d2',
  NONE: '#757575'
};

export const PRIORITY_ORDER: Priority[] = ['HIGH', 'MEDIUM', 'LOW', 'NONE'];

export const DEFAULT_DURATION_MINUTES = 60;
export const HOURS_PER_WORKDAY = 8;
export const WORKDAYS_PER_WEEK = 5;

export type CalendarViewType = 'timeGridDay' | 'timeGridWeek' | 'dayGridMonth';

export type LoadLevel = 'success' | 'warning' | 'error';

export interface TechnicianLoad {
  technicianId: number;
  technicianName: string;
  assignedHours: number;
  capacityHours: number;
  percentage: number;
  level: LoadLevel;
}

export const getEventDurationMinutes = (event: PlannerEvent): number =>
  event.estimatedDurationMinutes && event.estimatedDurationMinutes > 0
    ? event.estimatedDurationMinutes
    : DEFAULT_DURATION_MINUTES;

export const getEventStart = (event: PlannerEvent): Date | null => {
  if (event.estimatedStartDate) {
    const start = new Date(event.estimatedStartDate);
    return Number.isNaN(start.getTime()) ? null : start;
  }
  if (event.dueDate) {
    const due = new Date(event.dueDate);
    if (Number.isNaN(due.getTime())) return null;
    return new Date(due.getTime() - getEventDurationMinutes(event) * 60000);
  }
  return null;
};

export const getEventEnd = (event: PlannerEvent): Date | null => {
  const start = getEventStart(event);
  if (!start) return null;
  return new Date(start.getTime() + getEventDurationMinutes(event) * 60000);
};

export const getLoadLevel = (percentage: number): LoadLevel => {
  if (percentage > 100) return 'error';
  if (percentage >= 80) return 'warning';
  return 'success';
};

const startOfDay = (date: Date): Date =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate());

/** Monday 00:00 of the week containing date. */
const startOfWeek = (date: Date): Date => {
  const day = startOfDay(date);
  const offset = (day.getDay() + 6) % 7;
  day.setDate(day.getDate() - offset);
  return day;
};

/**
 * Pure function. Computes per-technician load for the selected day
 * (timeGridDay) or for the week containing referenceDate (week / month views).
 * Capacity: 8h per working day, 5 working days per week.
 */
export const computeTechnicianLoad = (
  events: PlannerEvent[],
  view: CalendarViewType,
  referenceDate: Date
): TechnicianLoad[] => {
  const isDay = view === 'timeGridDay';
  const rangeStart = isDay ? startOfDay(referenceDate) : startOfWeek(referenceDate);
  const rangeEnd = new Date(rangeStart);
  rangeEnd.setDate(rangeEnd.getDate() + (isDay ? 1 : 7));
  const capacityHours = isDay
    ? HOURS_PER_WORKDAY
    : HOURS_PER_WORKDAY * WORKDAYS_PER_WEEK;

  const minutesByTech = new Map<number, { name: string; minutes: number }>();
  events.forEach((event) => {
    if (event.primaryUserId === null || event.primaryUserId === undefined) return;
    const start = getEventStart(event);
    const end = getEventEnd(event);
    if (!start || !end) return;
    const overlapStart = Math.max(start.getTime(), rangeStart.getTime());
    const overlapEnd = Math.min(end.getTime(), rangeEnd.getTime());
    if (overlapEnd <= overlapStart) return;
    const minutes = (overlapEnd - overlapStart) / 60000;
    const current = minutesByTech.get(event.primaryUserId);
    minutesByTech.set(event.primaryUserId, {
      name: current?.name ?? event.primaryUserName ?? `#${event.primaryUserId}`,
      minutes: (current?.minutes ?? 0) + minutes
    });
  });

  return Array.from(minutesByTech.entries())
    .map(([technicianId, { name, minutes }]) => {
      const assignedHours = Math.round((minutes / 60) * 10) / 10;
      const percentage = Math.round((assignedHours / capacityHours) * 100);
      return {
        technicianId,
        technicianName: name,
        assignedHours,
        capacityHours,
        percentage,
        level: getLoadLevel(percentage)
      };
    })
    .sort((a, b) => b.percentage - a.percentage);
};
