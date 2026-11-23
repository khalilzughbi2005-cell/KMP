package dev.khalil.mounjarolog.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.pow

/** One point per weigh-in day: the day's mean weight and the smoothed trend up to that day. */
data class TrendPoint(val date: LocalDate, val weightKg: Double, val trendKg: Double)

/** Share of each new day's weight that flows into the trend. Higher reacts faster, lower smooths more. */
const val TREND_ALPHA = 0.2

/**
 * Exponentially smoothed weight. Several weigh-ins on one day are averaged first.
 * A gap of n days weighs the new value as if n smoothing steps had passed.
 */
fun trendSeries(weights: List<WeightMeasurement>): List<TrendPoint> {
    val daily = weights.groupBy { it.date }
        .mapValues { (_, items) -> items.map { it.weightKg }.average() }
        .toSortedMap()
    var trend: Double? = null
    var previous: LocalDate? = null
    return daily.map { (date, weight) ->
        val last = trend
        val next = if (last == null || previous == null) weight else {
            val days = ChronoUnit.DAYS.between(previous, date).coerceAtLeast(1).toInt()
            weight + (last - weight) * (1 - TREND_ALPHA).pow(days)
        }
        trend = next
        previous = date
        TrendPoint(date, weight, next)
    }
}

fun List<TrendPoint>.trendAt(date: LocalDate): Double? = lastOrNull { !it.date.isAfter(date) }?.trendKg

/** Trend slope in kg per week over the last [days] days (least squares). Null without enough spread. */
fun weeklyRate(series: List<TrendPoint>, days: Long = 28): Double? {
    val last = series.lastOrNull() ?: return null
    val window = series.filter { !it.date.isBefore(last.date.minusDays(days)) }
    if (window.size < 2) return null
    val xs = window.map { ChronoUnit.DAYS.between(window.first().date, it.date).toDouble() }
    if (xs.last() < 7) return null
    val ys = window.map { it.trendKg }
    val mx = xs.average()
    val my = ys.average()
    val den = xs.sumOf { (it - mx) * (it - mx) }
    if (den == 0.0) return null
    val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
    return slope * 7.0
}

data class WeekMean(val weekStart: LocalDate, val meanKg: Double, val count: Int)

/** Mean of all weigh-ins per calendar week (Monday start). */
fun weeklyMeans(weights: List<WeightMeasurement>): List<WeekMean> =
    weights.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .map { (start, items) -> WeekMean(start, items.map { it.weightKg }.average(), items.size) }
        .sortedBy { it.weekStart }

/** Share of week-to-week comparisons where the weekly mean went down. */
fun shareOfWeeksDown(means: List<WeekMean>): Double? {
    val pairs = means.zipWithNext()
    if (pairs.isEmpty()) return null
    return pairs.count { (a, b) -> b.meanKg < a.meanKg }.toDouble() / pairs.size
}

/**
 * Whole weeks (at least 3) for which the trend has stayed within [toleranceKg] of today's trend.
 * shortcut: fixed 3 weeks / 0.5 kg are app conventions, not clinical thresholds.
 */
fun plateauWeeks(series: List<TrendPoint>, toleranceKg: Double = 0.5): Int? {
    val last = series.lastOrNull() ?: return null
    var weeks = 0
    while (true) {
        val boundary = last.date.minusDays((weeks + 1) * 7L)
        if (series.first().date.isAfter(boundary)) break
        val stable = series.filter { !it.date.isBefore(boundary) }
            .all { abs(it.trendKg - last.trendKg) <= toleranceKg }
        if (!stable) break
        weeks++
    }
    return weeks.takeIf { it >= 3 }
}

/** Consecutive injections with the same dose. */
data class DosePhase(
    val doseMg: Double,
    val start: LocalDate,
    val endExclusive: LocalDate,
    val injections: Int,
    val changeKg: Double?
) {
    val days: Long get() = ChronoUnit.DAYS.between(start, endExclusive)
}

fun dosePhases(data: AppData, series: List<TrendPoint>, today: LocalDate): List<DosePhase> {
    val ordered = data.injections.sortedWith(compareBy<Injection> { it.date }.thenBy { it.id })
    val groups = mutableListOf<MutableList<Injection>>()
    ordered.forEach { injection ->
        val current = groups.lastOrNull()
        if (current != null && current.last().doseMg == injection.doseMg) current += injection
        else groups += mutableListOf(injection)
    }
    return groups.mapIndexed { index, group ->
        val start = group.first().date
        val end = groups.getOrNull(index + 1)?.first()?.date
            ?: minOf(today.plusDays(1), group.last().date.plusDays(7))
        val before = series.trendAt(start) ?: series.firstOrNull { !it.date.isBefore(start) && it.date.isBefore(end) }?.trendKg
        val after = series.trendAt(end.minusDays(1))
        val change = if (before != null && after != null && series.any { it.date.isAfter(start) && it.date.isBefore(end) }) after - before else null
        DosePhase(group.first().doseMg, start, end, group.size, change)
    }
}

data class DoseSummary(val doseMg: Double, val weeks: Double, val changeKg: Double?, val kgPerWeek: Double?, val ongoing: Boolean)

fun doseSummaries(phases: List<DosePhase>, today: LocalDate): List<DoseSummary> =
    phases.groupBy { it.doseMg }.map { (dose, items) ->
        val weeks = items.sumOf { it.days } / 7.0
        val measured = items.filter { it.changeKg != null }
        val change = if (measured.isEmpty()) null else measured.sumOf { it.changeKg!! }
        val measuredWeeks = measured.sumOf { it.days } / 7.0
        DoseSummary(
            doseMg = dose,
            weeks = weeks,
            changeKg = change,
            kgPerWeek = if (change != null && measuredWeeks >= 1) change / measuredWeeks else null,
            ongoing = phases.lastOrNull()?.let { it.doseMg == dose && it.endExclusive.isAfter(today) } == true
        )
    }.sortedBy { it.doseMg }

/** For every injection cycle: each day's weight minus its trend, by cycle day 1..7. */
data class CycleRow(val injectionDate: LocalDate, val doseMg: Double, val deviations: List<Double?>)

fun cycleRows(data: AppData, series: List<TrendPoint>): List<CycleRow> {
    val byDate = series.associateBy { it.date }
    return effectiveDoseWindows(data.injections).map { window ->
        val values = (0 until 7).map { day ->
            val date = window.start.plusDays(day.toLong())
            if (date.isBefore(window.endExclusive)) byDate[date]?.let { it.weightKg - it.trendKg } else null
        }
        CycleRow(window.start, window.injection.doseMg, values)
    }.filter { row -> row.deviations.any { it != null } }
}

fun cycleDayAverages(rows: List<CycleRow>): List<Double?> =
    (0 until 7).map { day -> rows.mapNotNull { it.deviations[day] }.takeIf { it.isNotEmpty() }?.average() }

/** Average appetite on cycle days 1–3 and 5–7. */
fun appetiteByCycle(data: AppData): Pair<Double?, Double?> {
    val rated = data.dayLogs.mapNotNull { log ->
        val day = data.doseContextFor(log.date).dayInWindow
        if (log.appetite != null && day != null) day to log.appetite else null
    }
    fun avg(days: IntRange) = rated.filter { it.first in days }.map { it.second }.takeIf { it.isNotEmpty() }?.average()
    return avg(1..3) to avg(5..7)
}

/** Days with a side-effect note, by dose active on that day. */
fun sideEffectDaysByDose(data: AppData): Map<Double, Int> =
    data.dayLogs.filter { it.sideEffects.isNotBlank() }
        .mapNotNull { data.doseContextFor(it.date).doseMg }
        .groupingBy { it }.eachCount()

/** Date the trend would reach [targetKg] at the current rate, or null when not heading there. */
fun goalForecast(currentKg: Double, kgPerWeek: Double?, targetKg: Double, today: LocalDate): LocalDate? {
    if (kgPerWeek == null || kgPerWeek > -0.05 || currentKg <= targetKg) return null
    val weeks = (currentKg - targetKg) / -kgPerWeek
    if (weeks > 104) return null
    return today.plusDays((weeks * 7).toLong())
}

val InjectionSites = listOf(
    "Bauch links", "Bauch rechts",
    "Oberschenkel links", "Oberschenkel rechts",
    "Oberarm links", "Oberarm rechts"
)

/** Next site in the rotation after the most recently used one. */
fun suggestNextSite(injections: List<Injection>): String {
    val last = injections.maxWithOrNull(compareBy<Injection> { it.date }.thenBy { it.id })?.injectionSite
    val index = InjectionSites.indexOfFirst { it.equals(last?.trim(), ignoreCase = true) }
    return InjectionSites[(index + 1) % InjectionSites.size]
}

/** Highest full 5 % step of the starting weight that the trend has reached, e.g. 10. */
fun percentMilestone(startKg: Double, trendKg: Double): Int? {
    if (startKg <= 0) return null
    val step = ((startKg - trendKg) / startKg * 100 / 5).toInt() * 5
    return step.takeIf { it >= 5 }
}
