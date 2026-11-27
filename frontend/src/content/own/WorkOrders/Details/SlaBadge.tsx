import { useEffect, useState } from 'react';
import { Box, Chip, Link, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import WorkOrder from '../../../../models/owns/workOrder';
import { SlaStatus } from '../../../../models/owns/slaPolicy';

const REFRESH_INTERVAL_MS = 30000;

const STATUS_COLORS: Record<
  SlaStatus,
  { background: string; color: string }
> = {
  ON_TRACK: { background: '#2e7d32', color: '#ffffff' },
  AT_RISK: { background: '#f9a825', color: '#212121' },
  BREACHED: { background: '#c62828', color: '#ffffff' },
  MET: { background: '#00838f', color: '#ffffff' }
};

const formatDuration = (millis: number): string => {
  const totalMinutes = Math.floor(Math.abs(millis) / 60000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0) return `${days} d ${hours} h`;
  if (hours > 0) return `${hours} h ${minutes} min`;
  return `${minutes} min`;
};

export default function SlaBadge({ workOrder }: { workOrder: WorkOrder }) {
  const { t }: { t: any } = useTranslation();
  const [now, setNow] = useState<number>(Date.now());

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), REFRESH_INTERVAL_MS);
    return () => clearInterval(interval);
  }, []);

  const { slaStatus, serviceContract, slaPolicy } = workOrder;
  const hasSla = !!slaStatus || !!slaPolicy;
  if (!hasSla && !serviceContract) return null;

  const resolutionDue = workOrder.slaResolutionDueAt
    ? new Date(workOrder.slaResolutionDueAt).getTime()
    : null;
  const firstResponseDue = workOrder.slaFirstResponseDueAt
    ? new Date(workOrder.slaFirstResponseDueAt).getTime()
    : null;

  const renderTimer = () => {
    if (!slaStatus || slaStatus === 'MET') return null;
    if (resolutionDue === null || Number.isNaN(resolutionDue)) return null;
    const remaining = resolutionDue - now;
    return (
      <Typography variant="body2">
        {remaining >= 0
          ? `${t('sla_time_remaining')}: ${formatDuration(remaining)}`
          : `${t('sla_time_exceeded')}: ${formatDuration(remaining)}`}
      </Typography>
    );
  };

  const renderFirstResponse = () => {
    if (workOrder.firstRespondedAt || !slaStatus || slaStatus === 'MET')
      return null;
    if (firstResponseDue === null || Number.isNaN(firstResponseDue))
      return null;
    const remaining = firstResponseDue - now;
    return (
      <Typography variant="body2">
        {remaining >= 0
          ? `${t('sla_first_response_pending')}: ${formatDuration(remaining)}`
          : `${t('sla_first_response_overdue')}: ${formatDuration(remaining)}`}
      </Typography>
    );
  };

  return (
    <Stack spacing={0.5} sx={{ mb: 2 }}>
      {slaStatus && (
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
          <Chip
            size="small"
            label={`${t('sla')}: ${t(`sla_status_${slaStatus}`)}`}
            sx={STATUS_COLORS[slaStatus]}
          />
          {slaPolicy?.name && (
            <Typography variant="caption">{slaPolicy.name}</Typography>
          )}
        </Stack>
      )}
      {renderTimer()}
      {renderFirstResponse()}
      {serviceContract && (
        <Box>
          <Typography variant="body2" component="span">
            {`${t('contract')}: `}
          </Typography>
          <Link component={RouterLink} to="/app/contracts" variant="body2">
            {`${serviceContract.name} (${serviceContract.contractNumber})`}
          </Link>
        </Box>
      )}
    </Stack>
  );
}
