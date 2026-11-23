package dev.khalil.mounjarolog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.CycleRow
import dev.khalil.mounjarolog.model.appetiteByCycle
import dev.khalil.mounjarolog.model.cycleDayAverages
import dev.khalil.mounjarolog.model.cycleRows
import dev.khalil.mounjarolog.model.doseSummaries
import dev.khalil.mounjarolog.model.dosePhases
import dev.khalil.mounjarolog.model.sideEffectDaysByDose
import dev.khalil.mounjarolog.model.trendSeries
import dev.khalil.mounjarolog.model.weeklyMeans
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.abs
import kotlin.math.min

@Composable
fun InsightsScreen(data: AppData) {
    val today = LocalDate.now()
    var range by rememberSaveable { mutableStateOf(RangePreset.THREE) }
    val series = remember(data) { trendSeries(data.weights) }
    val phases = remember(data) { dosePhases(data, series, today) }
    val summaries = remember(phases) { doseSummaries(phases, today) }
    val weeks = remember(data) { weeklyMeans(data.weights) }
    val cycles = remember(data) { cycleRows(data, series) }
    val dayAverages = remember(cycles) { cycleDayAverages(cycles) }
    val (appetiteEarly, appetiteLate) = remember(data) { appetiteByCycle(data) }
    val sideEffects = remember(data) { sideEffectDaysByDose(data) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text("Auswertung", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp, bottom = 16.dp))
        if (series.isEmpty()) {
            EmptyState("Sobald du ein paar Mal gewogen hast, erscheinen hier Trend, Wochenmittel und Muster.")
            return@Column
        }
        Segmented(RangePreset.entries.map { it.label }, range.ordinal, { range = RangePreset.entries[it] })
        TrendChart(
            series = series,
            phases = phases,
            milestones = data.milestones,
            start = rangeStart(range, series, today),
            end = today,
            yAxis = true,
            modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 16.dp)
        )
        Text("Linie = geglätteter Trend · Punkte = einzelne Messungen · Tippen für Werte", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 6.dp))

        if (weeks.isNotEmpty()) {
            Section("Wochenmittel") {
                TableRow(listOf("KW", "Ø kg", "Δ", "Wägungen"), header = true)
                val recent = weeks.takeLast(8)
                recent.asReversed().forEach { w ->
                    val previous = weeks.getOrNull(weeks.indexOf(w) - 1)
                    TableRow(
                        listOf(
                            w.weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString(),
                            oneDecimal(w.meanKg),
                            previous?.let { signed(w.meanKg - it.meanKg) } ?: "",
                            w.count.toString() + "×"
                        )
                    )
                }
            }
        }

        if (summaries.isNotEmpty()) {
            Section("Je Dosis") {
                summaries.forEach { s ->
                    MetricRow(
                        formatDose(s.doseMg),
                        s.kgPerWeek?.let { signed(it, " kg/Wo") } ?: "–",
                        oneDecimal(s.weeks).removeSuffix(",0") + " Wochen" + (if (s.ongoing) " · aktuell" else "") +
                            (s.changeKg?.let { " · gesamt " + signed(it, " kg") } ?: "")
                    )
                }
                Text(
                    "Gemessen am Trend. Spätere Phasen sind oft langsamer, weil weniger Gewicht da ist, das sich verändert.",
                    style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (cycles.size >= 2) CyclePattern(cycles, dayAverages)

        if (appetiteEarly != null || appetiteLate != null) {
            Section("Appetit im Zyklus") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    StatBlock(appetiteEarly?.let { oneDecimal(it) + " / 5" } ?: "–", "Tag 1–3 nach der Spritze")
                    StatBlock(appetiteLate?.let { oneDecimal(it) + " / 5" } ?: "–", "Tag 5–7")
                }
            }
        }

        if (sideEffects.isNotEmpty()) {
            Section("Tage mit Nebenwirkungen") {
                sideEffects.toSortedMap().forEach { (dose, days) -> MetricRow(formatDose(dose), "$days Tage") }
            }
        }

        Text(
            "Alle Werte zeigen zeitliche Zusammenhänge, keine Ursachen. Fragen zu Dosis oder Behandlung mit deiner Ärztin oder deinem Arzt klären.",
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 32.dp)
        )
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = if (header) 6.dp else 11.dp)) {
            cells.forEachIndexed { i, cell ->
                Text(
                    cell,
                    modifier = Modifier.weight(if (i == 0) 0.7f else 1f),
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                    style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
                    color = if (header) Muted else Ink
                )
            }
        }
        HorizontalDivider(color = if (header) Ink else Hairline)
    }
}

@Composable
private fun CyclePattern(rows: List<CycleRow>, averages: List<Double?>) {
    val strongest = averages.withIndex().filter { it.value != null }.maxByOrNull { abs(it.value!!) }
    val spread = averages.filterNotNull().let { if (it.size < 2) 0.0 else it.max() - it.min() }
    Section("Zyklus-Muster") {
        Text(
            if (strongest != null && spread >= 0.2)
                "An Tag ${strongest.index + 1} nach der Spritze liegst du im Schnitt " +
                    oneDecimal(abs(strongest.value!!)) + " kg " + (if (strongest.value!! > 0) "über" else "unter") + " deinem Trend."
            else "Kein deutliches Muster nach Zyklustag.",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Jede Zeile ein Zyklus, jede Spalte ein Tag nach der Spritze. Gefüllt = über dem Trend, Ring = darunter, größer = stärker.",
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(48.dp))
            (1..7).forEach { Text("T$it", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Muted, textAlign = TextAlign.Center) }
        }
        rows.takeLast(12).asReversed().forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.injectionDate.format(ShortDateFormatter), Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
                row.deviations.forEach { value ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) { Dot(value) }
                }
            }
        }
    }
}

@Composable
private fun Dot(value: Double?) {
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = value?.let { signed(it, " kg zum Trend") } ?: "keine Messung" }
    ) {
        val maxR = size.minDimension / 2 * 0.85f
        if (value == null) {
            drawCircle(Hairline, maxR * 0.18f)
            return@Canvas
        }
        val r = maxR * (0.25f + 0.75f * min(1.0, abs(value) / 0.6).toFloat())
        if (value >= 0) drawCircle(Ink, r, Offset(size.width / 2, size.height / 2))
        else drawCircle(Ink, r - 1.dp.toPx(), Offset(size.width / 2, size.height / 2), style = Stroke(2.dp.toPx()))
    }
}
