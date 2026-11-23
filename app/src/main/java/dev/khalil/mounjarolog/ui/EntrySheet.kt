package dev.khalil.mounjarolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.khalil.mounjarolog.MainViewModel
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.DayLog
import dev.khalil.mounjarolog.model.InjectionSites
import dev.khalil.mounjarolog.model.SupportedDoses
import dev.khalil.mounjarolog.model.latestInjection
import dev.khalil.mounjarolog.model.latestWeight
import dev.khalil.mounjarolog.model.suggestNextSite
import java.time.LocalDate

enum class EntryTab(val label: String) { WEIGHT("Gewicht"), DOSE("Spritze"), NOTES("Notiz") }

data class EntryRequest(val date: LocalDate, val tab: EntryTab, val withDose: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    request: EntryRequest,
    data: AppData,
    busy: Boolean,
    viewModel: MainViewModel,
    notify: (Result<Unit>, String) -> Unit,
    onClose: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(request.tab) }
    var dateText by rememberSaveable { mutableStateOf(request.date.toString()) }
    val date = LocalDate.parse(dateText)
    val lastWeight = data.latestWeight()
    val lastDose = data.latestInjection()?.doseMg

    var weightText by rememberSaveable { mutableStateOf(oneDecimal(lastWeight?.weightKg ?: 90.0)) }
    var dose by rememberSaveable { mutableStateOf(if (request.withDose || request.tab == EntryTab.DOSE) lastDose ?: SupportedDoses.first() else null) }
    var site by rememberSaveable { mutableStateOf(suggestNextSite(data.injections)) }
    var doseNote by rememberSaveable { mutableStateOf("") }

    val existing = data.dayLogs.lastOrNull { it.date == date }
    var appetite by rememberSaveable(dateText) { mutableStateOf(existing?.appetite) }
    var sideEffects by rememberSaveable(dateText) { mutableStateOf(existing?.sideEffects.orEmpty()) }
    var waistText by rememberSaveable(dateText) { mutableStateOf(existing?.waistCm?.let(::oneDecimal).orEmpty()) }
    var note by rememberSaveable(dateText) { mutableStateOf(existing?.note.orEmpty()) }
    var question by rememberSaveable(dateText) { mutableStateOf(existing?.question.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun done(result: Result<Unit>, message: String) {
        notify(result, message)
        if (result.isSuccess) onClose()
    }

    fun save() {
        error = null
        when (tab) {
            EntryTab.WEIGHT -> {
                val weight = parseDecimal(weightText)
                if (weight == null) error = "Bitte ein gültiges Gewicht eingeben."
                else viewModel.addEntry(date, weight, dose, if (dose != null) site else "") {
                    done(it, if (dose != null) "Gewicht und Spritze gespeichert." else "Gewicht gespeichert.")
                }
            }
            EntryTab.DOSE -> viewModel.addEntry(date, null, dose ?: lastDose ?: SupportedDoses.first(), site, doseNote) {
                done(it, "Spritze gespeichert.")
            }
            EntryTab.NOTES -> {
                val waist = if (waistText.isBlank()) null else parseDecimal(waistText)
                if (waistText.isNotBlank() && waist == null) error = "Bitte einen gültigen Umfang eingeben."
                else viewModel.upsertDayLog(DayLog(existing?.id ?: 0, date, appetite, sideEffects, note, waist, question)) {
                    done(it, "Notiz gespeichert.")
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Paper
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Segmented(EntryTab.entries.map { it.label }, tab.ordinal, { tab = EntryTab.entries[it]; error = null })
            DatePickerButton(
                date = date,
                onDateChange = { dateText = it.toString() },
                label = (if (date == LocalDate.now()) "Heute, " + date.format(ShortDateFormatter) else date.format(WeekdayDateFormatter)) + " · ändern"
            )

            when (tab) {
                EntryTab.WEIGHT -> {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        BasicTextField(
                            value = weightText,
                            onValueChange = { weightText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                            textStyle = MaterialTheme.typography.displayMedium.copy(color = Ink, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.width(220.dp)
                        )
                        Text("kg", style = MaterialTheme.typography.titleMedium, color = Muted, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    lastWeight?.let { last ->
                        val delta = parseDecimal(weightText)?.let { it - last.weightKg }
                        Text(
                            "zuletzt " + oneDecimal(last.weightKg) + " am " + last.date.format(ShortDateFormatter) +
                                (delta?.let { " · " + signed(it) } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf(-0.1, 0.1).forEach { step ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Fill)
                                    .clickable(role = Role.Button) {
                                        parseDecimal(weightText)?.let { weightText = oneDecimal(it + step) }
                                    }
                                    .padding(horizontal = 26.dp, vertical = 12.dp)
                            ) {
                                Text((if (step < 0) "− " else "+ ") + "0,1", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    Label("Heute auch gespritzt?", Modifier.padding(top = 28.dp, bottom = 8.dp))
                    ChoiceChips(listOf<Double?>(null) + SupportedDoses, dose, { it?.let(::formatDose) ?: "Nein" }) { dose = it }
                    if (dose != null) {
                        Label("Einstichstelle", Modifier.padding(top = 20.dp, bottom = 8.dp))
                        ChoiceChips(InjectionSites, site, { it }) { site = it }
                    }
                }

                EntryTab.DOSE -> {
                    Label("Dosis", Modifier.padding(top = 16.dp, bottom = 8.dp))
                    ChoiceChips(SupportedDoses, dose ?: lastDose, { formatDose(it) }) { dose = it }
                    Label("Einstichstelle · Vorschlag: " + suggestNextSite(data.injections), Modifier.padding(top = 20.dp, bottom = 8.dp))
                    ChoiceChips(InjectionSites, site, { it }) { site = it }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(doseNote, { doseNote = it }, Modifier.fillMaxWidth(), label = { Text("Notiz (optional)") })
                }

                EntryTab.NOTES -> Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DayLogFields(
                        appetite = appetite, onAppetite = { appetite = it },
                        sideEffects = sideEffects, onSideEffects = { sideEffects = it },
                        waistText = waistText, onWaist = { waistText = it },
                        note = note, onNote = { note = it },
                        question = question, onQuestion = { question = it }
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            AccentButton("Speichern", ::save, Modifier.fillMaxWidth().padding(top = 28.dp), enabled = !busy)
        }
    }
}
