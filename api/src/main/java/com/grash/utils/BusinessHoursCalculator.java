package com.grash.utils;

import com.grash.model.Holiday;
import com.grash.model.WorkSchedule;
import com.grash.model.WorkScheduleShift;
import lombok.extern.slf4j.Slf4j;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure business hours arithmetic (no Spring, no I/O).
 * <p>
 * Rules:
 * <ul>
 *     <li>All the calendar logic runs in the time zone of the schedule; the spans are converted to instants with
 *     {@link ZonedDateTime}, so days with 23 or 25 hours (DST) are handled without any 24 h assumption.</li>
 *     <li>A shift belongs to the day it STARTS. When endTime is not after startTime it ends the next day.</li>
 *     <li>A holiday removes the shifts that start on its date (a night shift that started the previous day and ends
 *     on the holiday is kept). A holiday applies when its workSchedule is null or the same schedule; a recurring one
 *     matches month and day of any year.</li>
 *     <li>The search is limited to {@link #MAX_DAYS} days; when it is exhausted (e.g. a schedule without shifts or
 *     with every day a holiday) the wall clock result (start + minutes) is returned and a WARN is logged.</li>
 * </ul>
 * Changes of a schedule or of the holidays are NOT applied retroactively: the callers store the result.
 */
@Slf4j
public final class BusinessHoursCalculator {

    public static final int MAX_DAYS = 400;

    private BusinessHoursCalculator() {
    }

    /**
     * Instant at which {@code minutes} business minutes have elapsed since {@code start}. When start is outside the
     * business hours the count begins at the next span. Landing exactly on the end of a span returns that end.
     */
    public static Instant addBusinessMinutes(Instant start, long minutes, WorkSchedule schedule,
                                             Collection<Holiday> holidays) {
        if (start == null) throw new IllegalArgumentException("start is required");
        if (minutes <= 0) return start;
        Instant wallClock = start.plus(Duration.ofMinutes(minutes));
        if (schedule == null || schedule.getShifts() == null || schedule.getShifts().isEmpty()) {
            log.warn("Business hours requested without shifts; using wall clock minutes");
            return wallClock;
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(schedule.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            log.warn("Invalid time zone '{}' in work schedule {}; using wall clock minutes", schedule.getTimezone(),
                    schedule.getId());
            return wallClock;
        }

        HolidayMatcher holidayMatcher = new HolidayMatcher(holidays, schedule);
        Duration remaining = Duration.ofMinutes(minutes);
        Instant cursor = start;
        LocalDate startDate = start.atZone(zone).toLocalDate();
        // starts one day earlier so that a night shift that began yesterday and ends today is considered
        LocalDate day = startDate.minusDays(1);
        LocalDate lastDay = startDate.plusDays(MAX_DAYS);

        while (!day.isAfter(lastDay)) {
            if (!holidayMatcher.isHoliday(day)) {
                for (Span span : spansStartingOn(day, schedule.getShifts(), zone)) {
                    Instant from = span.start.isAfter(cursor) ? span.start : cursor;
                    if (!from.isBefore(span.end)) continue;
                    Duration available = Duration.between(from, span.end);
                    if (remaining.compareTo(available) <= 0) return from.plus(remaining);
                    remaining = remaining.minus(available);
                    cursor = span.end;
                }
            }
            day = day.plusDays(1);
        }
        log.warn("Business hours search exhausted {} days for work schedule {}; using wall clock minutes",
                MAX_DAYS, schedule.getId());
        return wallClock;
    }

    /**
     * Business minutes elapsed between two instants (same rules as {@link #addBusinessMinutes}); 0 when
     * {@code to} is not after {@code from}. Partial minutes are truncated.
     */
    public static long businessMinutesBetween(Instant from, Instant to, WorkSchedule schedule,
                                              Collection<Holiday> holidays) {
        if (from == null || to == null || !to.isAfter(from)) return 0;
        if (schedule == null || schedule.getShifts() == null || schedule.getShifts().isEmpty()) {
            return Duration.between(from, to).toMinutes();
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(schedule.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return Duration.between(from, to).toMinutes();
        }
        HolidayMatcher holidayMatcher = new HolidayMatcher(holidays, schedule);
        Duration total = Duration.ZERO;
        LocalDate day = from.atZone(zone).toLocalDate().minusDays(1);
        LocalDate lastDay = to.atZone(zone).toLocalDate();
        if (Duration.between(from, to).toDays() > MAX_DAYS) lastDay = from.atZone(zone).toLocalDate().plusDays(MAX_DAYS);
        while (!day.isAfter(lastDay)) {
            if (!holidayMatcher.isHoliday(day)) {
                for (Span span : spansStartingOn(day, schedule.getShifts(), zone)) {
                    Instant a = span.start.isAfter(from) ? span.start : from;
                    Instant b = span.end.isBefore(to) ? span.end : to;
                    if (a.isBefore(b)) total = total.plus(Duration.between(a, b));
                }
            }
            day = day.plusDays(1);
        }
        return total.toMinutes();
    }

    /** Spans (instants) of the shifts that start on the given local date, ordered by start. */
    private static List<Span> spansStartingOn(LocalDate day, List<WorkScheduleShift> shifts, ZoneId zone) {
        List<Span> spans = new ArrayList<>();
        int isoDay = day.getDayOfWeek().getValue();
        for (WorkScheduleShift shift : shifts) {
            if (shift.getDayOfWeek() != isoDay || shift.getStartTime() == null || shift.getEndTime() == null)
                continue;
            LocalTime startTime = shift.getStartTime();
            LocalTime endTime = shift.getEndTime();
            LocalDate endDay = endTime.isAfter(startTime) ? day : day.plusDays(1);
            Instant start = ZonedDateTime.of(day, startTime, zone).toInstant();
            Instant end = ZonedDateTime.of(endDay, endTime, zone).toInstant();
            if (end.isAfter(start)) spans.add(new Span(start, end));
        }
        spans.sort(Comparator.comparing((Span s) -> s.start));
        return spans;
    }

    private static final class Span {
        private final Instant start;
        private final Instant end;

        private Span(Instant start, Instant end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class HolidayMatcher {
        private final Set<LocalDate> exactDates = new HashSet<>();
        private final Set<MonthDay> recurring = new HashSet<>();

        private HolidayMatcher(Collection<Holiday> holidays, WorkSchedule schedule) {
            if (holidays == null) return;
            for (Holiday holiday : holidays) {
                if (holiday == null || holiday.getDate() == null || !appliesTo(holiday, schedule)) continue;
                exactDates.add(holiday.getDate());
                if (holiday.isRecurringAnnually()) recurring.add(MonthDay.from(holiday.getDate()));
            }
        }

        private static boolean appliesTo(Holiday holiday, WorkSchedule schedule) {
            WorkSchedule target = holiday.getWorkSchedule();
            if (target == null) return true;
            if (target == schedule) return true;
            return target.getId() != null && Objects.equals(target.getId(), schedule.getId());
        }

        private boolean isHoliday(LocalDate date) {
            return exactDates.contains(date) || recurring.contains(MonthDay.from(date));
        }
    }
}
