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
  editMapsIntegration,
  testMapsIntegration
} from '../../../../slices/integration';
import {
  IntegrationTestResult,
  MapsConfigPatchDTO,
  MapsProvider
} from '../../../../models/owns/integration';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import useAuth from '../../../../hooks/useAuth';
import SecretField from './SecretField';
import {
  getIntegrationErrorMessage,
  numToStr,
  secretPatch
} from './integrationUtils';

interface MapsConfigModalProps {
  open: boolean;
  onClose: () => void;
}

function MapsConfigModal({ open, onClose }: MapsConfigModalProps) {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { user } = useAuth();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { maps } = useSelector((state) => state.integrations);
  const isAdmin = user?.role?.code === 'ADMIN';
  const mapsLoaded = !!maps;
  const [enabled, setEnabled] = useState<boolean>(false);
  const [provider, setProvider] = useState<MapsProvider | ''>('');
  const [apiKey, setApiKey] = useState<string>('');
  const [clearKey, setClearKey] = useState<boolean>(false);
  const [lat, setLat] = useState<string>('');
  const [lng, setLng] = useState<string>('');
  const [radius, setRadius] = useState<string>('');
  const [saving, setSaving] = useState<boolean>(false);
  const [testing, setTesting] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<IntegrationTestResult | null>(null);
  const [errorMessage, setErrorMessage] = useState<string>('');

  // Only (re)initialize the form when the modal opens, never on store updates,
  // so that pending edits are not lost.
  useEffect(() => {
    if (open) {
      setEnabled(!!maps?.enabled);
      setProvider(maps?.provider ?? '');
      setApiKey('');
      setClearKey(false);
      setLat(numToStr(maps?.defaultCenterLat));
      setLng(numToStr(maps?.defaultCenterLng));
      setRadius(numToStr(maps?.searchRadiusKm));
      setFeedback(null);
      setErrorMessage('');
    }
  }, [open, mapsLoaded]);

  const latInvalid =
    lat.trim() !== '' &&
    (Number.isNaN(Number(lat)) || Number(lat) < -90 || Number(lat) > 90);
  const lngInvalid =
    lng.trim() !== '' &&
    (Number.isNaN(Number(lng)) || Number(lng) < -180 || Number(lng) > 180);
  const radiusInvalid =
    radius.trim() !== '' && (Number.isNaN(Number(radius)) || Number(radius) <= 0);
  const hasErrors = latInvalid || lngInvalid || radiusInvalid;

  const toNumberOrNull = (value: string): number | null =>
    value.trim() === '' ? null : Number(value);

  const buildPatch = (): MapsConfigPatchDTO => {
    const patch: MapsConfigPatchDTO = {};
    if (!maps) return patch;
    if (enabled !== maps.enabled) patch.enabled = enabled;
    if (provider !== '' && provider !== maps.provider)
      patch.provider = provider;
    const key = secretPatch(apiKey, clearKey);
    if (key !== undefined) patch.apiKey = key;
    if (lat.trim() !== numToStr(maps.defaultCenterLat))
      patch.defaultCenterLat = toNumberOrNull(lat);
    if (lng.trim() !== numToStr(maps.defaultCenterLng))
      patch.defaultCenterLng = toNumberOrNull(lng);
    if (radius.trim() !== numToStr(maps.searchRadiusKm))
      patch.searchRadiusKm = toNumberOrNull(radius);
    return patch;
  };

  const savePending = async (): Promise<boolean> => {
    const patch = buildPatch();
    if (Object.keys(patch).length === 0) return true;
    try {
      await dispatch(editMapsIntegration(patch));
      setApiKey('');
      setClearKey(false);
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
        const result = await dispatch(testMapsIntegration());
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
      <DialogTitle>{t('integration_maps_title')}</DialogTitle>
      <DialogContent dividers>
        {!maps ? (
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
              label={t('maps_enable')}
            />
            <TextField
              select
              label={t('integration_provider')}
              value={provider}
              onChange={(event) =>
                setProvider(event.target.value as MapsProvider)
              }
              disabled={!isAdmin}
              fullWidth
            >
              <MenuItem value="GOOGLE_MAPS">Google Maps</MenuItem>
              <MenuItem value="MAPBOX">Mapbox</MenuItem>
            </TextField>
            <SecretField
              label={t('maps_api_key')}
              hasValue={maps.hasApiKey}
              last4={maps.apiKeyLast4}
              value={apiKey}
              cleared={clearKey}
              disabled={!isAdmin}
              onChange={(value) => {
                setApiKey(value);
                setClearKey(false);
              }}
              onClear={() => {
                setApiKey('');
                setClearKey(true);
              }}
              onUndoClear={() => setClearKey(false)}
            />
            <Stack direction="row" spacing={2}>
              <TextField
                label={t('maps_center_lat')}
                value={lat}
                onChange={(event) => setLat(event.target.value)}
                error={latInvalid}
                helperText={latInvalid ? t('maps_lat_invalid') : ''}
                disabled={!isAdmin}
                fullWidth
              />
              <TextField
                label={t('maps_center_lng')}
                value={lng}
                onChange={(event) => setLng(event.target.value)}
                error={lngInvalid}
                helperText={lngInvalid ? t('maps_lng_invalid') : ''}
                disabled={!isAdmin}
                fullWidth
              />
            </Stack>
            <TextField
              label={t('maps_search_radius')}
              value={radius}
              onChange={(event) => setRadius(event.target.value)}
              error={radiusInvalid}
              helperText={radiusInvalid ? t('maps_radius_invalid') : ''}
              disabled={!isAdmin}
              fullWidth
            />
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
          disabled={!isAdmin || !maps || hasErrors || busy}
          onClick={handleTest}
        >
          {testing ? t('integration_testing') : t('integration_test_connection')}
        </Button>
        <Button
          variant="contained"
          disabled={!isAdmin || !maps || hasErrors || busy}
          onClick={handleSave}
        >
          {t('save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default MapsConfigModal;
