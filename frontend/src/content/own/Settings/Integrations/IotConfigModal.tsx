import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  InputAdornment,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useContext, useEffect, useState } from 'react';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import RefreshIcon from '@mui/icons-material/Refresh';
import { useDispatch, useSelector } from '../../../../store';
import {
  editIotIntegration,
  getIotTelemetry,
  rotateIotToken
} from '../../../../slices/integration';
import { IotTelemetryStatus } from '../../../../models/owns/integration';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import { getErrorMessage } from '../../../../utils/api';
import useAuth from '../../../../hooks/useAuth';
import ConfirmDialog from '../../components/ConfirmDialog';

interface IotConfigModalProps {
  open: boolean;
  onClose: () => void;
}

const statusColors: {
  [key in IotTelemetryStatus]: 'success' | 'warning' | 'error' | 'default';
} = {
  PROCESSED: 'success',
  TRIGGERED: 'warning',
  ASSET_NOT_FOUND: 'error',
  METER_NOT_FOUND: 'error',
  REJECTED: 'default'
};

function IotConfigModal({ open, onClose }: IotConfigModalProps) {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { user } = useAuth();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { iot, telemetry, loadingTelemetry } = useSelector(
    (state) => state.integrations
  );
  const isAdmin = user?.role?.code === 'ADMIN';
  const [alertUrl, setAlertUrl] = useState<string>('');
  const [newToken, setNewToken] = useState<string | null>(null);
  const [confirmRotate, setConfirmRotate] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);

  useEffect(() => {
    if (open) {
      setAlertUrl(iot?.webhookAlertUrl ?? '');
      dispatch(getIotTelemetry(20));
    } else {
      // the token is only kept in memory while the modal is open
      setNewToken(null);
    }
  }, [open]);

  const copy = (text: string) => {
    navigator.clipboard.writeText(text);
    showSnackBar(t('copied'), 'success');
  };

  const handleToggle = async (enabled: boolean) => {
    try {
      await dispatch(editIotIntegration({ enabled }));
      showSnackBar(t('iot_config_saved'), 'success');
    } catch (error) {
      showSnackBar(getErrorMessage(error), 'error');
    }
  };

  const handleSaveAlertUrl = async () => {
    setSaving(true);
    try {
      await dispatch(
        editIotIntegration({ webhookAlertUrl: alertUrl.trim() || null })
      );
      showSnackBar(t('iot_config_saved'), 'success');
    } catch (error) {
      showSnackBar(getErrorMessage(error), 'error');
    } finally {
      setSaving(false);
    }
  };

  const generateToken = async () => {
    setConfirmRotate(false);
    try {
      const response = await dispatch(rotateIotToken());
      setNewToken(response.token);
      showSnackBar(t('iot_token_saved'), 'success');
    } catch (error) {
      showSnackBar(getErrorMessage(error), 'error');
    }
  };

  const onGenerateClick = () => {
    if (iot?.hasToken) setConfirmRotate(true);
    else generateToken();
  };

  const payloadExample = iot
    ? `curl -X POST "${iot.ingestUrl}" \\
  -H "Content-Type: application/json" \\
  -H "X-IoT-Token: ${newToken ?? '<YOUR_TOKEN>'}" \\
  -d '{
  "assetCode": "ASSET-001",
  "metricName": "temperature",
  "value": 85.4,
  "timestamp": "${new Date().toISOString()}"
}'`
    : '';

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{t('integration_iot_title')}</DialogTitle>
      <DialogContent dividers>
        {!iot ? (
          <Box display="flex" justifyContent="center" p={3}>
            <CircularProgress />
          </Box>
        ) : (
          <Stack spacing={3}>
            {!isAdmin && <Alert severity="info">{t('iot_admin_only')}</Alert>}
            <FormControlLabel
              control={
                <Switch
                  checked={iot.enabled}
                  disabled={!isAdmin}
                  onChange={(event) => handleToggle(event.target.checked)}
                />
              }
              label={t('iot_enable')}
            />
            <TextField
              label={t('iot_ingest_url')}
              value={iot.ingestUrl}
              fullWidth
              InputProps={{
                readOnly: true,
                endAdornment: (
                  <InputAdornment position="end">
                    <Tooltip title={t('copy')}>
                      <IconButton onClick={() => copy(iot.ingestUrl)}>
                        <ContentCopyIcon />
                      </IconButton>
                    </Tooltip>
                  </InputAdornment>
                )
              }}
            />
            <Box>
              <Stack direction="row" spacing={2} alignItems="center">
                <Button
                  variant="outlined"
                  disabled={!isAdmin}
                  onClick={onGenerateClick}
                >
                  {t('iot_generate_token')}
                </Button>
                {!newToken && (
                  <Typography variant="body2" color="text.secondary">
                    {iot.hasToken
                      ? `••••••••${iot.tokenLast4 ?? ''}`
                      : t('iot_no_token')}
                  </Typography>
                )}
              </Stack>
              {newToken && (
                <Alert severity="warning" sx={{ mt: 2 }}>
                  <Typography variant="body2" gutterBottom>
                    {t('iot_token_shown_once')}
                  </Typography>
                  <TextField
                    label={t('iot_new_token')}
                    value={newToken}
                    fullWidth
                    size="small"
                    InputProps={{
                      readOnly: true,
                      endAdornment: (
                        <InputAdornment position="end">
                          <Tooltip title={t('copy')}>
                            <IconButton onClick={() => copy(newToken)}>
                              <ContentCopyIcon />
                            </IconButton>
                          </Tooltip>
                        </InputAdornment>
                      )
                    }}
                  />
                </Alert>
              )}
            </Box>
            <Stack direction="row" spacing={2} alignItems="flex-start">
              <TextField
                label={t('iot_alert_url')}
                helperText={t('iot_alert_url_helper')}
                value={alertUrl}
                onChange={(event) => setAlertUrl(event.target.value)}
                disabled={!isAdmin}
                fullWidth
              />
              <Button
                variant="contained"
                disabled={!isAdmin || saving}
                onClick={handleSaveAlertUrl}
                sx={{ mt: 1 }}
              >
                {t('save')}
              </Button>
            </Stack>
            <Box>
              <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="center"
              >
                <Typography variant="h6">{t('iot_payload_example')}</Typography>
                <Button
                  size="small"
                  startIcon={<ContentCopyIcon />}
                  onClick={() => copy(payloadExample)}
                >
                  {t('copy')}
                </Button>
              </Stack>
              <Box
                component="pre"
                sx={{
                  mt: 1,
                  p: 2,
                  borderRadius: 1,
                  overflowX: 'auto',
                  fontSize: 12,
                  bgcolor: 'action.hover'
                }}
              >
                {payloadExample}
              </Box>
            </Box>
            <Box>
              <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="center"
              >
                <Typography variant="h6">{t('iot_recent_readings')}</Typography>
                <Button
                  size="small"
                  startIcon={<RefreshIcon />}
                  disabled={loadingTelemetry}
                  onClick={() => dispatch(getIotTelemetry(20))}
                >
                  {t('iot_refresh')}
                </Button>
              </Stack>
              {telemetry.length === 0 ? (
                <Typography variant="body2" color="text.secondary" mt={1}>
                  {t('iot_no_readings')}
                </Typography>
              ) : (
                <Box sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>{t('iot_reading_date')}</TableCell>
                        <TableCell>{t('iot_asset_code')}</TableCell>
                        <TableCell>{t('iot_metric')}</TableCell>
                        <TableCell>{t('value')}</TableCell>
                        <TableCell>{t('status')}</TableCell>
                        <TableCell>{t('iot_message')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {telemetry.map((reading) => (
                        <TableRow key={reading.id}>
                          <TableCell>
                            {new Date(
                              reading.timestamp ?? reading.createdAt
                            ).toLocaleString()}
                          </TableCell>
                          <TableCell>{reading.assetCode}</TableCell>
                          <TableCell>{reading.metricName}</TableCell>
                          <TableCell>{reading.value}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              color={statusColors[reading.status] ?? 'default'}
                              label={t(`iot_status_${reading.status}`)}
                            />
                          </TableCell>
                          <TableCell>{reading.message}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </Box>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('close')}</Button>
      </DialogActions>
      <ConfirmDialog
        open={confirmRotate}
        onCancel={() => setConfirmRotate(false)}
        onConfirm={generateToken}
        confirmText={t('iot_generate_token')}
        question={t('iot_rotate_confirm')}
      />
    </Dialog>
  );
}

export default IotConfigModal;
