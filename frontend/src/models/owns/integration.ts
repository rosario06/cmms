export interface IotIntegration {
  enabled: boolean;
  hasToken: boolean;
  tokenLast4: string | null;
  webhookAlertUrl: string | null;
  ingestUrl: string;
}

export type MapsProvider = 'GOOGLE_MAPS' | 'MAPBOX';

export interface MapsIntegration {
  enabled: boolean;
  provider: MapsProvider | null;
  hasApiKey: boolean;
  apiKeyLast4: string | null;
  defaultCenterLat: number | null;
  defaultCenterLng: number | null;
  searchRadiusKm: number | null;
}

export type PaymentsProvider = 'STRIPE' | 'CARDNET' | 'AZUL' | 'PAYPAL';
export type PaymentsEnvironment = 'SANDBOX' | 'PRODUCTION';

export interface PaymentsIntegration {
  enabled: boolean;
  provider: PaymentsProvider | null;
  environment: PaymentsEnvironment;
  publicKey: string | null;
  hasSecretKey: boolean;
  secretKeyLast4: string | null;
  hasWebhookSecret: boolean;
  webhookSecretLast4: string | null;
  platformCommissionPercent: number | null;
}

export type NotificationsProvider = 'META_CLOUD_API' | 'TWILIO';

export interface NotificationsIntegration {
  whatsappEnabled: boolean;
  provider: NotificationsProvider | null;
  phoneNumberId: string | null;
  accountSid: string | null;
  hasAccessToken: boolean;
  accessTokenLast4: string | null;
  notifyOnWorkOrderCreated: boolean;
  notifyOnTechnicianAssigned: boolean;
  notifyOnTechnicianArrived: boolean;
  notifyOnWorkOrderCompleted: boolean;
}

export interface Integrations {
  iot: IotIntegration;
  maps?: MapsIntegration;
  payments?: PaymentsIntegration;
  notifications?: NotificationsIntegration;
}

export interface IntegrationTestResult {
  success: boolean;
  message: string;
}

// Secrets: absent/null = no change, non-empty string = replace, "" = delete.
export interface MapsConfigPatchDTO {
  enabled?: boolean;
  provider?: MapsProvider;
  apiKey?: string;
  defaultCenterLat?: number | null;
  defaultCenterLng?: number | null;
  searchRadiusKm?: number | null;
}

export interface PaymentsConfigPatchDTO {
  enabled?: boolean;
  provider?: PaymentsProvider;
  environment?: PaymentsEnvironment;
  publicKey?: string;
  secretKey?: string;
  webhookSecret?: string;
  platformCommissionPercent?: number | null;
}

export interface NotificationsConfigPatchDTO {
  whatsappEnabled?: boolean;
  provider?: NotificationsProvider;
  phoneNumberId?: string;
  accountSid?: string;
  accessToken?: string;
  notifyOnWorkOrderCreated?: boolean;
  notifyOnTechnicianAssigned?: boolean;
  notifyOnTechnicianArrived?: boolean;
  notifyOnWorkOrderCompleted?: boolean;
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
