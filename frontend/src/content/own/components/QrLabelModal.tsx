import {
  Box,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  Typography
} from '@mui/material';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import CloseIcon from '@mui/icons-material/Close';
import DownloadIcon from '@mui/icons-material/Download';
import PrintIcon from '@mui/icons-material/Print';
import { QRCodeCanvas } from 'qrcode.react';

interface QrLabelModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  // Absolute URL encoded in the QR
  value: string;
  fileName?: string;
}

const QR_SIZE = 280;

const escapeHtml = (text: string): string =>
  text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

export default function QrLabelModal({
  open,
  onClose,
  title,
  value,
  fileName = 'qr-label'
}: QrLabelModalProps) {
  const { t }: { t: any } = useTranslation();
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const handleDownload = () => {
    const qrCanvas = canvasRef.current;
    if (!qrCanvas) return;
    const padding = 24;
    const textHeight = 60;
    const out = document.createElement('canvas');
    out.width = qrCanvas.width + padding * 2;
    out.height = qrCanvas.height + padding * 2 + textHeight;
    const ctx = out.getContext('2d');
    if (!ctx) return;
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, out.width, out.height);
    ctx.drawImage(qrCanvas, padding, padding);
    ctx.fillStyle = '#000000';
    ctx.textAlign = 'center';
    ctx.font = 'bold 20px sans-serif';
    ctx.fillText(title, out.width / 2, qrCanvas.height + padding + 30, out.width - 20);
    ctx.font = '12px sans-serif';
    ctx.fillText(value, out.width / 2, qrCanvas.height + padding + 52, out.width - 20);
    const link = document.createElement('a');
    link.href = out.toDataURL('image/png');
    link.download = `${fileName}.png`;
    link.click();
  };

  const handlePrint = () => {
    const qrCanvas = canvasRef.current;
    if (!qrCanvas) return;
    const dataUrl = qrCanvas.toDataURL('image/png');
    const printWindow = window.open('', '_blank', 'width=600,height=700');
    if (!printWindow) return;
    printWindow.document.write(`<html>
  <head>
    <title>${escapeHtml(title)}</title>
    <style>
      body { font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; margin: 0; padding: 24px; }
      img { width: 320px; height: 320px; }
      h1 { font-size: 22px; margin: 16px 0 4px; text-align: center; }
      p { font-size: 11px; color: #333; word-break: break-all; text-align: center; margin: 0; }
    </style>
  </head>
  <body>
    <img src="${dataUrl}" />
    <h1>${escapeHtml(title)}</h1>
    <p>${escapeHtml(value)}</p>
  </body>
</html>`);
    printWindow.document.close();
    printWindow.onafterprint = () => printWindow.close();
    printWindow.focus();
    setTimeout(() => printWindow.print(), 300);
  };

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
          {t('qr_label')}
        </Typography>
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Stack alignItems="center" spacing={2} sx={{ pb: 1 }}>
          <Box sx={{ bgcolor: 'white', p: 1, borderRadius: 1, boxShadow: 1 }}>
            <QRCodeCanvas
              ref={canvasRef}
              value={value}
              size={QR_SIZE}
              level="M"
              marginSize={2}
            />
          </Box>
          <Typography variant="h5" textAlign="center">
            {title}
          </Typography>
          <Typography
            variant="caption"
            color="text.secondary"
            textAlign="center"
            sx={{ wordBreak: 'break-all' }}
          >
            {value}
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={handleDownload}
            >
              {t('download_png')}
            </Button>
            <Button
              variant="outlined"
              startIcon={<PrintIcon />}
              onClick={handlePrint}
            >
              {t('print')}
            </Button>
          </Stack>
        </Stack>
      </DialogContent>
    </Dialog>
  );
}
