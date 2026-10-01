package dev.khalil.mounjarolog.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DoseWindow(
    val injection: Injection,
    val start: LocalDate,
    val endExclusive: LocalDate
)

data class ContinuitySegment(
    val start: LocalDate,
    val end: LocalDate,
    val dotted: Boolean
)

/**
 * Each injection owns at most seven calendar days, but a newer injection cuts the
 * previous window immediately so graph backgrounds never overlap.
 */
fun effectiveDoseWindows(injections: List<Injection>): List<DoseWindow> {
    val ordered = injections.sortedWith(compareBy<Injection> { it.date }.thenBy { it.id })
    return ordered.mapIndexedNotNull { index, injection ->
        val naturalEnd = injection.date.plusDays(7)
        val nextDate = ordered.getOrNull(index + 1)?.date
        val end = when {
            nextDate == null -> naturalEnd
            nextDate.isBefore(naturalEnd) -> nextDate
            else -> naturalEnd
        }
        if (!end.isAfter(injection.date)) null
        else DoseWindow(injection, injection.date, end)
    }
}

/**
 * A line remains solid for seven days after every weight or dose event.
 * It becomes dotted only after a continuous period longer than seven days with
 * neither another weight measurement nor another injection.
 */
fun continuitySegments(
    start: LocalDate,
    end: LocalDate,
    injectionDates: List<LocalDate>
): List<ContinuitySegment> {
    if (!end.isAfter(start)) return emptyList()

    val resetEvents = injectionDates
        .asSequence()
        .filter { it.isAfter(start) && it.isBefore(end) }
        .distinct()
        .sorted()
        .toList()

    val boundaries = resetEvents + end
    val result = mutableListOf<ContinuitySegment>()
    var cursor = start

    boundaries.forEach { boundary ->
        if (!boundary.isAfter(cursor)) return@forEach
        val days = ChronoUnit.DAYS.between(cursor, boundary)
        if (days <= 7) {
            result += ContinuitySegment(cursor, boundary, dotted = false)
        } else {
            val dottedStart = cursor.plusDays(7)
            result += ContinuitySegment(cursor, dottedStart, dotted = false)
            result += ContinuitySegment(dottedStart, boundary, dotted = true)
        }
        cursor = boundary
    }

    return result
}
