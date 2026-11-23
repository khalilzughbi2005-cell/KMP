package dev.khalil.mounjarolog.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val German: Locale = Locale.GERMANY

val DisplayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM uuuu", German)
val ShortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM", German)
val WeekdayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", German)
val ShortWeekdayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d.M.", German)
val MonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM uuuu", German)

fun formatDose(dose: Double): String =
    (if (dose % 1.0 == 0.0) dose.toInt().toString() else oneDecimal(dose)) + " mg"

fun oneDecimal(value: Double): String = String.format(German, "%.1f", value)

/** Signed with a real minus sign; zero has no sign. */
fun signed(value: Double, suffix: String = ""): String {
    val text = oneDecimal(kotlin.math.abs(value))
    val sign = when {
        text == oneDecimal(0.0) -> ""
        value > 0 -> "+"
        else -> "−"
    }
    return sign + text + suffix
}

/** Accepts "98,6" and "98.6". */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = Muted
    )
}

/** A titled block without a card around it. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 28.dp)) {
        Label(title, Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
fun MetricRow(label: String, value: String, detail: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = if (detail == null) 12.dp else 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(bottom = 12.dp))
        }
        HorizontalDivider(color = Hairline)
    }
}

/** Big number with a caption underneath. */
@Composable
fun StatBlock(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
fun EmptyState(text: String) {
    Text(text, color = Muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 16.dp))
}

/** Pill choices; the selected one is filled ink. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Ink else Fill)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(label(option), style = MaterialTheme.typography.labelLarge, color = if (on) Paper else Ink)
            }
        }
    }
}

/** iOS-like segmented control in the Plakat style. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Fill)
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            val on = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Paper else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Ink else Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Full-width accent button for the one main action of a screen. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Accent else Faint)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Paper, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun DatePickerButton(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    label: String = date.format(DisplayDateFormatter)
) {
    val context = LocalContext.current
    TextButton(onClick = {
        DatePickerDialog(
            context,
            { _, year, month, day -> onDateChange(LocalDate.of(year, month + 1, day)) },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth
        ).show()
    }) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}
