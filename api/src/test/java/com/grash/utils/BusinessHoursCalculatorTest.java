package com.grash.utils;

import com.grash.model.Holiday;
import com.grash.model.WorkSchedule;
import com.grash.model.WorkScheduleShift;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessHoursCalculatorTest {

    private static final String SANTO_DOMINGO = "America/Santo_Domingo"; // UTC-4, no DST
    private static final String NEW_YORK = "America/New_York";

    // 2025-03-03 is a Monday

    private static Instant at(String zone, int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(zone)).toInstant();
    }

    private static Instant sd(int year, int month, int day, int hour, int minute) {
        return at(SANTO_DOMINGO, year, month, day, hour, minute);
    }

    private static WorkScheduleShift shift(int dayOfWeek, String start, String end) {
        WorkScheduleShift shift = new WorkScheduleShift();
        shift.setDayOfWeek(dayOfWeek);
        shift.setStartTime(LocalTime.parse(start));
        shift.setEndTime(LocalTime.parse(end));
        return shift;
    }

    private static WorkSchedule schedule(long id, String timezone, WorkScheduleShift... shifts) {
        WorkSchedule schedule = new WorkSchedule();
        schedule.setId(id);
        schedule.setName("schedule " + id);
        schedule.setTimezone(timezone);
        schedule.setShifts(new ArrayList<>(List.of(shifts)));
        return schedule;
    }

    /** Monday to Friday 08:00-17:00. */
    private static WorkSchedule officeHours() {
        return schedule(1L, SANTO_DOMINGO, shift(1, "08:00", "17:00"), shift(2, "08:00", "17:00"),
                shift(3, "08:00", "17:00"), shift(4, "08:00", "17:00"), shift(5, "08:00", "17:00"));
    }

    private static Holiday holiday(int year, int month, int day, boolean recurring, WorkSchedule schedule) {
        Holiday holiday = new Holiday();
        holiday.setName("holiday");
        holiday.setDate(LocalDate.of(year, month, day));
        holiday.setRecurringAnnually(recurring);
        holiday.setWorkSchedule(schedule);
        return holiday;
    }

    @Test
    void simpleWorkday_staysInTheSameDay() {
        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 3, 9, 0), 60, officeHours(),
                Collections.emptyList());

        assertEquals(sd(2025, 3, 3, 10, 0), due);
    }

    @Test
    void overflowsToTheNextBusinessDay() {
        // 30 minutes on Monday (16:30-17:00) + 60 on Tuesday
        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 3, 16, 30), 90, officeHours(), null);

        assertEquals(sd(2025, 3, 4, 9, 0), due);
    }

    @Test
    void landingExactlyOnTheEndOfTheSpanReturnsTheEnd() {
        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 3, 16, 0), 60, officeHours(), null);

        assertEquals(sd(2025, 3, 3, 17, 0), due);
    }

    @Test
    void crossesTheWeekend() {
        // Friday 16:00 -> 60 min on Friday, 120 min on Monday
        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 7, 16, 0), 180, officeHours(),
                Collections.emptyList());

        assertEquals(sd(2025, 3, 10, 10, 0), due);
    }

    @Test
    void holidaySkipsTheDay() {
        Holiday monday = holiday(2025, 3, 10, false, null);

        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 7, 16, 0), 180, officeHours(),
                List.of(monday));

        assertEquals(sd(2025, 3, 11, 10, 0), due);
    }

    @Test
    void annualHolidayMatchesAnyYear() {
        Holiday recurring = holiday(2020, 3, 10, true, null);

        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 7, 16, 0), 180, officeHours(),
                List.of(recurring));

        assertEquals(sd(2025, 3, 11, 10, 0), due);
    }

    @Test
    void nonRecurringHolidayOfAnotherYearIsIgnored() {
        Holiday otherYear = holiday(2020, 3, 10, false, null);

        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 7, 16, 0), 180, officeHours(),
                List.of(otherYear));

        assertEquals(sd(2025, 3, 10, 10, 0), due);
    }

    @Test
    void holidayOfAnotherScheduleIsIgnoredAndOfTheSameScheduleApplies() {
        WorkSchedule schedule = officeHours();
        WorkSchedule other = schedule(2L, SANTO_DOMINGO, shift(1, "08:00", "17:00"));
        WorkSchedule sameById = new WorkSchedule();
        sameById.setId(1L);

        Instant ignored = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 7, 16, 0), 180, schedule,
                List.of(holiday(2025, 3, 10, false, other)));
        Instant applied = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 7, 16, 0), 180, schedule,
                List.of(holiday(2025, 3, 10, false, sameById)));

        assertEquals(sd(2025, 3, 10, 10, 0), ignored);
        assertEquals(sd(2025, 3, 11, 10, 0), applied);
    }

    @Test
    void nightShiftCrossesMidnight() {
        WorkSchedule night = schedule(3L, SANTO_DOMINGO, shift(1, "22:00", "06:00"));

        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 3, 23, 0), 120, night, null);

        assertEquals(sd(2025, 3, 4, 1, 0), due);
    }

    @Test
    void startInsideTheNightShiftOfThePreviousDay() {
        WorkSchedule night = schedule(3L, SANTO_DOMINGO, shift(1, "22:00", "06:00"));

        // Tuesday 02:00 belongs to the shift that started on Monday
        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 4, 2, 0), 60, night, null);

        assertEquals(sd(2025, 3, 4, 3, 0), due);
    }

    @Test
    void nightShiftContinuesOnTheNextWeek() {
        WorkSchedule night = schedule(3L, SANTO_DOMINGO, shift(1, "22:00", "06:00"));

        // 4 hours left of Monday's shift (02:00-06:00 Tuesday) and 4 more on the next Monday
        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 4, 2, 0), 8 * 60, night, null);

        assertEquals(sd(2025, 3, 11, 2, 0), due);
    }

    @Test
    void holidayRemovesTheShiftsThatStartThatDayButKeepsTheNightShiftThatEndsThatDay() {
        WorkSchedule night = schedule(3L, SANTO_DOMINGO, shift(1, "22:00", "06:00"), shift(2, "22:00", "06:00"));
        Holiday tuesday = holiday(2025, 3, 4, false, null);

        // Monday shift: 23:00-06:00 = 7 h; Tuesday shift is skipped; the missing hour is on the next Monday
        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 3, 23, 0), 8 * 60, night,
                List.of(tuesday));

        assertEquals(sd(2025, 3, 10, 23, 0), due);
    }

    @Test
    void severalShiftsInTheSameDay() {
        WorkSchedule split = schedule(4L, SANTO_DOMINGO, shift(1, "14:00", "18:00"), shift(1, "08:00", "12:00"));

        // 1 h until noon, lunch break, 1 h in the afternoon
        Instant due = BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 3, 11, 0), 120, split, null);

        assertEquals(sd(2025, 3, 3, 15, 0), due);
    }

    @Test
    void startOutsideTheBusinessHoursBeginsAtTheNextSpan() {
        WorkSchedule schedule = officeHours();

        // Saturday
        assertEquals(sd(2025, 3, 10, 8, 30),
                BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 8, 10, 0), 30, schedule, null));
        // Monday before opening
        assertEquals(sd(2025, 3, 3, 8, 30),
                BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 3, 6, 0), 30, schedule, null));
        // Monday after closing
        assertEquals(sd(2025, 3, 4, 8, 30),
                BusinessHoursCalculator.addBusinessMinutes(sd(2025, 3, 3, 18, 0), 30, schedule, null));
    }

    @Test
    void daylightSavingSpringForwardDayHasOneHourLess() {
        // New York: 2025-03-09 02:00 -> 03:00 (the Sunday has 23 hours). Sunday shift 00:00-06:00 lasts 5 real hours.
        WorkSchedule sunday = schedule(5L, NEW_YORK, shift(7, "00:00", "06:00"), shift(1, "08:00", "09:00"));
        Instant start = at(NEW_YORK, 2025, 3, 9, 0, 0);

        Instant fiveHours = BusinessHoursCalculator.addBusinessMinutes(start, 300, sunday, null);
        Instant extraMinute = BusinessHoursCalculator.addBusinessMinutes(start, 301, sunday, null);

        assertEquals(start.plusSeconds(5 * 3600), fiveHours);
        assertEquals(at(NEW_YORK, 2025, 3, 9, 6, 0), fiveHours);
        assertEquals(at(NEW_YORK, 2025, 3, 10, 8, 1), extraMinute);
    }

    @Test
    void daylightSavingFallBackDayHasOneHourMore() {
        // New York: 2025-11-02 02:00 -> 01:00 (the Sunday has 25 hours). Shift 00:00-06:00 lasts 7 real hours.
        WorkSchedule sunday = schedule(5L, NEW_YORK, shift(7, "00:00", "06:00"));
        Instant start = at(NEW_YORK, 2025, 11, 2, 0, 0);

        Instant due = BusinessHoursCalculator.addBusinessMinutes(start, 7 * 60, sunday, null);

        assertEquals(start.plusSeconds(7 * 3600), due);
        assertEquals(at(NEW_YORK, 2025, 11, 2, 6, 0), due);
    }

    @Test
    void searchLimitFallsBackToWallClockMinutes() {
        // day 8 never happens: the search exhausts the limit
        WorkSchedule unreachable = schedule(6L, SANTO_DOMINGO, shift(8, "08:00", "17:00"));
        Instant start = sd(2025, 3, 3, 9, 0);

        Instant due = BusinessHoursCalculator.addBusinessMinutes(start, 90, unreachable, null);

        assertEquals(start.plusSeconds(90 * 60), due);
    }

    @Test
    void scheduleWithoutShiftsFallsBackToWallClockMinutes() {
        WorkSchedule empty = schedule(7L, SANTO_DOMINGO);
        Instant start = sd(2025, 3, 3, 9, 0);

        assertEquals(start.plusSeconds(600), BusinessHoursCalculator.addBusinessMinutes(start, 10, empty, null));
        assertEquals(start.plusSeconds(600), BusinessHoursCalculator.addBusinessMinutes(start, 10, null, null));
    }

    @Test
    void invalidTimezoneFallsBackToWallClockMinutes() {
        WorkSchedule broken = schedule(8L, "Not/AZone", shift(1, "08:00", "17:00"));
        Instant start = sd(2025, 3, 3, 9, 0);

        assertEquals(start.plusSeconds(600), BusinessHoursCalculator.addBusinessMinutes(start, 10, broken, null));
    }

    @Test
    void zeroOrNegativeMinutesReturnTheStart() {
        Instant start = sd(2025, 3, 8, 10, 0);

        assertEquals(start, BusinessHoursCalculator.addBusinessMinutes(start, 0, officeHours(), null));
        assertEquals(start, BusinessHoursCalculator.addBusinessMinutes(start, -5, officeHours(), null));
    }

    @Test
    void businessMinutesBetweenCountsOnlyBusinessTime() {
        WorkSchedule schedule = officeHours();

        assertEquals(180, BusinessHoursCalculator.businessMinutesBetween(sd(2025, 3, 7, 16, 0),
                sd(2025, 3, 10, 10, 0), schedule, null));
        // with Monday as a holiday only the 60 minutes of Friday count (and Tuesday until 10:00 = 120)
        assertEquals(180, BusinessHoursCalculator.businessMinutesBetween(sd(2025, 3, 7, 16, 0),
                sd(2025, 3, 11, 10, 0), schedule, List.of(holiday(2025, 3, 10, false, null))));
        assertEquals(0, BusinessHoursCalculator.businessMinutesBetween(sd(2025, 3, 8, 0, 0),
                sd(2025, 3, 9, 0, 0), schedule, null));
        assertEquals(0, BusinessHoursCalculator.businessMinutesBetween(sd(2025, 3, 8, 0, 0),
                sd(2025, 3, 8, 0, 0), schedule, null));
    }
}
