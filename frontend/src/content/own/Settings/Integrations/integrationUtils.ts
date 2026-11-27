import { getErrorMessage } from '../../../../utils/api';

// Secrets semantics: undefined = no change, non-empty = replace, "" = delete.
export const secretPatch = (
  value: string,
  cleared: boolean
): string | undefined => {
  if (cleared) return '';
  return value !== '' ? value : undefined;
};

// utils/api.ts drops the response body on HTTP 409 and throws 'conflict_error'.
export const getIntegrationErrorMessage = (
  error: any,
  conflictMessage: string
): string => {
  const message = getErrorMessage(error);
  return message === 'conflict_error' ? conflictMessage : message;
};

export const E164_REGEX = /^\+[1-9]\d{6,14}$/;

export const numToStr = (value: number | null | undefined): string =>
  value === null || value === undefined ? '' : String(value);
