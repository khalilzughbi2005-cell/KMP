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

        assertEquals(5.0, data.doseContextFor(injectionDate).doseMg, 0.0)
        assertEquals(1, data.doseContextFor(injectionDate).dayInWindow)
        assertEquals(5.0, data.doseContextFor(injectionDate.plusDays(6)).doseMg, 0.0)
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
}
