package dev.khalil.mounjarolog.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val DisplayDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM uuuu", Locale.getDefault())

val MonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM uuuu", Locale.getDefault())

fun formatDose(dose: Double): String =
    if (dose % 1.0 == 0.0) dose.toInt().toString() + " mg"
    else oneDecimal(dose) + " mg"

fun oneDecimal(value: Double): String =
    String.format(Locale.getDefault(), "%.1f", value)

fun signed(value: Double, suffix: String = ""): String =
    (if (value > 0) "+" else "") + oneDecimal(value) + suffix

fun doseColor(dose: Double?): Color = when (dose) {
    5.0 -> Color(0xFF2E7D32)
    7.5 -> Color(0xFF1976D2)
    10.0 -> Color(0xFF6A1B9A)
    12.5 -> Color(0xFFEF6C00)
    15.0 -> Color(0xFFC62828)
    else -> Color(0xFF757575)
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

@Composable
fun DoseDot(dose: Double?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(doseColor(dose), RoundedCornerShape(50))
    )
}

@Composable
fun DatePickerButton(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    label: String = date.format(DisplayDateFormatter)
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    onDateChange(LocalDate.of(year, month + 1, day))
                },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth
            ).show()
        }
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null)
        Text("  " + label)
    }
}
