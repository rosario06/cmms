import { Box, Card, LinearProgress, Stack, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { TechnicianLoad } from './utils';

interface OwnProps {
  loads: TechnicianLoad[];
}

function TechnicianLoadPanel({ loads }: OwnProps) {
  const { t }: { t: any } = useTranslation();
  return (
    <Card sx={{ p: 2 }}>
      <Typography variant="h6" mb={1}>
        {t('planner_technician_load')}
      </Typography>
      {loads.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {t('planner_no_load')}
        </Typography>
      ) : (
        <Stack spacing={1.5}>
          {loads.map((load) => (
            <Box key={load.technicianId}>
              <Stack direction="row" justifyContent="space-between">
                <Typography variant="body2" noWrap>
                  {load.technicianName}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {`${load.assignedHours} / ${load.capacityHours} h (${load.percentage}%)`}
                </Typography>
              </Stack>
              <LinearProgress
                variant="determinate"
                color={load.level}
                value={Math.min(load.percentage, 100)}
                sx={{ height: 8, borderRadius: 4 }}
              />
            </Box>
          ))}
        </Stack>
      )}
    </Card>
  );
}

export default TechnicianLoadPanel;
