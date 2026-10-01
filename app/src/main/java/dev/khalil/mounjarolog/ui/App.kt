package dev.khalil.mounjarolog.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khalil.mounjarolog.BuildConfig
import dev.khalil.mounjarolog.MainViewModel
import dev.khalil.mounjarolog.data.BackupCodec
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.DayLog
import dev.khalil.mounjarolog.model.Injection
import dev.khalil.mounjarolog.model.Milestone
import dev.khalil.mounjarolog.model.SupportedDoses
import dev.khalil.mounjarolog.model.WeightMeasurement
import dev.khalil.mounjarolog.model.doseContextFor
import dev.khalil.mounjarolog.model.latestInjection
import dev.khalil.mounjarolog.model.latestWeight
import dev.khalil.mounjarolog.model.startingWeight
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private enum class AppTab(val label: String) {
    HOME("Home"),
    PROGRESS("Progress"),
    CALENDAR("Calendar"),
    HISTORY("History"),
    MORE("More")
}

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    WEIGHT("Weight"),
    INJECTION("Dose"),
    NOTES("Notes")
}

@Composable
fun MounjaroLogApp(
    viewModel: MainViewModel,
    quickAddMode: String? = null,
    onQuickAddConsumed: () -> Unit = {}
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var addEntryDate by remember { mutableStateOf<LocalDate?>(null) }
    var addEntryMode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(quickAddMode) {
        if (quickAddMode != null) {
            addEntryDate = LocalDate.now()
            addEntryMode = quickAddMode
            onQuickAddConsumed()
        }
    }

    fun notify(result: Result<Unit>, success: String) {
        scope.launch {
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = { success },
                    onFailure = { it.message ?: "Something went wrong." }
                )
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    AppTab.HOME -> Icons.Default.Home
                                    AppTab.PROGRESS -> Icons.Default.ShowChart
                                    AppTab.CALENDAR -> Icons.Default.CalendarMonth
                                    AppTab.HISTORY -> Icons.Default.ListAlt
                                    AppTab.MORE -> Icons.Default.MoreHoriz
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                AppTab.HOME -> HomeScreen(
                    data = data,
                    busy = busy,
                    onSaveEntry = { date, weight, dose, site, appetite, sideEffects, note, done ->
                        viewModel.addEntry(
                            date = date,
                            weightKg = weight,
                            doseMg = dose,
                            injectionSite = site,
                            appetite = appetite,
                            sideEffects = sideEffects,
                            note = note
                        ) { result ->
                            notify(result, "Entry saved.")
                            done(result.isSuccess)
                        }
                    }
                )
                AppTab.PROGRESS -> ProgressScreen(data)
                AppTab.CALENDAR -> CalendarScreen(
                    data = data,
                    viewModel = viewModel,
                    onAddEntry = {
                        addEntryDate = it
                        addEntryMode = null
                    },
                    notify = ::notify
                )
                AppTab.HISTORY -> HistoryScreen(
                    data = data,
                    viewModel = viewModel,
                    notify = ::notify
                )
                AppTab.MORE -> MoreScreen(
                    data = data,
                    viewModel = viewModel,
                    notify = ::notify
                )
            }

            if (busy) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Saving…", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    addEntryDate?.let { initialDate ->
        NewEntryDialog(
            initialDate = initialDate,
            initialMode = addEntryMode,
            onDismiss = {
                addEntryDate = null
                addEntryMode = null
            },
            onSave = { date, weight, dose, site, appetite, sideEffects, note ->
                viewModel.addEntry(
                    date = date,
                    weightKg = weight,
                    doseMg = dose,
                    injectionSite = site,
                    appetite = appetite,
                    sideEffects = sideEffects,
                    note = note
                ) { result ->
                    notify(result, "Entry saved.")
                    if (result.isSuccess) {
                        addEntryDate = null
                        addEntryMode = null
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    data: AppData,
    busy: Boolean,
    onSaveEntry: (
        LocalDate,
        Double?,
        Double?,
        String,
        Int?,
        String,
        String,
        (Boolean) -> Unit
    ) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Mounjaro Log",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Weight, doses and your own notes — stored on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            QuickEntryCard(busy = busy, onSave = onSaveEntry)
        }
        item {
            CurrentStatusCard(data)
        }
        item {
            SectionCard(
                title = "Weight progress",
                subtitle = "Tap a point to inspect it. Dotted lines mark unmeasured time."
            ) {
                CompactProgressChart(data)
            }
        }
        item {
            RecentHistoryCard(data)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun QuickEntryCard(
    busy: Boolean,
    onSave: (
        LocalDate,
        Double?,
        Double?,
        String,
        Int?,
        String,
        String,
        (Boolean) -> Unit
    ) -> Unit
) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var weightText by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf<Double?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var injectionSite by rememberSaveable { mutableStateOf("") }
    var appetite by rememberSaveable { mutableStateOf<Int?>(null) }
    var sideEffects by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    SectionCard(
        title = "Quick entry",
        subtitle = "Weight and dose are independent — either can be left blank."
    ) {
        DatePickerButton(
            date = LocalDate.parse(dateText),
            onDateChange = { dateText = it.toString() }
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = weightText,
            onValueChange = {
                weightText = it.replace(',', '.')
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Current weight (kg) — optional") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            items(SupportedDoses) { item ->
                FilterChip(
                    selected = dose == item,
                    onClick = { dose = item },
                    label = { Text(formatDose(item)) }
                )
            }
        }

        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide optional details" else "Add notes & optional details")
        }

        if (expanded) {
            OutlinedTextField(
                value = injectionSite,
                onValueChange = { injectionSite = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Injection site — optional") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text("Appetite — optional self-rating")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = appetite == null,
                        onClick = { appetite = null },
                        label = { Text("None") }
                    )
                }
                items((1..5).toList()) { level ->
                    FilterChip(
                        selected = appetite == level,
                        onClick = { appetite = level },
                        label = { Text(level.toString()) }
                    )
                }
            }
            OutlinedTextField(
                value = sideEffects,
                onValueChange = { sideEffects = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Side effects / symptoms — optional") },
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note — optional") },
            minLines = if (expanded) 2 else 1
        )

        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            onClick = {
                val weight = if (weightText.isBlank()) null else weightText.toDoubleOrNull()
                when {
                    weightText.isNotBlank() && weight == null -> error = "Enter a valid weight."
                    weight == null && dose == null && appetite == null &&
                        sideEffects.isBlank() && note.isBlank() ->
                        error = "Add at least one value."
                    else -> {
                        error = null
                        onSave(
                            LocalDate.parse(dateText),
                            weight,
                            dose,
                            injectionSite,
                            appetite,
                            sideEffects,
                            note
                        ) { saved ->
                            if (saved) {
                                dateText = LocalDate.now().toString()
                                weightText = ""
                                dose = null
                                injectionSite = ""
                                appetite = null
                                sideEffects = ""
                                note = ""
                                expanded = false
                            }
                        }
                    }
                }
            }
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("  Save entry")
        }
    }
}

@Composable
private fun CurrentStatusCard(data: AppData) {
    val latestWeight = data.latestWeight()
    val startingWeight = data.startingWeight()
    val latestInjection = data.latestInjection()
    val today = LocalDate.now()

    SectionCard("Current status") {
        MetricRow(
            "Latest weight",
            latestWeight?.let { oneDecimal(it.weightKg) + " kg" } ?: "—"
        )
        MetricRow(
            "Total change",
            if (latestWeight != null && startingWeight != null) {
                val change = latestWeight.weightKg - startingWeight.weightKg
                val percent = if (startingWeight.weightKg == 0.0) 0.0
                else change / startingWeight.weightKg * 100.0
                signed(change, " kg") + " · " + signed(percent, "%")
            } else "—"
        )
        MetricRow(
            "Last dose",
            latestInjection?.let { formatDose(it.doseMg) } ?: "—"
        )

        if (latestInjection != null) {
            val days = ChronoUnit.DAYS.between(latestInjection.date, today)
            val windowText = when {
                days < 0 -> "Future entry"
                days <= 6 -> "Day " + (days + 1) + " of 7"
                else -> "Outside 7-day window"
            }
            MetricRow("Dose window", windowText)

            val currentRun = data.injections
                .sortedBy { it.date }
                .asReversed()
                .takeWhile { it.doseMg == latestInjection.doseMg }
                .size
            MetricRow(
                "Current dose run",
                currentRun.toString() + " injection" + if (currentRun == 1) "" else "s"
            )
            MetricRow(
                "7-day point",
                latestInjection.date.plusDays(7).format(DisplayDateFormatter)
            )
        }
    }
}

@Composable
private fun RecentHistoryCard(data: AppData) {
    val rows = buildList<SimpleEvent> {
        data.weights.forEach {
            add(SimpleEvent(it.date, "Weight", oneDecimal(it.weightKg) + " kg", false))
        }
        data.injections.forEach {
            add(SimpleEvent(it.date, "Injection", formatDose(it.doseMg), true))
        }
        data.dayLogs.forEach {
            add(SimpleEvent(it.date, "Daily note", it.sideEffects.ifBlank { "Notes saved" }, false))
        }
    }.sortedByDescending { it.date }.take(6)

    SectionCard("Recent history") {
        if (rows.isEmpty()) {
            EmptyState("Your first saved entry will appear here.")
        } else {
            rows.forEachIndexed { index, row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (row.injection) Icons.Default.Vaccines else Icons.Default.MonitorWeight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(row.title, fontWeight = FontWeight.Medium)
                        Text(
                            row.date.format(DisplayDateFormatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(row.value, fontWeight = FontWeight.SemiBold)
                }
                if (index != rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

private data class SimpleEvent(
    val date: LocalDate,
    val title: String,
    val value: String,
    val injection: Boolean
)

@Composable
private fun ProgressScreen(data: AppData) {
    var modeName by rememberSaveable { mutableStateOf(GraphMode.WEIGHT.name) }
    var presetName by rememberSaveable { mutableStateOf(RangePreset.TWO_MONTHS.name) }
    var customStartText by rememberSaveable { mutableStateOf(LocalDate.now().minusMonths(2).toString()) }
    var customEndText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var doseBands by rememberSaveable { mutableStateOf(true) }
    var injectionPosts by rememberSaveable { mutableStateOf(true) }
    var milestones by rememberSaveable { mutableStateOf(true) }

    val mode = GraphMode.valueOf(modeName)
    val preset = RangePreset.valueOf(presetName)
    val end = if (preset == RangePreset.CUSTOM) LocalDate.parse(customEndText) else LocalDate.now()
    val initialStart = graphStart(
        data,
        preset,
        if (preset == RangePreset.CUSTOM) LocalDate.parse(customStartText) else null,
        end
    )
    val start = if (initialStart.isAfter(end)) end else initialStart

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Progress",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Colours show timing context; they do not prove that a dose caused a weight change.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GraphMode.entries) { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = { modeName = item.name },
                        label = { Text(item.label) }
                    )
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RangePreset.entries) { item ->
                    FilterChip(
                        selected = preset == item,
                        onClick = { presetName = item.name },
                        label = { Text(item.label) }
                    )
                }
            }
        }
        if (preset == RangePreset.CUSTOM) {
            item {
                SectionCard("Custom range") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            DatePickerButton(
                                date = LocalDate.parse(customStartText),
                                onDateChange = { customStartText = it.toString() },
                                label = "From " + LocalDate.parse(customStartText).format(DisplayDateFormatter)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DatePickerButton(
                        date = LocalDate.parse(customEndText),
                        onDateChange = { customEndText = it.toString() },
                        label = "To " + LocalDate.parse(customEndText).format(DisplayDateFormatter)
                    )
                }
            }
        }
        item {
            SectionCard(
                title = mode.label,
                subtitle = start.format(DisplayDateFormatter) + " – " + end.format(DisplayDateFormatter)
            ) {
                ProgressChart(
                    data = data,
                    mode = mode,
                    start = start,
                    end = end,
                    options = GraphOptions(
                        doseBands = doseBands,
                        injectionPosts = injectionPosts,
                        milestones = milestones
                    ),
                    modifier = Modifier.fillMaxWidth().height(390.dp)
                )
            }
        }
        if (mode != GraphMode.DOSE) {
            item {
                SectionCard("Graph layers") {
                    ToggleRow("Dose-window backgrounds", doseBands) { doseBands = it }
                    ToggleRow("Injection & day-7 posts", injectionPosts) { injectionPosts = it }
                    if (mode == GraphMode.WEIGHT) {
                        ToggleRow("Milestones", milestones) { milestones = it }
                    }
                }
            }
        }
        item {
            SectionCard(
                title = "Dose colours",
                subtitle = "2.5 mg and measurements outside a 7-day window are grey."
            ) {
                SupportedDoses.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { dose ->
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DoseDot(dose)
                                Text("  " + formatDose(dose))
                            }
                        }
                    }
                }
            }
        }
        item {
            VisibleRangeSummary(data, start, end)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun VisibleRangeSummary(data: AppData, start: LocalDate, end: LocalDate) {
    val weights = data.weights.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    SectionCard("Visible range") {
        MetricRow("Measurements", weights.size.toString())
        if (weights.size >= 2) {
            val delta = weights.last().weightKg - weights.first().weightKg
            MetricRow("Weight change", signed(delta, " kg"))
        } else {
            MetricRow("Weight change", "—")
        }
        MetricRow(
            "Injections",
            data.injections.count { !it.date.isBefore(start) && !it.date.isAfter(end) }.toString()
        )
    }
}

@Composable
private fun CalendarScreen(
    data: AppData,
    viewModel: MainViewModel,
    onAddEntry: (LocalDate) -> Unit,
    notify: (Result<Unit>, String) -> Unit
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateText by rememberSaveable { mutableStateOf<String?>(null) }
    var editingWeight by remember { mutableStateOf<WeightMeasurement?>(null) }
    var editingInjection by remember { mutableStateOf<Injection?>(null) }
    var editingDayDate by remember { mutableStateOf<LocalDate?>(null) }

    val month = YearMonth.parse(monthText)
    val leading = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Calendar",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tap a day to view or change its records.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                    Text("‹")
                }
                Text(
                    month.atDay(1).format(MonthFormatter),
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                    Text("›")
                }
            }
        }
        item {
            SectionCard("Month") {
                Row(Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                val slots = leading + days
                for (rowIndex in 0 until ((slots + 6) / 7)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val day = rowIndex * 7 + col - leading + 1
                            if (day !in 1..days) {
                                Spacer(Modifier.weight(1f).height(68.dp))
                            } else {
                                val date = month.atDay(day)
                                val dayWeights = data.weights.filter { it.date == date }
                                val dayInjections = data.injections.filter { it.date == date }
                                val dayLog = data.dayLogs.any { it.date == date }
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .height(68.dp)
                                        .clickable { selectedDateText = date.toString() }
                                        .padding(3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        day.toString(),
                                        fontWeight = if (date == LocalDate.now()) FontWeight.Bold
                                        else FontWeight.Normal
                                    )
                                    if (dayWeights.isNotEmpty()) {
                                        Text(
                                            "●",
                                            color = doseColor(data.doseContextFor(date).doseMg)
                                        )
                                    }
                                    if (dayInjections.isNotEmpty()) {
                                        Text("◆", color = doseColor(dayInjections.last().doseMg))
                                    }
                                    if (dayLog) {
                                        Text("•", color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            val monthWeights = data.weights.filter { YearMonth.from(it.date) == month }
            SectionCard("Month summary") {
                MetricRow("Measurements", monthWeights.size.toString())
                MetricRow(
                    "Injections",
                    data.injections.count { YearMonth.from(it.date) == month }.toString()
                )
                MetricRow(
                    "Daily notes",
                    data.dayLogs.count { YearMonth.from(it.date) == month }.toString()
                )
                MetricRow(
                    "Weight change",
                    if (monthWeights.size >= 2) {
                        signed(monthWeights.last().weightKg - monthWeights.first().weightKg, " kg")
                    } else "—"
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    selectedDateText?.let { text ->
        val date = LocalDate.parse(text)
        DayDetailsDialog(
            date = date,
            data = data,
            onDismiss = { selectedDateText = null },
            onAddEntry = {
                selectedDateText = null
                onAddEntry(date)
            },
            onEditWeight = {
                selectedDateText = null
                editingWeight = it
            },
            onEditInjection = {
                selectedDateText = null
                editingInjection = it
            },
            onEditDaily = {
                selectedDateText = null
                editingDayDate = date
            }
        )
    }

    editingWeight?.let { item ->
        WeightEditorDialog(
            item = item,
            onDismiss = { editingWeight = null },
            onSave = { date, weight, note ->
                viewModel.updateWeight(item.id, date, weight, note) {
                    notify(it, "Weight updated.")
                    if (it.isSuccess) editingWeight = null
                }
            },
            onDelete = {
                viewModel.deleteWeight(item.id) {
                    notify(it, "Weight deleted.")
                    if (it.isSuccess) editingWeight = null
                }
            }
        )
    }

    editingInjection?.let { item ->
        InjectionEditorDialog(
            item = item,
            onDismiss = { editingInjection = null },
            onSave = { date, dose, site, note ->
                viewModel.updateInjection(item.id, date, dose, site, note) {
                    notify(it, "Injection updated.")
                    if (it.isSuccess) editingInjection = null
                }
            },
            onDelete = {
                viewModel.deleteInjection(item.id) {
                    notify(it, "Injection deleted.")
                    if (it.isSuccess) editingInjection = null
                }
            }
        )
    }

    editingDayDate?.let { date ->
        val log = data.dayLogs.lastOrNull { it.date == date }
        DayLogEditorDialog(
            initialDate = date,
            item = log,
            onDismiss = { editingDayDate = null },
            onSave = { appetite, sideEffects, note ->
                viewModel.upsertDayLog(date, appetite, sideEffects, note) {
                    notify(it, "Daily notes updated.")
                    if (it.isSuccess) editingDayDate = null
                }
            },
            onDelete = log?.let {
                {
                    viewModel.deleteDayLog(it.id) { result ->
                        notify(result, "Daily note deleted.")
                        if (result.isSuccess) editingDayDate = null
                    }
                }
            }
        )
    }
}

@Composable
private fun DayDetailsDialog(
    date: LocalDate,
    data: AppData,
    onDismiss: () -> Unit,
    onAddEntry: () -> Unit,
    onEditWeight: (WeightMeasurement) -> Unit,
    onEditInjection: (Injection) -> Unit,
    onEditDaily: () -> Unit
) {
    val weights = data.weights.filter { it.date == date }
    val injections = data.injections.filter { it.date == date }
    val log = data.dayLogs.lastOrNull { it.date == date }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DisplayDateFormatter)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (weights.isEmpty() && injections.isEmpty() && log == null) {
                    EmptyState("Nothing logged on this day.")
                }
                weights.forEach { weight ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEditWeight(weight) }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Weight")
                            Text(oneDecimal(weight.weightKg) + " kg", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                injections.forEach { injection ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEditInjection(injection) }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DoseDot(injection.doseMg)
                                Text("  Injection")
                            }
                            Text(formatDose(injection.doseMg), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (log != null) {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onEditDaily)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Daily notes", fontWeight = FontWeight.SemiBold)
                            log.appetite?.let { Text("Appetite: " + it + "/5") }
                            if (log.sideEffects.isNotBlank()) Text(log.sideEffects)
                            if (log.note.isNotBlank()) Text(log.note)
                        }
                    }
                } else {
                    TextButton(onClick = onEditDaily) { Text("Add daily notes") }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAddEntry) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Add entry")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun HistoryScreen(
    data: AppData,
    viewModel: MainViewModel,
    notify: (Result<Unit>, String) -> Unit
) {
    var filterName by rememberSaveable { mutableStateOf(HistoryFilter.ALL.name) }
    var editingWeight by remember { mutableStateOf<WeightMeasurement?>(null) }
    var editingInjection by remember { mutableStateOf<Injection?>(null) }
    var editingDayLog by remember { mutableStateOf<DayLog?>(null) }
    val filter = HistoryFilter.valueOf(filterName)

    val events = buildList<HistoryRow> {
        if (filter == HistoryFilter.ALL || filter == HistoryFilter.WEIGHT) {
            data.weights.forEach { add(HistoryRow.Weight(it)) }
        }
        if (filter == HistoryFilter.ALL || filter == HistoryFilter.INJECTION) {
            data.injections.forEach { add(HistoryRow.Dose(it)) }
        }
        if (filter == HistoryFilter.ALL || filter == HistoryFilter.NOTES) {
            data.dayLogs.forEach { add(HistoryRow.Note(it)) }
        }
    }.sortedWith(compareByDescending<HistoryRow> { it.date }.thenByDescending { it.sortOrder })

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tap any record to edit it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryFilter.entries) { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filterName = item.name },
                        label = { Text(item.label) }
                    )
                }
            }
        }
        if (events.isEmpty()) {
            item { EmptyState("No records in this filter yet.") }
        } else {
            items(events, key = { it.key }) { event ->
                HistoryCard(
                    event = event,
                    data = data,
                    onClick = {
                        when (event) {
                            is HistoryRow.Weight -> editingWeight = event.item
                            is HistoryRow.Dose -> editingInjection = event.item
                            is HistoryRow.Note -> editingDayLog = event.item
                        }
                    }
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    editingWeight?.let { item ->
        WeightEditorDialog(
            item = item,
            onDismiss = { editingWeight = null },
            onSave = { date, weight, note ->
                viewModel.updateWeight(item.id, date, weight, note) {
                    notify(it, "Weight updated.")
                    if (it.isSuccess) editingWeight = null
                }
            },
            onDelete = {
                viewModel.deleteWeight(item.id) {
                    notify(it, "Weight deleted.")
                    if (it.isSuccess) editingWeight = null
                }
            }
        )
    }

    editingInjection?.let { item ->
        InjectionEditorDialog(
            item = item,
            onDismiss = { editingInjection = null },
            onSave = { date, dose, site, note ->
                viewModel.updateInjection(item.id, date, dose, site, note) {
                    notify(it, "Injection updated.")
                    if (it.isSuccess) editingInjection = null
                }
            },
            onDelete = {
                viewModel.deleteInjection(item.id) {
                    notify(it, "Injection deleted.")
                    if (it.isSuccess) editingInjection = null
                }
            }
        )
    }

    editingDayLog?.let { item ->
        DayLogEditorDialog(
            initialDate = item.date,
            item = item,
            onDismiss = { editingDayLog = null },
            onSave = { appetite, sideEffects, note ->
                viewModel.upsertDayLog(item.date, appetite, sideEffects, note) {
                    notify(it, "Daily notes updated.")
                    if (it.isSuccess) editingDayLog = null
                }
            },
            onDelete = {
                viewModel.deleteDayLog(item.id) {
                    notify(it, "Daily note deleted.")
                    if (it.isSuccess) editingDayLog = null
                }
            }
        )
    }
}

private sealed class HistoryRow {
    abstract val date: LocalDate
    abstract val key: String
    abstract val sortOrder: Int

    data class Weight(val item: WeightMeasurement) : HistoryRow() {
        override val date = item.date
        override val key = "weight-" + item.id
        override val sortOrder = 3
    }

    data class Dose(val item: Injection) : HistoryRow() {
        override val date = item.date
        override val key = "dose-" + item.id
        override val sortOrder = 2
    }

    data class Note(val item: DayLog) : HistoryRow() {
        override val date = item.date
        override val key = "note-" + item.id
        override val sortOrder = 1
    }
}

@Composable
private fun HistoryCard(
    event: HistoryRow,
    data: AppData,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when (event) {
                is HistoryRow.Weight -> {
                    doseColor(data.doseContextFor(event.item.date).doseMg).copy(alpha = 0.08f)
                }
                is HistoryRow.Dose -> doseColor(event.item.doseMg).copy(alpha = 0.08f)
                is HistoryRow.Note -> MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (event) {
                    is HistoryRow.Weight -> Icons.Default.MonitorWeight
                    is HistoryRow.Dose -> Icons.Default.Vaccines
                    is HistoryRow.Note -> Icons.Default.ListAlt
                },
                contentDescription = null
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    when (event) {
                        is HistoryRow.Weight -> oneDecimal(event.item.weightKg) + " kg"
                        is HistoryRow.Dose -> formatDose(event.item.doseMg) + " injection"
                        is HistoryRow.Note -> "Daily notes"
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    event.date.format(DisplayDateFormatter),
                    style = MaterialTheme.typography.bodySmall
                )
                when (event) {
                    is HistoryRow.Weight -> {
                        val context = data.doseContextFor(event.item.date)
                        Text(
                            if (context.doseMg == null) "Outside dose window"
                            else "Day " + context.dayInWindow + " of " + formatDose(context.doseMg),
                            style = MaterialTheme.typography.bodySmall,
                            color = doseColor(context.doseMg)
                        )
                        if (event.item.note.isNotBlank()) {
                            Text(event.item.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is HistoryRow.Dose -> {
                        if (event.item.injectionSite.isNotBlank()) {
                            Text(
                                "Site: " + event.item.injectionSite,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (event.item.note.isNotBlank()) {
                            Text(event.item.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is HistoryRow.Note -> {
                        event.item.appetite?.let {
                            Text("Appetite: " + it + "/5", style = MaterialTheme.typography.bodySmall)
                        }
                        if (event.item.sideEffects.isNotBlank()) {
                            Text(event.item.sideEffects, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun MoreScreen(
    data: AppData,
    viewModel: MainViewModel,
    notify: (Result<Unit>, String) -> Unit
) {
    var editingMilestone by remember { mutableStateOf<Milestone?>(null) }
    var addingMilestone by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Stats & data",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Your numbers stay descriptive; treatment decisions are not automated.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { OverviewStats(data) }
        item { CurrentDoseStats(data) }
        item { DoseBreakdown(data) }
        item {
            SectionCard("Milestones") {
                if (data.milestones.isEmpty()) {
                    EmptyState("Add your own target lines to the weight graph.")
                } else {
                    data.milestones.forEach { milestone ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editingMilestone = milestone }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    oneDecimal(milestone.targetKg) + " kg",
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (milestone.label.isNotBlank()) {
                                    Text(
                                        milestone.label,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { addingMilestone = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Add milestone")
                }
            }
        }
        item {
            DataManagementCard(data, viewModel, notify)
        }
        item {
            SectionCard("About") {
                MetricRow("App version", BuildConfig.VERSION_NAME)
                MetricRow("Storage", "On-device SQLite")
                Text(
                    "Normal app updates preserve the database. Uninstalling Android apps can remove private app data, so export a JSON backup before changing phones or uninstalling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (addingMilestone) {
        MilestoneEditorDialog(
            item = null,
            onDismiss = { addingMilestone = false },
            onSave = { target, label ->
                viewModel.addMilestone(target, label) {
                    notify(it, "Milestone added.")
                    if (it.isSuccess) addingMilestone = false
                }
            }
        )
    }

    editingMilestone?.let { milestone ->
        MilestoneEditorDialog(
            item = milestone,
            onDismiss = { editingMilestone = null },
            onSave = { target, label ->
                viewModel.updateMilestone(milestone.id, target, label) {
                    notify(it, "Milestone updated.")
                    if (it.isSuccess) editingMilestone = null
                }
            },
            onDelete = {
                viewModel.deleteMilestone(milestone.id) {
                    notify(it, "Milestone deleted.")
                    if (it.isSuccess) editingMilestone = null
                }
            }
        )
    }
}

@Composable
private fun OverviewStats(data: AppData) {
    val orderedWeights = data.weights.sortedBy { it.date }
    val orderedInjections = data.injections.sortedBy { it.date }
    val start = data.startingWeight()
    val latest = data.latestWeight()
    val lowest = data.weights.minByOrNull { it.weightKg }
    val highest = data.weights.maxByOrNull { it.weightKg }

    fun averageInterval(dates: List<LocalDate>): Double? {
        if (dates.size < 2) return null
        val intervals = dates.zipWithNext().map {
            ChronoUnit.DAYS.between(it.first, it.second).toDouble()
        }.filter { it >= 0.0 }
        return if (intervals.isEmpty()) null else intervals.average()
    }

    fun trend(days: Long): Double? {
        val endWeight = latest ?: return null
        val targetDate = endWeight.date.minusDays(days)
        val reference = orderedWeights
            .filter { !it.date.isAfter(targetDate) }
            .maxByOrNull { it.date }
            ?: return null
        return endWeight.weightKg - reference.weightKg
    }

    SectionCard("Overview") {
        MetricRow("Starting weight", start?.let { oneDecimal(it.weightKg) + " kg" } ?: "—")
        MetricRow("Latest weight", latest?.let { oneDecimal(it.weightKg) + " kg" } ?: "—")
        MetricRow("Lowest recorded", lowest?.let { oneDecimal(it.weightKg) + " kg" } ?: "—")
        MetricRow("Highest recorded", highest?.let { oneDecimal(it.weightKg) + " kg" } ?: "—")
        MetricRow(
            "Total change",
            if (start != null && latest != null) {
                signed(latest.weightKg - start.weightKg, " kg")
            } else "—"
        )
        MetricRow(
            "Body-weight change",
            if (start != null && latest != null && start.weightKg != 0.0) {
                signed((latest.weightKg - start.weightKg) / start.weightKg * 100.0, "%")
            } else "—"
        )
        MetricRow("7-day trend", trend(7)?.let { signed(it, " kg") } ?: "—")
        MetricRow("30-day trend", trend(30)?.let { signed(it, " kg") } ?: "—")
        MetricRow("Measurements", data.weights.size.toString())
        MetricRow("Injections", data.injections.size.toString())
        MetricRow("Daily notes", data.dayLogs.size.toString())
        MetricRow(
            "Average weigh-in interval",
            averageInterval(orderedWeights.map { it.date })?.let { oneDecimal(it) + " days" } ?: "—"
        )
        MetricRow(
            "Average injection interval",
            averageInterval(orderedInjections.map { it.date })?.let { oneDecimal(it) + " days" } ?: "—"
        )

        if (orderedWeights.size >= 2) {
            val first = orderedWeights.first()
            val last = orderedWeights.last()
            val days = ChronoUnit.DAYS.between(first.date, last.date).toDouble()
            if (days > 0) {
                val weekly = (first.weightKg - last.weightKg) * 7.0 / days
                MetricRow("Average weekly loss", oneDecimal(weekly) + " kg/wk")
            }
        }
    }
}

@Composable
private fun CurrentDoseStats(data: AppData) {
    val latest = data.latestInjection()
    SectionCard("Current dose") {
        if (latest == null) {
            EmptyState("No injection logged yet.")
        } else {
            val run = data.injections.sortedBy { it.date }.asReversed()
                .takeWhile { it.doseMg == latest.doseMg }
                .reversed()
            val runStart = run.firstOrNull()?.date
            val relevantWeights = if (runStart == null) emptyList()
            else data.weights.filter { !it.date.isBefore(runStart) }
            val runDays = runStart?.let {
                (ChronoUnit.DAYS.between(it, LocalDate.now()).coerceAtLeast(0) + 1).toString()
            }

            MetricRow("Dose", formatDose(latest.doseMg))
            MetricRow("Consecutive injections", run.size.toString())
            MetricRow("Run started", runStart?.format(DisplayDateFormatter) ?: "—")
            MetricRow("Days in current run", runDays ?: "—")
            MetricRow(
                "Change since run started",
                if (relevantWeights.size >= 2) {
                    signed(
                        relevantWeights.last().weightKg - relevantWeights.first().weightKg,
                        " kg"
                    )
                } else "—"
            )
        }
    }
}

@Composable
private fun DoseBreakdown(data: AppData) {
    SectionCard(
        title = "By dose",
        subtitle = "Weight changes are grouped by timing context, not attributed as cause."
    ) {
        SupportedDoses.forEach { dose ->
            val injections = data.injections.count { it.doseMg == dose }
            val weights = data.weights.filter { data.doseContextFor(it.date).doseMg == dose }
            val delta = if (weights.size >= 2) {
                weights.last().weightKg - weights.first().weightKg
            } else null

            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DoseDot(dose)
                Text("  " + formatDose(dose), modifier = Modifier.weight(1f))
                Text(
                    injections.toString() + " inj." +
                        (delta?.let { " · " + signed(it, " kg") } ?: ""),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DataManagementCard(
    data: AppData,
    viewModel: MainViewModel,
    notify: (Result<Unit>, String) -> Unit
) {
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<Pair<String, AppData>?>(null) }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(BackupCodec.toJson(data))
                } ?: error("Could not open the selected file.")
            }
            notify(result.map { Unit }, "Backup exported.")
        }
    }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(BackupCodec.toCsv(data))
                } ?: error("Could not open the selected file.")
            }
            notify(result.map { Unit }, "CSV exported.")
        }
    }

    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                } ?: error("Could not read the selected file.")
                raw to BackupCodec.fromJson(raw)
            }
            result.onSuccess { pendingRestore = it }
            result.onFailure {
                notify(Result.failure(it), "")
            }
        }
    }

    SectionCard(
        title = "Backup & export",
        subtitle = "Automatic rotating snapshots are also kept privately inside the app."
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                exportJson.launch(
                    "mounjaro-log-backup-" + LocalDate.now().toString() + ".json"
                )
            }
        ) {
            Text("Export full JSON backup")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                exportCsv.launch(
                    "mounjaro-log-data-" + LocalDate.now().toString() + ".csv"
                )
            }
        ) {
            Text("Export CSV")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { importJson.launch(arrayOf("application/json", "text/plain")) }
        ) {
            Text("Restore JSON backup")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Restoring replaces the current dataset only after confirmation. A local snapshot of the current data is written first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pendingRestore?.let { pair ->
        val preview = pair.second
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "This backup contains " +
                        preview.weights.size + " weight measurements, " +
                        preview.injections.size + " injections, " +
                        preview.dayLogs.size + " daily notes and " +
                        preview.milestones.size + " milestones. " +
                        "It will replace the current dataset."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreBackup(pair.first) {
                            notify(it, "Backup restored.")
                            if (it.isSuccess) pendingRestore = null
                        }
                    }
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            }
        )
    }
}
