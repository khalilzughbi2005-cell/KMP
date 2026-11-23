package dev.khalil.mounjarolog.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.khalil.mounjarolog.BuildConfig
import dev.khalil.mounjarolog.MainViewModel
import dev.khalil.mounjarolog.data.BackupCodec
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.Milestone
import dev.khalil.mounjarolog.reminder.InjectionReminder
import java.time.LocalDate

@Composable
fun MoreScreen(
    data: AppData,
    viewModel: MainViewModel,
    notify: (Result<Unit>, String) -> Unit
) {
    val context = LocalContext.current
    var editingMilestone by remember { mutableStateOf<Milestone?>(null) }
    var addingMilestone by rememberSaveable { mutableStateOf(false) }
    var reminder by remember { mutableStateOf(InjectionReminder.isEnabled(context)) }
    val editors = rememberEditors()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text("Mehr", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))

        Section("Ziele") {
            if (data.milestones.isEmpty()) {
                Text("Eigene Zielgewichte erscheinen als gestrichelte Linie im Diagramm, mit einer Prognose auf der Startseite.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            data.milestones.forEach { m ->
                MetricRow(m.label.ifBlank { "Ziel" }, oneDecimal(m.targetKg) + " kg", onClick = { editingMilestone = m })
            }
            TextButton(onClick = { addingMilestone = true }) { Text("+ Ziel hinzufügen") }
        }

        Section("Fragen für den Arzttermin") {
            val questions = data.dayLogs.filter { it.question.isNotBlank() }.sortedByDescending { it.date }
            if (questions.isEmpty()) {
                Text("Hier sammeln sich Fragen, die du beim nächsten Termin stellen willst.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            questions.forEach { q ->
                MetricRow(q.question, "", q.date.format(DisplayDateFormatter), onClick = { editors.dayLog = q.date })
            }
            TextButton(onClick = { editors.dayLog = LocalDate.now() }) { Text("+ Frage notieren") }
        }

        Section("Erinnerung") {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Spritze fällig", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        InjectionReminder.dueDate(data)?.let { "Nächste: " + it.format(WeekdayDateFormatter) + ", 9:00 Uhr" }
                            ?: "Startet nach der ersten Spritze",
                        style = MaterialTheme.typography.bodySmall, color = Muted
                    )
                }
                Switch(checked = reminder, onCheckedChange = {
                    reminder = it
                    InjectionReminder.setEnabled(context, it, data)
                })
            }
            HorizontalDivider(color = Hairline)
        }

        BackupSection(data, viewModel, notify)

        Section("Über") {
            MetricRow("Version", BuildConfig.VERSION_NAME)
            Text(
                "Alle Daten bleiben auf diesem Gerät; die App hat keinen Internetzugriff. Updates behalten die Daten. Vor einer Deinstallation oder einem Handywechsel eine Sicherung exportieren.",
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    if (addingMilestone) {
        MilestoneEditorDialog(
            item = null,
            onDismiss = { addingMilestone = false },
            onSave = { target, label -> viewModel.addMilestone(target, label) { notify(it, "Ziel gespeichert."); if (it.isSuccess) addingMilestone = false } }
        )
    }
    editingMilestone?.let { m ->
        MilestoneEditorDialog(
            item = m,
            onDismiss = { editingMilestone = null },
            onSave = { target, label -> viewModel.updateMilestone(m.id, target, label) { notify(it, "Ziel geändert."); if (it.isSuccess) editingMilestone = null } },
            onDelete = { viewModel.deleteMilestone(m.id) { notify(it, "Ziel gelöscht."); if (it.isSuccess) editingMilestone = null } }
        )
    }
    RecordEditorDialogs(data, editors, viewModel, notify)
}

@Composable
private fun BackupSection(
    data: AppData,
    viewModel: MainViewModel,
    notify: (Result<Unit>, String) -> Unit
) {
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<Pair<String, AppData>?>(null) }

    fun write(uri: android.net.Uri?, text: String, message: String) {
        if (uri == null) return
        val result = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                ?: error("Die Datei konnte nicht geöffnet werden.")
        }
        notify(result.map { }, message)
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        write(it, BackupCodec.toJson(data), "Sicherung exportiert.")
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        write(it, BackupCodec.toCsv(data), "CSV exportiert.")
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Die Datei konnte nicht gelesen werden.")
                raw to BackupCodec.fromJson(raw)
            }.onSuccess { pendingRestore = it }.onFailure { notify(Result.failure(it), "") }
        }
    }

    Section("Sicherung & Export") {
        MetricRow("Sicherung exportieren (JSON)", "›", onClick = { exportJson.launch("mounjaro-log-sicherung-" + LocalDate.now() + ".json") })
        MetricRow("Tabelle exportieren (CSV)", "›", onClick = { exportCsv.launch("mounjaro-log-daten-" + LocalDate.now() + ".csv") })
        MetricRow("Sicherung wiederherstellen", "›", onClick = { importJson.launch(arrayOf("application/json", "text/plain")) })
        Text(
            "Zusätzlich speichert die App automatisch die letzten fünf Stände intern. Vor dem Wiederherstellen wird der aktuelle Stand gesichert.",
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp)
        )
    }

    pendingRestore?.let { (raw, preview) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Sicherung wiederherstellen?") },
            text = {
                Text(
                    "Die Sicherung enthält ${preview.weights.size} Wägungen, ${preview.injections.size} Spritzen, " +
                        "${preview.dayLogs.size} Notiztage und ${preview.milestones.size} Ziele. Sie ersetzt die aktuellen Daten."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.restoreBackup(raw) { notify(it, "Sicherung wiederhergestellt."); if (it.isSuccess) pendingRestore = null }
                }) { Text("Wiederherstellen") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Abbrechen") } }
        )
    }
}
