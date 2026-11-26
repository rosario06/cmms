export interface IotIntegration {
  enabled: boolean;
  hasToken: boolean;
  tokenLast4: string | null;
  webhookAlertUrl: string | null;
  ingestUrl: string;
}

export interface Integrations {
  iot: IotIntegration;
}

export interface IotTokenResponse {
  token: string;
  tokenLast4: string;
}

export type IotTelemetryStatus =
  | 'PROCESSED'
  | 'TRIGGERED'
  | 'ASSET_NOT_FOUND'
  | 'METER_NOT_FOUND'
  | 'REJECTED';

export interface IotTelemetry {
  id: number;
  assetCode: string;
  metricName: string;
  value: number;
  timestamp: string;
  status: IotTelemetryStatus;
  message: string | null;
  createdAt: string;
}

export interface IotConfigPatchDTO {
  enabled?: boolean;
  webhookAlertUrl?: string | null;
}
