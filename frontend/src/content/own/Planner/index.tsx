import { useContext, useEffect, useMemo, useRef, useState } from 'react';
import { Helmet } from 'react-helmet-async';
import FullCalendar, { EventInput, LocaleSingularArg } from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import enGb from '@fullcalendar/core/locales/en-gb';
import {
  Box,
  Button,
  ButtonGroup,
  Card,
  CircularProgress,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  styled,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import useAuth from '../../../hooks/useAuth';
import { PermissionEntity } from '../../../models/owns/role';
import WorkOrder from '../../../models/owns/workOrder';
import { PlannerEvent, ScheduleResponse } from '../../../models/owns/planner';
import { useDispatch, useSelector } from '../../../store';
import { getPlanner, scheduleWorkOrder } from '../../../slices/planner';
import { getUsersMini } from '../../../slices/user';
import { getErrorMessage } from '../../../utils/api';
import { getCalendarLocale } from '../../../i18n/i18n';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { TitleContext } from '../../../contexts/TitleContext';
import PermissionErrorMessage from '../components/PermissionErrorMessage';
import UnassignedPanel from './UnassignedPanel';
import TechnicianLoadPanel from './TechnicianLoadPanel';
import {
  CalendarViewType,
  computeTechnicianLoad,
  DEFAULT_DURATION_MINUTES,
  getEventDurationMinutes,
  getEventEnd,
  getEventStart,
  PRIORITY_COLORS,
  PRIORITY_ORDER
} from './utils';

const CalendarWrapper = styled(Box)`
  position: relative;
  & .fc-license-message {
    display: none;
  }
`;

const DEFAULT_START_HOUR = 8;

function Planner() {
  const { t }: { t: any } = useTranslation();
  const { i18n } = useTranslation();
  const dispatch = useDispatch();
  const { hasViewPermission, hasEditPermission } = useAuth();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { setTitle } = useContext(TitleContext);
  const calendarRef = useRef<FullCalendar | null>(null);
  const { scheduled, unassigned, loadingGet } = useSelector(
    (state) => state.planner
  );
  const { usersMini } = useSelector((state) => state.users);
  const [view, setView] = useState<CalendarViewType>('timeGridWeek');
  const [title, setCalendarTitle] = useState<string>('');
  const [referenceDate, setReferenceDate] = useState<Date>(new Date());
  const [range, setRange] = useState<{ start: Date; end: Date } | null>(null);
  const [technicianId, setTechnicianId] = useState<number | ''>('');
  const [calendarLocale, setCalendarLocale] =
    useState<LocaleSingularArg>(enGb);

  useEffect(() => {
    setTitle(t('planner'));
  }, []);

  useEffect(() => {
    getCalendarLocale(i18n.language).then(setCalendarLocale);
  }, [i18n.language]);

  useEffect(() => {
    if (usersMini.length === 0) dispatch(getUsersMini());
  }, []);

  const loadPlanner = (): void => {
    if (!range) return;
    dispatch(
      getPlanner(range.start, range.end, technicianId === '' ? null : technicianId)
    ).catch((err) =>
      showSnackBar(getErrorMessage(err, t('planner_load_failed')), 'error')
    );
  };

  useEffect(() => {
    loadPlanner();
  }, [range?.start?.getTime(), range?.end?.getTime(), technicianId]);

  // Permission check is UI-only; the backend performs the real validation.
  const canEdit = (event: PlannerEvent): boolean =>
    hasEditPermission(PermissionEntity.WORK_ORDERS, {
      id: event.id,
      primaryUser: event.primaryUserId ? { id: event.primaryUserId } : undefined
    } as unknown as WorkOrder);

  const calendarEvents = useMemo(() => {
    const result: EventInput[] = [];
    scheduled.forEach((event) => {
      const start = getEventStart(event);
      const end = getEventEnd(event);
      if (!start || !end) return;
      result.push({
        id: String(event.id),
        title: event.primaryUserName
          ? `${event.title} (${event.primaryUserName})`
          : event.title,
        start,
        end,
        allDay: false,
        backgroundColor: PRIORITY_COLORS[event.priority],
        borderColor: PRIORITY_COLORS[event.priority],
        editable: canEdit(event)
      });
    });
    return result;
  }, [scheduled]);

  const loads = useMemo(() => {
    let reference = referenceDate;
    if (view !== 'timeGridDay' && range) {
      const now = new Date();
      if (now >= range.start && now < range.end) reference = now;
    }
    return computeTechnicianLoad(scheduled, view, reference);
  }, [scheduled, view, referenceDate, range]);

  const applyScheduleResult = (response: ScheduleResponse): void => {
    if (response.warnings?.some((w) => w.includes('TECHNICIAN_OVERLAP'))) {
      showSnackBar(t('planner_technician_overlap'), 'warning');
    }
  };

  const handleEventChange = (info: {
    event: { id: string; start: Date | null; end: Date | null };
    revert: () => void;
  }): void => {
    const { event } = info;
    const original = scheduled.find((e) => String(e.id) === event.id);
    if (!event.start) {
      info.revert();
      return;
    }
    let durationMinutes = original
      ? getEventDurationMinutes(original)
      : DEFAULT_DURATION_MINUTES;
    if (event.end) {
      durationMinutes = Math.max(
        1,
        Math.round((event.end.getTime() - event.start.getTime()) / 60000)
      );
    }
    const end =
      event.end ?? new Date(event.start.getTime() + durationMinutes * 60000);
    dispatch(
      scheduleWorkOrder(Number(event.id), {
        estimatedStartDate: event.start.toISOString(),
        dueDate: end.toISOString(),
        estimatedDurationMinutes: durationMinutes
      })
    )
      .then(applyScheduleResult)
      .catch((err) => {
        info.revert();
        showSnackBar(
          getErrorMessage(err, t('planner_schedule_failed')),
          'error'
        );
      });
  };

  const handleExternalDrop = (info: {
    date: Date;
    allDay: boolean;
    draggedEl: HTMLElement;
  }): void => {
    const id = Number(info.draggedEl.getAttribute('data-id'));
    if (Number.isNaN(id)) return;
    const original = unassigned.find((e) => e.id === id);
    const durationMinutes = original?.estimatedDurationMinutes
      ? original.estimatedDurationMinutes
      : DEFAULT_DURATION_MINUTES;
    const start = new Date(info.date);
    if (info.allDay) start.setHours(DEFAULT_START_HOUR, 0, 0, 0);
    const end = new Date(start.getTime() + durationMinutes * 60000);
    dispatch(
      scheduleWorkOrder(id, {
        estimatedStartDate: start.toISOString(),
        dueDate: end.toISOString(),
        estimatedDurationMinutes: durationMinutes,
        ...(technicianId !== '' ? { primaryUserId: technicianId } : {})
      })
    )
      .then((response: ScheduleResponse) => {
        applyScheduleResult(response);
        loadPlanner();
      })
      .catch((err) =>
        showSnackBar(
          getErrorMessage(err, t('planner_schedule_failed')),
          'error'
        )
      );
  };

  const changeView = (newView: CalendarViewType): void => {
    calendarRef.current?.getApi().changeView(newView);
  };

  if (!hasViewPermission(PermissionEntity.WORK_ORDERS))
    return <PermissionErrorMessage message="no_access_wo" />;

  return (
    <>
      <Helmet>
        <title>{t('planner')}</title>
      </Helmet>
      <Box paddingX={4} paddingY={2}>
        <Stack
          direction="row"
          justifyContent="space-between"
          alignItems="center"
          flexWrap="wrap"
          spacing={2}
          mb={2}
        >
          <Stack direction="row" spacing={1} alignItems="center">
            <ButtonGroup size="small">
              <Button onClick={() => calendarRef.current?.getApi().prev()}>
                {t('previous')}
              </Button>
              <Button onClick={() => calendarRef.current?.getApi().today()}>
                {t('today')}
              </Button>
              <Button onClick={() => calendarRef.current?.getApi().next()}>
                {t('next')}
              </Button>
            </ButtonGroup>
            <Typography variant="h5">{title}</Typography>
          </Stack>
          <Stack direction="row" spacing={2} alignItems="center">
            <ButtonGroup size="small">
              <Button
                variant={view === 'timeGridDay' ? 'contained' : 'outlined'}
                onClick={() => changeView('timeGridDay')}
              >
                {t('day')}
              </Button>
              <Button
                variant={view === 'timeGridWeek' ? 'contained' : 'outlined'}
                onClick={() => changeView('timeGridWeek')}
              >
                {t('week')}
              </Button>
              <Button
                variant={view === 'dayGridMonth' ? 'contained' : 'outlined'}
                onClick={() => changeView('dayGridMonth')}
              >
                {t('month')}
              </Button>
            </ButtonGroup>
            <FormControl size="small" sx={{ minWidth: 220 }}>
              <InputLabel id="planner-technician-label">
                {t('planner_technician')}
              </InputLabel>
              <Select
                labelId="planner-technician-label"
                label={t('planner_technician')}
                value={technicianId}
                onChange={(e) =>
                  setTechnicianId(
                    e.target.value === '' ? '' : Number(e.target.value)
                  )
                }
              >
                <MenuItem value="">{t('planner_all_technicians')}</MenuItem>
                {usersMini.map((user) => (
                  <MenuItem key={user.id} value={user.id}>
                    {`${user.firstName} ${user.lastName}`}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Stack>
        </Stack>
        <Stack direction="row" spacing={2} mb={2} alignItems="center">
          <Typography variant="subtitle2">{t('planner_legend')}</Typography>
          {PRIORITY_ORDER.map((priority) => (
            <Stack
              key={priority}
              direction="row"
              spacing={0.5}
              alignItems="center"
            >
              <Box
                sx={{
                  width: 14,
                  height: 14,
                  borderRadius: '3px',
                  backgroundColor: PRIORITY_COLORS[priority]
                }}
              />
              <Typography variant="caption">{t(priority)}</Typography>
            </Stack>
          ))}
        </Stack>
        <Grid container spacing={2}>
          <Grid item xs={12} md={3}>
            <Stack spacing={2}>
              <UnassignedPanel events={unassigned} canEdit={canEdit} />
              <TechnicianLoadPanel loads={loads} />
            </Stack>
          </Grid>
          <Grid item xs={12} md={9}>
            <Card>
              <CalendarWrapper p={2}>
                {loadingGet && (
                  <Stack
                    position="absolute"
                    top="45%"
                    left="45%"
                    zIndex={10}
                  >
                    <CircularProgress size={48} />
                  </Stack>
                )}
                <FullCalendar
                  ref={calendarRef}
                  plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
                  initialView="timeGridWeek"
                  locale={calendarLocale}
                  headerToolbar={false}
                  allDaySlot={false}
                  height={700}
                  editable
                  droppable
                  eventDisplay="block"
                  dayMaxEventRows={4}
                  events={calendarEvents}
                  datesSet={(arg) => {
                    setView(arg.view.type as CalendarViewType);
                    setCalendarTitle(arg.view.title);
                    setReferenceDate(calendarRef.current?.getApi().getDate() ?? arg.start);
                    setRange({ start: arg.start, end: arg.end });
                  }}
                  eventDrop={handleEventChange}
                  eventResize={handleEventChange}
                  drop={handleExternalDrop}
                />
              </CalendarWrapper>
            </Card>
          </Grid>
        </Grid>
      </Box>
    </>
  );
}

export default Planner;
