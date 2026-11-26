import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Stack,
  Switch,
  TextField
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useContext, useEffect, useState } from 'react';
import { useDispatch, useSelector } from '../../../../store';
import {
  editPaymentsIntegration,
  testPaymentsIntegration
} from '../../../../slices/integration';
import {
  IntegrationTestResult,
  PaymentsConfigPatchDTO,
  PaymentsEnvironment,
  PaymentsProvider
} from '../../../../models/owns/integration';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import useAuth from '../../../../hooks/useAuth';
import SecretField from './SecretField';
import {
  getIntegrationErrorMessage,
  numToStr,
  secretPatch
} from './integrationUtils';

interface PaymentsConfigModalProps {
  open: boolean;
  onClose: () => void;
}

function PaymentsConfigModal({ open, onClose }: PaymentsConfigModalProps) {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { user } = useAuth();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { payments } = useSelector((state) => state.integrations);
  const isAdmin = user?.role?.code === 'ADMIN';
  const paymentsLoaded = !!payments;
  const [enabled, setEnabled] = useState<boolean>(false);
  const [provider, setProvider] = useState<PaymentsProvider | ''>('');
  const [environment, setEnvironment] =
    useState<PaymentsEnvironment>('SANDBOX');
  const [publicKey, setPublicKey] = useState<string>('');
  const [secretKey, setSecretKey] = useState<string>('');
  const [clearSecretKey, setClearSecretKey] = useState<boolean>(false);
  const [webhookSecret, setWebhookSecret] = useState<string>('');
  const [clearWebhookSecret, setClearWebhookSecret] = useState<boolean>(false);
  const [commission, setCommission] = useState<string>('');
  const [saving, setSaving] = useState<boolean>(false);
  const [testing, setTesting] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<IntegrationTestResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string>('');

  // Only (re)initialize the form when the modal opens, never on store updates,
  // so that pending edits are not lost.
  useEffect(() => {
    if (open) {
      setEnabled(!!payments?.enabled);
      setProvider(payments?.provider ?? '');
      setEnvironment(payments?.environment ?? 'SANDBOX');
      setPublicKey(payments?.publicKey ?? '');
      setSecretKey('');
      setClearSecretKey(false);
      setWebhookSecret('');
      setClearWebhookSecret(false);
      setCommission(numToStr(payments?.platformCommissionPercent));
      setFeedback(null);
      setErrorMessage('');
    }
  }, [open, paymentsLoaded]);

  const commissionInvalid =
    commission.trim() !== '' &&
    (Number.isNaN(Number(commission)) ||
      Number(commission) < 0 ||
      Number(commission) > 100);

  const buildPatch = (): PaymentsConfigPatchDTO => {
    const patch: PaymentsConfigPatchDTO = {};
    if (!payments) return patch;
    if (enabled !== payments.enabled) patch.enabled = enabled;
    if (provider !== '' && provider !== payments.provider)
      patch.provider = provider;
    if (environment !== payments.environment) patch.environment = environment;
    if (publicKey.trim() !== (payments.publicKey ?? ''))
      patch.publicKey = publicKey.trim();
    const secret = secretPatch(secretKey, clearSecretKey);
    if (secret !== undefined) patch.secretKey = secret;
    const webhook = secretPatch(webhookSecret, clearWebhookSecret);
    if (webhook !== undefined) patch.webhookSecret = webhook;
    if (commission.trim() !== numToStr(payments.platformCommissionPercent))
      patch.platformCommissionPercent =
        commission.trim() === '' ? null : Number(commission);
    return patch;
  };

  const savePending = async (): Promise<boolean> => {
    const patch = buildPatch();
    if (Object.keys(patch).length === 0) return true;
    try {
      await dispatch(editPaymentsIntegration(patch));
      setSecretKey('');
      setClearSecretKey(false);
      setWebhookSecret('');
      setClearWebhookSecret(false);
      return true;
    } catch (error) {
      setErrorMessage(
        getIntegrationErrorMessage(error, t('integration_conflict_error'))
      );
      return false;
    }
  };

  const handleSave = async () => {
    setSaving(true);
    setErrorMessage('');
    if (await savePending()) showSnackBar(t('iot_config_saved'), 'success');
    setSaving(false);
  };

  const handleTest = async () => {
    setTesting(true);
    setErrorMessage('');
    setFeedback(null);
    if (await savePending()) {
      try {
        const result = await dispatch(testPaymentsIntegration());
        setFeedback(result);
      } catch (error) {
        setFeedback({
          success: false,
          message: getIntegrationErrorMessage(
            error,
            t('integration_conflict_error')
          )
        });
      }
    }
    setTesting(false);
  };

  const busy = saving || testing;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t('integration_payments_title')}</DialogTitle>
      <DialogContent dividers>
        {!payments ? (
          <Box display="flex" justifyContent="center" p={3}>
            <CircularProgress />
          </Box>
        ) : (
          <Stack spacing={3}>
            {!isAdmin && <Alert severity="info">{t('iot_admin_only')}</Alert>}
            {errorMessage && <Alert severity="error">{errorMessage}</Alert>}
            <FormControlLabel
              control={
                <Switch
                  checked={enabled}
                  disabled={!isAdmin}
                  onChange={(event) => setEnabled(event.target.checked)}
                />
              }
              label={t('payments_enable')}
            />
            <TextField
              select
              label={t('payments_gateway')}
              value={provider}
              onChange={(event) =>
                setProvider(event.target.value as PaymentsProvider)
              }
              disabled={!isAdmin}
              fullWidth
            >
              <MenuItem value="STRIPE">Stripe</MenuItem>
              <MenuItem value="AZUL">Azul</MenuItem>
              <MenuItem value="CARDNET">CardNET</MenuItem>
              <MenuItem value="PAYPAL">PayPal</MenuItem>
            </TextField>
            <TextField
              select
              label={t('payments_environment')}
              value={environment}
              onChange={(event) =>
                setEnvironment(event.target.value as PaymentsEnvironment)
              }
              disabled={!isAdmin}
              fullWidth
            >
              <MenuItem value="SANDBOX">{t('payments_sandbox')}</MenuItem>
              <MenuItem value="PRODUCTION">{t('payments_production')}</MenuItem>
            </TextField>
            {environment === 'PRODUCTION' && (
              <Alert severity="error">{t('payments_production_warning')}</Alert>
            )}
            <TextField
              label={t('payments_public_key')}
              value={publicKey}
              onChange={(event) => setPublicKey(event.target.value)}
              disabled={!isAdmin}
              fullWidth
            />
            <SecretField
              label={t('payments_secret_key')}
              hasValue={payments.hasSecretKey}
              last4={payments.secretKeyLast4}
              value={secretKey}
              cleared={clearSecretKey}
              disabled={!isAdmin}
              onChange={(value) => {
                setSecretKey(value);
                setClearSecretKey(false);
              }}
              onClear={() => {
                setSecretKey('');
                setClearSecretKey(true);
              }}
              onUndoClear={() => setClearSecretKey(false)}
            />
            <SecretField
              label={t('payments_webhook_secret')}
              hasValue={payments.hasWebhookSecret}
              last4={payments.webhookSecretLast4}
              value={webhookSecret}
              cleared={clearWebhookSecret}
              disabled={!isAdmin}
              onChange={(value) => {
                setWebhookSecret(value);
                setClearWebhookSecret(false);
              }}
              onClear={() => {
                setWebhookSecret('');
                setClearWebhookSecret(true);
              }}
              onUndoClear={() => setClearWebhookSecret(false)}
            />
            <TextField
              label={t('payments_commission')}
              type="number"
              inputProps={{ min: 0, max: 100, step: 0.1 }}
              value={commission}
              onChange={(event) => setCommission(event.target.value)}
              error={commissionInvalid}
              helperText={commissionInvalid ? t('payments_commission_invalid') : ''}
              disabled={!isAdmin}
              fullWidth
            />
            <Alert severity="info">{t('payments_test_note')}</Alert>
            {feedback && (
              <Alert severity={feedback.success ? 'success' : 'error'}>
                {feedback.message}
              </Alert>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('close')}</Button>
        <Button
          variant="outlined"
          disabled={!isAdmin || !payments || commissionInvalid || busy}
          onClick={handleTest}
        >
          {testing ? t('integration_testing') : t('integration_test_connection')}
        </Button>
        <Button
          variant="contained"
          disabled={!isAdmin || !payments || commissionInvalid || busy}
          onClick={handleSave}
        >
          {t('save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default PaymentsConfigModal;
