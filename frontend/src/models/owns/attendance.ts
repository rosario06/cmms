export default interface AttendanceLog {
  id: number;
  workOrderId: number;
  technicianId: number;
  technicianName: string;
  assetId: number | null;
  checkInTime: string;
  checkOutTime: string | null;
  checkInLat: number | null;
  checkInLng: number | null;
  checkOutLat: number | null;
  checkOutLng: number | null;
  qrCodeScanned: string;
  totalMinutes: number | null;
  notes: string | null;
  active: boolean;
}

export interface CheckInPayload {
  code: string;
  latitude?: number;
  longitude?: number;
  notes?: string;
}

export interface CheckOutPayload {
  code?: string;
  latitude?: number;
  longitude?: number;
  notes?: string;
}
