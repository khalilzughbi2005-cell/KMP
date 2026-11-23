package dev.khalil.mounjarolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.khalil.mounjarolog.MainViewModel
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.DayLog
import dev.khalil.mounjarolog.model.Injection
import dev.khalil.mounjarolog.model.WeightMeasurement
import dev.khalil.mounjarolog.model.doseContextFor
import java.time.LocalDate
import java.time.YearMonth

private sealed class Record {
    abstract val date: LocalDate
    abstract val key: String
    abstract val order: Int

    data class Weight(val item: WeightMeasurement) : Record() {
        override val date = item.date; override val key = "w" + item.id; override val order = 3
    }

    data class Dose(val item: Injection) : Record() {
        override val date = item.date; override val key = "d" + item.id; override val order = 2
    }

    data class Note(val item: DayLog) : Record() {
        override val date = item.date; override val key = "n" + item.id; override val order = 1
    }
}

@Composable
fun HistoryScreen(
    data: AppData,
    viewModel: MainViewModel,
    notify: (Result<Unit>, String) -> Unit,
    onAddForDate: (LocalDate) -> Unit
) {
    var view by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(0) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    val editors = rememberEditors()

    val rows = remember(data, filter) {
        buildList<Record> {
            if (filter == 0 || filter == 1) data.weights.forEach { add(Record.Weight(it)) }
            if (filter == 0 || filter == 2) data.injections.forEach { add(Record.Dose(it)) }
            if (filter == 0 || filter == 3) data.dayLogs.forEach { add(Record.Note(it)) }
        }.sortedWith(compareByDescending<Record> { it.date }.thenByDescending { it.order })
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Text("Verlauf", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp, bottom = 16.dp))
            Segmented(listOf("Liste", "Monat"), view, { view = it })
        }
        if (view == 0) {
            item {
                Spacer(Modifier.height(16.dp))
                ChoiceChips(listOf(0, 1, 2, 3), filter, { listOf("Alles", "Gewicht", "Spritzen", "Notizen")[it] }) { filter = it }
                Spacer(Modifier.height(8.dp))
            }
            if (rows.isEmpty()) item { EmptyState("Hier erscheinen deine Einträge.") }
            items(rows, key = { it.key }) { row ->
                RecordRow(row, data) {
                    when (row) {
                        is Record.Weight -> editors.weight = row.item
                        is Record.Dose -> editors.injection = row.item
                        is Record.Note -> editors.dayLog = row.item.date
                    }
                }
            }
        } else {
            item {
                MonthGrid(data, YearMonth.parse(monthText), { monthText = it.toString() }) { selectedDay = it.toString() }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    selectedDay?.let { text ->
        DayDetailsDialog(
            date = LocalDate.parse(text),
            data = data,
            onDismiss = { selectedDay = null },
            onAdd = { selectedDay = null; onAddForDate(LocalDate.parse(text)) },
            onPick = { selectedDay = null; it() },
            editors = editors
        )
    }
    RecordEditorDialogs(data, editors, viewModel, notify)
}

@Composable
private fun RecordRow(row: Record, data: AppData, onClick: () -> Unit) {
    val (title, value, detail) = when (row) {
        is Record.Weight -> {
            val ctx = data.doseContextFor(row.item.date)
            Triple("Gewicht", oneDecimal(row.item.weightKg) + " kg", listOfNotNull(ctx.doseMg?.let { "Zyklustag " + ctx.dayInWindow }, row.item.note.ifBlank { null }).joinToString(" · "))
        }
        is Record.Dose -> Triple("Spritze", formatDose(row.item.doseMg), listOf(row.item.injectionSite, row.item.note).filter { it.isNotBlank() }.joinToString(" · "))
        is Record.Note -> Triple(
            "Notiz",
            row.item.appetite?.let { "Appetit $it/5" } ?: row.item.waistCm?.let { oneDecimal(it) + " cm" } ?: "",
            listOf(row.item.sideEffects, row.item.note, row.item.question.ifBlank { "" }.let { if (it.isBlank()) "" else "Frage: $it" }).filter { it.isNotBlank() }.joinToString(" · ")
        )
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Text(row.date.format(ShortWeekdayFormatter), style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(end = 16.dp).size(width = 72.dp, height = 20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider(color = Hairline)
    }
}

@Composable
private fun MonthGrid(data: AppData, month: YearMonth, onMonth: (YearMonth) -> Unit, onDay: (LocalDate) -> Unit) {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
        Text(month.atDay(1).format(MonthFormatter), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        TextButton(onClick = { onMonth(month.plusMonths(1)) }) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Muted, textAlign = TextAlign.Center)
        }
    }
    for (week in 0 until (leading + days + 6) / 7) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0..6) {
                val day = week * 7 + col - leading + 1
                if (day !in 1..days) {
                    Spacer(Modifier.weight(1f).aspectRatio(0.8f))
                    continue
                }
                val date = month.atDay(day)
                val weight = data.weights.lastOrNull { it.date == date }
                val injected = data.injections.any { it.date == date }
                val noted = data.dayLogs.any { it.date == date }
                Column(
                    Modifier
                        .weight(1f)
                        .aspectRatio(0.8f)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (injected) Ink else if (date == today) Fill else Paper)
                        .clickable { onDay(date) }
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val fg = if (injected) Paper else Ink
                    Text(day.toString(), style = MaterialTheme.typography.labelLarge, color = if (date == today && !injected) Accent else fg)
                    if (weight != null) Text(oneDecimal(weight.weightKg), style = MaterialTheme.typography.labelSmall, color = if (injected) Paper else Muted)
                    if (noted) Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(fg))
                }
            }
        }
    }
    Text(
        "Schwarz = Spritzentag · Zahl = Gewicht · Punkt = Notiz",
        style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 12.dp)
    )
    val inMonth = data.weights.filter { YearMonth.from(it.date) == month }
    if (inMonth.isNotEmpty()) {
        Section("Dieser Monat") {
            MetricRow("Wägungen", inMonth.size.toString())
            MetricRow("Spritzen", data.injections.count { YearMonth.from(it.date) == month }.toString())
            MetricRow("Durchschnitt", oneDecimal(inMonth.map { it.weightKg }.average()) + " kg")
        }
    }
}

/** Which record is being edited; shared by Verlauf and the day dialog. */
class Editors {
    var weight by mutableStateOf<WeightMeasurement?>(null)
    var injection by mutableStateOf<Injection?>(null)
    var dayLog by mutableStateOf<LocalDate?>(null)
}

@Composable
fun rememberEditors() = remember { Editors() }

@Composable
private fun DayDetailsDialog(
    date: LocalDate,
    data: AppData,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onPick: (() -> Unit) -> Unit,
    editors: Editors
) {
    val weights = data.weights.filter { it.date == date }
    val injections = data.injections.filter { it.date == date }
    val log = data.dayLogs.lastOrNull { it.date == date }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(WeekdayDateFormatter)) },
        text = {
            Column {
                if (weights.isEmpty() && injections.isEmpty() && log == null) EmptyState("An diesem Tag ist nichts eingetragen.")
                weights.forEach { w -> MetricRow("Gewicht", oneDecimal(w.weightKg) + " kg", onClick = { onPick { editors.weight = w } }) }
                injections.forEach { i -> MetricRow("Spritze", formatDose(i.doseMg), i.injectionSite.ifBlank { null }, onClick = { onPick { editors.injection = i } }) }
                MetricRow(
                    "Notizen", if (log == null) "hinzufügen" else "ansehen",
                    log?.let { listOf(it.sideEffects, it.note).filter { s -> s.isNotBlank() }.joinToString(" · ").ifBlank { null } },
                    onClick = { onPick { editors.dayLog = date } }
                )
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text("Eintragen", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
fun RecordEditorDialogs(
    data: AppData,
    editors: Editors,
    viewModel: MainViewModel,
    notify: (Result<Unit>, String) -> Unit
) {
    editors.weight?.let { item ->
        WeightEditorDialog(
            item = item,
            onDismiss = { editors.weight = null },
            onSave = { date, weight, note ->
                viewModel.updateWeight(item.id, date, weight, note) { notify(it, "Gewicht geändert."); if (it.isSuccess) editors.weight = null }
            },
            onDelete = { viewModel.deleteWeight(item.id) { notify(it, "Gewicht gelöscht."); if (it.isSuccess) editors.weight = null } }
        )
    }
    editors.injection?.let { item ->
        InjectionEditorDialog(
            item = item,
            onDismiss = { editors.injection = null },
            onSave = { date, dose, site, note ->
                viewModel.updateInjection(item.id, date, dose, site, note) { notify(it, "Spritze geändert."); if (it.isSuccess) editors.injection = null }
            },
            onDelete = { viewModel.deleteInjection(item.id) { notify(it, "Spritze gelöscht."); if (it.isSuccess) editors.injection = null } }
        )
    }
    editors.dayLog?.let { date ->
        val log = data.dayLogs.lastOrNull { it.date == date }
        DayLogEditorDialog(
            initialDate = date,
            item = log,
            onDismiss = { editors.dayLog = null },
            onSave = { viewModel.upsertDayLog(it) { r -> notify(r, "Notizen gespeichert."); if (r.isSuccess) editors.dayLog = null } },
            onDelete = log?.let { { viewModel.deleteDayLog(it.id) { r -> notify(r, "Notizen gelöscht."); if (r.isSuccess) editors.dayLog = null } } }
        )
    }
}
