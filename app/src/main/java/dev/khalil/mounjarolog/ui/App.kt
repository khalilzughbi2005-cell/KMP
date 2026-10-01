package dev.khalil.mounjarolog.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khalil.mounjarolog.MainViewModel
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.Injection
import dev.khalil.mounjarolog.model.SupportedDoses
import dev.khalil.mounjarolog.model.WeightMeasurement
import dev.khalil.mounjarolog.model.doseContextFor
import dev.khalil.mounjarolog.model.latestInjection
import dev.khalil.mounjarolog.model.latestWeight
import dev.khalil.mounjarolog.model.startingWeight
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private enum class Screen(val label: String) {
    HOME("Home"), PROGRESS("Progress"), CALENDAR("Calendar"), HISTORY("History"), STATS("Stats")
}

private enum class RangePreset(val label: String) {
    MONTH("1M"), TWO_MONTHS("2M"), THREE_MONTHS("3M"), SIX_MONTHS("6M"),
    YEAR("1Y"), ALL("All"), CUSTOM("From…")
}

private enum class GraphMode(val label: String) {
    WEIGHT("Weight"), CHANGE("Change"), WEEKLY("Weekly"), DOSE("Dose")
}

private val displayDate = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.getDefault())

@Composable
fun MounjaroLogApp(viewModel: MainViewModel) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                Screen.entries.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = {
                            Icon(
                                when (item) {
                                    Screen.HOME -> Icons.Default.Home
                                    Screen.PROGRESS -> Icons.Default.BarChart
                                    Screen.CALENDAR -> Icons.Default.CalendarMonth
                                    Screen.HISTORY -> Icons.Default.List
                                    Screen.STATS -> Icons.Default.Insights
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(data, viewModel, snackbar)
                Screen.PROGRESS -> ProgressScreen(data)
                Screen.CALENDAR -> CalendarScreen(data)
                Screen.HISTORY -> HistoryScreen(data, viewModel)
                Screen.STATS -> StatsScreen(data)
            }
        }
    }
}

@Composable
private fun HomeScreen(data: AppData, viewModel: MainViewModel, snackbar: SnackbarHostState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Mounjaro Log", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Your local weight & dose timeline", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { QuickEntry(viewModel, snackbar) }
        item { CurrentStatus(data) }
        item {
            SectionCard("Progress") {
                ProgressChart(
                    data = data,
                    mode = GraphMode.WEIGHT,
                    start = defaultStart(data, RangePreset.TWO_MONTHS, null),
                    end = LocalDate.now(),
                    modifier = Modifier.fillMaxWidth().height(230.dp)
                )
            }
        }
        item { RecentEvents(data) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun QuickEntry(viewModel: MainViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var weight by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf<Double?>(null) }
    var note by rememberSaveable { mutableStateOf("") }

    SectionCard("Quick entry") {
        OutlinedButton(
            onClick = {
                val d = LocalDate.parse(date)
                DatePickerDialog(
                    context,
                    { _, year, month, day -> date = LocalDate.of(year, month + 1, day).toString() },
                    d.year, d.monthValue - 1, d.dayOfMonth
                ).show()
            }
        ) { Text(LocalDate.parse(date).format(displayDate)) }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it.replace(',', '.') },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Current weight (kg) — optional") },
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))
        Text("Dose — optional", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = dose == null,
                    onClick = { dose = null },
                    label = { Text("None") }
                )
            }
            items(SupportedDoses) { d ->
                FilterChip(
                    selected = dose == d,
                    onClick = { dose = d },
                    label = { Text(formatDose(d)) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note — optional") },
            maxLines = 3
        )

        Spacer(Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val parsedWeight = weight.toDoubleOrNull()
                viewModel.addEntry(LocalDate.parse(date), parsedWeight, dose, note) { ok ->
                    if (ok) {
                        weight = ""
                        dose = null
                        note = ""
                    }
                }
            }
        ) { Text("Save entry") }

        LaunchedEffect(weight, dose) {
            if (weight.isNotBlank() && weight.toDoubleOrNull() == null) {
                snackbar.showSnackbar("Weight must be a number, e.g. 94.6")
            }
        }
    }
}

@Composable
private fun CurrentStatus(data: AppData) {
    val latest = data.latestWeight()
    val start = data.startingWeight()
    val injection = data.latestInjection()
    SectionCard("Current status") {
        MetricRow("Latest weight", latest?.let { "${oneDecimal(it.weightKg)} kg" } ?: "—")
        MetricRow(
            "Total change",
            if (latest != null && start != null) {
                val change = latest.weightKg - start.weightKg
                val pct = if (start.weightKg != 0.0) change / start.weightKg * 100 else 0.0
                "${signed(change)} kg · ${signed(pct)}%"
            } else "—"
        )
        MetricRow("Current dose", injection?.let { formatDose(it.doseMg) } ?: "—")
        MetricRow(
            "Last injection",
            injection?.let {
                val days = ChronoUnit.DAYS.between(it.date, LocalDate.now())
                when {
                    days == 0L -> "Today"
                    days == 1L -> "Yesterday"
                    days > 1 -> "$days days ago"
                    else -> it.date.format(displayDate)
                }
            } ?: "—"
        )
        if (injection != null) {
            val countAtDose = data.injections.count { it.doseMg == injection.doseMg && !it.date.isAfter(injection.date) }
            MetricRow("Injections at ${formatDose(injection.doseMg)}", countAtDose.toString())
        }
    }
}

@Composable
private fun RecentEvents(data: AppData) {
    val events = buildList {
        data.weights.forEach { add(Triple(it.date, "Weight", "${oneDecimal(it.weightKg)} kg")) }
        data.injections.forEach { add(Triple(it.date, "Injection", formatDose(it.doseMg))) }
    }.sortedByDescending { it.first }.take(6)

    SectionCard("Recent history") {
        if (events.isEmpty()) {
            EmptyHint("Your first saved weight or injection will appear here.")
        } else {
            events.forEachIndexed { index, e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (e.second == "Weight") Icons.Default.MonitorWeight else Icons.Default.Vaccines,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(e.second, fontWeight = FontWeight.Medium)
                        Text(e.first.format(displayDate), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(e.third, fontWeight = FontWeight.SemiBold)
                }
                if (index != events.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProgressScreen(data: AppData) {
    val context = LocalContext.current
    var preset by rememberSaveable { mutableStateOf(RangePreset.TWO_MONTHS) }
    var customStart by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf(GraphMode.WEIGHT) }
    val end = LocalDate.now()
    val start = defaultStart(data, preset, customStart?.let(LocalDate::parse))

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Dose context is descriptive, not a claim of causation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GraphMode.entries) { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RangePreset.entries) { r ->
                    FilterChip(
                        selected = preset == r,
                        onClick = {
                            if (r == RangePreset.CUSTOM) {
                                val initial = customStart?.let(LocalDate::parse) ?: start
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        customStart = LocalDate.of(y, m + 1, d).toString()
                                        preset = RangePreset.CUSTOM
                                    },
                                    initial.year, initial.monthValue - 1, initial.dayOfMonth
                                ).show()
                            } else {
                                preset = r
                            }
                        },
                        label = { Text(r.label) }
                    )
                }
            }
        }
        item {
            SectionCard(mode.label) {
                Text("${start.format(displayDate)} – ${end.format(displayDate)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                ProgressChart(data, mode, start, end, Modifier.fillMaxWidth().height(360.dp))
            }
        }
        item {
            DoseLegend()
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ProgressChart(
    data: AppData,
    mode: GraphMode,
    start: LocalDate,
    end: LocalDate,
    modifier: Modifier = Modifier
) {
    val weights = data.weights.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.sortedBy { it.date }
    val injections = data.injections.filter { !it.date.plusDays(6).isBefore(start) && !it.date.isAfter(end) }.sortedBy { it.date }

    if ((mode != GraphMode.DOSE && weights.isEmpty()) || (mode == GraphMode.DOSE && injections.isEmpty())) {
        Box(modifier, contentAlignment = Alignment.Center) { EmptyHint("Not enough data in this range yet.") }
        return
    }

    val totalDays = max(1L, ChronoUnit.DAYS.between(start, end)).toFloat()
    val startWeight = data.startingWeight()?.weightKg

    val points: List<Pair<LocalDate, Double>> = when (mode) {
        GraphMode.WEIGHT -> weights.map { it.date to it.weightKg }
        GraphMode.CHANGE -> weights.map { it.date to ((startWeight ?: it.weightKg) - it.weightKg) }
        GraphMode.DOSE -> injections.map { it.date to it.doseMg }
        GraphMode.WEEKLY -> weights.zipWithNext().mapNotNull { (a, b) ->
            val days = ChronoUnit.DAYS.between(a.date, b.date).toDouble()
            if (days <= 0) null else b.date to ((a.weightKg - b.weightKg) * 7.0 / days)
        }
    }

    if (points.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { EmptyHint("More measurements are needed for this view.") }
        return
    }

    val rawMin = points.minOf { it.second }
    val rawMax = points.maxOf { it.second }
    val pad = max(0.5, (rawMax - rawMin) * 0.18)
    val yMin = if (rawMin == rawMax) rawMin - 1.0 else rawMin - pad
    val yMax = if (rawMin == rawMax) rawMax + 1.0 else rawMax + pad

    val chartSurface = MaterialTheme.colorScheme.surface
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.background(chartSurface, RoundedCornerShape(12.dp))) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 16.dp.toPx()
        val chartWidth = right - left
        val chartHeight = bottom - top

        fun x(date: LocalDate): Float {
            val d = ChronoUnit.DAYS.between(start, date).toFloat().coerceIn(0f, totalDays)
            return left + (d / totalDays) * chartWidth
        }
        fun y(value: Double): Float {
            val fraction = ((value - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
            return bottom - fraction * chartHeight
        }

        if (mode != GraphMode.DOSE) {
            injections.forEach { injection ->
                val bandStart = maxDate(start, injection.date)
                val bandEnd = minDate(end, injection.date.plusDays(6))
                if (!bandEnd.isBefore(bandStart)) {
                    val color = doseColor(injection.doseMg)
                    val x1 = x(bandStart)
                    val x2 = if (bandEnd == end) right else x(bandEnd.plusDays(1))
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(color.copy(alpha = 0.03f), color.copy(alpha = 0.22f)),
                            startY = top,
                            endY = bottom
                        ),
                        topLeft = Offset(x1, top),
                        size = androidx.compose.ui.geometry.Size((x2 - x1).coerceAtLeast(2f), chartHeight)
                    )
                    drawLine(color.copy(alpha = 0.65f), Offset(x(maxDate(injection.date, start)), top), Offset(x(maxDate(injection.date, start)), bottom), 2.dp.toPx())
                    val endPost = injection.date.plusDays(7)
                    if (!endPost.isAfter(end)) {
                        drawLine(color.copy(alpha = 0.55f), Offset(x(endPost), top), Offset(x(endPost), bottom), 2.dp.toPx())
                    }
                }
            }
        }

        repeat(4) { i ->
            val yy = top + chartHeight * i / 3f
            drawLine(Color.Gray.copy(alpha = 0.16f), Offset(left, yy), Offset(right, yy), 1.dp.toPx())
        }

        val dotted = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))

        points.zipWithNext().forEach { (a, b) ->
            val gapDays = ChronoUnit.DAYS.between(a.first, b.first)
            drawLine(
                color = lineColor,
                start = Offset(x(a.first), y(a.second)),
                end = Offset(x(b.first), y(b.second)),
                strokeWidth = 3.dp.toPx(),
                pathEffect = if (mode != GraphMode.DOSE && gapDays > 1) dotted else null
            )
        }

        if (mode != GraphMode.DOSE && points.isNotEmpty() && points.last().first.isBefore(end)) {
            val p = points.last()
            drawLine(
                color = lineColor.copy(alpha = 0.75f),
                start = Offset(x(p.first), y(p.second)),
                end = Offset(right, y(p.second)),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dotted
            )
        }

        points.forEach { p ->
            val pointColor = if (mode == GraphMode.WEIGHT || mode == GraphMode.CHANGE) {
                val ctx = data.doseContextFor(p.first)
                if (ctx.doseMg == null || ctx.doseMg == 2.5) Color.Gray else doseColor(ctx.doseMg)
            } else if (mode == GraphMode.DOSE) doseColor(p.second) else lineColor
            drawCircle(pointColor, radius = 5.dp.toPx(), center = Offset(x(p.first), y(p.second)))
        }

        data.milestones
            .filter { mode == GraphMode.WEIGHT && it.targetKg in yMin..yMax }
            .forEach { milestone ->
                drawLine(
                    color = Color.Gray.copy(alpha = 0.55f),
                    start = Offset(left, y(milestone.targetKg)),
                    end = Offset(right, y(milestone.targetKg)),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dotted
                )
            }
    }
}

@Composable
private fun DoseLegend() {
    SectionCard("Dose colours") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2.5, 5.0, 7.5).forEach { dose -> DoseLegendItem(dose) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10.0, 12.5, 15.0).forEach { dose -> DoseLegendItem(dose) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Grey also marks measurements outside a 7-day injection window.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DoseLegendItem(dose: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(doseColor(dose), RoundedCornerShape(50)))
        Text("  ${formatDose(dose)}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CalendarScreen(data: AppData) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val first = month.atDay(1)
    val days = month.lengthOfMonth()
    val leading = first.dayOfWeek.value - 1

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Calendar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("‹") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM uuuu")), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("›") }
            }
        }
        item {
            SectionCard("Month") {
                Row(Modifier.fillMaxWidth()) {
                    listOf("M","T","W","T","F","S","S").forEach { Text(it, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold) }
                }
                val slots = leading + days
                for (row in 0 until ((slots + 6) / 7)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val day = row * 7 + col - leading + 1
                            if (day !in 1..days) {
                                Spacer(Modifier.weight(1f).height(64.dp))
                            } else {
                                val date = month.atDay(day)
                                val weight = data.weights.lastOrNull { it.date == date }
                                val injection = data.injections.lastOrNull { it.date == date }
                                Column(
                                    Modifier.weight(1f).height(64.dp).padding(3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(day.toString(), fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                    if (weight != null) Text("●", color = doseColor(data.doseContextFor(date).doseMg))
                                    if (injection != null) Text("◆", color = doseColor(injection.doseMg))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            val monthWeights = data.weights.filter { YearMonth.from(it.date) == month }
            val monthInjections = data.injections.count { YearMonth.from(it.date) == month }
            SectionCard("Month summary") {
                MetricRow("Measurements", monthWeights.size.toString())
                MetricRow("Injections", monthInjections.toString())
                val change = if (monthWeights.size >= 2) monthWeights.last().weightKg - monthWeights.first().weightKg else null
                MetricRow("Weight change", change?.let { "${signed(it)} kg" } ?: "—")
            }
        }
    }
}

@Composable
private fun HistoryScreen(data: AppData, viewModel: MainViewModel) {
    val events = buildList<HistoryItem> {
        data.weights.forEach { add(HistoryItem.Weight(it)) }
        data.injections.forEach { add(HistoryItem.Dose(it)) }
    }.sortedByDescending { it.date }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Weight and injection records stay independent.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (events.isEmpty()) {
            item { EmptyHint("No history yet.") }
        } else {
            items(events, key = { it.key }) { event ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (event is HistoryItem.Weight) Icons.Default.MonitorWeight else Icons.Default.Vaccines,
                            contentDescription = null
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(event.title, fontWeight = FontWeight.SemiBold)
                            Text(event.date.format(displayDate), style = MaterialTheme.typography.bodySmall)
                            if (event is HistoryItem.Weight) {
                                val ctx = data.doseContextFor(event.item.date)
                                Text(
                                    if (ctx.doseMg == null) "Outside dose window"
                                    else "Day ${ctx.dayInWindow} of ${formatDose(ctx.doseMg)} window",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = doseColor(ctx.doseMg)
                                )
                            }
                        }
                        IconButton(onClick = {
                            when (event) {
                                is HistoryItem.Weight -> viewModel.deleteWeight(event.item.id)
                                is HistoryItem.Dose -> viewModel.deleteInjection(event.item.id)
                            }
                        }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private sealed class HistoryItem {
    abstract val date: LocalDate
    abstract val key: String
    abstract val title: String

    data class Weight(val item: WeightMeasurement) : HistoryItem() {
        override val date = item.date
        override val key = "w${item.id}"
        override val title = "${oneDecimal(item.weightKg)} kg"
    }
    data class Dose(val item: Injection) : HistoryItem() {
        override val date = item.date
        override val key = "d${item.id}"
        override val title = "${formatDose(item.doseMg)} injection"
    }
}

@Composable
private fun StatsScreen(data: AppData) {
    val start = data.startingWeight()
    val latest = data.latestWeight()
    val lowest = data.weights.minByOrNull { it.weightKg }
    val injection = data.latestInjection()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            SectionCard("Overview") {
                MetricRow("Starting weight", start?.let { "${oneDecimal(it.weightKg)} kg" } ?: "—")
                MetricRow("Latest weight", latest?.let { "${oneDecimal(it.weightKg)} kg" } ?: "—")
                MetricRow("Lowest weight", lowest?.let { "${oneDecimal(it.weightKg)} kg" } ?: "—")
                MetricRow(
                    "Total change",
                    if (start != null && latest != null) "${signed(latest.weightKg - start.weightKg)} kg" else "—"
                )
                MetricRow(
                    "Body-weight change",
                    if (start != null && latest != null && start.weightKg != 0.0)
                        "${signed((latest.weightKg - start.weightKg) / start.weightKg * 100)}%" else "—"
                )
                MetricRow("Measurements", data.weights.size.toString())
                MetricRow("Injections", data.injections.size.toString())
            }
        }
        item {
            SectionCard("Current dose") {
                MetricRow("Dose", injection?.let { formatDose(it.doseMg) } ?: "—")
                MetricRow(
                    "Injection count at dose",
                    injection?.let { current -> data.injections.count { it.doseMg == current.doseMg }.toString() } ?: "—"
                )
                val currentDoseWeights = injection?.let { current ->
                    val firstDoseDate = data.injections.filter { it.doseMg == current.doseMg }.minByOrNull { it.date }?.date
                    if (firstDoseDate == null) emptyList() else data.weights.filter { !it.date.isBefore(firstDoseDate) }
                }.orEmpty()
                MetricRow(
                    "Change since dose began",
                    if (currentDoseWeights.size >= 2)
                        "${signed(currentDoseWeights.last().weightKg - currentDoseWeights.first().weightKg)} kg"
                    else "—"
                )
            }
        }
        item {
            SectionCard("By dose") {
                SupportedDoses.forEach { dose ->
                    val doseInjections = data.injections.filter { it.doseMg == dose }
                    val contexts = data.weights.filter { data.doseContextFor(it.date).doseMg == dose }
                    val delta = if (contexts.size >= 2) contexts.last().weightKg - contexts.first().weightKg else null
                    MetricRow(
                        formatDose(dose),
                        "${doseInjections.size} inj." + (delta?.let { " · ${signed(it)} kg" } ?: "")
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
}

private fun defaultStart(data: AppData, preset: RangePreset, custom: LocalDate?): LocalDate {
    val today = LocalDate.now()
    return when (preset) {
        RangePreset.MONTH -> today.minusMonths(1)
        RangePreset.TWO_MONTHS -> today.minusMonths(2)
        RangePreset.THREE_MONTHS -> today.minusMonths(3)
        RangePreset.SIX_MONTHS -> today.minusMonths(6)
        RangePreset.YEAR -> today.minusYears(1)
        RangePreset.ALL -> listOfNotNull(
            data.weights.minByOrNull { it.date }?.date,
            data.injections.minByOrNull { it.date }?.date
        ).minOrNull() ?: today.minusMonths(1)
        RangePreset.CUSTOM -> custom ?: today.minusMonths(2)
    }
}

private fun formatDose(dose: Double): String =
    if (dose % 1.0 == 0.0) "${dose.toInt()} mg" else "${oneDecimal(dose)} mg"

private fun oneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
private fun signed(value: Double): String = (if (value > 0) "+" else "") + oneDecimal(value)

private fun doseColor(dose: Double?): Color = when (dose) {
    5.0 -> Color(0xFF2E7D32)
    7.5 -> Color(0xFF1976D2)
    10.0 -> Color(0xFF6A1B9A)
    12.5 -> Color(0xFFEF6C00)
    15.0 -> Color(0xFFC62828)
    else -> Color(0xFF757575)
}

private fun maxDate(a: LocalDate, b: LocalDate) = if (a.isAfter(b)) a else b
private fun minDate(a: LocalDate, b: LocalDate) = if (a.isBefore(b)) a else b
