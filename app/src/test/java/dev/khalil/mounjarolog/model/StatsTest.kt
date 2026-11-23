package dev.khalil.mounjarolog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private val day0 = LocalDate.of(2026, 7, 6)

    private fun weights(vararg kg: Double) =
        kg.mapIndexed { i, w -> WeightMeasurement(i + 1L, day0.plusDays(i.toLong()), w) }

    @Test
    fun trendOfConstantWeightIsConstantAndSameDayWeighInsAreAveraged() {
        val series = trendSeries(
            weights(100.0, 100.0, 100.0) + WeightMeasurement(9, day0, 102.0)
        )
        assertEquals(3, series.size)
        assertEquals(101.0, series[0].weightKg, 1e-9)
        assertTrue(series.last().trendKg in 100.0..101.0)
    }

    @Test
    fun trendDampsASingleSpike() {
        val series = trendSeries(weights(100.0, 100.0, 101.0))
        assertEquals(100.2, series.last().trendKg, 1e-9)
    }

    @Test
    fun weeklyRateOfSteadyLossIsNegative() {
        val series = trendSeries((0 until 40).map { WeightMeasurement(it + 1L, day0.plusDays(it.toLong()), 100.0 - it * 0.1) })
        val rate = weeklyRate(series)!!
        assertEquals(-0.7, rate, 0.05)
    }

    @Test
    fun weeklyRateNeedsAtLeastAWeek() {
        assertNull(weeklyRate(trendSeries(weights(100.0, 99.0))))
    }

    @Test
    fun shareOfWeeksDownCountsWeekToWeekDrops() {
        val means = listOf(
            WeekMean(day0, 100.0, 3),
            WeekMean(day0.plusWeeks(1), 99.0, 3),
            WeekMean(day0.plusWeeks(2), 99.5, 3)
        )
        assertEquals(0.5, shareOfWeeksDown(means)!!, 1e-9)
    }

    @Test
    fun plateauNeedsThreeStableWeeks() {
        val falling = (0 until 30).map { WeightMeasurement(it + 1L, day0.plusDays(it.toLong()), 100.0 - it * 0.1) }
        assertNull(plateauWeeks(trendSeries(falling)))

        val flat = (0 until 30).map { WeightMeasurement(it + 1L, day0.plusDays(it.toLong()), 95.0 + (it % 2) * 0.2) }
        assertEquals(4, plateauWeeks(trendSeries(flat)))
    }

    @Test
    fun dosePhasesGroupConsecutiveDosesAndMeasureChange() {
        val data = AppData(
            weights = (0 until 21).map { WeightMeasurement(it + 1L, day0.plusDays(it.toLong()), 100.0 - it * 0.1) },
            injections = listOf(
                Injection(1, day0, 2.5),
                Injection(2, day0.plusDays(7), 2.5),
                Injection(3, day0.plusDays(14), 5.0)
            )
        )
        val series = trendSeries(data.weights)
        val phases = dosePhases(data, series, today = day0.plusDays(20))
        assertEquals(2, phases.size)
        assertEquals(2, phases[0].injections)
        assertEquals(14, phases[0].days)
        assertEquals(day0.plusDays(21), phases[1].endExclusive)
        assertTrue(phases[0].changeKg!! < 0)

        val summaries = doseSummaries(phases, today = day0.plusDays(20))
        assertEquals(listOf(2.5, 5.0), summaries.map { it.doseMg })
        assertTrue(summaries.last().ongoing)
        assertNotNull(summaries.first().kgPerWeek)
    }

    @Test
    fun cycleRowsPlaceDeviationsOnCycleDays() {
        val data = AppData(
            weights = weights(100.0, 100.0, 100.0),
            injections = listOf(Injection(1, day0, 5.0))
        )
        val rows = cycleRows(data, trendSeries(data.weights))
        assertEquals(1, rows.size)
        assertEquals(0.0, rows[0].deviations[0]!!, 1e-9)
        assertNull(rows[0].deviations[5])
    }

    @Test
    fun appetiteIsSplitByCycleDay() {
        val data = AppData(
            injections = listOf(Injection(1, day0, 5.0)),
            dayLogs = listOf(
                DayLog(1, day0, appetite = 1),
                DayLog(2, day0.plusDays(5), appetite = 4)
            )
        )
        assertEquals(1.0 to 4.0, appetiteByCycle(data))
    }

    @Test
    fun goalForecastOnlyWhenTrendHeadsThere() {
        val today = day0
        assertEquals(today.plusDays(14), goalForecast(91.0, -0.5, 90.0, today))
        assertNull(goalForecast(91.0, 0.1, 90.0, today))
        assertNull(goalForecast(89.0, -0.5, 90.0, today))
    }

    @Test
    fun siteRotationFollowsTheLastUsedSite() {
        assertEquals(InjectionSites[0], suggestNextSite(emptyList()))
        assertEquals("Bauch rechts", suggestNextSite(listOf(Injection(1, day0, 5.0, "bauch links"))))
        assertEquals(InjectionSites[0], suggestNextSite(listOf(Injection(1, day0, 5.0, InjectionSites.last()))))
    }

    @Test
    fun percentMilestoneRoundsDownToFiveSteps() {
        assertEquals(10, percentMilestone(100.0, 88.0))
        assertNull(percentMilestone(100.0, 97.0))
    }
}
