import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography
} from '@mui/material';
import { useContext, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import CloseIcon from '@mui/icons-material/Close';
import AttendanceLog from '../../../../models/owns/attendance';
import { useDispatch } from '../../../../store';
import { checkIn, checkOut } from '../../../../slices/attendance';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import { getErrorMessage } from '../../../../utils/api';

// Minimal local typing: BarcodeDetector is not in every TypeScript DOM lib.
interface DetectedBarcode {
  rawValue: string;
}
interface BarcodeDetectorLike {
  detect: (source: HTMLVideoElement) => Promise<DetectedBarcode[]>;
}
interface BarcodeDetectorConstructor {
  new (options?: { formats?: string[] }): BarcodeDetectorLike;
}

const getBarcodeDetector = (): BarcodeDetectorConstructor | undefined =>
  (window as unknown as { BarcodeDetector?: BarcodeDetectorConstructor })
    .BarcodeDetector;

const isCameraSupported = (): boolean =>
  !!getBarcodeDetector() && !!navigator.mediaDevices?.getUserMedia;

const GEO_TIMEOUT_MS = 8000;

const getPosition = (): Promise<{ latitude: number; longitude: number } | null> =>
  new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve(null);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) =>
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude
        }),
      () => resolve(null),
      { timeout: GEO_TIMEOUT_MS, enableHighAccuracy: true, maximumAge: 0 }
    );
  });

interface ScanAttendanceModalProps {
  open: boolean;
  onClose: () => void;
  workOrderId: number;
  activeLog: AttendanceLog | null;
  onDone?: () => void;
}

type Mode = 'camera' | 'manual';

interface ResultState {
  log: AttendanceLog;
  isCheckOut: boolean;
  withoutLocation: boolean;
}

export default function ScanAttendanceModal({
  open,
  onClose,
  workOrderId,
  activeLog,
  onDone
}: ScanAttendanceModalProps) {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const cameraSupported = isCameraSupported();
  const [mode, setMode] = useState<Mode>(cameraSupported ? 'camera' : 'manual');
  const [manualCode, setManualCode] = useState<string>('');
  const [busy, setBusy] = useState<boolean>(false);
  const [locating, setLocating] = useState<boolean>(false);
  const [error, setError] = useState<string>('');
  const [cameraError, setCameraError] = useState<string>('');
  const [result, setResult] = useState<ResultState | null>(null);
  const [videoEl, setVideoEl] = useState<HTMLVideoElement | null>(null);
  const isCheckOut = !!activeLog;
  const processingRef = useRef<boolean>(false);

  const handleCode = async (rawCode: string) => {
    const code = rawCode.trim();
    if (!code || processingRef.current) return;
    processingRef.current = true;
    setBusy(true);
    setError('');
    setLocating(true);
    const position = await getPosition();
    setLocating(false);
    const coords = position ?? {};
    try {
      const log: AttendanceLog = isCheckOut
        ? await dispatch(checkOut(workOrderId, { code, ...coords }))
        : await dispatch(checkIn(workOrderId, { code, ...coords }));
      setResult({ log, isCheckOut, withoutLocation: !position });
      showSnackBar(
        isCheckOut ? t('check_out_success') : t('check_in_success'),
        'success'
      );
      if (!position) showSnackBar(t('attendance_no_location'), 'warning');
      if (onDone) onDone();
    } catch (err) {
      const message =
        (err as Error)?.message === 'conflict_error'
          ? t('attendance_conflict')
          : getErrorMessage(err, t('attendance_error'));
      setError(message);
      showSnackBar(message, 'error');
    } finally {
      processingRef.current = false;
      setBusy(false);
    }
  };
  const handleCodeRef = useRef(handleCode);
  handleCodeRef.current = handleCode;

  useEffect(() => {
    if (open) {
      setResult(null);
      setError('');
      setCameraError('');
      setManualCode('');
      setMode(isCameraSupported() ? 'camera' : 'manual');
    }
  }, [open]);

  useEffect(() => {
    const Detector = getBarcodeDetector();
    if (
      !open ||
      mode !== 'camera' ||
      result ||
      busy ||
      error ||
      !videoEl ||
      !Detector ||
      !navigator.mediaDevices?.getUserMedia
    ) {
      return undefined;
    }
    let cancelled = false;
    let stream: MediaStream | null = null;
    let interval: ReturnType<typeof setInterval> | null = null;
    const stop = () => {
      if (interval) clearInterval(interval);
      interval = null;
      if (stream) stream.getTracks().forEach((track) => track.stop());
      stream = null;
      videoEl.srcObject = null;
    };
    const start = async () => {
      try {
        const mediaStream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' }
        });
        if (cancelled) {
          mediaStream.getTracks().forEach((track) => track.stop());
          return;
        }
        stream = mediaStream;
        videoEl.srcObject = mediaStream;
        await videoEl.play();
        const detector = new Detector({ formats: ['qr_code'] });
        interval = setInterval(async () => {
          if (cancelled || processingRef.current) return;
          try {
            const codes = await detector.detect(videoEl);
            if (codes.length && !cancelled && !processingRef.current) {
              const value = codes[0].rawValue;
              stop();
              handleCodeRef.current(value);
            }
          } catch {
            // transient detection error, try again on next tick
          }
        }, 400);
      } catch {
        if (!cancelled) setCameraError(t('camera_permission_denied'));
      }
    };
    start();
    return () => {
      cancelled = true;
      stop();
    };
  }, [open, mode, result, busy, error, videoEl]);

  const formatDate = (date: string) => new Date(date).toLocaleString();

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}
      >
        <Typography variant="h5" fontWeight={700}>
          {t('check_in_out_qr')}
        </Typography>
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pb: 1 }}>
          <Alert severity="info">
            {isCheckOut ? t('attendance_will_check_out') : t('attendance_will_check_in')}
          </Alert>
          {result ? (
            <Alert severity="success">
              <Typography variant="h6">
                {result.isCheckOut ? t('check_out_success') : t('check_in_success')}
              </Typography>
              <Typography variant="body2">
                {t('check_in_time')}: {formatDate(result.log.checkInTime)}
              </Typography>
              {result.log.checkOutTime && (
                <Typography variant="body2">
                  {t('check_out_time')}: {formatDate(result.log.checkOutTime)}
                </Typography>
              )}
              {result.isCheckOut && result.log.totalMinutes !== null && (
                <Typography variant="body2">
                  {t('total_minutes')}: {result.log.totalMinutes}
                </Typography>
              )}
              {result.withoutLocation && (
                <Typography variant="body2">
                  {t('attendance_no_location')}
                </Typography>
              )}
            </Alert>
          ) : (
            <>
              <Tabs
                value={mode}
                onChange={(_event, value: Mode) => setMode(value)}
                variant="fullWidth"
              >
                <Tab
                  value="camera"
                  label={t('scan_with_camera')}
                  disabled={!cameraSupported}
                />
                <Tab value="manual" label={t('enter_code')} />
              </Tabs>
              {!cameraSupported && (
                <Alert severity="warning">{t('camera_not_supported')}</Alert>
              )}
              {mode === 'camera' && cameraSupported && (
                <Box
                  sx={{
                    bgcolor: 'black',
                    borderRadius: 1,
                    overflow: 'hidden',
                    minHeight: 220
                  }}
                >
                  <video
                    ref={setVideoEl}
                    muted
                    playsInline
                    style={{ width: '100%', display: 'block' }}
                  />
                </Box>
              )}
              {cameraError && <Alert severity="error">{cameraError}</Alert>}
              {mode === 'manual' && (
                <Stack spacing={1}>
                  <TextField
                    fullWidth
                    size="small"
                    label={t('qr_code_value')}
                    value={manualCode}
                    onChange={(event) => setManualCode(event.target.value)}
                    disabled={busy}
                  />
                  <Button
                    variant="contained"
                    disabled={busy || !manualCode.trim()}
                    onClick={() => handleCode(manualCode)}
                  >
                    {isCheckOut ? t('check_out') : t('check_in')}
                  </Button>
                </Stack>
              )}
              {busy && (
                <Stack direction="row" spacing={1} alignItems="center">
                  <CircularProgress size="1.2rem" />
                  <Typography variant="body2">
                    {locating
                      ? t('attendance_getting_location')
                      : t('attendance_processing')}
                  </Typography>
                </Stack>
              )}
              {error && (
                <Alert
                  severity="error"
                  action={
                    mode === 'camera' ? (
                      <Button
                        color="inherit"
                        size="small"
                        onClick={() => setError('')}
                      >
                        {t('attendance_scan_again')}
                      </Button>
                    ) : undefined
                  }
                >
                  {error}
                </Alert>
              )}
            </>
          )}
        </Stack>
      </DialogContent>
    </Dialog>
  );
}
