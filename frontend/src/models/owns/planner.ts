import { Priority } from './workOrder';

export interface PlannerEvent {
  id: number;
  title: string;
  priority: Priority;
  status: string;
  dueDate: string | null;
  estimatedStartDate: string | null;
  estimatedDurationMinutes: number | null;
  primaryUserId: number | null;
  primaryUserName: string | null;
  assetName: string | null;
  locationName: string | null;
}

export interface PlannerResponse {
  scheduled: PlannerEvent[];
  unassigned: PlannerEvent[];
}

export interface ScheduleRequest {
  dueDate?: string;
  estimatedStartDate?: string;
  estimatedDurationMinutes?: number;
  primaryUserId?: number;
}

export interface ScheduleResponse {
  event: PlannerEvent;
  warnings: string[];
  conflictingWorkOrderIds?: number[];
}
