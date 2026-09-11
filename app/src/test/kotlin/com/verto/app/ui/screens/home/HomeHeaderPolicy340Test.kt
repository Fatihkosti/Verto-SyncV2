package com.verto.app.ui.screens.home

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeaderPolicy340Test {
    @Test
    fun `period boundaries follow local time contract`() {
        assertEquals(HomeHeaderPeriod.EVENING, homeHeaderPeriodFor(LocalTime.of(3, 59)))
        assertEquals(HomeHeaderPeriod.MORNING, homeHeaderPeriodFor(LocalTime.of(4, 0)))
        assertEquals(HomeHeaderPeriod.MORNING, homeHeaderPeriodFor(LocalTime.of(11, 59)))
        assertEquals(HomeHeaderPeriod.DAY, homeHeaderPeriodFor(LocalTime.of(12, 0)))
        assertEquals(HomeHeaderPeriod.DAY, homeHeaderPeriodFor(LocalTime.of(15, 59)))
        assertEquals(HomeHeaderPeriod.EVENING, homeHeaderPeriodFor(LocalTime.of(16, 0)))
        assertEquals(HomeHeaderPeriod.EVENING, homeHeaderPeriodFor(LocalTime.of(23, 59)))
        assertEquals(HomeHeaderPeriod.EVENING, homeHeaderPeriodFor(LocalTime.MIDNIGHT))
    }

    @Test
    fun `catalog is 30 by 30 by 30 unique and compact`() {
        listOf(
            HomeHeaderMessages.morning,
            HomeHeaderMessages.day,
            HomeHeaderMessages.evening,
        ).forEach { messages ->
            assertEquals(30, messages.size)
            assertEquals(30, messages.distinct().size)
            assertTrue(messages.all { it.length <= 55 })
        }
        assertTrue(HomeHeaderMessages.morning.intersect(HomeHeaderMessages.day.toSet()).isEmpty())
        assertTrue(HomeHeaderMessages.day.intersect(HomeHeaderMessages.evening.toSet()).isEmpty())
        assertTrue(HomeHeaderMessages.morning.intersect(HomeHeaderMessages.evening.toSet()).isEmpty())
    }

    @Test
    fun `each period has 30-day non-repeating deterministic cycle`() {
        val start = LocalDate.of(2026, 8, 1)
        val selectors = listOf<(LocalDate) -> String>(
            { date -> HomeHeaderMessages.morning[homeHeaderMessageIndex(date)] },
            { date -> HomeHeaderMessages.day[homeHeaderMessageIndex(date)] },
            { date -> HomeHeaderMessages.evening[homeHeaderMessageIndex(date)] },
        )
        selectors.forEach { select ->
            val cycle = (0L until 30L).map { offset -> select(start.plusDays(offset)) }
            assertEquals(30, cycle.distinct().size)
            assertEquals(select(start), select(start.plusDays(30)))
        }
    }

    @Test
    fun `same local date gets distinct period messages`() {
        val date = LocalDate.of(2026, 8, 23)
        val morning = homeHeaderCopyFor(LocalDateTime.of(date, LocalTime.of(8, 0))).phrase
        val day = homeHeaderCopyFor(LocalDateTime.of(date, LocalTime.of(13, 0))).phrase
        val evening = homeHeaderCopyFor(LocalDateTime.of(date, LocalTime.of(20, 0))).phrase
        assertNotEquals(morning, day)
        assertNotEquals(day, evening)
        assertNotEquals(morning, evening)
    }

    @Test
    fun `next boundary schedules only midnight 04 12 and 16`() {
        val zone = ZoneId.of("Africa/Khartoum")
        fun z(hour: Int, minute: Int): ZonedDateTime =
            ZonedDateTime.of(2026, 8, 23, hour, minute, 0, 0, zone)

        assertEquals(z(4, 0), nextHomeHeaderBoundary(z(3, 50)))
        assertEquals(z(12, 0), nextHomeHeaderBoundary(z(4, 10)))
        assertEquals(z(16, 0), nextHomeHeaderBoundary(z(12, 10)))
        assertEquals(z(0, 0).plusDays(1), nextHomeHeaderBoundary(z(16, 10)))
        assertEquals(z(0, 0).plusDays(1), nextHomeHeaderBoundary(z(23, 50)))
        assertEquals(z(4, 0), nextHomeHeaderBoundary(z(0, 10)))
    }

    @Test
    fun `midnight keeps evening period but advances daily message`() {
        val before = LocalDateTime.of(2026, 8, 23, 23, 59)
        val after = LocalDateTime.of(2026, 8, 24, 0, 0)
        assertEquals(HomeHeaderPeriod.EVENING, homeHeaderPeriodFor(before.toLocalTime()))
        assertEquals(HomeHeaderPeriod.EVENING, homeHeaderPeriodFor(after.toLocalTime()))
        assertNotEquals(homeHeaderCopyFor(before).phrase, homeHeaderCopyFor(after).phrase)
    }

    @Test
    fun `blank display name uses existing safe fallback`() {
        assertEquals("Verto", homeDisplayName("  "))
    }
}
