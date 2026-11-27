import {
  Box,
  CircularProgress,
  Collapse,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from '@mui/material';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import { useDispatch, useSelector } from '../../../../store';
import { getAttendanceLogs } from '../../../../slices/attendance';

interface AttendanceSectionProps {
  workOrderId: number;
  // Changes after each check-in / check-out so an open table reloads
  refreshKey: number;
}

const formatCoords = (lat: number | null, lng: number | null): string =>
  lat !== null && lat !== undefined && lng !== null && lng !== undefined
    ? `${lat.toFixed(5)}, ${lng.toFixed(5)}`
    : '-';

const formatDate = (date: string | null): string =>
  date ? new Date(date).toLocaleString() : '-';

export default function AttendanceSection({
  workOrderId,
  refreshKey
}: AttendanceSectionProps) {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const [expanded, setExpanded] = useState<boolean>(false);
  const { logsByWorkOrder, loadingLogs } = useSelector(
    (state) => state.attendance
  );
  const logs = logsByWorkOrder[workOrderId] ?? [];

  useEffect(() => {
    if (expanded) dispatch(getAttendanceLogs(workOrderId));
  }, [expanded, workOrderId, refreshKey]);

  return (
    <Box>
      <Box
        sx={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}
        onClick={() => setExpanded((value) => !value)}
      >
        <Typography variant="h6" fontWeight="bold">
          {t('attendance')}
        </Typography>
        {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
      </Box>
      <Collapse in={expanded} unmountOnExit>
        {loadingLogs[workOrderId] && logs.length === 0 ? (
          <CircularProgress size="1.5rem" sx={{ mt: 1 }} />
        ) : logs.length === 0 ? (
          <Typography variant="body2" sx={{ mt: 1 }}>
            {t('attendance_empty')}
          </Typography>
        ) : (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t('attendance_technician')}</TableCell>
                  <TableCell>{t('check_in_time')}</TableCell>
                  <TableCell>{t('check_out_time')}</TableCell>
                  <TableCell>{t('minutes')}</TableCell>
                  <TableCell>{t('attendance_coordinates')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {logs.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell>{log.technicianName}</TableCell>
                    <TableCell>{formatDate(log.checkInTime)}</TableCell>
                    <TableCell>{formatDate(log.checkOutTime)}</TableCell>
                    <TableCell>{log.totalMinutes ?? '-'}</TableCell>
                    <TableCell>
                      {`${formatCoords(
                        log.checkInLat,
                        log.checkInLng
                      )} / ${formatCoords(log.checkOutLat, log.checkOutLng)}`}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
      </Collapse>
    </Box>
  );
}
