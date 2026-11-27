import { useEffect, useRef } from 'react';
import { Draggable } from '@fullcalendar/interaction';
import { Box, Card, Chip, Stack, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { PlannerEvent } from '../../../models/owns/planner';
import { DEFAULT_DURATION_MINUTES, PRIORITY_COLORS } from './utils';

interface OwnProps {
  events: PlannerEvent[];
  canEdit: (event: PlannerEvent) => boolean;
}

const DRAGGABLE_CLASS = 'planner-draggable';

function UnassignedPanel({ events, canEdit }: OwnProps) {
  const { t }: { t: any } = useTranslation();
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const draggable = new Draggable(containerRef.current, {
      itemSelector: `.${DRAGGABLE_CLASS}`,
      eventData: (el: HTMLElement) => {
        const data = el.getAttribute('data-event');
        return data ? JSON.parse(data) : {};
      }
    });
    return () => draggable.destroy();
  }, []);

  return (
    <Card sx={{ p: 2 }}>
      <Typography variant="h6" mb={1}>
        {t('planner_unscheduled')}
      </Typography>
      <Box
        ref={containerRef}
        sx={{ maxHeight: 420, overflowY: 'auto', pr: 0.5 }}
      >
        {events.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            {t('planner_no_unscheduled')}
          </Typography>
        )}
        <Stack spacing={1}>
          {events.map((event) => {
            const draggable = canEdit(event);
            return (
              <Box
                key={event.id}
                className={draggable ? DRAGGABLE_CLASS : undefined}
                data-id={event.id}
                data-event={JSON.stringify({
                  id: String(event.id),
                  title: event.title,
                  duration: {
                    minutes:
                      event.estimatedDurationMinutes ||
                      DEFAULT_DURATION_MINUTES
                  },
                  create: false
                })}
                sx={{
                  p: 1,
                  borderRadius: 1,
                  border: 1,
                  borderColor: 'divider',
                  borderLeft: `5px solid ${PRIORITY_COLORS[event.priority]}`,
                  cursor: draggable ? 'grab' : 'not-allowed',
                  opacity: draggable ? 1 : 0.6,
                  backgroundColor: 'background.paper'
                }}
              >
                <Typography variant="subtitle2" noWrap>
                  {event.title}
                </Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Chip
                    size="small"
                    label={t(event.priority)}
                    sx={{
                      backgroundColor: PRIORITY_COLORS[event.priority],
                      color: 'white'
                    }}
                  />
                  {event.assetName && (
                    <Typography variant="caption" noWrap>
                      {event.assetName}
                    </Typography>
                  )}
                </Stack>
              </Box>
            );
          })}
        </Stack>
      </Box>
    </Card>
  );
}

export default UnassignedPanel;
