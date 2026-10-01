package dev.khalil.mounjarolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.khalil.mounjarolog.model.DayLog
import dev.khalil.mounjarolog.model.Injection
import dev.khalil.mounjarolog.model.Milestone
import dev.khalil.mounjarolog.model.SupportedDoses
import dev.khalil.mounjarolog.model.WeightMeasurement
import java.time.LocalDate

@Composable
fun NewEntryDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (
        date: LocalDate,
        weightKg: Double?,
        doseMg: Double?,
        injectionSite: String,
        appetite: Int?,
        sideEffects: String,
        note: String
    ) -> Unit
) {
    var dateText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var weightText by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf<Double?>(null) }
    var site by rememberSaveable { mutableStateOf("") }
    var appetite by rememberSaveable { mutableStateOf<Int?>(null) }
    var sideEffects by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add entry") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DatePickerButton(
                    date = LocalDate.parse(dateText),
                    onDateChange = { dateText = it.toString() }
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.replace(',', '.') },
                    label = { Text("Weight (kg) — optional") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text("Dose — optional")
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

                TextButton(onClick = { advanced = !advanced }) {
                    Text(if (advanced) "Hide optional details" else "Add optional details")
                }

                if (advanced) {
                    OutlinedTextField(
                        value = site,
                        onValueChange = { site = it },
                        label = { Text("Injection site — optional") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
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
                        label = { Text("Side effects / symptoms — optional") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note — optional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                error?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val weight = if (weightText.isBlank()) null else weightText.toDoubleOrNull()
                    when {
                        weightText.isNotBlank() && weight == null ->
                            error = "Enter a valid weight."
                        weight == null && dose == null && appetite == null &&
                            sideEffects.isBlank() && note.isBlank() ->
                            error = "Add at least one value."
                        else -> onSave(
                            LocalDate.parse(dateText),
                            weight,
                            dose,
                            site,
                            appetite,
                            sideEffects,
                            note
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun WeightEditorDialog(
    item: WeightMeasurement,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double, String) -> Unit,
    onDelete: () -> Unit
) {
    var dateText by rememberSaveable(item.id) { mutableStateOf(item.date.toString()) }
    var weightText by rememberSaveable(item.id) { mutableStateOf(item.weightKg.toString()) }
    var note by rememberSaveable(item.id) { mutableStateOf(item.note) }
    var confirmDelete by rememberSaveable(item.id) { mutableStateOf(false) }
    var error by rememberSaveable(item.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DatePickerButton(LocalDate.parse(dateText), { dateText = it.toString() })
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.replace(',', '.') },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    minLines = 2
                )
                error?.let { Text(it) }
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete weight") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val value = weightText.toDoubleOrNull()
                if (value == null) error = "Enter a valid weight."
                else onSave(LocalDate.parse(dateText), value, note)
            }) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete this weight?",
            message = "This removes the measurement from your history and graphs.",
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
        title = { Text("Edit injection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DatePickerButton(LocalDate.parse(dateText), { dateText = it.toString() })
                Text("Dose")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SupportedDoses) { itemDose ->
                        FilterChip(
                            selected = dose == itemDose,
                            onClick = { dose = itemDose },
                            label = { Text(formatDose(itemDose)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = site,
                    onValueChange = { site = it },
                    label = { Text("Injection site — optional") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    minLines = 2
                )
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete injection") }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(LocalDate.parse(dateText), dose, site, note)
            }) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete this injection?",
            message = "Dose-window colours and dose statistics will be recalculated.",
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete
        )
    }
}

@Composable
fun DayLogEditorDialog(
    initialDate: LocalDate,
    item: DayLog?,
    onDismiss: () -> Unit,
    onSave: (Int?, String, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var appetite by rememberSaveable(item?.id, initialDate) { mutableStateOf(item?.appetite) }
    var sideEffects by rememberSaveable(item?.id, initialDate) { mutableStateOf(item?.sideEffects.orEmpty()) }
    var note by rememberSaveable(item?.id, initialDate) { mutableStateOf(item?.note.orEmpty()) }
    var confirmDelete by rememberSaveable(item?.id, initialDate) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily notes · " + initialDate.format(DisplayDateFormatter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    label = { Text("Side effects / symptoms") },
                    minLines = 2
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    minLines = 2
                )
                if (item != null && onDelete != null) {
                    OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete daily note") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(appetite, sideEffects, note) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog(
            title = "Delete this daily note?",
            message = "The weight and injection records for the day are not affected.",
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete
        )
    }
}

@Composable
fun MilestoneEditorDialog(
    item: Milestone?,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var targetText by rememberSaveable(item?.id) { mutableStateOf(item?.targetKg?.toString().orEmpty()) }
    var label by rememberSaveable(item?.id) { mutableStateOf(item?.label.orEmpty()) }
    var error by rememberSaveable(item?.id) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable(item?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add milestone" else "Edit milestone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.replace(',', '.') },
                    label = { Text("Target weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label — optional") },
                    singleLine = true
                )
                error?.let { Text(it) }
                if (item != null && onDelete != null) {
                    OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete milestone") }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val target = targetText.toDoubleOrNull()
                if (target == null) error = "Enter a valid target weight."
                else onSave(target, label)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog(
            title = "Delete this milestone?",
            message = "The milestone line will be removed from your graph.",
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
        confirmButton = {
            Button(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
