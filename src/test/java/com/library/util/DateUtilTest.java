package com.library.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DateUtilTest {

    @Test
    void parsesValidDates() {
        assertEquals(LocalDate.of(2026, 1, 2), DateUtil.parseDate("2026-01-02"));
        assertEquals(LocalDate.of(2026, 12, 31), DateUtil.parseDate(" 2026-12-31 "));
    }

    @Test
    void toleratesDirtyInput() {
        assertNull(DateUtil.parseDate(null));
        assertNull(DateUtil.parseDate(""));
        assertNull(DateUtil.parseDate("not-a-date"));
        assertNull(DateUtil.parseDate("2026/01/02"));
        assertNull(DateUtil.parseDate("2026-13-01"));
    }

    @Test
    void parsesDateTime() {
        assertEquals(2026, DateUtil.parseDateTime("2026-01-02 03:04:05").getYear());
        assertEquals(4, DateUtil.parseDateTime("2026-01-02T03:04:05").getMinute());
        assertNull(DateUtil.parseDateTime("garbage"));
    }

    @Test
    void daysBetween() {
        assertEquals(4, DateUtil.daysBetween("2026-01-05", "2026-01-01"));
        assertEquals(-1, DateUtil.daysBetween("2026-01-01", "2026-01-02"));
        assertNull(DateUtil.daysBetween("bad", "2026-01-01"));
    }

    @Test
    void todayAndNowFormats() {
        assertEquals(10, DateUtil.todayStr().length());
        assertEquals(19, DateUtil.nowStr().length());
        assertEquals("2026-02-03", DateUtil.format(LocalDate.of(2026, 2, 3)));
    }
}
