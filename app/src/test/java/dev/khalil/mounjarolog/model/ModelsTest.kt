package dev.khalil.mounjarolog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ModelsTest {
    private val injectionDate = LocalDate.of(2026, 10, 1)

    @Test
    fun doseWindowIncludesInjectionDateThroughSixDaysAfter() {
        val data = AppData(
            injections = listOf(Injection(1, injectionDate, 5.0))
        )

        assertEquals(5.0, data.doseContextFor(injectionDate).doseMg!!, 0.0)
        assertEquals(1, data.doseContextFor(injectionDate).dayInWindow)
        assertEquals(5.0, data.doseContextFor(injectionDate.plusDays(6)).doseMg!!, 0.0)
        assertEquals(7, data.doseContextFor(injectionDate.plusDays(6)).dayInWindow)
        assertNull(data.doseContextFor(injectionDate.plusDays(7)).doseMg)
        assertNull(data.doseContextFor(injectionDate.plusDays(7)).dayInWindow)
    }

    @Test
    fun newestInjectionDefinesContextWhenWindowsOverlap() {
        val data = AppData(
            injections = listOf(
                Injection(1, injectionDate, 5.0),
                Injection(2, injectionDate.plusDays(5), 7.5)
            )
        )

        val context = data.doseContextFor(injectionDate.plusDays(6))
        assertEquals(7.5, context.doseMg!!, 0.0)
        assertEquals(injectionDate.plusDays(5), context.injectionDate)
        assertEquals(2, context.dayInWindow)
    }

    @Test
    fun weightHelpersUseCalendarOrder() {
        val first = WeightMeasurement(1, injectionDate, 100.0)
        val second = WeightMeasurement(2, injectionDate.plusDays(3), 98.5)
        val data = AppData(weights = listOf(second, first))

        assertEquals(first, data.startingWeight())
        assertEquals(second, data.latestWeight())
    }

    @Test
    fun doseWindowsAreCutByTheNextInjection() {
        val data = listOf(
            Injection(1, injectionDate, 5.0),
            Injection(2, injectionDate.plusDays(4), 7.5),
            Injection(3, injectionDate.plusDays(12), 10.0)
        )

        val windows = effectiveDoseWindows(data)

        assertEquals(injectionDate, windows[0].start)
        assertEquals(injectionDate.plusDays(4), windows[0].endExclusive)
        assertEquals(injectionDate.plusDays(4), windows[1].start)
        assertEquals(injectionDate.plusDays(11), windows[1].endExclusive)
        assertEquals(injectionDate.plusDays(12), windows[2].start)
        assertEquals(injectionDate.plusDays(19), windows[2].endExclusive)
    }

    @Test
    fun continuityOnlyBecomesDottedAfterSevenDaysWithoutDoseOrWeightEvent() {
        val start = injectionDate
        val end = injectionDate.plusDays(20)
        val segments = continuitySegments(
            start = start,
            end = end,
            injectionDates = listOf(injectionDate.plusDays(5), injectionDate.plusDays(13))
        )

        assertEquals(
            listOf(
                ContinuitySegment(start, injectionDate.plusDays(5), false),
                ContinuitySegment(injectionDate.plusDays(5), injectionDate.plusDays(12), false),
                ContinuitySegment(injectionDate.plusDays(12), injectionDate.plusDays(13), true),
                ContinuitySegment(injectionDate.plusDays(13), end, false)
            ),
            segments
        )
    }

}
