import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useContext, useEffect, useState } from 'react';
import { useDispatch, useSelector } from '../../../../store';
import {
  editNotificationsIntegration,
  testWhatsappNotification
} from '../../../../slices/integration';
import {
  IntegrationTestResult,
  NotificationsConfigPatchDTO,
  NotificationsProvider
} from '../../../../models/owns/integration';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import useAuth from '../../../../hooks/useAuth';
import SecretField from './SecretField';
import {
  E164_REGEX,
  getIntegrationErrorMessage,
  secretPatch
} from './integrationUtils';

interface NotificationsConfigModalProps {
  open: boolean;
  onClose: () => void;
}

interface EventFlags {
  notifyOnWorkOrderCreated: boolean;
  notifyOnTechnicianAssigned: boolean;
  notifyOnTechnicianArrived: boolean;
  notifyOnWorkOrderCompleted: boolean;
}

const eventKeys: { key: keyof EventFlags; label: string }[] = [
  { key: 'notifyOnWorkOrderCreated', label: 'whatsapp_event_created' },
  { key: 'notifyOnTechnicianAssigned', label: 'whatsapp_event_assigned' },
  { key: 'notifyOnTechnicianArrived', label: 'whatsapp_event_arrived' },
  { key: 'notifyOnWorkOrderCompleted', label: 'whatsapp_event_completed' }
];

const emptyEvents: EventFlags = {
  notifyOnWorkOrderCreated: false,
  notifyOnTechnicianAssigned: false,
  notifyOnTechnicianArrived: false,
  notifyOnWorkOrderCompleted: false
};

function NotificationsConfigModal({
  open,
  onClose
}: NotificationsConfigModalProps) {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { user } = useAuth();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { notifications } = useSelector((state) => state.integrations);
  const isAdmin = user?.role?.code === 'ADMIN';
  const notificationsLoaded = !!notifications;
  const [enabled, setEnabled] = useState<boolean>(false);
  const [provider, setProvider] = useState<NotificationsProvider | ''>('');
  const [phoneNumberId, setPhoneNumberId] = useState<string>('');
  const [accountSid, setAccountSid] = useState<string>('');
  const [accessToken, setAccessToken] = useState<string>('');
  const [clearToken, setClearToken] = useState<boolean>(false);
  const [events, setEvents] = useState<EventFlags>(emptyEvents);
  const [testPhone, setTestPhone] = useState<string>('');
  const [saving, setSaving] = useState<boolean>(false);
  const [testing, setTesting] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<IntegrationTestResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string>('');

  // Only (re)initialize the form when the modal opens, never on store updates,
  // so that pending edits are not lost.
  useEffect(() => {
    if (open) {
      setEnabled(!!notifications?.whatsappEnabled);
      setProvider(notifications?.provider ?? '');
      setPhoneNumberId(notifications?.phoneNumberId ?? '');
      setAccountSid(notifications?.accountSid ?? '');
      setAccessToken('');
      setClearToken(false);
      setEvents({
        notifyOnWorkOrderCreated: !!notifications?.notifyOnWorkOrderCreated,
        notifyOnTechnicianAssigned: !!notifications?.notifyOnTechnicianAssigned,
        notifyOnTechnicianArrived: !!notifications?.notifyOnTechnicianArrived,
        notifyOnWorkOrderCompleted: !!notifications?.notifyOnWorkOrderCompleted
      });
      setTestPhone('');
      setFeedback(null);
      setErrorMessage('');
    }
  }, [open, notificationsLoaded]);

  const isTwilio = provider === 'TWILIO';
  const phoneInvalid = testPhone.trim() !== '' && !E164_REGEX.test(testPhone.trim());

  const buildPatch = (): NotificationsConfigPatchDTO => {
    const patch: NotificationsConfigPatchDTO = {};
    if (!notifications) return patch;
    if (enabled !== notifications.whatsappEnabled)
      patch.whatsappEnabled = enabled;
    if (provider !== '' && provider !== notifications.provider)
      patch.provider = provider;
    if (phoneNumberId.trim() !== (notifications.phoneNumberId ?? ''))
      patch.phoneNumberId = phoneNumberId.trim();
    if (isTwilio && accountSid.trim() !== (notifications.accountSid ?? ''))
      patch.accountSid = accountSid.trim();
    const token = secretPatch(accessToken, clearToken);
    if (token !== undefined) patch.accessToken = token;
    eventKeys.forEach(({ key }) => {
      if (events[key] !== notifications[key]) patch[key] = events[key];
    });
    return patch;
  };

  const savePending = async (): Promise<boolean> => {
    const patch = buildPatch();
    if (Object.keys(patch).length === 0) return true;
    try {
      await dispatch(editNotificationsIntegration(patch));
      setAccessToken('');
      setClearToken(false);
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
        const result = await dispatch(testWhatsappNotification(testPhone.trim()));
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
      <DialogTitle>{t('integration_whatsapp_title')}</DialogTitle>
      <DialogContent dividers>
        {!notifications ? (
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
              label={t('whatsapp_enable')}
            />
            <TextField
              select
              label={t('integration_provider')}
              value={provider}
              onChange={(event) =>
                setProvider(event.target.value as NotificationsProvider)
              }
              disabled={!isAdmin}
              fullWidth
            >
              <MenuItem value="META_CLOUD_API">Meta Cloud API</MenuItem>
              <MenuItem value="TWILIO">Twilio</MenuItem>
            </TextField>
            <TextField
              label={
                isTwilio ? t('whatsapp_origin_number') : t('whatsapp_phone_number_id')
              }
              value={phoneNumberId}
              onChange={(event) => setPhoneNumberId(event.target.value)}
              disabled={!isAdmin}
              fullWidth
            />
            {isTwilio && (
              <TextField
                label={t('whatsapp_account_sid')}
                value={accountSid}
                onChange={(event) => setAccountSid(event.target.value)}
                disabled={!isAdmin}
                fullWidth
              />
            )}
            <SecretField
              label={t('whatsapp_access_token')}
              hasValue={notifications.hasAccessToken}
              last4={notifications.accessTokenLast4}
              value={accessToken}
              cleared={clearToken}
              disabled={!isAdmin}
              onChange={(value) => {
                setAccessToken(value);
                setClearToken(false);
              }}
              onClear={() => {
                setAccessToken('');
                setClearToken(true);
              }}
              onUndoClear={() => setClearToken(false)}
            />
            <Box>
              <Typography variant="h6">{t('whatsapp_events')}</Typography>
              <Stack>
                {eventKeys.map(({ key, label }) => (
                  <FormControlLabel
                    key={key}
                    control={
                      <Switch
                        checked={events[key]}
                        disabled={!isAdmin}
                        onChange={(event) =>
                          setEvents({ ...events, [key]: event.target.checked })
                        }
                      />
                    }
                    label={t(label)}
                  />
                ))}
              </Stack>
            </Box>
            <Divider />
            <Stack direction="row" spacing={2} alignItems="flex-start">
              <TextField
                label={t('whatsapp_test_phone')}
                placeholder="+18095551234"
                value={testPhone}
                onChange={(event) => setTestPhone(event.target.value)}
                error={phoneInvalid}
                helperText={
                  phoneInvalid ? t('whatsapp_phone_invalid') : t('whatsapp_phone_helper')
                }
                disabled={!isAdmin}
                fullWidth
              />
              <Button
                variant="outlined"
                disabled={
                  !isAdmin || testPhone.trim() === '' || phoneInvalid || busy
                }
                onClick={handleTest}
                sx={{ mt: 1, flexShrink: 0 }}
              >
                {testing ? t('integration_testing') : t('whatsapp_send_test')}
              </Button>
            </Stack>
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
          variant="contained"
          disabled={!isAdmin || !notifications || busy}
          onClick={handleSave}
        >
          {t('save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default NotificationsConfigModal;
