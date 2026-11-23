package dev.khalil.mounjarolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.khalil.mounjarolog.model.DayLog
import dev.khalil.mounjarolog.model.Injection
import dev.khalil.mounjarolog.model.InjectionSites
import dev.khalil.mounjarolog.model.Milestone
import dev.khalil.mounjarolog.model.SupportedDoses
import dev.khalil.mounjarolog.model.WeightMeasurement
import java.time.LocalDate

private val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

@Composable
fun WeightEditorDialog(
    item: WeightMeasurement,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double, String) -> Unit,
    onDelete: () -> Unit
) {
    var dateText by rememberSaveable(item.id) { mutableStateOf(item.date.toString()) }
    var weightText by rememberSaveable(item.id) { mutableStateOf(oneDecimal(item.weightKg)) }
    var note by rememberSaveable(item.id) { mutableStateOf(item.note) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }
    var error by rememberSaveable(item.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gewicht bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DatePickerButton(LocalDate.parse(dateText), { dateText = it.toString() })
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Gewicht (kg)") },
                    keyboardOptions = decimal,
                    singleLine = true
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz") }, minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { confirmDelete = true }) { Text("Gewicht löschen") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = parseDecimal(weightText)
                if (value == null) error = "Bitte ein gültiges Gewicht eingeben."
                else onSave(LocalDate.parse(dateText), value, note)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Dieses Gewicht löschen?",
            message = "Die Messung verschwindet aus Verlauf und Diagrammen.",
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete
        )
    }
}

@Composable
fun InjectionEditorDialog(
    item: Injection,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var dateText by rememberSaveable(item.id) { mutableStateOf(item.date.toString()) }
    var dose by rememberSaveable(item.id) { mutableStateOf(item.doseMg) }
    var site by rememberSaveable(item.id) { mutableStateOf(item.injectionSite) }
    var note by rememberSaveable(item.id) { mutableStateOf(item.note) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spritze bearbeiten") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DatePickerButton(LocalDate.parse(dateText), { dateText = it.toString() })
                ChoiceChips(SupportedDoses, dose, { formatDose(it) }) { dose = it }
                ChoiceChips(InjectionSites, site, { it }) { site = it }
                OutlinedTextField(value = site, onValueChange = { site = it }, label = { Text("Einstichstelle") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz") }, minLines = 2)
                TextButton(onClick = { confirmDelete = true }) { Text("Spritze löschen") }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(LocalDate.parse(dateText), dose, site, note) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Diese Spritze löschen?",
            message = "Zyklen und Dosis-Auswertungen werden neu berechnet.",
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete
        )
    }
}

/** Appetite, side effects, waist, note and a question for the doctor, all for one calendar day. */
@Composable
fun DayLogEditorDialog(
    initialDate: LocalDate,
    item: DayLog?,
    onDismiss: () -> Unit,
    onSave: (DayLog) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val key = item?.id to initialDate
    var appetite by rememberSaveable(key) { mutableStateOf(item?.appetite) }
    var sideEffects by rememberSaveable(key) { mutableStateOf(item?.sideEffects.orEmpty()) }
    var waistText by rememberSaveable(key) { mutableStateOf(item?.waistCm?.let(::oneDecimal).orEmpty()) }
    var note by rememberSaveable(key) { mutableStateOf(item?.note.orEmpty()) }
    var question by rememberSaveable(key) { mutableStateOf(item?.question.orEmpty()) }
    var confirmDelete by rememberSaveable(key) { mutableStateOf(false) }
    var error by rememberSaveable(key) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notizen · " + initialDate.format(DisplayDateFormatter)) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DayLogFields(
                    appetite = appetite, onAppetite = { appetite = it },
                    sideEffects = sideEffects, onSideEffects = { sideEffects = it },
                    waistText = waistText, onWaist = { waistText = it },
                    note = note, onNote = { note = it },
                    question = question, onQuestion = { question = it }
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (item != null && onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Notizen dieses Tages löschen") }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val waist = if (waistText.isBlank()) null else parseDecimal(waistText)
                if (waistText.isNotBlank() && waist == null) error = "Bitte einen gültigen Umfang eingeben."
                else onSave(DayLog(item?.id ?: 0, initialDate, appetite, sideEffects, note, waist, question))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )

    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog(
            title = "Notizen löschen?",
            message = "Gewicht und Spritzen dieses Tages bleiben erhalten.",
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete
        )
    }
}

/** Shared by the day-log dialog and the entry sheet. */
@Composable
fun DayLogFields(
    appetite: Int?, onAppetite: (Int?) -> Unit,
    sideEffects: String, onSideEffects: (String) -> Unit,
    waistText: String, onWaist: (String) -> Unit,
    note: String, onNote: (String) -> Unit,
    question: String, onQuestion: (String) -> Unit
) {
    Label("Appetit (1 = kaum, 5 = sehr groß)")
    ChoiceChips(listOf(1, 2, 3, 4, 5), appetite, { it.toString() }) { onAppetite(if (appetite == it) null else it) }
    OutlinedTextField(sideEffects, onSideEffects, label = { Text("Nebenwirkungen") }, minLines = 2)
    OutlinedTextField(
        waistText, onWaist, label = { Text("Taillenumfang (cm)") },
        keyboardOptions = decimal, singleLine = true
    )
    OutlinedTextField(note, onNote, label = { Text("Notiz") }, minLines = 2)
    OutlinedTextField(question, onQuestion, label = { Text("Frage für den Arzttermin") }, minLines = 2)
}

@Composable
fun MilestoneEditorDialog(
    item: Milestone?,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var targetText by rememberSaveable(item?.id) { mutableStateOf(item?.targetKg?.let(::oneDecimal).orEmpty()) }
    var label by rememberSaveable(item?.id) { mutableStateOf(item?.label.orEmpty()) }
    var error by rememberSaveable(item?.id) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable(item?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Ziel hinzufügen" else "Ziel bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Zielgewicht (kg)") },
                    keyboardOptions = decimal,
                    singleLine = true
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Bezeichnung (optional)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (item != null && onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Ziel löschen") }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val target = parseDecimal(targetText)
                if (target == null) error = "Bitte ein gültiges Zielgewicht eingeben."
                else onSave(target, label)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )

    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog(
            title = "Dieses Ziel löschen?",
            message = "Die Ziellinie verschwindet aus dem Diagramm.",
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete
        )
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
