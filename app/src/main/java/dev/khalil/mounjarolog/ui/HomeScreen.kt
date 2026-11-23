package dev.khalil.mounjarolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.dosePhases
import dev.khalil.mounjarolog.model.doseContextFor
import dev.khalil.mounjarolog.model.goalForecast
import dev.khalil.mounjarolog.model.latestInjection
import dev.khalil.mounjarolog.model.percentMilestone
import dev.khalil.mounjarolog.model.plateauWeeks
import dev.khalil.mounjarolog.model.shareOfWeeksDown
import dev.khalil.mounjarolog.model.suggestNextSite
import dev.khalil.mounjarolog.model.trendSeries
import dev.khalil.mounjarolog.model.weeklyMeans
import dev.khalil.mounjarolog.model.weeklyRate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private val MonthName = DateTimeFormatter.ofPattern("MMMM", German)

@Composable
fun HomeScreen(data: AppData, onAdd: (EntryTab) -> Unit) {
    val today = LocalDate.now()
    val series = remember(data) { trendSeries(data.weights) }
    val rate = remember(series) { weeklyRate(series) }
    val plateau = remember(series) { plateauWeeks(series) }
    val phases = remember(data) { dosePhases(data, series, today) }
    val context = data.doseContextFor(today)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 104.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(today.format(WeekdayDateFormatter), style = MaterialTheme.typography.bodyMedium, color = Muted)
                if (context.doseMg != null) {
                    Text(
                        formatDose(context.doseMg) + " · Tag " + context.dayInWindow,
                        style = MaterialTheme.typography.labelMedium,
                        color = Paper,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Ink).padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            val last = series.lastOrNull()
            if (last == null) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text("Willkommen.", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Trag dein erstes Gewicht ein. Ab ein paar Messungen zeigt die App deinen geglätteten Trend statt einzelner Tageswerte.",
                        style = MaterialTheme.typography.bodyLarge, color = Muted, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    if (plateau != null) PlateauHeader(data, plateau, series.first().weightKg, last.trendKg, onAdd)
                    else TrendHeader(data, last.weightKg, last.trendKg, last.date, rate, today)
                }

                TrendChart(
                    series = series,
                    phases = phases,
                    milestones = data.milestones,
                    start = rangeStart(RangePreset.SIX, series, today),
                    end = today,
                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 20.dp)
                )

                Column(Modifier.padding(horizontal = 24.dp)) {
                    SinceStart(data, series.first().weightKg, series.first().date, last.trendKg, rate, today)
                }
            }

            Column(Modifier.padding(horizontal = 24.dp)) { CycleStrip(data, today) }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Paper)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccentButton("Gewicht eintragen", { onAdd(EntryTab.WEIGHT) }, Modifier.weight(1f))
            Box(
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Fill)
                    .clickable(role = Role.Button) {
                        val due = data.latestInjection()?.date?.plusDays(7)
                        onAdd(if (due == null || !due.isAfter(today)) EntryTab.DOSE else EntryTab.NOTES)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Spritze oder Notiz eintragen", tint = Ink)
            }
        }
    }
}

@Composable
private fun TrendHeader(data: AppData, lastWeight: Double, trend: Double, lastDate: LocalDate, rate: Double?, today: LocalDate) {
    Label("Dein Trend", Modifier.padding(top = 12.dp))
    Row(verticalAlignment = Alignment.Top) {
        Text(oneDecimal(trend), style = MaterialTheme.typography.displayLarge)
        Text("kg", style = MaterialTheme.typography.titleMedium, color = Muted, modifier = Modifier.padding(start = 8.dp, top = 14.dp))
    }
    // The single measurement is context, never the headline: daily water swings are not progress.
    val text = buildAnnotatedString {
        append(if (lastDate == today) "Heute gewogen: " else "Zuletzt gewogen (" + lastDate.format(ShortDateFormatter) + "): ")
        append(oneDecimal(lastWeight) + " kg.")
        if (abs(lastWeight - trend) >= 0.5) append(" Ein einzelner Tag sagt wenig, Wasser schwankt oft um 1–2 kg.")
        if (rate != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Ink)) {
                append(
                    when {
                        rate <= -0.05 -> " Der Trend fällt: " + signed(rate, " kg pro Woche.")
                        rate >= 0.05 -> " Der Trend steigt leicht: " + signed(rate, " kg pro Woche.")
                        else -> " Der Trend ist gerade stabil."
                    }
                )
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Muted, modifier = Modifier.padding(top = 12.dp))
    if (data.weights.size < 5) {
        Text("Der Trend wird mit jeder Messung genauer.", style = MaterialTheme.typography.bodySmall, color = Faint, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PlateauHeader(data: AppData, weeks: Int, startKg: Double, trend: Double, onAdd: (EntryTab) -> Unit) {
    Text(
        "Seit $weeks Wochen stabil.",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 16.dp)
    )
    Text(
        "Plateaus sind bei dieser Behandlung häufig und kein Rückschritt.",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
        color = Muted
    )
    Section("Was sich trotzdem zeigt") {
        MetricRow("Gehalten", oneDecimal(startKg - trend) + " kg", "unter deinem Startgewicht, ohne Rückgang")
        val since = LocalDate.now().minusWeeks(weeks.toLong())
        val waists = data.dayLogs.filter { it.waistCm != null }.sortedBy { it.date }
        val before = waists.lastOrNull { !it.date.isAfter(since) } ?: waists.firstOrNull()
        val now = waists.lastOrNull()
        if (before != null && now != null && now.date.isAfter(before.date)) {
            MetricRow("Taille", signed(now.waistCm!! - before.waistCm!!, " cm"), "seit " + before.date.format(ShortDateFormatter))
        }
        val appetite = data.dayLogs.filter { !it.date.isBefore(since) }.mapNotNull { it.appetite }
        if (appetite.isNotEmpty()) {
            MetricRow("Appetit", "Ø " + oneDecimal(appetite.average()) + " von 5", "in dieser Zeit")
        }
    }
    Section("Vielleicht hilfreich") {
        Text(
            "Notiere Fragen für deinen nächsten Arzttermin, etwa ob eine Anpassung sinnvoll ist. Auch der Taillenumfang zeigt oft Veränderung, wenn die Waage es nicht tut.",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "Frage notieren",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Fill)
                .clickable(role = Role.Button) { onAdd(EntryTab.NOTES) }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SinceStart(data: AppData, startKg: Double, startDate: LocalDate, trend: Double, rate: Double?, today: LocalDate) {
    val share = remember(data) { shareOfWeeksDown(weeklyMeans(data.weights)) }
    val goal = data.milestones.filter { it.targetKg < trend }.maxByOrNull { it.targetKg }
    val forecast = goal?.let { goalForecast(trend, rate, it.targetKg, today) }

    Section("Seit Start") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatBlock(signed(trend - startKg, " kg"), "seit " + startDate.format(ShortDateFormatter))
            if (share != null) StatBlock((share * 100).roundToInt().toString() + " %", "der Wochen im Minus")
            when {
                goal != null && forecast != null -> StatBlock(forecast.format(MonthName), "Ziel " + oneDecimal(goal.targetKg) + " kg, beim jetzigen Tempo")
                rate != null -> StatBlock(signed(rate), "kg pro Woche")
            }
        }
        percentMilestone(startKg, trend)?.let {
            Text(
                "Meilenstein: $it % deines Startgewichts.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun CycleStrip(data: AppData, today: LocalDate) {
    val last = data.latestInjection()
    Section("Dieser Zyklus") {
        if (last == null) {
            Text("Noch keine Spritze eingetragen.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            return@Section
        }
        val day = ChronoUnit.DAYS.between(last.date, today).toInt()
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(7) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                i == day -> Accent
                                i < day -> Ink
                                else -> Hairline
                            }
                        )
                )
            }
        }
        val due = last.date.plusDays(7)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Spritze " + last.date.format(ShortWeekdayFormatter) + (if (last.injectionSite.isNotBlank()) " · " + last.injectionSite else ""),
                style = MaterialTheme.typography.bodyMedium, color = Muted
            )
            Text(
                when {
                    due.isAfter(today) -> "nächste " + due.format(ShortWeekdayFormatter)
                    due == today -> "heute fällig"
                    else -> "fällig seit " + due.format(ShortWeekdayFormatter)
                },
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
            )
        }
        if (!due.isAfter(today)) {
            Text(
                "Vorschlag für die Stelle: " + suggestNextSite(data.injections),
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
