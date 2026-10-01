package dev.khalil.mounjarolog.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class WeightMeasurement(
    val id: Long,
    val date: LocalDate,
    val weightKg: Double,
    val note: String = ""
)

data class Injection(
    val id: Long,
    val date: LocalDate,
    val doseMg: Double,
    val injectionSite: String = "",
    val note: String = ""
)

data class DayLog(
    val id: Long,
    val date: LocalDate,
    val appetite: Int? = null,
    val sideEffects: String = "",
    val note: String = ""
)

data class Milestone(
    val id: Long,
    val targetKg: Double,
    val label: String = ""
)

data class AppData(
    val weights: List<WeightMeasurement> = emptyList(),
    val injections: List<Injection> = emptyList(),
    val dayLogs: List<DayLog> = emptyList(),
    val milestones: List<Milestone> = emptyList()
)

data class DoseContext(
    val doseMg: Double?,
    val injectionDate: LocalDate?,
    val dayInWindow: Int?
)

fun AppData.doseContextFor(date: LocalDate): DoseContext {
    val injection = injections
        .asSequence()
        .filter { !it.date.isAfter(date) }
        .maxWithOrNull(compareBy<Injection> { it.date }.thenBy { it.id })

    if (injection == null) return DoseContext(null, null, null)
    val days = ChronoUnit.DAYS.between(injection.date, date).toInt()
    return if (days in 0..6) {
        DoseContext(injection.doseMg, injection.date, days + 1)
    } else {
        DoseContext(null, injection.date, null)
    }
}

fun AppData.latestWeight(): WeightMeasurement? = weights.maxByOrNull { it.date }
fun AppData.startingWeight(): WeightMeasurement? = weights.minByOrNull { it.date }
fun AppData.latestInjection(): Injection? = injections.maxWithOrNull(compareBy<Injection> { it.date }.thenBy { it.id })

val SupportedDoses = listOf(2.5, 5.0, 7.5, 10.0, 12.5, 15.0)
